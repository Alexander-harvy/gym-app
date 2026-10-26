package com.mygym.log.data

import androidx.room.withTransaction
import com.mygym.log.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GymRepository(val db: GymDatabase, val preferences: PreferenceStore) {
    val dao = db.dao()
    val state = dao.observe()
    val lock = Mutex()
    val runs = RunRepository(db, lock)
    suspend fun seed() = lock.withLock {
        if (!preferences.isSeeded.first()) {
            db.withTransaction {
                val names = listOf(Triple("Barbell squat", "Legs", "Barbell"), Triple("Bench press", "Chest", "Barbell"),
                    Triple("Deadlift", "Back", "Barbell"), Triple("Overhead press", "Shoulders", "Barbell"),
                    Triple("Dumbbell row", "Back", "Dumbbell"), Triple("Pull-up", "Back", "Bodyweight"), Triple("Push-up", "Chest", "Bodyweight"))
                names.forEachIndexed { index, (name, muscle, equipment) ->
                    val id = "seed-$index"
                    if (dao.allExercises().none { it.id == id }) dao.put(Exercise(id, name, muscle, equipment, if (equipment == "Bodyweight") "BODYWEIGHT" else "WEIGHTED"))
                }
            }
            preferences.markSeeded()
        }
    }
    suspend fun exercise(e: Exercise) { Rules.text(e.name, "Name"); Rules.text(e.muscle, "Muscle group"); Rules.text(e.equipment, "Equipment"); require(e.tracking in listOf("WEIGHTED", "BODYWEIGHT")); dao.put(e) }
    suspend fun routine(r: Routine) { Rules.text(r.name, "Routine name"); dao.put(r) }
    suspend fun createRoutine(name: String): String = db.withTransaction {
        Rules.text(name,"Plan name")
        val r=Routine(name=name,position=(dao.allRoutines().maxOfOrNull{it.position} ?: -1)+1)
        dao.put(r);r.id
    }
    suspend fun target(r: RoutineExercise) { Rules.targets(r.targetSets, r.targetReps, r.restSeconds); dao.put(r) }
    suspend fun start(routineId: String? = null, name: String? = null, notes: String = ""): String = lock.withLock {
        name?.let { Rules.text(it,"Workout name") }
        require(notes.length<=10000) { "Notes must be at most 10,000 characters." }
        db.withTransaction {
            dao.active()?.let { return@withTransaction it.id }
            val r = dao.allRoutines().find { it.id == routineId }
            val w = Workout(name = name ?: r?.name ?: "Workout", notes=notes, timerDuration = preferences.flow.first().restSeconds * 1000L)
            dao.put(w)
            dao.allRoutineExercises().filter { it.routineId == routineId }.sortedBy { it.position }.forEach { target ->
                val e = dao.allExercises().first { it.id == target.exerciseId }
                val we = WorkoutExercise(workoutId = w.id, exerciseId = e.id, nameSnapshot = e.name, trackingSnapshot = e.tracking, position = target.position, restSeconds = target.restSeconds)
                dao.put(we)
                repeat(target.targetSets) { dao.put(WorkoutSet(workoutExerciseId = we.id, position = it, reps = target.targetReps)) }
            }
            w.id
        }
    }
    suspend fun addExercise(workoutId: String, e: Exercise, targetSets: Int = 1, targetReps: Int = 8, restSeconds: Int? = null) = db.withTransaction {
        val rest=restSeconds ?: preferences.flow.first().restSeconds
        Rules.targets(targetSets,targetReps,rest)
        require(dao.workout(workoutId) != null) { "Workout no longer exists." }
        val we = WorkoutExercise(workoutId = workoutId, exerciseId = e.id, nameSnapshot = e.name, trackingSnapshot = e.tracking,
            position = (dao.allWorkoutExercises().filter { it.workoutId == workoutId }.maxOfOrNull { it.position } ?: -1) + 1,
            restSeconds = rest)
        dao.put(we)
        repeat(targetSets) { dao.put(WorkoutSet(workoutExerciseId = we.id, position = it, reps=targetReps)) }
    }
    suspend fun addCustomExercise(workoutId: String, e: Exercise, sets: Int, reps: Int, rest: Int) = db.withTransaction {
        Rules.targets(sets,reps,rest)
        exercise(e)
        addExercise(workoutId,e,sets,reps,rest)
    }
    suspend fun addRoutineExercise(routineId: String, e: Exercise, sets: Int, reps: Int, rest: Int, custom: Boolean=false) = db.withTransaction {
        require(dao.allRoutines().any { it.id==routineId }) { "Routine no longer exists." }
        Rules.targets(sets,reps,rest)
        if(custom)exercise(e)
        val position=(dao.allRoutineExercises().filter{it.routineId==routineId}.maxOfOrNull{it.position} ?: -1)+1
        target(RoutineExercise(routineId=routineId,exerciseId=e.id,position=position,targetSets=sets,targetReps=reps,restSeconds=rest))
    }
    suspend fun saveAsRoutine(workoutId: String, name: String): String = db.withTransaction {
        Rules.text(name,"Plan name")
        require(dao.workout(workoutId)!=null) { "Workout no longer exists." }
        val exercises=dao.allWorkoutExercises().filter{it.workoutId==workoutId}.sortedBy{it.position}
        require(exercises.isNotEmpty()) { "Add at least one exercise before saving a plan." }
        val sets=dao.allSets().groupBy{it.workoutExerciseId}
        val routine=Routine(name=name,position=(dao.allRoutines().maxOfOrNull{it.position} ?: -1)+1)
        dao.put(routine)
        exercises.forEachIndexed { index,e ->
            val working=sets[e.id].orEmpty().filter{!it.warmup}.sortedBy{it.position}
            target(RoutineExercise(routineId=routine.id,exerciseId=e.exerciseId,position=index,
                targetSets=working.size.coerceAtLeast(1),targetReps=working.firstOrNull()?.reps ?: 8,restSeconds=e.restSeconds))
        }
        routine.id
    }
    suspend fun exerciseRest(id: String, rest: Int) = db.withTransaction {
        Rules.targets(1,1,rest)
        val e=dao.workoutExercise(id) ?: error("Exercise no longer exists.")
        dao.put(e.copy(restSeconds=rest))
    }
    suspend fun addSet(e: WorkoutExercise) = db.withTransaction {
        require(dao.workoutExercise(e.id) != null) { "Exercise no longer exists." }
        val last = dao.allSets().filter { it.workoutExerciseId == e.id }.maxByOrNull { it.position }
        dao.put(WorkoutSet(workoutExerciseId = e.id, position = (last?.position ?: -1) + 1, weightKg = last?.weightKg ?: 0.0, reps = last?.reps ?: 8))
    }
    suspend fun saveSet(s: WorkoutSet) = db.withTransaction {
        Rules.set(s.weightKg, s.reps)
        val old = dao.set(s.id) ?: error("Set no longer exists.")
        dao.put(s)
        val e = dao.workoutExercise(s.workoutExerciseId) ?: error("Exercise no longer exists.")
        val w = dao.workout(e.workoutId) ?: error("Workout no longer exists.")
        if (s.completed && !old.completed && w.activeSlot == 1 && preferences.flow.first().autoRest) {
            val duration = e.restSeconds * 1000L
            dao.put(w.copy(timerEnd = System.currentTimeMillis() + duration, timerRemaining = duration, timerDuration = duration))
        }
    }
    suspend fun workoutText(id: String, name: String, notes: String) = db.withTransaction {
        Rules.text(name, "Workout name"); require(notes.length <= 10000) { "Notes must be at most 10,000 characters." }
        dao.workout(id)?.let { dao.put(it.copy(name = name, notes = notes)) }
    }
    suspend fun exerciseNotes(id: String, notes: String) = db.withTransaction {
        require(notes.length <= 10000) { "Notes must be at most 10,000 characters." }
        dao.workoutExercise(id)?.let { dao.put(it.copy(notes = notes)) }
    }
    suspend fun finish(id: String) = db.withTransaction {
        val w = dao.workout(id) ?: error("Workout no longer exists.")
        if (w.activeSlot == 1) {
            require(dao.allSets().any { s -> s.completed && dao.allWorkoutExercises().any { it.id == s.workoutExerciseId && it.workoutId == id } }) { "Complete at least one set before finishing." }
            dao.put(w.copy(finishedAt = System.currentTimeMillis(), activeSlot = null, timerEnd = null, timerRemaining = 0))
        }
    }
    suspend fun timer(id: String, action: String) = db.withTransaction {
        val w = dao.workout(id) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.put(when(action) {
            "start" -> w.copy(timerEnd = now + w.timerDuration, timerRemaining = w.timerDuration)
            "pause" -> w.copy(timerEnd = null, timerRemaining = Metrics.remaining(w, now))
            "resume" -> w.copy(timerEnd = now + w.timerRemaining)
            "reset" -> w.copy(timerEnd = null, timerRemaining = w.timerDuration)
            else -> w.copy(timerEnd = null, timerRemaining = 0)
        })
    }
    suspend fun move(kind: String, id: String, delta: Int) = db.withTransaction {
        when(kind) {
            "routine" -> { val rows = dao.allRoutines().sortedWith(compareBy<Routine> { it.position }.thenBy { it.id }); val i = rows.indexOfFirst { it.id == id }; val j = i + delta
                if(i >= 0 && j in rows.indices) { val list = rows.toMutableList(); java.util.Collections.swap(list,i,j); list.forEachIndexed { p,r -> dao.put(r.copy(position=p)) } } }
            "target" -> { val row = dao.allRoutineExercises().first { it.id == id }; val rows = dao.allRoutineExercises().filter { it.routineId == row.routineId }.sortedWith(compareBy<RoutineExercise>{it.position}.thenBy{it.id}); val i=rows.indexOfFirst{it.id==id}; val j=i+delta
                if(j in rows.indices) { val list=rows.toMutableList(); java.util.Collections.swap(list,i,j); list.forEachIndexed{p,r->dao.put(r.copy(position=p))} } }
            "exercise" -> { val row=dao.workoutExercise(id) ?: return@withTransaction; val rows=dao.allWorkoutExercises().filter{it.workoutId==row.workoutId}.sortedWith(compareBy<WorkoutExercise>{it.position}.thenBy{it.id}); val i=rows.indexOfFirst{it.id==id}; val j=i+delta
                if(j in rows.indices) {val list=rows.toMutableList();java.util.Collections.swap(list,i,j);list.forEachIndexed{p,r->dao.put(r.copy(position=p))}} }
        }
    }
}
