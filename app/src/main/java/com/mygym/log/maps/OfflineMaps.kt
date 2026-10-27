package com.mygym.log.maps

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.math.*

data class MapArea(val id: String,val name: String,val west: Double,val south: Double,val east: Double,val north: Double,val maxZoom: Int=14) {
    fun contains(lat: Double,lon: Double)=lat in south..north && lon in west..east
    fun tiles(): List<Triple<Int,Int,Int>> {
        fun x(lon: Double,z: Int)=floor((lon+180)/360*(1 shl z)).toInt()
        fun y(lat: Double,z: Int)=floor((1-asinh(tan(Math.toRadians(lat)))/Math.PI)/2*(1 shl z)).toInt()
        return (10..maxZoom).flatMap { z -> (x(west,z)..x(east,z)).flatMap { xx -> (y(north,z)..y(south,z)).map { yy -> Triple(z,xx,yy) } } }
    }
}

/** Downloads only public map resources. Never accepts or uploads run records. */
class OfflineMaps(private val context: Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val areas=MutableStateFlow<List<MapArea>>(emptyList())
    val message=MutableStateFlow("Preparing offline maps…")
    val downloading=MutableStateFlow(true)
    private var task: Job?=null
    private val root=File(context.filesDir,"maps")
    init { restoreIncluded(false) }
    fun restoreIncluded(restoreRemoved: Boolean=true) {
        if(downloading.value && task!=null)return
        downloading.value=true
        task=scope.launch { try {
        for(bundle in listOf("manila","metro-manila")) {
        if(restoreRemoved)context.getSharedPreferences("offline-maps",0).edit().putBoolean("$bundle-removed",false).apply()
        val target=File(root,bundle)
        if(!File(target,"area.json").exists() && !context.getSharedPreferences("offline-maps",0).getBoolean("$bundle-removed",false)) {
            fun copy(asset: String,dest: File) {
                val children=context.assets.list(asset).orEmpty()
                if(children.isEmpty()) {dest.parentFile?.mkdirs();context.assets.open(asset).use { input -> dest.outputStream().use { input.copyTo(it) } }}
                else {dest.mkdirs();children.filter {it!="area.json"}.forEach {copy("$asset/$it",File(dest,it))};if("area.json" in children)copy("$asset/area.json",File(dest,"area.json"))}
            }
            copy("maps/$bundle",target)
        }
        }
        root.listFiles().orEmpty().filter {it.isDirectory && !File(it,"area.json").exists()}.forEach {it.deleteRecursively()}
        refresh();message.value=if(areas.value.isEmpty())"No downloaded maps. Route sketch is available." else "${areas.value.size} offline areas ready"
    } catch(e: Exception){message.value="Map setup failed. Route sketch is available. ${e.message}"} finally {downloading.value=false} } }
    private fun refresh() {
        areas.value=root.listFiles().orEmpty().mapNotNull { dir -> runCatching {
            val j=JSONObject(File(dir,"area.json").readText())
            MapArea(dir.name,j.getString("name"),j.getDouble("west"),j.getDouble("south"),j.getDouble("east"),j.getDouble("north"),j.optInt("maxZoom",14))
        }.getOrNull() }
    }
    fun directory(area: MapArea)=File(root,area.id)
    fun download(lat: Double,lon: Double) {
        if(downloading.value)return
        if(!lat.isFinite() || !lon.isFinite() || lat !in -80.0..80.0 || lon !in -179.9..179.9) {message.value="Enter a valid map center (latitude −80 to 80).";return}
        // Roughly 10 × 10 km, well below the provider's per-area limit.
        val halfLat=0.045;val halfLon=0.045/cos(Math.toRadians(lat))
        val area=MapArea("area-${System.currentTimeMillis()}","Area %.3f, %.3f".format(java.util.Locale.US,lat,lon),lon-halfLon,lat-halfLat,lon+halfLon,lat+halfLat)
        val tiles=area.tiles()
        if(tiles.size>3000 || area.west < -180 || area.east>180) {message.value="Area is too large.";return}
        downloading.value=true
        task=scope.launch {
            val dir=directory(area);var bytes=0L
            try {
                root.mkdirs();require(root.usableSpace>150L*1024*1024){"At least 150 MB free storage is needed."}
                fun fetch(url: String): ByteArray {
                    val c=URL(url).openConnection().apply {connectTimeout=15000;readTimeout=15000;setRequestProperty("User-Agent","Gym-Android/1.5 (github.com/Alexander-harvy/gym-app)")}
                    return c.getInputStream().use { input ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=5*1024*1024){"Map resource too large"};out.write(buffer,0,count)}
                        out.toByteArray()
                    }
                }
                val template=JSONObject(String(fetch("https://tiles.openfreemap.org/planet"))).getJSONArray("tiles").getString(0)
                tiles.forEachIndexed { index,(z,x,y) ->
                    ensureActive();message.value="Downloading area · ${index+1}/${tiles.size} tiles"
                    var data=fetch(template.replace("{z}","$z").replace("{x}","$x").replace("{y}","$y"))
                    bytes+=data.size;require(bytes<=49_000_000L){"Area exceeds the provider's 50 MB limit."}
                    if(data.size>2 && data[0]==0x1f.toByte() && data[1]==0x8b.toByte())data=java.util.zip.GZIPInputStream(data.inputStream()).use {it.readBytes()}
                    val file=File(dir,"tiles/$z/$x/$y.pbf");file.parentFile?.mkdirs();file.writeBytes(data)
                }
                ensureActive()
                val font=File(dir,"fonts/0-255.pbf");font.parentFile?.mkdirs();font.writeBytes(fetch("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf"))
                // Publish only after every tile and the label font is present.
                File(dir,"area.json").writeText(JSONObject().put("name",area.name).put("west",area.west).put("south",area.south).put("east",area.east).put("north",area.north).put("tiles",tiles.size).put("bytes",bytes).toString())
                root.listFiles().orEmpty().filter {it.isDirectory && !File(it,"area.json").exists()}.forEach {it.deleteRecursively()}
        refresh();message.value="Area saved offline"
            } catch(e: CancellationException) {dir.deleteRecursively();message.value="Download cancelled";throw e}
              catch(e: Exception) {dir.deleteRecursively();message.value="Download failed: ${e.message}. Retry when connected."}
            finally {downloading.value=false}
        }
    }
    fun cancel(){task?.cancel()}
    fun delete(area: MapArea) { if(downloading.value)return;scope.launch {context.getSharedPreferences("offline-maps",0).edit().putBoolean("${area.id}-removed",true).apply();directory(area).deleteRecursively();refresh();message.value="Offline area removed"} }
    fun style(area: MapArea): String {
        val path=directory(area).toURI().toString().replace("file:/","file:///")
        // Entire style is local: no network tile requests, telemetry, sprites or remote fonts.
        return """{"version":8,"name":"Gym offline streets","glyphs":"${path}fonts/{range}.pbf","sources":{"streets":{"type":"vector","tiles":["${path}tiles/{z}/{x}/{y}.pbf"],"minzoom":10,"maxzoom":${area.maxZoom},"bounds":[${area.west},${area.south},${area.east},${area.north}]}},"layers":[
        {"id":"background","type":"background","paint":{"background-color":"#17191c"}},
        {"id":"parks","type":"fill","source":"streets","source-layer":"landuse","paint":{"fill-color":"#26372b"}},
        {"id":"water","type":"fill","source":"streets","source-layer":"water","paint":{"fill-color":"#183544"}},
        {"id":"buildings","type":"fill","source":"streets","source-layer":"building","minzoom":13,"paint":{"fill-color":"#32353b"}},
        {"id":"roads","type":"line","source":"streets","source-layer":"transportation","paint":{"line-color":"#787a7e","line-width":["interpolate",["linear"],["zoom"],10,0.5,14,2,18,7]}},
        {"id":"road-labels","type":"symbol","source":"streets","source-layer":"transportation_name","minzoom":13,"layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:latin"],["get","name"],["get","ref"]],"text-font":["Noto Sans Regular"],"text-size":11},"paint":{"text-color":"#e3e4e5","text-halo-color":"#17191c","text-halo-width":1.5}},
        {"id":"places","type":"symbol","source":"streets","source-layer":"place","layout":{"text-field":["coalesce",["get","name:en"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":13},"paint":{"text-color":"#eeeeee","text-halo-color":"#17191c","text-halo-width":1.5}}
        ]}"""
    }
}
