package com.mygym.log.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mygym.log.GymApplication
import com.mygym.log.data.RunPoint
import com.mygym.log.maps.*
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.geometry.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.annotations.MarkerOptions

private class OfflineStreetMapView(context: android.content.Context): MapView(context) {
    private var downX=0f;private var downY=0f
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN){downX=event.x;downY=event.y}
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=android.view.MotionEvent.ACTION_UP && event.actionMasked!=android.view.MotionEvent.ACTION_CANCEL)
        val result=super.dispatchTouchEvent(event)
        if(event.actionMasked==android.view.MotionEvent.ACTION_UP && kotlin.math.abs(event.x-downX)<8*resources.displayMetrics.density && kotlin.math.abs(event.y-downY)<8*resources.displayMetrics.density)performClick()
        return result
    }
    override fun performClick(): Boolean=super.performClick()
}

@Composable fun OfflineMapControls(points: List<RunPoint>) {
    val maps=(LocalContext.current.applicationContext as GymApplication).offlineMaps
    val areas by maps.areas.collectAsStateWithLifecycle()
    val busy by maps.downloading.collectAsStateWithLifecycle()
    val status by maps.message.collectAsStateWithLifecycle()
    var lat by remember {mutableStateOf("14.5995")};var lon by remember{mutableStateOf("120.9842")}
    var deleting by remember{mutableStateOf<MapArea?>(null)}
    Disclosure("Offline running areas") {
        Text(status,style=MaterialTheme.typography.bodySmall)
        Text("Manila City and Metro Manila are included. Other downloads cover about 10 × 10 km. Wi-Fi recommended; runs and steps stay on this phone.",style=MaterialTheme.typography.bodySmall)
        TextButton({maps.restoreIncluded()},enabled=!busy){Text("Restore included Manila maps")}
        areas.forEach { area -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(area.name,modifier=Modifier.weight(1f));TextButton({deleting=area},enabled=!busy){Text("Remove")}
        } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat,{lat=it},label={Text("Latitude")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(lon,{lon=it},label={Text("Longitude")},modifier=Modifier.weight(1f),singleLine=true)
        }
        points.lastOrNull()?.let { p -> TextButton({lat=p.latitude.toString();lon=p.longitude.toString()}){Text("Use latest route location")} }
        Action("Download running area",!busy){maps.download(lat.toDoubleOrNull() ?: Double.NaN,lon.toDoubleOrNull() ?: Double.NaN)}
        if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());TextButton({maps.cancel()}){Text("Cancel download")}}
    }
    deleting?.let { area -> Confirm("Remove ${area.name}?","Only the street map is removed. Saved activities and routes remain. Use Restore included Manila maps to bring back the included maps without internet.",busy,{deleting=null},{maps.delete(area);deleting=null}) }
}

@Composable fun StreetRouteView(points: List<RunPoint>) {
    val context=LocalContext.current
    val maps=(context.applicationContext as GymApplication).offlineMaps
    val areas by maps.areas.collectAsStateWithLifecycle()
    val area=areas.sortedBy { it.east-it.west }.firstOrNull { a -> points.isEmpty() && a.id=="manila" || points.isNotEmpty() && points.all {a.contains(it.latitude,it.longitude)} }
    var sketch by remember{mutableStateOf(false)}
    var failed by remember(area?.id){mutableStateOf(false)}
    if(area==null || sketch || failed) {
        RouteView(points)
        Text(if(area==null)"Outside downloaded coverage · route sketch" else if(failed)"Street map unavailable · route sketch" else "Route sketch",style=MaterialTheme.typography.bodySmall)
        if(area!=null)TextButton({sketch=false;failed=false}){Text("Show street map")}
        return
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val view=remember(area.id) {runCatching {MapLibre.getInstance(context);OfflineStreetMapView(context).apply {onCreate(null)}}.getOrNull()}
    if(view==null){RouteView(points);Text("Street map unavailable on this device");return}
    var map by remember(view){mutableStateOf<MapLibreMap?>(null)}
    var ready by remember(view){mutableStateOf(false)}
    DisposableEffect(view,lifecycle) {
        val failure=MapView.OnDidFailLoadingMapListener {failed=true}
        view.addOnDidFailLoadingMapListener(failure)
        val observer=LifecycleEventObserver {_,event -> when(event) {
            Lifecycle.Event.ON_START->view.onStart();Lifecycle.Event.ON_RESUME->view.onResume()
            Lifecycle.Event.ON_PAUSE->view.onPause();Lifecycle.Event.ON_STOP->view.onStop();else->Unit
        } }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))view.onStart()
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
        view.getMapAsync {m -> map=m;m.uiSettings.isAttributionEnabled=false;m.uiSettings.isLogoEnabled=false
            m.setStyle(Style.Builder().fromJson(maps.style(area))) {ready=true}
        }
        onDispose {lifecycle.removeObserver(observer);view.removeOnDidFailLoadingMapListener(failure);view.onPause();view.onStop();view.onDestroy()}
    }
    fun fit(m: MapLibreMap) {
        if(points.size>1) m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map{LatLng(it.latitude,it.longitude)}).build(),40))
        else m.moveCamera(CameraUpdateFactory.newLatLngZoom(points.firstOrNull()?.let{LatLng(it.latitude,it.longitude)} ?: LatLng((area.north+area.south)/2,(area.west+area.east)/2),if(points.isEmpty())11.0 else 15.0))
    }
    var fitted by remember(view){mutableStateOf(false)}
    LaunchedEffect(ready,points) {val m=map ?: return@LaunchedEffect;if(!ready)return@LaunchedEffect
        m.clear()
        points.groupBy{it.segment}.values.filter{it.size>1}.forEach { segment -> m.addPolyline(PolylineOptions().addAll(segment.map {LatLng(it.latitude,it.longitude)}).color(android.graphics.Color.rgb(255,214,0)).width(4f)) }
        points.firstOrNull()?.let {m.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title("Start"))}
        points.lastOrNull()?.let {m.addMarker(MarkerOptions().position(LatLng(it.latitude,it.longitude)).title("Latest / finish"))}
        if(!fitted){fit(m);fitted=true}
    }
    AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(300.dp))
    TextButton({context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright")))}) { Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap",style=MaterialTheme.typography.labelSmall) }
    Row {TextButton({map?.let{fit(it)}}){Text("Fit route")};TextButton({sketch=true}){Text("Route sketch")}}
}
