package com.mygym.log.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mygym.log.domain.Rules
import com.mygym.log.domain.RunMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import java.io.InputStream

object BackupValidator {
    const val MAX_BYTES = 50 * 1024 * 1024
    val json = Json { prettyPrint = true; encodeDefaults = true }
    fun decode(stream: InputStream): Backup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            require(output.size() + read <= MAX_BYTES) { "Backup exceeds the 50 MiB limit." }
            output.write(buffer, 0, read)
        }
        val bytes = output.toByteArray()
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: error("Backup must be a JSON object.")
        fun fields(obj: JsonObject, expected: String) {
            require(obj.keys.containsAll(expected.split(','))) { "Backup is missing required fields." }
        }
        fields(root,"schemaVersion,exportedAt,data,preferences")
        val version=root.getValue("schemaVersion").jsonPrimitive.int
        require(version in 1..4) { "Unsupported backup schema version." }
        val data=root["data"] as? JsonObject ?: error("Missing backup data.")
        fields(data,"exercises,routines,routineExercises,workouts,workoutExercises,sets")
        if(version>=2)fields(data,"runs,runPoints")
        if(version>=3)fields(data,"bodyMeasurements")
        val pref=root["preferences"] as? JsonObject ?: error("Missing preferences.")
        fields(pref,"unit,theme,restSeconds,autoRest")
        val tables=mutableMapOf("exercises" to "id,name,muscle,equipment,tracking,archived", "routines" to "id,name,position",
            "routineExercises" to "id,routineId,exerciseId,position,targetSets,targetReps,restSeconds",
            "workouts" to "id,name,startedAt,finishedAt,activeSlot,notes,timerEnd,timerRemaining,timerDuration",
            "workoutExercises" to "id,workoutId,exerciseId,nameSnapshot,trackingSnapshot,position,notes,restSeconds",
            "sets" to "id,workoutExerciseId,position,weightKg,reps,warmup,completed")
        if(version>=2) {
            tables["runs"]="id,name,startedAt,finishedAt,state,activeSlot,durationMs,lastTickElapsedMs,distanceMeters,segment,status"
            tables["runPoints"]="id,runId,sequence,segment,latitude,longitude,accuracyMeters,timestamp,elapsedRealtimeMs"
        }
        if(version>=3) {
            tables["routineExercises"]="id,routineId,exerciseId,position,targetSets,targetReps,restSeconds,targetWeightKg"
            tables["bodyMeasurements"]="id,recordedAt,weightKg,heightCm,notes"
        }
        if(version>=4) tables["runs"]=tables.getValue("runs")+",activityType,steps,stepStatus"
        tables.forEach { (key, expected) ->
            val rows=data[key] as? JsonArray ?: error("Invalid backup table: $key.")
            rows.forEach { fields(it as? JsonObject ?: error("Invalid backup record."),expected) }
        }
        return json.decodeFromJsonElement<Backup>(root).also(::validate)
    }
    fun validate(b: Backup) {
        require(b.schemaVersion in 1..4) { "Unsupported backup schema version." }
        require(b.exportedAt > 0) { "Invalid export timestamp." }
        Rules.preferences(b.preferences)
        val d=b.data
        require(b.schemaVersion!=1 || (d.runs.isEmpty() && d.runPoints.isEmpty())) { "Running records need backup schema 2." }
        require(b.schemaVersion>=3 || (d.bodyMeasurements.isEmpty() && d.routineExercises.all{it.targetWeightKg==0.0})) { "Body measurements and plan loads need backup schema 3." }
        fun ids(values: List<String>) { require(values.size <= 100000 && values.all { it.isNotBlank() && it.length <= 100 } && values.distinct().size == values.size) { "Invalid or duplicate IDs." } }
        ids(d.exercises.map{it.id}); ids(d.routines.map{it.id}); ids(d.routineExercises.map{it.id}); ids(d.workouts.map{it.id}); ids(d.workoutExercises.map{it.id}); ids(d.sets.map{it.id})
        val ex=d.exercises.map{it.id}.toSet(); val routines=d.routines.map{it.id}.toSet(); val workouts=d.workouts.map{it.id}.toSet(); val we=d.workoutExercises.map{it.id}.toSet()
        d.exercises.forEach { Rules.text(it.name,"Exercise name"); Rules.text(it.muscle,"Muscle group"); Rules.text(it.equipment,"Equipment"); require(it.tracking in listOf("WEIGHTED","BODYWEIGHT")) }
        d.routines.forEach { Rules.text(it.name,"Routine name"); require(it.position>=0) }
        d.routineExercises.forEach { require(it.routineId in routines && it.exerciseId in ex && it.position>=0) { "Broken routine relationship." }; Rules.targets(it.targetSets,it.targetReps,it.restSeconds);Rules.set(it.targetWeightKg,it.targetReps) }
        ids(d.bodyMeasurements.map{it.id});d.bodyMeasurements.forEach{Rules.body(it)}
        require(d.workouts.count { it.activeSlot == 1 } <= 1) { "Multiple active workouts." }
        d.workouts.forEach {
            Rules.text(it.name,"Workout name"); require(it.notes.length<=10000)
            require(it.startedAt>0 && (it.finishedAt == null || it.finishedAt>=it.startedAt)) { "Invalid workout timestamps." }
            require((it.finishedAt == null && it.activeSlot == 1) || (it.finishedAt != null && it.activeSlot == null)) { "Invalid active workout state." }
            require(it.timerDuration in 1000..3600000 && it.timerRemaining in 0..it.timerDuration && (it.timerEnd == null || it.timerEnd>0)) { "Invalid timer state." }
            require(it.activeSlot == 1 || (it.timerEnd == null && it.timerRemaining == 0L))
        }
        d.workoutExercises.forEach { require(it.workoutId in workouts && it.exerciseId in ex && it.position>=0) { "Broken workout relationship." }; Rules.text(it.nameSnapshot,"Exercise snapshot"); require(it.trackingSnapshot in listOf("WEIGHTED","BODYWEIGHT")); require(it.notes.length<=10000 && it.restSeconds in 1..4600) }
        d.sets.forEach { require(it.workoutExerciseId in we && it.position>=0) { "Broken set relationship." }; Rules.set(it.weightKg,it.reps) }
        fun <T> ordering(rows: List<T>, group: (T)->String, position: (T)->Int) { require(rows.groupBy(group).values.all { list -> list.map(position).distinct().size == list.size }) { "Duplicate ordering positions." } }
        require(d.routines.map{it.position}.distinct().size==d.routines.size) { "Duplicate routine positions." }
        ordering(d.routineExercises,{it.routineId},{it.position}); ordering(d.workoutExercises,{it.workoutId},{it.position}); ordering(d.sets,{it.workoutExerciseId},{it.position})
        ids(d.runs.map{it.id});ids(d.runPoints.map{it.id})
        require(d.runs.count{it.activeSlot==1}<=1) { "Multiple active runs." }
        val runs=d.runs.associateBy{it.id}
        val grouped=d.runPoints.groupBy{it.runId}
        require(grouped.keys.all{it in runs}) { "Orphaned run points." }
        d.runPoints.forEach { require(RunMath.valid(it) && it.sequence>=0 && it.segment>=0) { "Invalid run point." } }
        d.runs.forEach { run ->
            require(run.activityType in listOf("RUN","WALK") && run.steps in 0..100000000 && run.stepStatus in listOf("UNAVAILABLE","DENIED","COUNTING")) { "Invalid walking data." }
            require(b.schemaVersion>=4 || (run.activityType=="RUN" && run.steps==0L && run.stepStatus=="UNAVAILABLE")) { "Walking data needs backup schema 4." }
            Rules.text(run.name,"Run name");require(run.status.length<=250)
            require(run.startedAt>0 && (run.finishedAt==null || run.finishedAt>=run.startedAt)) { "Invalid run timestamps." }
            require(run.state in listOf("RUNNING","PAUSED","FINISHED") && run.segment>=0) { "Invalid run state." }
            require(if(run.state=="FINISHED")run.activeSlot==null && run.finishedAt!=null else run.activeSlot==1 && run.finishedAt==null)
            require(if(run.state=="RUNNING")run.lastTickElapsedMs!=null && run.lastTickElapsedMs>=0 else run.lastTickElapsedMs==null)
            require(run.durationMs in 0..RunMath.MAX_DURATION_MS && run.distanceMeters.isFinite() && run.distanceMeters>=0) { "Invalid running metrics." }
            val points=grouped[run.id].orEmpty().sortedBy{it.sequence}
            require(points.map{it.sequence}==points.indices.toList()) { "Missing or duplicate route ordering." }
            require(points.all{it.segment<=run.segment}) { "Invalid route segment." }
            require(points.zipWithNext().all{(a,b)->b.segment>=a.segment && (b.segment!=a.segment || b.elapsedRealtimeMs>a.elapsedRealtimeMs)}) { "Invalid route timestamps or segments." }
            require(points.zipWithNext().all{(a,b)-> a.segment!=b.segment ||
                (b.elapsedRealtimeMs-a.elapsedRealtimeMs<=RunMath.MAX_GAP_MS && RunMath.distance(a,b)/((b.elapsedRealtimeMs-a.elapsedRealtimeMs)/1000.0)<=12.0001)}) { "Implausible route interval." }
            require(kotlin.math.abs(run.distanceMeters-RunMath.routeDistance(points))<.1) { "Run distance does not match its route." }
        }
    }
}

