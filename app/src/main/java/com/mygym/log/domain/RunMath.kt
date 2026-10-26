package com.mygym.log.domain

import com.mygym.log.data.Run
import com.mygym.log.data.RunPoint
import kotlin.math.*

object RunMath {
    const val MAX_ACCURACY = 35.0
    const val MAX_GAP_MS = 30_000L
    const val MAX_DURATION_MS = 365L * 24 * 60 * 60 * 1000
    fun distance(a: RunPoint, b: RunPoint): Double {
        val lat1=Math.toRadians(a.latitude); val lat2=Math.toRadians(b.latitude)
        val dLat=lat2-lat1; val dLon=Math.toRadians(b.longitude-a.longitude)
        val h=sin(dLat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dLon/2).pow(2)
        return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    fun elapsed(run: Run, elapsedNow: Long): Long = (run.durationMs +
        if(run.state=="RUNNING") (elapsedNow-(run.lastTickElapsedMs ?: elapsedNow)).coerceAtLeast(0) else 0).coerceAtMost(MAX_DURATION_MS)
    fun paceSecondsPerKm(distanceMeters: Double, durationMs: Long): Double? =
        if(distanceMeters>=10 && durationMs>0) durationMs.toDouble()/distanceMeters else null
    fun valid(point: RunPoint) = point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
        point.longitude.isFinite() && point.longitude in -180.0..180.0 && point.accuracyMeters.isFinite() &&
        point.accuracyMeters in 0.0..MAX_ACCURACY && point.timestamp>0 && point.elapsedRealtimeMs>=0

    data class Fix(val point: RunPoint, val addedMeters: Double)
    fun accept(candidate: RunPoint, last: RunPoint?, segment: Int, elapsedNow: Long): Fix? {
        if(!valid(candidate) || elapsedNow-candidate.elapsedRealtimeMs !in -2000L..15000L) return null
        if(last==null || last.segment!=segment) return Fix(candidate.copy(segment=segment),0.0)
        val delta=candidate.elapsedRealtimeMs-last.elapsedRealtimeMs
        if(delta<=0) return null
        // Break the route at signal gaps rather than counting a straight-line shortcut.
        if(delta>MAX_GAP_MS) return Fix(candidate.copy(segment=segment+1),0.0)
        val meters=distance(last,candidate)
        if(meters/(delta/1000.0)>12.0) return null // obvious GPS jumps, not running speed
        if(meters<max(3.0,min(last.accuracyMeters,candidate.accuracyMeters)*.3)) return null
        return Fix(candidate.copy(segment=segment),meters)
    }
    fun routeDistance(points: List<RunPoint>): Double = points.zipWithNext().sumOf { (a,b) -> if(a.segment==b.segment)distance(a,b) else 0.0 }
}
