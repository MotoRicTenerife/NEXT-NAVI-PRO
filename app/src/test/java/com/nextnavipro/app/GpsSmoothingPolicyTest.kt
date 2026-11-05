package com.nextnavipro.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSmoothingPolicyTest {
    @Test
    fun movingResponseIsFasterThanStationaryNoiseSuppression() {
        val stationary = GpsSmoothingPolicy.alpha(1f, 3f, 8f, 0.2f, null)
        val moving = GpsSmoothingPolicy.alpha(1f, 8f, 8f, 12f, 5f)
        assertTrue(stationary <= 0.22f)
        assertTrue(moving > stationary)
        assertTrue(moving >= 0.78f)
    }

    @Test
    fun sameTimeConstantProducesConsistentResponseAcrossCadences() {
        val quarterSecond = GpsSmoothingPolicy.alpha(0.25f, 2f, 5f, 5f, 0f)
        val oneSecond = GpsSmoothingPolicy.alpha(1f, 2f, 5f, 5f, 0f)
        assertTrue(oneSecond > quarterSecond)
        assertTrue(quarterSecond > 0f)
    }

    @Test
    fun sharpCourseChangeRaisesResponse() {
        val straight = GpsSmoothingPolicy.alpha(0.8f, 5f, 6f, 7f, 5f)
        val turn = GpsSmoothingPolicy.alpha(0.8f, 5f, 6f, 7f, 65f)
        assertTrue(turn >= 0.78f)
        assertTrue(turn > straight)
    }

    @Test
    fun largeDisplacementIsNotOverSmoothed() {
        val alpha = GpsSmoothingPolicy.alpha(1f, 18f, 20f, 1f, null)
        assertTrue(alpha >= 0.78f)
    }

    @Test
    fun invalidTimingAndMeasurementsRemainBounded() {
        val alpha = GpsSmoothingPolicy.alpha(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        assertTrue(alpha in 0.12f..0.95f)
    }
}
