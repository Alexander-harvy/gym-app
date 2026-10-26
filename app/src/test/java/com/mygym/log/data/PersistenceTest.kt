package com.mygym.log.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: GymDatabase
    private lateinit var repo: GymRepository
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        context.deleteDatabase("persistence-test.db")
        open()
    }
    private fun open() {
        db=Room.databaseBuilder(context,GymDatabase::class.java,"persistence-test.db").addMigrations(GymDatabase.MIGRATION_1_2,GymDatabase.MIGRATION_2_3,GymDatabase.MIGRATION_3_4).build()
        repo=GymRepository(db,PreferenceStore(context))
    }
    private fun legacy(version: Int) {
        val sql=db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE RoutineExercise_old (id TEXT NOT NULL PRIMARY KEY, routineId TEXT NOT NULL, exerciseId TEXT NOT NULL, position INTEGER NOT NULL, targetSets INTEGER NOT NULL, targetReps INTEGER NOT NULL, restSeconds INTEGER NOT NULL, FOREIGN KEY(routineId) REFERENCES Routine(id) ON DELETE CASCADE, FOREIGN KEY(exerciseId) REFERENCES Exercise(id) ON DELETE RESTRICT)")
        sql.execSQL("INSERT INTO RoutineExercise_old SELECT id,routineId,exerciseId,position,targetSets,targetReps,restSeconds FROM RoutineExercise")
        sql.execSQL("DROP TABLE RoutineExercise")
        sql.execSQL("ALTER TABLE RoutineExercise_old RENAME TO RoutineExercise")
        sql.execSQL("CREATE INDEX index_RoutineExercise_routineId ON RoutineExercise(routineId)")
        sql.execSQL("CREATE INDEX index_RoutineExercise_exerciseId ON RoutineExercise(exerciseId)")
        sql.execSQL("DROP TABLE BodyMeasurement")
        if(version<=2){sql.execSQL("DROP TABLE RunPoint");sql.execSQL("DROP TABLE Run")}
        if(version==1)sql.execSQL("DROP INDEX index_Workout_startedAt")
        sql.execSQL("PRAGMA user_version = $version")
        db.close();open()
    }
    @After fun close(){db.close();context.deleteDatabase("persistence-test.db")}
    @Test fun activeWorkoutSurvivesDatabaseReopenAndRoutineEdits() = runBlocking { withContext(Dispatchers.IO) {
        val e=Exercise(name="Squat",muscle="Legs",equipment="Barbell")
        repo.exercise(e)
        val r=Routine(name="Leg day");repo.routine(r)
        val target=RoutineExercise(routineId=r.id,exerciseId=e.id,position=0,targetSets=3,targetReps=8)
        repo.target(target)
        val id=repo.start(r.id)
        val we=repo.dao.allWorkoutExercises().single()
        val s=repo.dao.allSets().first()
        repo.saveSet(s.copy(weightKg=80.0,reps=5,completed=true))
        repo.exerciseNotes(we.id,"Saved note")
        repo.target(target.copy(targetSets=1,targetReps=20));repo.exercise(e.copy(name="Renamed"))
        repo.dao.deleteRoutine(r.id)
        db.close();open()
        assertEquals(id,repo.start())
        assertEquals(1,repo.dao.allWorkouts().size)
        assertEquals("Squat",repo.dao.allWorkoutExercises().single().nameSnapshot)
        assertEquals("Saved note",repo.dao.allWorkoutExercises().single().notes)
        assertEquals(3,repo.dao.allSets().size)
        assertEquals(80.0,repo.dao.set(s.id)!!.weightKg,0.0)
        assertTrue(repo.dao.set(s.id)!!.completed)
        repo.finish(id)
        assertNull(repo.dao.active())
    } }
    @Test fun invalidRestoreLeavesExistingRecordsAndValidRestoreReplaces() = runBlocking { withContext(Dispatchers.IO) {
        repo.exercise(Exercise(name="Keep me",muscle="Back",equipment="Barbell"))
        val before=repo.dao.snapshot()
        val service=BackupService(repo,context.contentResolver)
        val bad=Backup(schemaVersion=999,data=Snapshot(),preferences=Preferences())
        try {service.restore(bad);fail("Invalid restore accepted")}catch(_: IllegalArgumentException){}
        assertEquals(before,repo.dao.snapshot())
        val fresh=Exercise(name="Restored",muscle="Legs",equipment="Dumbbell")
        val b=Backup(data=Snapshot(exercises=listOf(fresh)),preferences=Preferences(unit="lb"))
        service.restore(b)
        assertEquals(listOf(fresh),repo.dao.allExercises())
        assertTrue(repo.dao.allWorkouts().isEmpty())
        assertEquals("lb",repo.preferences.flow.first().unit)
    } }
    @Test fun restoreRoundTripRetainsAllSixTablesAndActiveTimer() = runBlocking { withContext(Dispatchers.IO) {
        val e=Exercise(name="Pull-up",muscle="Back",equipment="Bar",tracking="BODYWEIGHT");repo.exercise(e)
        val r=Routine(name="Pull");repo.routine(r)
        repo.target(RoutineExercise(routineId=r.id,exerciseId=e.id,position=0))
        val id=repo.start(r.id)
        repo.timer(id,"start")
        val snapshot=repo.dao.snapshot()
        val backup=Backup(data=snapshot,preferences=Preferences(unit="lb",theme="LIGHT",restSeconds=120,autoRest=false))
        val service=BackupService(repo,context.contentResolver)
        service.restore(Backup(data=Snapshot(),preferences=Preferences()))
        assertTrue(repo.dao.allExercises().isEmpty())
        service.restore(backup)
        assertEquals(snapshot,repo.dao.snapshot())
        assertEquals(backup.preferences,repo.preferences.flow.first())
        assertEquals(id,repo.start())
    } }
    @Test fun concurrentStartsReturnSameWorkout() = runBlocking { withContext(Dispatchers.IO) {
        val ids=coroutineScope{(1..8).map{async{repo.start()}}.awaitAll()}
        assertEquals(1,ids.distinct().size)
        assertEquals(1,repo.dao.allWorkouts().size)
    } }
    @Test fun migrationRetainsWorkoutAndAddsHistoryIndex() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start()
        // The sole v1 -> v2 schema difference is this non-unique index.
        legacy(1)
        assertEquals(id,repo.dao.active()!!.id)
        db.openHelper.readableDatabase.query("PRAGMA index_list('Workout')").use { cursor ->
            var found=false
            while(cursor.moveToNext()) if(cursor.getString(cursor.getColumnIndexOrThrow("name"))=="index_Workout_startedAt")found=true
            assertTrue(found)
        }
    } }
    @Test fun runningMigrationPreservesExistingStrengthHistory() = runBlocking { withContext(Dispatchers.IO) {
        val workout=repo.start()
        val exercise=Exercise(name="Squat",muscle="Legs",equipment="Barbell");repo.exercise(exercise)
        repo.addExercise(workout,exercise)
        val before=repo.dao.snapshot()
        legacy(2)
        assertEquals(before,repo.dao.snapshot())
        val id=repo.runs.start(1000,100)
        assertEquals(id,repo.dao.activeRun()!!.id)
    } }
    @Test fun runningPauseResumeAndFinishKeepDistanceAndExcludePauseTime() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.runs.start(1000,100)
        fun point(lat: Double,t: Long)=RunPoint(runId=id,sequence=0,segment=0,latitude=lat,longitude=0.0,accuracyMeters=5.0,timestamp=1000+t,elapsedRealtimeMs=t)
        assertTrue(repo.runs.record(id,point(0.0,100),100))
        assertTrue(repo.runs.record(id,point(.00009,5100),5100))
        val distance=repo.dao.activeRun()!!.distanceMeters
        assertEquals(10.0,distance,.1)
        repo.runs.pause(id,10100)
        assertFalse(repo.runs.record(id,point(.01,20000),20000))
        repo.runs.resume(id,30000)
        assertTrue(repo.runs.record(id,point(.01,30100),30100))
        repo.runs.finish(id,40000,35000)
        val saved=repo.dao.run(id)!!
        assertEquals(15000L,saved.durationMs)
        assertEquals(distance,saved.distanceMeters,.0001)
        assertEquals("FINISHED",saved.state);assertNull(repo.dao.activeRun())
        assertEquals(listOf(0,0,1),repo.dao.allRunPoints().map{it.segment})
    } }
    @Test fun interruptedRunReopensPausedWithoutCountingDowntime() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.runs.start(1000,100)
        repo.runs.checkpoint(id,5100)
        db.close();open()
        repo.runs.recoverInterrupted()
        val saved=repo.dao.activeRun()!!
        assertEquals(id,saved.id);assertEquals("PAUSED",saved.state)
        assertEquals(5000L,saved.durationMs);assertNull(saved.lastTickElapsedMs)
        repo.runs.resume(id,100)
        repo.runs.checkpoint(id,1100)
        assertEquals(6000L,repo.dao.activeRun()!!.durationMs)
    } }
    @Test fun concurrentRunStartsShareOneSessionAndDeletionCascadesPoints() = runBlocking { withContext(Dispatchers.IO) {
        val ids=coroutineScope{(1..8).map{async{repo.runs.start(1000,100)}}.awaitAll()}
        assertEquals(1,ids.distinct().size)
        val id=ids.first()
        repo.runs.record(id,RunPoint(runId=id,sequence=0,segment=0,latitude=0.0,longitude=0.0,accuracyMeters=5.0,timestamp=1000,elapsedRealtimeMs=100),100)
        repo.runs.delete(id)
        assertTrue(repo.dao.allRuns().isEmpty());assertTrue(repo.dao.allRunPoints().isEmpty())
    } }
    @Test fun runBackupRestoreRetainsRouteAndPausesImportedTracking() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.runs.start(1000,100)
        repo.runs.record(id,RunPoint(runId=id,sequence=0,segment=0,latitude=14.0,longitude=121.0,accuracyMeters=5.0,timestamp=1000,elapsedRealtimeMs=100),100)
        repo.runs.checkpoint(id,5100)
        val backup=Backup(data=repo.dao.snapshot(),preferences=Preferences())
        val service=BackupService(repo,context.contentResolver)
        try {service.restore(backup);fail("Restore should require pausing tracking")}catch(_: IllegalArgumentException){}
        assertEquals("RUNNING",repo.dao.activeRun()!!.state)
        repo.runs.pause(id,5100)
        service.restore(backup)
        assertEquals(backup.data.runPoints,repo.dao.allRunPoints())
        val restored=repo.dao.activeRun()!!
        assertEquals("PAUSED",restored.state);assertEquals(5000L,restored.durationMs);assertNull(restored.lastTickElapsedMs)
    } }
    @Test fun namedWorkoutAndInlineCustomExercisePersistWithChosenTargets() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start(name="My home circuit",notes="Use the band today")
        val custom=Exercise(name="My band row",muscle="My pulling group",equipment="Homemade band")
        repo.addCustomExercise(id,custom,4,12,75)
        db.close();open()
        val saved=repo.dao.active()!!
        assertEquals("My home circuit",saved.name);assertEquals("Use the band today",saved.notes)
        assertEquals(listOf(custom),repo.dao.allExercises())
        val exercise=repo.dao.allWorkoutExercises().single()
        assertEquals(custom.id,exercise.exerciseId);assertEquals(75,exercise.restSeconds)
        assertEquals(4,repo.dao.allSets().size)
        assertTrue(repo.dao.allSets().all{it.reps==12 && !it.completed})
    } }
    @Test fun invalidCustomExerciseSetupRollsBackLibraryAndWorkoutChanges() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start(name="Custom session")
        val e=Exercise(name="My movement",muscle="General",equipment="None")
        try{repo.addCustomExercise(id,e,0,8,90);fail("Invalid sets accepted")}catch(_: IllegalArgumentException){}
        try{repo.addCustomExercise("missing",e,3,8,90);fail("Missing workout accepted")}catch(_: IllegalArgumentException){}
        assertTrue(repo.dao.allExercises().isEmpty());assertTrue(repo.dao.allWorkoutExercises().isEmpty());assertTrue(repo.dao.allSets().isEmpty())
        try{repo.start(name=" ");fail("Blank name accepted")}catch(_: IllegalArgumentException){}
        assertEquals(id,repo.dao.active()!!.id)
    } }
    @Test fun customPlanTargetsAreCopiedIntoIndependentWorkout() = runBlocking { withContext(Dispatchers.IO) {
        val plan=repo.createRoutine("My own push day")
        val e=Exercise(name="Chair push-up",muscle="Chest and arms",equipment="My chair",tracking="BODYWEIGHT")
        repo.addRoutineExercise(plan,e,2,15,120,custom=true)
        val id=repo.start(plan)
        val original=repo.dao.allRoutineExercises().single()
        repo.target(original.copy(targetSets=5,targetReps=20,restSeconds=30))
        repo.exercise(e.copy(name="Renamed chair exercise"))
        assertEquals("My own push day",repo.dao.workout(id)!!.name)
        assertEquals("Chair push-up",repo.dao.allWorkoutExercises().single().nameSnapshot)
        assertEquals(120,repo.dao.allWorkoutExercises().single().restSeconds)
        assertEquals(2,repo.dao.allSets().size);assertTrue(repo.dao.allSets().all{it.reps==15 && it.weightKg==0.0})
    } }
    @Test fun saveWorkoutAsPlanCopiesWorkingTargetsWithoutModifyingOriginal() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start(name="Custom legs")
        val e=Exercise(name="My squat variation",muscle="Legs",equipment="Dumbbell")
        repo.addCustomExercise(id,e,3,10,60)
        val sets=repo.dao.allSets().sortedBy{it.position}
        repo.saveSet(sets.first().copy(warmup=true,weightKg=10.0,completed=true))
        repo.saveSet(sets[1].copy(weightKg=25.0,reps=12,completed=true))
        val before=repo.dao.snapshot()
        val plan=repo.saveAsRoutine(id,"Repeat my legs")
        val target=repo.dao.allRoutineExercises().single()
        assertEquals(2,target.targetSets);assertEquals(12,target.targetReps);assertEquals(60,target.restSeconds)
        assertEquals(before.workouts,repo.dao.allWorkouts());assertEquals(before.sets,repo.dao.allSets())
        repo.finish(id)
        val copy=repo.start(plan)
        assertNotEquals(id,copy)
        val copiedId=repo.dao.allWorkoutExercises().first{it.workoutId==copy}.id
        val copiedSets=repo.dao.allSets().filter{it.workoutExerciseId==copiedId}
        assertEquals(2,copiedSets.size);assertTrue(copiedSets.all{it.reps==12 && it.weightKg==25.0 && !it.completed && !it.warmup})
    } }
    @Test fun customRestAppliesToNextCompletionWithoutResettingCurrentTimer() = runBlocking { withContext(Dispatchers.IO) {
        repo.preferences.save(Preferences(restSeconds=90,autoRest=true))
        val id=repo.start(name="My workout")
        val e=Exercise(name="My lift",muscle="General",equipment="None")
        repo.addCustomExercise(id,e,1,8,90)
        repo.timer(id,"start")
        val before=repo.dao.workout(id)!!
        val we=repo.dao.allWorkoutExercises().single()
        repo.exerciseRest(we.id,45)
        assertEquals(before,repo.dao.workout(id))
        try{repo.exerciseRest(we.id,0);fail("Invalid rest accepted")}catch(_: IllegalArgumentException){}
        assertEquals(45,repo.dao.workoutExercise(we.id)!!.restSeconds)
        repo.saveSet(repo.dao.allSets().single().copy(completed=true))
        assertEquals(45000L,repo.dao.workout(id)!!.timerDuration)
    } }
    @Test fun customWorkoutAndPlanRoundTripThroughBackup() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start(name="自分のワークアウト",notes="My own notes")
        val e=Exercise(name="My invented movement",muscle="User-defined group",equipment="User-defined equipment",tracking="BODYWEIGHT")
        repo.addCustomExercise(id,e,2,7,40)
        repo.saveAsRoutine(id,"My repeatable plan")
        val original=repo.dao.snapshot()
        val b=Backup(data=original,preferences=Preferences())
        BackupValidator.validate(b)
        val service=BackupService(repo,context.contentResolver)
        service.restore(Backup(data=Snapshot(),preferences=Preferences()))
        service.restore(b)
        assertEquals(original,repo.dao.snapshot())
    } }
    @Test fun migrationThreeToFourRetainsGymAndRunningData() = runBlocking { withContext(Dispatchers.IO) {
        val e=Exercise(name="Existing exercise",muscle="Legs",equipment="Barbell");repo.exercise(e)
        val plan=repo.createRoutine("Existing plan");repo.addRoutineExercise(plan,e,3,8,90)
        repo.start(plan)
        val run=repo.runs.start(1000,100);repo.runs.pause(run,1100)
        val before=repo.dao.snapshot()
        legacy(3)
        assertEquals(before,repo.dao.snapshot())
        assertEquals(0.0,repo.dao.allRoutineExercises().single().targetWeightKg,0.0)
        repo.bodyMeasurement(BodyMeasurement(weightKg=75.0,heightCm=180.0))
        assertEquals(1,repo.dao.allBodyMeasurements().size)
    } }
    @Test fun startingLoadsPopulateSetsButOnlyCompletedWorkingSetsCount() = runBlocking { withContext(Dispatchers.IO) {
        val e=Exercise(name="Custom load",muscle="Back",equipment="Cable")
        val plan=repo.createRoutine("Loaded plan")
        repo.addRoutineExercise(plan,e,3,8,90,custom=true,weightKg=42.5)
        val id=repo.start(plan)
        val sets=repo.dao.allSets()
        assertTrue(sets.all{it.weightKg==42.5 && !it.completed})
        assertEquals(0.0,com.mygym.log.domain.Metrics.volume(repo.dao.snapshot()),0.0)
        repo.saveSet(sets[0].copy(completed=true))
        repo.saveSet(sets[1].copy(completed=true,warmup=true))
        repo.finish(id)
        assertEquals(340.0,com.mygym.log.domain.Metrics.volume(repo.dao.snapshot()),.0001)
        repo.target(repo.dao.allRoutineExercises().single().copy(targetWeightKg=99.0))
        assertEquals(42.5,repo.dao.set(sets[0].id)!!.weightKg,0.0)
    } }
    @Test fun customAndPredefinedLoadsAreSavedAndInvalidLoadLeavesNoOrphan() = runBlocking { withContext(Dispatchers.IO) {
        val id=repo.start()
        val predefined=Exercise(name="Bench",muscle="Chest",equipment="Barbell");repo.exercise(predefined)
        repo.addExercise(id,predefined,2,10,60,weightKg=50.0)
        val custom=Exercise(name="My exercise",muscle="General",equipment="None")
        repo.addCustomExercise(id,custom,1,12,30,weightKg=7.5)
        val sets=repo.dao.allSets();assertEquals(listOf(7.5,50.0,50.0),sets.map{it.weightKg}.sorted())
        try{repo.addCustomExercise(id,custom.copy(id="bad"),1,8,60,Double.NaN);fail("Invalid weight accepted")}catch(_: IllegalArgumentException){}
        assertEquals(2,repo.dao.allExercises().size)
    } }
    @Test fun bodyMeasurementsAndLoadsPersistAndRoundTripWithoutAffectingExerciseMetrics() = runBlocking { withContext(Dispatchers.IO) {
        val a=BodyMeasurement(recordedAt=1000,weightKg=75.0,heightCm=178.0)
        val b=BodyMeasurement(recordedAt=2000,weightKg=74.0)
        repo.bodyMeasurement(a);repo.bodyMeasurement(b)
        val e=Exercise(name="Loaded",muscle="Back",equipment="Cable");repo.exercise(e)
        val plan=repo.createRoutine("Load preset");repo.addRoutineExercise(plan,e,2,10,60,weightKg=25.0)
        db.close();open()
        assertEquals(listOf(b,a),repo.dao.allBodyMeasurements())
        assertEquals(0.0,com.mygym.log.domain.Metrics.volume(repo.dao.snapshot()),0.0)
        val backup=Backup(data=repo.dao.snapshot(),preferences=Preferences())
        val service=BackupService(repo,context.contentResolver)
        service.restore(Backup(data=Snapshot(),preferences=Preferences()))
        service.restore(backup)
        assertEquals(backup.data,repo.dao.snapshot())
        repo.dao.deleteBodyMeasurement(b.id)
        assertEquals(listOf(a),repo.dao.allBodyMeasurements())
        assertEquals(25.0,repo.dao.allRoutineExercises().single().targetWeightKg,0.0)
    } }
}
