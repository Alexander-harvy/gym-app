package com.mygym.log.domain

import com.mygym.log.data.*
import org.junit.Assert.*
import org.junit.Test

class RunMathTest {
    private fun point(lat: Double=0.0,lon: Double=0.0,t: Long=1000,accuracy: Double=5.0)=
        RunPoint(runId="r",sequence=0,segment=0,latitude=lat,longitude=lon,accuracyMeters=accuracy,timestamp=1000+t,elapsedRealtimeMs=t)
    @Test fun distanceAndPaceHaveExplicitMetricUnits() {
        assertEquals(111.195,RunMath.distance(point(),point(lat=.001)),.01)
        assertEquals(300.0,RunMath.paceSecondsPerKm(2000.0,600000)!!,.0001)
        assertNull(RunMath.paceSecondsPerKm(0.0,1000));assertNull(RunMath.paceSecondsPerKm(5.0,1000))
    }
    @Test fun filtersInaccurateStaleDuplicateFutureAndJumpingFixes() {
        val first=point()
        assertNull(RunMath.accept(point(lat=.0001,t=6000,accuracy=100.0),first,0,6000))
        assertNull(RunMath.accept(point(t=2000),first,0,30000))
        assertNull(RunMath.accept(point(t=3000),first,0,0))
        assertNull(RunMath.accept(first,first,0,1000))
        assertNull(RunMath.accept(point(lat=.1,t=6000),first,0,6000))
        assertNotNull(RunMath.accept(point(lat=.0001,t=6000),first,0,6000))
    }
    @Test fun gapsAndResumeBeginANewSegmentWithoutAddingDistance() {
        val first=point()
        val gap=RunMath.accept(point(lat=.1,t=40000),first,0,40000)!!
        assertEquals(1,gap.point.segment);assertEquals(0.0,gap.addedMeters,0.0)
        val resumed=RunMath.accept(point(lat=.2,t=100),first,2,100)!!
        assertEquals(2,resumed.point.segment);assertEquals(0.0,resumed.addedMeters,0.0)
        assertEquals(0.0,RunMath.routeDistance(listOf(first,resumed.point)),0.0)
    }
    @Test fun elapsedUsesMonotonicTimeAndStopsWhilePaused() {
        val run=Run(state="RUNNING",durationMs=5000,lastTickElapsedMs=10000)
        assertEquals(7000L,RunMath.elapsed(run,12000))
        assertEquals(5000L,RunMath.elapsed(run.copy(state="PAUSED",lastTickElapsedMs=null),999999))
        assertEquals(5000L,RunMath.elapsed(run,100))
    }
    @Test fun coordinateValidationAndDatelineDistanceStayFinite() {
        assertFalse(RunMath.valid(point(lat=91.0)));assertFalse(RunMath.valid(point(lon=Double.NaN)))
        assertTrue(RunMath.valid(point(lat=-90.0,lon=180.0)))
        assertEquals(22.239,RunMath.distance(point(lon=179.9999),point(lon=-179.9999)),.01)
    }
}
