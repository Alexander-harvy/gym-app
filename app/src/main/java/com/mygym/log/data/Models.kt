package com.mygym.log.data

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity
data class Exercise(@PrimaryKey val id: String = newId(), val name: String, val muscle: String,
    val equipment: String, val tracking: String = "WEIGHTED", val archived: Boolean = false)

@Serializable
@Entity
data class Routine(@PrimaryKey val id: String = newId(), val name: String, val position: Int = 0)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("routineId"), Index("exerciseId")])
data class RoutineExercise(@PrimaryKey val id: String = newId(), val routineId: String, val exerciseId: String,
    val position: Int, val targetSets: Int = 3, val targetReps: Int = 8, val restSeconds: Int = 90)

@Serializable
@Entity(indices = [Index("finishedAt"), Index("startedAt"), Index(value = ["activeSlot"], unique = true)])
data class Workout(@PrimaryKey val id: String = newId(), val name: String, val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null, val activeSlot: Int? = 1, val notes: String = "",
    val timerEnd: Long? = null, val timerRemaining: Long = 0, val timerDuration: Long = 90000)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("workoutId"), Index("exerciseId")])
data class WorkoutExercise(@PrimaryKey val id: String = newId(), val workoutId: String, val exerciseId: String,
    val nameSnapshot: String, val trackingSnapshot: String, val position: Int, val notes: String = "", val restSeconds: Int = 90)

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = WorkoutExercise::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")])
data class WorkoutSet(@PrimaryKey val id: String = newId(), val workoutExerciseId: String, val position: Int,
    val weightKg: Double = 0.0, val reps: Int = 8, val warmup: Boolean = false, val completed: Boolean = false)

@Serializable
data class Snapshot(val exercises: List<Exercise> = emptyList(), val routines: List<Routine> = emptyList(),
    val routineExercises: List<RoutineExercise> = emptyList(), val workouts: List<Workout> = emptyList(),
    val workoutExercises: List<WorkoutExercise> = emptyList(), val sets: List<WorkoutSet> = emptyList(),
    val runs: List<Run> = emptyList(), val runPoints: List<RunPoint> = emptyList())

@Serializable
data class Preferences(val unit: String = "kg", val theme: String = "DARK", val restSeconds: Int = 90,
    val autoRest: Boolean = true)

@Serializable
data class Backup(val schemaVersion: Int = 2, val exportedAt: Long = System.currentTimeMillis(),
    val data: Snapshot, val preferences: Preferences)

@Serializable
@Entity(indices = [Index("startedAt"), Index(value = ["activeSlot"], unique = true)])
data class Run(@PrimaryKey val id: String = newId(), val name: String = "Outdoor run",
    val startedAt: Long = System.currentTimeMillis(), val finishedAt: Long? = null,
    val state: String = "PAUSED", val activeSlot: Int? = 1,
    val durationMs: Long = 0, val lastTickElapsedMs: Long? = null,
    val distanceMeters: Double = 0.0, val segment: Int = 0, val status: String = "Ready to start")

@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Run::class, parentColumns = ["id"], childColumns = ["runId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["runId", "sequence"], unique = true)])
data class RunPoint(@PrimaryKey val id: String = newId(), val runId: String, val sequence: Int, val segment: Int,
    val latitude: Double, val longitude: Double, val accuracyMeters: Double,
    val timestamp: Long, val elapsedRealtimeMs: Long)
