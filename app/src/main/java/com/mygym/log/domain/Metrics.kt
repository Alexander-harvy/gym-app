package com.mygym.log.domain

import com.mygym.log.data.*
import java.time.*
import java.time.temporal.TemporalAdjusters

object Weight {
    const val LB_PER_KG = 2.2046226218487757
    fun display(kg: Double, unit: String) = if (unit == "lb") kg * LB_PER_KG else kg
    fun canonical(value: Double, unit: String) = if (unit == "lb") value / LB_PER_KG else value
}

object Rules {
    fun text(value: String, label: String, max: Int = 120) {
        require(value.isNotBlank() && value.length <= max) { "$label must contain 1–$max characters." }
    }
    fun set(weightKg: Double, reps: Int) {
        require(weightKg.isFinite() && weightKg in 0.0..10000.0) { "Weight must be between 0 and 10,000 kg." }
        require(reps in 1..1000) { "Reps must be between 1 and 1,000." }
    }
    fun targets(sets: Int, reps: Int, rest: Int) {
        require(sets in 1..50) { "Target sets must be 1–50." }
        require(reps in 1..1000) { "Target reps must be 1–1,000." }
        require(rest in 1..3600) { "Rest must be 1–3,600 seconds." }
    }
    fun preferences(p: Preferences) {
        require(p.unit in listOf("kg", "lb") && p.theme in listOf("DARK", "LIGHT", "SYSTEM")) { "Invalid preferences." }
        require(p.restSeconds in 1..3600) { "Rest must be 1–3,600 seconds." }
    }
}

data class Performance(val set: WorkoutSet, val exercise: WorkoutExercise, val workout: Workout)
object Metrics {
    fun completed(data: Snapshot, exerciseId: String? = null): List<Performance> {
        val workouts = data.workouts.filter { it.finishedAt != null }.associateBy { it.id }
        val exercises = data.workoutExercises.filter { exerciseId == null || it.exerciseId == exerciseId }.associateBy { it.id }
        return data.sets.filter { it.completed && !it.warmup }.mapNotNull { set ->
            val e = exercises[set.workoutExerciseId] ?: return@mapNotNull null
            val w = workouts[e.workoutId] ?: return@mapNotNull null
            Performance(set, e, w)
        }
    }
    fun volume(data: Snapshot, exerciseId: String? = null) = completed(data, exerciseId)
        .filter { it.exercise.trackingSnapshot == "WEIGHTED" }.sumOf { it.set.weightKg * it.set.reps }
    fun heaviest(data: Snapshot, exerciseId: String) = completed(data, exerciseId).maxByOrNull { it.set.weightKg }
    fun weekly(data: Snapshot, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> = data.workouts.filter { it.finishedAt != null }
        .groupingBy { Instant.ofEpochMilli(it.finishedAt!!).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .eachCount().toSortedMap(compareByDescending { it })
    fun remaining(w: Workout, now: Long) = (w.timerEnd?.let { it - now } ?: w.timerRemaining).coerceAtLeast(0)
}
