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
        db=Room.databaseBuilder(context,GymDatabase::class.java,"persistence-test.db").addMigrations(GymDatabase.MIGRATION_1_2).build()
        repo=GymRepository(db,PreferenceStore(context))
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
        db.openHelper.writableDatabase.execSQL("DROP INDEX index_Workout_startedAt")
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 1")
        db.close();open()
        assertEquals(id,repo.dao.active()!!.id)
        db.openHelper.readableDatabase.query("PRAGMA index_list('Workout')").use { cursor ->
            var found=false
            while(cursor.moveToNext()) if(cursor.getString(cursor.getColumnIndexOrThrow("name"))=="index_Workout_startedAt")found=true
            assertTrue(found)
        }
    } }
}
