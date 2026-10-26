package com.mygym.log.domain

import com.mygym.log.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MetricsTest {
    @Test fun conversionsRoundTrip() {
        assertEquals(220.46226218, Weight.display(100.0,"lb"),0.000001)
        assertEquals(100.0, Weight.canonical(Weight.display(100.0,"lb"),"lb"),0.000001)
        assertEquals(0.0, Weight.canonical(0.0,"lb"),0.0)
    }
    @Test fun metricsExcludeWarmupIncompleteActiveAndBodyweightVolume() {
        val w=Workout(id="w",name="Done",startedAt=1000,finishedAt=2000,activeSlot=null)
        val active=Workout(id="a",name="Active",startedAt=1000)
        val e=WorkoutExercise(id="e",workoutId="w",exerciseId="x",nameSnapshot="Squat",trackingSnapshot="WEIGHTED",position=0)
        val bw=e.copy(id="bw",exerciseId="b",trackingSnapshot="BODYWEIGHT",position=1)
        val ae=e.copy(id="ae",workoutId="a")
        val set=WorkoutSet(id="s",workoutExerciseId="e",position=0,weightKg=100.0,reps=5,completed=true)
        val data=Snapshot(workouts=listOf(w,active),workoutExercises=listOf(e,bw,ae),sets=listOf(set,
            set.copy(id="warm",warmup=true,weightKg=200.0),set.copy(id="incomplete",completed=false,weightKg=300.0),
            set.copy(id="active",workoutExerciseId="ae",weightKg=400.0),set.copy(id="body",workoutExerciseId="bw",weightKg=10.0)))
        assertEquals(500.0,Metrics.volume(data),0.0)
        assertEquals(100.0,Metrics.heaviest(data,"x")!!.set.weightKg,0.0)
        assertEquals(2,Metrics.completed(data).size)
    }
    @Test fun timerUsesAbsoluteTimestampAndClampsExpiry() {
        val w=Workout(name="Timer",timerEnd=12000,timerRemaining=9000)
        assertEquals(2000L,Metrics.remaining(w,10000))
        assertEquals(0L,Metrics.remaining(w,13000))
        assertEquals(9000L,Metrics.remaining(w.copy(timerEnd=null),13000))
    }
    @Test fun weeklyCountsUseChosenTimezoneAndMondayBoundary() {
        val timestamp=Instant.parse("2026-10-25T17:00:00Z").toEpochMilli()
        val data=Snapshot(workouts=listOf(Workout(name="W",startedAt=1,finishedAt=timestamp,activeSlot=null)))
        assertEquals(LocalDate.parse("2026-10-26"),Metrics.weekly(data,ZoneId.of("Asia/Manila")).keys.single())
        assertEquals(LocalDate.parse("2026-10-19"),Metrics.weekly(data,ZoneId.of("UTC")).keys.single())
    }
    @Test fun invalidInputsRejected() {
        listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY,10001.0).forEach { invalid -> assertThrows(IllegalArgumentException::class.java){Rules.set(invalid,8)} }
        assertThrows(IllegalArgumentException::class.java){Rules.set(0.0,0)}
        assertThrows(IllegalArgumentException::class.java){Rules.text("  ","Name")}
        assertThrows(IllegalArgumentException::class.java){Rules.targets(0,8,90)}
        assertThrows(IllegalArgumentException::class.java){Rules.preferences(Preferences(restSeconds=0))}
        Rules.set(0.0,10)
    }
}
