package com.mygym.log.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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
    @Test fun legacyVersionOneWithoutRunningTablesStillRestores() {
        val original=valid().copy(schemaVersion=1)
        val root=BackupValidator.json.encodeToJsonElement(original).jsonObject
        val oldData=JsonObject(root.getValue("data").jsonObject.filterKeys{it !in listOf("runs","runPoints","bodyMeasurements")})
        val legacy=JsonObject(root.toMutableMap().apply{put("data",oldData)}).toString()
        assertEquals(original,BackupValidator.decode(legacy.byteInputStream()))
    }
    @Test fun invalidRunningCoordinatesOrDistancesCannotReachRestore() {
        val r=Run(id="r",startedAt=1000)
        val p=RunPoint(runId="r",sequence=0,segment=0,latitude=14.0,longitude=121.0,accuracyMeters=5.0,timestamp=1000,elapsedRealtimeMs=100)
        val valid=Backup(data=Snapshot(runs=listOf(r),runPoints=listOf(p)),preferences=Preferences())
        BackupValidator.validate(valid)
        listOf(valid.copy(data=valid.data.copy(runPoints=listOf(p.copy(latitude=Double.NaN)))),
            valid.copy(data=valid.data.copy(runs=listOf(r.copy(distanceMeters=100.0)))),
            valid.copy(data=valid.data.copy(runPoints=listOf(p.copy(runId="missing")))),
            valid.copy(data=valid.data.copy(runs=listOf(r,r.copy(id="another"))))).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java){BackupValidator.validate(bad)}
        }
    }
    @Test fun schemaTwoWithoutBodyOrLoadFieldsGetsSafeDefaults() {
        val e=Exercise(id="e",name="Exercise",muscle="General",equipment="None")
        val r=Routine(id="r",name="Plan")
        val old=Backup(schemaVersion=2,data=Snapshot(exercises=listOf(e),routines=listOf(r),routineExercises=listOf(RoutineExercise(routineId="r",exerciseId="e",position=0))),preferences=Preferences())
        val root=BackupValidator.json.encodeToJsonElement(old).jsonObject
        val fields=root.getValue("data").jsonObject.toMutableMap().apply {
            remove("bodyMeasurements")
            put("routineExercises",JsonArray(getValue("routineExercises").jsonArray.map{JsonObject(it.jsonObject.filterKeys{key->key!="targetWeightKg"})}))
        }
        val legacy=JsonObject(root.toMutableMap().apply{put("data",JsonObject(fields))}).toString()
        val decoded=BackupValidator.decode(legacy.byteInputStream())
        assertTrue(decoded.data.bodyMeasurements.isEmpty());assertEquals(0.0,decoded.data.routineExercises.single().targetWeightKg,0.0)
    }
    @Test fun schemaThreeRequiresValidBodyFieldsAndLoadValues() {
        val body=BodyMeasurement(weightKg=75.0,heightCm=180.0)
        val b=Backup(data=Snapshot(bodyMeasurements=listOf(body)),preferences=Preferences())
        assertEquals(b,BackupValidator.decode(BackupValidator.json.encodeToString(b).byteInputStream()))
        assertThrows(IllegalArgumentException::class.java){BackupValidator.validate(b.copy(data=b.data.copy(bodyMeasurements=listOf(body.copy(weightKg=Double.NaN)))))}
        assertThrows(IllegalArgumentException::class.java){BackupValidator.validate(b.copy(data=b.data.copy(bodyMeasurements=listOf(body,body))))}
        val root=BackupValidator.json.encodeToJsonElement(b).jsonObject
        val missing=JsonObject(root.toMutableMap().apply{put("data",JsonObject(root.getValue("data").jsonObject.filterKeys{it!="bodyMeasurements"}))})
        assertThrows(IllegalArgumentException::class.java){BackupValidator.decode(missing.toString().byteInputStream())}
    }
}
