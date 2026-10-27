package com.mygym.log.running

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.mygym.log.GymApplication
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35])
@LooperMode(LooperMode.Mode.PAUSED)
class RunTrackingServiceTest {
    private fun waitFor(condition: ()->Boolean) {
        val deadline=System.nanoTime()+10_000_000_000
        while(System.nanoTime()<deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if(condition())return
            Thread.sleep(20)
        }
        fail("Tracking service did not reach expected state")
    }
    @Test fun simulatedGpsPersistsPointsAndNotificationPauseStopsTracking() {
        val app=ApplicationProvider.getApplicationContext<GymApplication>()
        runBlocking { app.ready.await();app.repository.dao.clearRuns() }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller=Robolectric.buildService(RunTrackingService::class.java).create()
        val service=controller.get()
        val manager=service.getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        try {
            service.onStartCommand(Intent(app,RunTrackingService::class.java).setAction(RunTrackingService.START),0,1)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            waitFor { runBlocking { app.repository.dao.activeRun()?.state=="RUNNING" } }
            waitFor { shadowOf(manager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty() }
            val location=Location(LocationManager.GPS_PROVIDER).apply {
                latitude=14.5995;longitude=120.9842;accuracy=5f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()
            }
            shadowOf(manager).simulateLocation(location)
            waitFor { runBlocking { app.repository.dao.allRunPoints().size==1 } }
            service.onStartCommand(Intent(app,RunTrackingService::class.java).setAction(RunTrackingService.PAUSE),0,2)
            waitFor { runBlocking { app.repository.dao.activeRun()?.state=="PAUSED" } }
            val count=runBlocking{app.repository.dao.allRunPoints().size}
            shadowOf(manager).simulateLocation(location.apply{latitude+=.001})
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(count,runBlocking{app.repository.dao.allRunPoints().size})
        } finally { controller.destroy() }
    }
    @Test fun walkingWithoutStepPermissionStillTracksGps() {
        val app=ApplicationProvider.getApplicationContext<GymApplication>()
        runBlocking{app.ready.await();app.repository.dao.clearRuns()}
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val controller=Robolectric.buildService(RunTrackingService::class.java).create()
        val manager=controller.get().getSystemService(LocationManager::class.java)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,true)
        try {
            controller.get().onStartCommand(Intent(app,RunTrackingService::class.java).setAction(RunTrackingService.START).putExtra("activityType","WALK"),0,1)
            waitFor {runBlocking{app.repository.dao.activeRun()?.state=="RUNNING"}}
            waitFor {runBlocking {app.repository.dao.activeRun()?.stepStatus==(if(android.os.Build.VERSION.SDK_INT>=29)"DENIED" else "UNAVAILABLE")} }
            val walk=runBlocking{app.repository.dao.activeRun()!!}
            assertEquals("WALK",walk.activityType);assertEquals(0L,walk.steps)
            assertEquals(if(android.os.Build.VERSION.SDK_INT>=29)"DENIED" else "UNAVAILABLE",walk.stepStatus)
            assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        } finally {controller.destroy()}
    }
    @Test fun missingPrecisePermissionDoesNotCreateARun() {
        val app=ApplicationProvider.getApplicationContext<GymApplication>()
        runBlocking{app.ready.await();app.repository.dao.clearRuns()}
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller=Robolectric.buildService(RunTrackingService::class.java).create()
        try {
            controller.get().onStartCommand(Intent(app,RunTrackingService::class.java).setAction(RunTrackingService.START),0,1)
            assertNotNull(app.runError.value)
            assertNull(runBlocking{app.repository.dao.activeRun()})
        } finally { controller.destroy() }
    }
}
