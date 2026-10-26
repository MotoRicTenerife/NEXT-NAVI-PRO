package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsMotionEstimatorTest {
    @Test fun derivesMissingSpeedAndBearingFromConsecutivePositions() {
        val estimator = GpsMotionEstimator()
        val first = estimator.update(28.0, -16.0, 1_000L, null, null)
        assertEquals("UNAVAILABLE", first.source)
        val second = estimator.update(28.0, -15.99995, 2_000L, Float.NaN, Float.NaN)
        assertNotNull(second.speedMetersPerSecond)
        assertNotNull(second.bearingDegrees)
        assertTrue(second.speedInferred)
        assertTrue(second.bearingInferred)
        assertEquals("POSITION_DIFFERENTIAL", second.source)
        assertTrue(second.speedMetersPerSecond!! > 0f)
        assertTrue(second.bearingDegrees!! in 80f..100f)
    }

    @Test fun prefersFiniteReportedMotionAndRejectsNonFiniteValues() {
        val estimator = GpsMotionEstimator()
        estimator.update(28.0, -16.0, 1_000L, 5f, 90f)
        val result = estimator.update(28.0, -15.99995, 2_000L, Float.NaN, Float.POSITIVE_INFINITY)
        assertNotNull(result.speedMetersPerSecond)
        assertNotNull(result.bearingDegrees)
        assertTrue(result.speedInferred)
        assertTrue(result.bearingInferred)
    }

    @Test fun doesNotInferFromDuplicatePositionOrInvalidTimeWindow() {
        val estimator = GpsMotionEstimator()
        estimator.update(28.0, -16.0, 1_000L, null, null)
        val duplicate = estimator.update(28.0, -16.0, 2_000L, null, null)
        assertEquals(null, duplicate.speedMetersPerSecond)
        assertEquals(null, duplicate.bearingDegrees)
        val tooLate = estimator.update(28.0, -15.999, 20_000L, null, null)
        assertEquals(null, tooLate.speedMetersPerSecond)
        assertEquals(null, tooLate.bearingDegrees)
    }

    @Test fun outOfOrderFixDoesNotMoveTheMotionBaselineBackwards() {
        val estimator = GpsMotionEstimator()
        estimator.update(28.0, -16.0, 1_000L, null, null)
        val outOfOrder = estimator.update(28.0, -15.99, 900L, null, null)
        assertEquals(null, outOfOrder.speedMetersPerSecond)
        assertEquals(null, outOfOrder.bearingDegrees)

        val next = estimator.update(28.0, -15.99995, 2_000L, null, null)
        assertNotNull(next.speedMetersPerSecond)
        assertNotNull(next.bearingDegrees)
        assertTrue(next.speedMetersPerSecond!! < 20f)
        assertTrue(next.bearingDegrees!! in 80f..100f)
    }

    @Test fun implausibleGpsJumpDoesNotProduceAnInferredBearing() {
        val estimator = GpsMotionEstimator()
        estimator.update(28.0, -16.0, 1_000L, null, null)
        val jump = estimator.update(28.0, -15.99, 2_000L, null, null)
        assertEquals(null, jump.speedMetersPerSecond)
        assertEquals(null, jump.bearingDegrees)
        assertEquals("UNAVAILABLE", jump.source)
    }
}
