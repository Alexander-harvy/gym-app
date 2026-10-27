package com.mygym.log.data

import androidx.room.withTransaction
import com.mygym.log.domain.RunMath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RunRepository(private val db: GymDatabase, private val lock: Mutex) {
    private val dao=db.dao()
    suspend fun recoverInterrupted() = lock.withLock { db.withTransaction {
        dao.activeRun()?.takeIf{it.state=="RUNNING"}?.let {
            dao.put(it.copy(state="PAUSED",lastTickElapsedMs=null,status="Tracking was interrupted. Saved data is safe; resume when ready."))
        }
    } }
    suspend fun start(now: Long, elapsedNow: Long, activityType: String = "RUN"): String = lock.withLock { db.withTransaction {
        val existing=dao.activeRun()
        if(existing!=null) return@withTransaction existing.id
        require(activityType in listOf("RUN","WALK"))
        val run=Run(activityType=activityType,name=if(activityType=="WALK")"Outdoor walk" else "Outdoor run",startedAt=now,state="RUNNING",lastTickElapsedMs=elapsedNow,status="Waiting for an accurate GPS fix. Head outdoors.")
        dao.put(run);run.id
    } }
    suspend fun resume(id: String, elapsedNow: Long) = lock.withLock { db.withTransaction {
        val run=dao.run(id) ?: error("Run no longer exists.")
        require(run.activeSlot==1) { "This run is already finished." }
        if(run.state=="PAUSED") dao.put(run.copy(state="RUNNING",lastTickElapsedMs=elapsedNow,segment=run.segment+1,status="Waiting for an accurate GPS fix."))
    } }
    suspend fun checkpoint(id: String, elapsedNow: Long) = lock.withLock { db.withTransaction {
        dao.run(id)?.takeIf{it.state=="RUNNING"}?.let { dao.put(it.copy(durationMs=RunMath.elapsed(it,elapsedNow),lastTickElapsedMs=elapsedNow)) }
    } }
    suspend fun pause(id: String, elapsedNow: Long, status: String="Paused") = lock.withLock { db.withTransaction {
        dao.run(id)?.takeIf{it.state=="RUNNING"}?.let {
            dao.put(it.copy(state="PAUSED",durationMs=RunMath.elapsed(it,elapsedNow),lastTickElapsedMs=null,status=status))
        }
    } }
    suspend fun status(id: String, message: String) = lock.withLock { db.withTransaction {
        dao.run(id)?.takeIf{it.state=="RUNNING"}?.let { dao.put(it.copy(status=message.take(250))) }
    } }
    suspend fun record(id: String, point: RunPoint, elapsedNow: Long): Boolean = lock.withLock { db.withTransaction {
        val run=dao.run(id)?.takeIf{it.state=="RUNNING"} ?: return@withTransaction false
        val last=dao.lastRunPoint(id)
        val candidate=point.copy(runId=id,sequence=(last?.sequence ?: -1)+1)
        val fix=RunMath.accept(candidate,last,run.segment,elapsedNow) ?: return@withTransaction false
        dao.insert(fix.point)
        dao.put(run.copy(distanceMeters=run.distanceMeters+fix.addedMeters,segment=fix.point.segment,
            durationMs=RunMath.elapsed(run,elapsedNow),lastTickElapsedMs=elapsedNow,status="GPS recording · accuracy ±${point.accuracyMeters.toInt()} m"))
        true
    } }
    suspend fun finish(id: String, now: Long, elapsedNow: Long) = lock.withLock { db.withTransaction {
        val run=dao.run(id) ?: error("Run no longer exists.")
        if(run.activeSlot!=1) return@withTransaction
        require(run.durationMs>0 || RunMath.elapsed(run,elapsedNow)>0) { "Record some running time before finishing." }
        dao.put(run.copy(state="FINISHED",activeSlot=null,finishedAt=now.coerceAtLeast(run.startedAt),
            durationMs=RunMath.elapsed(run,elapsedNow),lastTickElapsedMs=null,status="Finished"))
    } }
    suspend fun steps(id: String, delta: Long, status: String) = lock.withLock { db.withTransaction {
        dao.run(id)?.takeIf { it.state=="RUNNING" && it.activityType=="WALK" }?.let {
            require(delta in 0..10000)
            dao.put(it.copy(steps=(it.steps+delta).coerceAtMost(100000000),stepStatus=status))
        }
    } }
    suspend fun delete(id: String) = lock.withLock { dao.deleteRun(id) }
}
