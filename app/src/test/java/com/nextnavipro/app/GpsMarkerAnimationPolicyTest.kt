package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsMarkerAnimationPolicyTest {
    @Test
    fun animationUsesTheWholeMeasuredGpsIntervalInsteadOfAnEarlyFixedCeiling() {
        assertEquals(1_500L, GpsMarkerAnimationPolicy.durationMillis(1_500L))
        assertEquals(2_000L, GpsMarkerAnimationPolicy.durationMillis(2_000L))
    }

    @Test
    fun veryShortIntervalsGetASmallFloorToAvoidZeroLengthAnimation() {
        assertEquals(120L, GpsMarkerAnimationPolicy.durationMillis(80L))
        assertEquals(120L, GpsMarkerAnimationPolicy.durationMillis(120L))
    }

    @Test
    fun firstFixAndLongGpsGapsDoNotInventAnInterpolatedJourney() {
        assertNull(GpsMarkerAnimationPolicy.durationMillis(0L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(2_501L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(-1L))
    }
}
