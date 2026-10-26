package com.mygym.log.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BackupValidatorTest {
    private fun valid(): Backup {
        val e=Exercise(id="e",name="Squat",muscle="Legs",equipment="Barbell")
        val w=Workout(id="w",name="Workout",startedAt=1)
        val we=WorkoutExercise(id="we",workoutId="w",exerciseId="e",nameSnapshot="Squat",trackingSnapshot="WEIGHTED",position=0)
        return Backup(data=Snapshot(exercises=listOf(e),workouts=listOf(w),workoutExercises=listOf(we),sets=listOf(WorkoutSet(workoutExerciseId="we",position=0))),preferences=Preferences())
    }
    @Test fun roundTripPreservesRecordsAndPreferences() {
        val b=valid().copy(preferences=Preferences(unit="lb",theme="LIGHT",restSeconds=120))
        val decoded=BackupValidator.decode(BackupValidator.json.encodeToString(b).byteInputStream())
        assertEquals(b,decoded)
    }
    @Test fun rejectsVersionDuplicateIdsOrphansAndInvalidValues() {
        val b=valid()
        val cases=listOf(b.copy(schemaVersion=99), b.copy(data=b.data.copy(exercises=b.data.exercises+b.data.exercises)),
            b.copy(data=b.data.copy(exercises=emptyList())),b.copy(data=b.data.copy(sets=b.data.sets.map{it.copy(reps=-1)})),
            b.copy(data=b.data.copy(workouts=b.data.workouts+ b.data.workouts.first().copy(id="second"))),
            b.copy(data=b.data.copy(sets=b.data.sets+b.data.sets.first().copy(id="duplicate-position"))))
        cases.forEach{candidate->assertThrows(IllegalArgumentException::class.java){BackupValidator.validate(candidate)}}
    }
    @Test fun rejectsMalformedAndOversizedFiles() {
        assertThrows(IllegalArgumentException::class.java){BackupValidator.decode("{broken".byteInputStream())}
        assertThrows(IllegalArgumentException::class.java){BackupValidator.decode(ByteArray(BackupValidator.MAX_BYTES+1).inputStream())}
        assertThrows(IllegalArgumentException::class.java){BackupValidator.decode("{\"data\":{},\"preferences\":{}}".byteInputStream())}
    }
}
