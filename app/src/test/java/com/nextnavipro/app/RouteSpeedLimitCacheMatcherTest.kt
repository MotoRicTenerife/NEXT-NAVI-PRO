package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSpeedLimitCacheMatcherTest {
    private val route = (0..30).map { index -> NavPoint(28.0, -16.0 + index * 0.0001) }
    private val sample = TomTomRouteLimitSample(0, 120, "TF-1", "TF-1", null)

    @Test fun acceptsRecentSampleWhenGpsIsConfidentAndDirectionMatches() {
        val result = RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), progressIndex = 10, progressDistanceMeters = 3f,
            accuracyMeters = 4f, bearingDegrees = 90f, speedMetersPerSecond = 20f,
            bearingAccuracyDegrees = 5f
        )
        assertEquals(sample, result)
    }

    @Test fun rejectsFixDisplacedTowardParallelCarriageway() {
        val result = RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), progressIndex = 10, progressDistanceMeters = 16f,
            accuracyMeters = 4f, bearingDegrees = 90f, speedMetersPerSecond = 20f,
            bearingAccuracyDegrees = 5f
        )
        assertNull(result)
    }

    @Test fun rejectsOppositeOrIncompatibleTravelDirection() {
        val result = RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), progressIndex = 10, progressDistanceMeters = 3f,
            accuracyMeters = 4f, bearingDegrees = 270f, speedMetersPerSecond = 20f,
            bearingAccuracyDegrees = 5f
        )
        assertNull(result)
    }

    @Test fun rejectsOldSampleBeyondAlongRouteWindow() {
        val result = RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), progressIndex = 30, progressDistanceMeters = 3f,
            accuracyMeters = 4f, bearingDegrees = 90f, speedMetersPerSecond = 20f,
            bearingAccuracyDegrees = 5f, maxSampleGapMeters = 100.0
        )
        assertNull(result)
    }

    @Test fun rejectsMissingOrPoorGpsAccuracyInsteadOfGuessingLimit() {
        assertNull(RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), 10, 2f, null, 90f, 20f
        ))
        assertNull(RouteSpeedLimitCacheMatcher.findEligibleSample(
            route, listOf(sample), 10, 2f, 22f, 90f, 20f
        ))
    }
}
