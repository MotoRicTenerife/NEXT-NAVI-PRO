package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsMarkerAnimationPolicyTest {
    @Test fun normalGnssCadenceDoesNotCreateOverlappingLongAnimations() {
        assertEquals(200L, GpsMarkerAnimationPolicy.durationMillis(250L, 6f, 24f))
    }

    @Test fun slowOrMissingFixCadenceRemainsBounded() {
        assertEquals(220L, GpsMarkerAnimationPolicy.durationMillis(2_000L, 20f, 10f))
    }

    @Test fun invalidTimingFallsBackToBoundedEstimate() {
        assertEquals(220L, GpsMarkerAnimationPolicy.durationMillis(0L, 100f, 1f))
    }

    @Test fun shortIntervalsNeverProduceZeroDuration() {
        assertEquals(100L, GpsMarkerAnimationPolicy.durationMillis(100L, 1f, 5f))
    }
}
