package com.mygym.log.maps
import org.junit.Test
import org.junit.Assert.*
class MapAreaTest {
    @Test fun manilaCoverageAndDownloadLimits() {
        val area=MapArea("manila","Manila City",120.94,14.53,121.04,14.66)
        assertTrue(area.contains(14.5995,120.9842))
        assertTrue(area.contains(14.636,120.966))
        assertFalse(area.contains(14.7,121.1))
        assertFalse(area.contains(Double.NaN,120.98))
        assertEquals(55,area.tiles().size)
        assertTrue(area.tiles().all {it.first in 10..14 && it.second>=0 && it.third>=0})
    }
}
