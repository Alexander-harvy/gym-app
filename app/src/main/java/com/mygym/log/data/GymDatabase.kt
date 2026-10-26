package com.mygym.log.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Dao
interface GymDao {
    @Query("SELECT * FROM Exercise ORDER BY name") fun exercises(): Flow<List<Exercise>>
    @Query("SELECT * FROM Routine ORDER BY position, id") fun routines(): Flow<List<Routine>>
    @Query("SELECT * FROM RoutineExercise ORDER BY position, id") fun routineExercises(): Flow<List<RoutineExercise>>
    @Query("SELECT * FROM Workout ORDER BY startedAt DESC") fun workouts(): Flow<List<Workout>>
    @Query("SELECT * FROM WorkoutExercise ORDER BY position, id") fun workoutExercises(): Flow<List<WorkoutExercise>>
    @Query("SELECT * FROM WorkoutSet ORDER BY position, id") fun sets(): Flow<List<WorkoutSet>>
    @Query("SELECT * FROM Exercise") suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM Routine") suspend fun allRoutines(): List<Routine>
    @Query("SELECT * FROM RoutineExercise") suspend fun allRoutineExercises(): List<RoutineExercise>
    @Query("SELECT * FROM Workout") suspend fun allWorkouts(): List<Workout>
    @Query("SELECT * FROM WorkoutExercise") suspend fun allWorkoutExercises(): List<WorkoutExercise>
    @Query("SELECT * FROM WorkoutSet") suspend fun allSets(): List<WorkoutSet>
    @Query("SELECT * FROM Workout WHERE activeSlot = 1 LIMIT 1") suspend fun active(): Workout?
    @Query("SELECT * FROM Workout WHERE id = :id") suspend fun workout(id: String): Workout?
    @Query("SELECT * FROM WorkoutSet WHERE id = :id") suspend fun set(id: String): WorkoutSet?
    @Query("SELECT * FROM WorkoutExercise WHERE id = :id") suspend fun workoutExercise(id: String): WorkoutExercise?
    @Upsert suspend fun put(value: Exercise)
    @Upsert suspend fun put(value: Routine)
    @Upsert suspend fun put(value: RoutineExercise)
    @Upsert suspend fun put(value: Workout)
    @Upsert suspend fun put(value: WorkoutExercise)
    @Upsert suspend fun put(value: WorkoutSet)
    @Query("DELETE FROM Routine WHERE id = :id") suspend fun deleteRoutine(id: String)
    @Query("DELETE FROM RoutineExercise WHERE id = :id") suspend fun deleteRoutineExercise(id: String)
    @Query("DELETE FROM Workout WHERE id = :id") suspend fun deleteWorkout(id: String)
    @Query("DELETE FROM WorkoutExercise WHERE id = :id") suspend fun deleteWorkoutExercise(id: String)
    @Query("DELETE FROM WorkoutSet WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM Workout") suspend fun clearWorkouts()
    @Query("DELETE FROM Routine") suspend fun clearRoutines()
    @Query("DELETE FROM Exercise") suspend fun clearExercises()
    @Query("SELECT * FROM Run ORDER BY startedAt DESC") fun runs(): Flow<List<Run>>
    @Query("SELECT * FROM RunPoint WHERE runId = :id ORDER BY sequence") fun pointsForRun(id: String): Flow<List<RunPoint>>
    @Query("SELECT * FROM Run ORDER BY startedAt DESC") suspend fun allRuns(): List<Run>
    @Query("SELECT * FROM RunPoint ORDER BY runId, sequence") suspend fun allRunPoints(): List<RunPoint>
    @Query("SELECT * FROM Run WHERE activeSlot = 1 LIMIT 1") suspend fun activeRun(): Run?
    @Query("SELECT * FROM Run WHERE id = :id") suspend fun run(id: String): Run?
    @Query("SELECT * FROM RunPoint WHERE runId = :id ORDER BY sequence DESC LIMIT 1") suspend fun lastRunPoint(id: String): RunPoint?
    @Upsert suspend fun put(value: Run)
    @Insert suspend fun insert(value: RunPoint)
    @Query("DELETE FROM Run WHERE id = :id") suspend fun deleteRun(id: String)
    @Query("DELETE FROM Run") suspend fun clearRuns()
}

@Database(entities = [Exercise::class, Routine::class, RoutineExercise::class, Workout::class, WorkoutExercise::class, WorkoutSet::class, Run::class, RunPoint::class], version = 3, exportSchema = true)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao
    companion object {
        // Version 2 adds a history-ordering index without changing any saved records.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE INDEX IF NOT EXISTS index_Workout_startedAt ON Workout (startedAt)") }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Run` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, `state` TEXT NOT NULL, `activeSlot` INTEGER, `durationMs` INTEGER NOT NULL, `lastTickElapsedMs` INTEGER, `distanceMeters` REAL NOT NULL, `segment` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Run_startedAt` ON `Run` (`startedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Run_activeSlot` ON `Run` (`activeSlot`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `RunPoint` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `segment` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracyMeters` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `elapsedRealtimeMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `Run` (`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_RunPoint_runId_sequence` ON `RunPoint` (`runId`, `sequence`)")
            }
        }
    }
}

fun GymDao.observe(): Flow<Snapshot> = combine(exercises(), routines(), routineExercises(), workouts(), workoutExercises(), sets(), runs()) { values ->
    @Suppress("UNCHECKED_CAST")
    Snapshot(values[0] as List<Exercise>, values[1] as List<Routine>, values[2] as List<RoutineExercise>,
        values[3] as List<Workout>, values[4] as List<WorkoutExercise>, values[5] as List<WorkoutSet>, values[6] as List<Run>)
}

suspend fun GymDao.snapshot() = Snapshot(allExercises(), allRoutines(), allRoutineExercises(), allWorkouts(), allWorkoutExercises(), allSets(), allRuns(), allRunPoints())
