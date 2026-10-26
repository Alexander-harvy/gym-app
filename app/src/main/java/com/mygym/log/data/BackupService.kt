package com.mygym.log.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.mygym.log.domain.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.InputStream

object BackupValidator {
    const val MAX_BYTES = 10 * 1024 * 1024
    val json = Json { prettyPrint = true; encodeDefaults = true }
    fun decode(stream: InputStream): Backup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            require(output.size() + read <= MAX_BYTES) { "Backup exceeds the 10 MiB limit." }
            output.write(buffer, 0, read)
        }
        val bytes = output.toByteArray()
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: error("Backup must be a JSON object.")
        fun fields(obj: JsonObject, expected: String) {
            require(obj.keys.containsAll(expected.split(','))) { "Backup is missing required fields." }
        }
        fields(root,"schemaVersion,exportedAt,data,preferences")
        val data=root["data"] as? JsonObject ?: error("Missing backup data.")
        fields(data,"exercises,routines,routineExercises,workouts,workoutExercises,sets")
        val pref=root["preferences"] as? JsonObject ?: error("Missing preferences.")
        fields(pref,"unit,theme,restSeconds,autoRest")
        mapOf("exercises" to "id,name,muscle,equipment,tracking,archived", "routines" to "id,name,position",
            "routineExercises" to "id,routineId,exerciseId,position,targetSets,targetReps,restSeconds",
            "workouts" to "id,name,startedAt,finishedAt,activeSlot,notes,timerEnd,timerRemaining,timerDuration",
            "workoutExercises" to "id,workoutId,exerciseId,nameSnapshot,trackingSnapshot,position,notes,restSeconds",
            "sets" to "id,workoutExerciseId,position,weightKg,reps,warmup,completed").forEach { (key, expected) ->
            val rows=data[key] as? JsonArray ?: error("Invalid backup table: $key.")
            rows.forEach { fields(it as? JsonObject ?: error("Invalid backup record."),expected) }
        }
        return json.decodeFromJsonElement<Backup>(root).also(::validate)
    }
    fun validate(b: Backup) {
        require(b.schemaVersion == 1) { "Unsupported backup schema version." }
        require(b.exportedAt > 0) { "Invalid export timestamp." }
        Rules.preferences(b.preferences)
        val d=b.data
        fun ids(values: List<String>) { require(values.size <= 100000 && values.all { it.isNotBlank() && it.length <= 100 } && values.distinct().size == values.size) { "Invalid or duplicate IDs." } }
        ids(d.exercises.map{it.id}); ids(d.routines.map{it.id}); ids(d.routineExercises.map{it.id}); ids(d.workouts.map{it.id}); ids(d.workoutExercises.map{it.id}); ids(d.sets.map{it.id})
        val ex=d.exercises.map{it.id}.toSet(); val routines=d.routines.map{it.id}.toSet(); val workouts=d.workouts.map{it.id}.toSet(); val we=d.workoutExercises.map{it.id}.toSet()
        d.exercises.forEach { Rules.text(it.name,"Exercise name"); Rules.text(it.muscle,"Muscle group"); Rules.text(it.equipment,"Equipment"); require(it.tracking in listOf("WEIGHTED","BODYWEIGHT")) }
        d.routines.forEach { Rules.text(it.name,"Routine name"); require(it.position>=0) }
        d.routineExercises.forEach { require(it.routineId in routines && it.exerciseId in ex && it.position>=0) { "Broken routine relationship." }; Rules.targets(it.targetSets,it.targetReps,it.restSeconds) }
        require(d.workouts.count { it.activeSlot == 1 } <= 1) { "Multiple active workouts." }
        d.workouts.forEach {
            Rules.text(it.name,"Workout name"); require(it.notes.length<=10000)
            require(it.startedAt>0 && (it.finishedAt == null || it.finishedAt>=it.startedAt)) { "Invalid workout timestamps." }
            require((it.finishedAt == null && it.activeSlot == 1) || (it.finishedAt != null && it.activeSlot == null)) { "Invalid active workout state." }
            require(it.timerDuration in 1000..3600000 && it.timerRemaining in 0..it.timerDuration && (it.timerEnd == null || it.timerEnd>0)) { "Invalid timer state." }
            require(it.activeSlot == 1 || (it.timerEnd == null && it.timerRemaining == 0L))
        }
        d.workoutExercises.forEach { require(it.workoutId in workouts && it.exerciseId in ex && it.position>=0) { "Broken workout relationship." }; Rules.text(it.nameSnapshot,"Exercise snapshot"); require(it.trackingSnapshot in listOf("WEIGHTED","BODYWEIGHT")); require(it.notes.length<=10000 && it.restSeconds in 1..3600) }
        d.sets.forEach { require(it.workoutExerciseId in we && it.position>=0) { "Broken set relationship." }; Rules.set(it.weightKg,it.reps) }
        fun <T> ordering(rows: List<T>, group: (T)->String, position: (T)->Int) { require(rows.groupBy(group).values.all { list -> list.map(position).distinct().size == list.size }) { "Duplicate ordering positions." } }
        require(d.routines.map{it.position}.distinct().size==d.routines.size) { "Duplicate routine positions." }
        ordering(d.routineExercises,{it.routineId},{it.position}); ordering(d.workoutExercises,{it.workoutId},{it.position}); ordering(d.sets,{it.workoutExerciseId},{it.position})
    }
}

class BackupService(private val repo: GymRepository, private val resolver: ContentResolver) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        repo.lock.withLock {
            val b=repo.db.withTransaction { Backup(data=repo.dao.snapshot(),preferences=repo.preferences.flow.first()) }
            val bytes=BackupValidator.json.encodeToString(b).toByteArray()
            require(bytes.size<=BackupValidator.MAX_BYTES) { "Data exceeds the 10 MiB backup limit." }
            resolver.openOutputStream(uri,"wt")?.use{it.write(bytes)} ?: error("Unable to open destination.")
        }
    }
    suspend fun read(uri: Uri): Backup = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use(BackupValidator::decode) ?: error("Unable to open backup.") }
    suspend fun restore(b: Backup) = withContext(Dispatchers.IO) {
        BackupValidator.validate(b)
        repo.lock.withLock {
            // DataStore and SQLite cannot share a transaction. Roll back preferences on a database failure.
            val previous=repo.preferences.flow.first()
            repo.preferences.save(b.preferences)
            try {
                repo.db.withTransaction {
                    val dao=repo.dao; dao.clearWorkouts(); dao.clearRoutines(); dao.clearExercises()
                    b.data.exercises.forEach { dao.put(it) }; b.data.routines.forEach { dao.put(it) }
                    b.data.routineExercises.forEach { dao.put(it) }; b.data.workouts.forEach { dao.put(it) }
                    b.data.workoutExercises.forEach { dao.put(it) }; b.data.sets.forEach { dao.put(it) }
                }
            } catch(e: Exception) { repo.preferences.save(previous); throw e }
            repo.preferences.markSeeded()
        }
    }
}
