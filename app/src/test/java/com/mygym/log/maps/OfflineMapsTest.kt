package com.mygym.log.maps

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OfflineMapsTest {
    @Test fun bundledAreasRestoreWithoutNetworkAndStyleReferencesOnlyLocalFiles()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val maps=OfflineMaps(context)
        val areas=withTimeout(20000){maps.areas.first {it.size==2}}
        assertEquals(setOf("manila","metro-manila"),areas.map{it.id}.toSet())
        areas.forEach {area ->
            val dir=maps.directory(area)
            val metadata=JSONObject(java.io.File(dir,"area.json").readText())
            assertEquals(metadata.getInt("tiles"),dir.walkTopDown().count{it.isFile && it.path.contains("tiles")})
            assertTrue(java.io.File(dir,"fonts/0-255.pbf").length()>0)
            val style=JSONObject(maps.style(area))
            assertTrue(style.getString("glyphs").startsWith("file:///"))
            val source=style.getJSONObject("sources").getJSONObject("streets")
            assertEquals(area.maxZoom,source.getInt("maxzoom"))
            assertTrue(source.getJSONArray("tiles").getString(0).startsWith("file:///"))
            assertFalse(maps.style(area).contains("https://"))
        }
        withTimeout(10000){maps.downloading.first{!it}}
        maps.delete(areas.single{it.id=="manila"})
        withTimeout(10000){maps.areas.first{it.size==1}}
        maps.restoreIncluded()
        assertEquals(2,withTimeout(20000){maps.areas.first{it.size==2}}.size)
    }
}
