package com.mygym.log.running

/** A fresh baseline for each registration excludes pauses, downtime and device reboots. */
class StepAccumulator {
    private var last: Long?=null
    fun reset(){last=null}
    fun count(total: Long): Long {
        if(total<0)return 0
        val previous=last;last=total
        return if(previous==null || total<previous)0 else (total-previous).coerceAtMost(10000)
    }
}
