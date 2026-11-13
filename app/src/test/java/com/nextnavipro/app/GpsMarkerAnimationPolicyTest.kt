package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsMarkerAnimationPolicyTest {
    @Test
    fun normalGpsCadenceDoesNotCreateOverlappingLongAnimations() {
        assertEquals(200L, GpsMarkerAnimationPolicy.durationMillis(250L))
        assertEquals(220L, GpsMarkerAnimationPolicy.durationMillis(500L))
    }

    @Test
    fun shortIntervalsKeepAUsefulAnimationFloor() {
        assertEquals(100L, GpsMarkerAnimationPolicy.durationMillis(80L))
        assertEquals(100L, GpsMarkerAnimationPolicy.durationMillis(120L))
    }

    @Test
    fun longGpsGapsDoNotInventAnInterpolatedJourney() {
        assertNull(GpsMarkerAnimationPolicy.durationMillis(0L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(2_501L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(-1L))
    }
}
