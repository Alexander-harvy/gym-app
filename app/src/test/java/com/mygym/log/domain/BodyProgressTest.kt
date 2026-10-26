package com.mygym.log.domain

import com.mygym.log.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class BodyProgressTest {
    @Test fun weightAndHeightHaveIndependentLatestValuesAndNeverAddLiftingVolume() {
        val first=BodyMeasurement(recordedAt=1000,weightKg=75.0,heightCm=178.0)
        val height=BodyMeasurement(recordedAt=2000,heightCm=179.0)
        val weight=BodyMeasurement(recordedAt=3000,weightKg=74.0)
        val values=listOf(first,weight,height)
        assertEquals(weight,BodyProgress.latestWeight(values));assertEquals(height,BodyProgress.latestHeight(values))
        assertEquals(-1.0,BodyProgress.weightChangeKg(values)!!,0.0)
        assertEquals(0.0,Metrics.volume(Snapshot(bodyMeasurements=values)),0.0)
        assertNull(BodyProgress.weightChangeKg(listOf(height,weight)))
    }
    @Test fun bodyInputRejectsEmptyInvalidOrNonFiniteMeasurements() {
        val invalid=listOf(BodyMeasurement(),BodyMeasurement(weightKg=Double.NaN),BodyMeasurement(heightCm=0.0),
            BodyMeasurement(weightKg=-1.0),BodyMeasurement(heightCm=Double.POSITIVE_INFINITY),BodyMeasurement(recordedAt=0,weightKg=75.0))
        invalid.forEach{value->assertThrows(IllegalArgumentException::class.java){Rules.body(value)}}
        Rules.body(BodyMeasurement(weightKg=Weight.canonical(165.3466966,"lb")))
        Rules.body(BodyMeasurement(heightCm=178.5))
    }
    @Test fun measurementDatesUseDeviceTimezoneAndRetainExistingEntryTime() {
        val now=Instant.parse("2026-10-25T17:00:00Z").toEpochMilli();val zone=ZoneId.of("Asia/Manila")
        assertEquals(now,BodyProgress.timestamp("2026-10-26",now=now,zone=zone))
        val previous=now-3600000
        assertEquals(previous,BodyProgress.timestamp("2026-10-26",previous,now,zone))
        assertEquals(LocalDate.parse("2026-10-25"),Instant.ofEpochMilli(BodyProgress.timestamp("2026-10-25",now=now,zone=zone)).atZone(zone).toLocalDate())
        assertThrows(IllegalArgumentException::class.java){BodyProgress.timestamp("2026-02-30",now=now,zone=zone)}
        assertThrows(IllegalArgumentException::class.java){BodyProgress.timestamp("2026-10-27",now=now,zone=zone)}
    }
}
