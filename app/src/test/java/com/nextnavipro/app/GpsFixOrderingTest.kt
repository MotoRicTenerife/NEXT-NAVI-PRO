package com.nextnavipro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFixOrderingTest {
    @Test
    fun rejectsOlderOrDuplicateMonotonicFixes() {
        assertTrue(GpsFixOrdering.isOutOfOrder(2_000_000_000L, 1_000_000_000L, 2000L, 3000L))
        assertTrue(GpsFixOrdering.isOutOfOrder(2_000_000_000L, 2_000_000_000L, 2000L, 3000L))
    }

    @Test
    fun monotonicTimeWinsOverWallClockAdjustments() {
        assertFalse(GpsFixOrdering.isOutOfOrder(2_000_000_000L, 3_000_000_000L, 5000L, 1000L))
    }

    @Test
    fun fallsBackToWallClockOnlyWhenMonotonicTimeIsUnavailable() {
        assertTrue(GpsFixOrdering.isOutOfOrder(0L, 0L, 2000L, 1999L))
        assertFalse(GpsFixOrdering.isOutOfOrder(0L, 0L, 2000L, 2001L))
        assertFalse(GpsFixOrdering.isOutOfOrder(0L, 0L, 0L, 0L))
    }
}
