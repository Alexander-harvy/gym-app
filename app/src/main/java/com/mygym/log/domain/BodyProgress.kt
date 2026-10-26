package com.mygym.log.domain

import com.mygym.log.data.BodyMeasurement
import java.time.*

object BodyProgress {
    fun latestWeight(entries: List<BodyMeasurement>)=entries.filter{it.weightKg!=null}.maxByOrNull{it.recordedAt}
    fun latestHeight(entries: List<BodyMeasurement>)=entries.filter{it.heightCm!=null}.maxByOrNull{it.recordedAt}
    fun weightChangeKg(entries: List<BodyMeasurement>): Double? {
        val weights=entries.filter{it.weightKg!=null}.sortedBy{it.recordedAt}
        return if(weights.size<2)null else weights.last().weightKg!!-weights.first().weightKg!!
    }
    fun timestamp(text: String,previous: Long?=null,now: Long=System.currentTimeMillis(),zone: ZoneId=ZoneId.systemDefault()): Long {
        val date=try{LocalDate.parse(text.trim())}catch(_: Exception){throw IllegalArgumentException("Use a date in YYYY-MM-DD format.")}
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        require(!date.isAfter(today)){"Measurement date cannot be in the future."}
        if(previous!=null && Instant.ofEpochMilli(previous).atZone(zone).toLocalDate()==date)return previous
        val time=if(date==today)now else date.atStartOfDay(zone).toInstant().toEpochMilli()
        require(time>0){"Use a date after January 1, 1970."}
        return time
    }
}
