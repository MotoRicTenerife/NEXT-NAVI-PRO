package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsMarkerAnimationPolicyTest {
    @Test
    fun animationCoversMostOfTheActualGpsFixInterval() {
        assertEquals(230L, GpsMarkerAnimationPolicy.durationMillis(250L))
        assertEquals(460L, GpsMarkerAnimationPolicy.durationMillis(500L))
        assertEquals(920L, GpsMarkerAnimationPolicy.durationMillis(1_000L))
        assertEquals(1_840L, GpsMarkerAnimationPolicy.durationMillis(2_000L))
    }

    @Test
    fun shortIntervalsRemainResponsiveAndLongIntervalsAreBounded() {
        assertEquals(80L, GpsMarkerAnimationPolicy.durationMillis(80L))
        assertEquals(110L, GpsMarkerAnimationPolicy.durationMillis(120L))
        assertEquals(2_200L, GpsMarkerAnimationPolicy.durationMillis(2_500L))
    }

    @Test
    fun invalidOrStaleIntervalsDoNotInventAnInterpolatedJourney() {
        assertNull(GpsMarkerAnimationPolicy.durationMillis(0L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(2_501L))
        assertNull(GpsMarkerAnimationPolicy.durationMillis(-1L))
    }
}
