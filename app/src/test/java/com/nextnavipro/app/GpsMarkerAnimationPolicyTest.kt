package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsMarkerAnimationPolicyTest {
    @Test
    fun reliablePredictionUsesItsFullMotionHorizon() {
        assertEquals(1_000L, GpsMarkerAnimationPolicy.durationMillis(1_000L, 1_000L))
        assertEquals(500L, GpsMarkerAnimationPolicy.durationMillis(500L, 500L))
    }

    @Test
    fun unreliablePredictionUsesOnlyAShortCorrectionAnimation() {
        assertEquals(200L, GpsMarkerAnimationPolicy.durationMillis(250L))
        assertEquals(220L, GpsMarkerAnimationPolicy.durationMillis(500L))
        assertEquals(100L, GpsMarkerAnimationPolicy.durationMillis(80L))
        assertEquals(100L, GpsMarkerAnimationPolicy.durationMillis(120L))
    }

    @Test
    fun invalidOrStaleIntervalsDoNotInventAnInterpolatedJourney() {
        assertNull(GpsMarkerAnimationPolicy.durationMillis(0L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(2_501L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(-1L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(1_100L, 1_100L))
    }
}