class BackupService(private val repo: GymRepository, private val resolver: ContentResolver) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val b=repo.lock.withLock { repo.db.withTransaction { Backup(data=repo.dao.snapshot(),preferences=repo.preferences.flow.first()) } }
        BackupValidator.validate(b)
        // File providers and JSON encoding must not block the GPS writer's mutex.
        val bytes=BackupValidator.json.encodeToString(b).toByteArray()
        require(bytes.size<=BackupValidator.MAX_BYTES) { "Data exceeds the 50 MiB backup limit." }
        resolver.openOutputStream(uri,"wt")?.use{it.write(bytes)} ?: error("Unable to open destination.")
    }
    suspend fun read(uri: Uri): Backup = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use(BackupValidator::decode) ?: error("Unable to open backup.") }
    suspend fun restore(b: Backup) = withContext(Dispatchers.IO) {
        BackupValidator.validate(b)
        repo.lock.withLock {
            require(repo.dao.activeRun()?.state!="RUNNING") { "Pause your running session before restoring a backup." }
            // DataStore and SQLite cannot share a transaction. Roll back preferences on a database failure.
            val previous=repo.preferences.flow.first()
            repo.preferences.save(b.preferences)
            try {
                repo.db.withTransaction {
                    val dao=repo.dao; dao.clearBodyMeasurements();dao.clearRuns();dao.clearWorkouts(); dao.clearRoutines(); dao.clearExercises()
                    b.data.exercises.forEach { dao.put(it) }; b.data.routines.forEach { dao.put(it) }
                    b.data.routineExercises.forEach { dao.put(it) }; b.data.workouts.forEach { dao.put(it) }
                    b.data.workoutExercises.forEach { dao.put(it) }; b.data.sets.forEach { dao.put(it) }
                    b.data.runs.forEach { dao.put(if(it.activeSlot==1)it.copy(state="PAUSED",lastTickElapsedMs=null,status="Restored run. Resume when ready.") else it) }
                    b.data.runPoints.forEach { dao.insert(it) }
                    b.data.bodyMeasurements.forEach { dao.put(it) }
                }
            } catch(e: Exception) { repo.preferences.save(previous); throw e }
            repo.preferences.markSeeded()
        }
    }
}
