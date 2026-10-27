package com.mygym.log.running
import org.junit.Test
import org.junit.Assert.*
class StepAccumulatorTest {
    @Test fun baselinePauseAndRebootDoNotAddOldSteps() {
        val counter=StepAccumulator()
        assertEquals(0L,counter.count(500));assertEquals(7L,counter.count(507))
        counter.reset();assertEquals(0L,counter.count(900));assertEquals(2L,counter.count(902))
        assertEquals(0L,counter.count(5));assertEquals(3L,counter.count(8))
        assertEquals(0L,counter.count(-1))
    }
}
