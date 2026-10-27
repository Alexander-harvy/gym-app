package com.mygym.log

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RetiredMapCleanupTest {
    @Test fun removesRetiredTilesWithoutTouchingActivityStorage() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        kotlinx.coroutines.runBlocking {(context as GymApplication).ready.await()}
        val tiles=File(context.filesDir,"maps/manila/tiles/test.pbf")
        tiles.parentFile!!.mkdirs();tiles.writeText("retired map")
        val unrelated=File(context.filesDir,"personal-records-test.json").apply{writeText("keep")}
        val db=context.getDatabasePath("cleanup-sentinel.db").apply{parentFile!!.mkdirs();writeText("keep database")}
        context.getSharedPreferences("offline-maps",0).edit().putBoolean("manila-removed",true).commit()
        try {
            assertTrue(RetiredMapCleanup.run(context))
            assertFalse(File(context.filesDir,"maps").exists())
            assertEquals("keep",unrelated.readText())
            assertEquals("keep database",db.readText())
            assertTrue(context.getSharedPreferences("offline-maps",0).all.isEmpty())
            assertTrue(RetiredMapCleanup.run(context))
        } finally {unrelated.delete();db.delete()}
    }
}
