package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GpsDisplayMotionPredictorTest {
    private fun predict(speed: Float = 25f, bearing: Float = 90f, accuracy: Float = 4f, age: Long = 0L, turn: Float? = 0f) =
        GpsDisplayMotionPredictor.predict(
            latitude = 28.0,
            longitude = -16.0,
            speedMetersPerSecond = speed,
            bearingDegrees = bearing,
            accuracyMeters = accuracy,
            bearingAccuracyDegrees = 8f,
            fixAgeMs = age,
            courseDeltaDegrees = turn
        )

    @Test fun predictsForwardBySpeedAndBearingOnlyForDisplay() {
        val predicted = predict()
        assertNotNull(predicted)
        assertEquals(28.0, predicted!!.latitude, 0.00001)
        assertTrue(predicted.longitude > -16.0)
        val eastMeters = (predicted.longitude + 16.0) * 111_000.0 * kotlin.math.cos(28.0 * Math.PI / 180.0)
        assertTrue(eastMeters in 5.0..6.0)
    }

    @Test fun stationaryOrPoorAccuracyFixIsNotPredicted() {
        assertNull(predict(speed = 0.2f))
        assertNull(predict(accuracy = 30f))
    }

    @Test fun sharpCourseChangeReducesPredictionDistance() {
        val straight = predict(turn = 0f)!!
        val turning = predict(turn = 45f)!!
        val straightMeters = hypot((straight.longitude + 16.0) * 98_000.0, (straight.latitude - 28.0) * 111_000.0)
        val turningMeters = hypot((turning.longitude + 16.0) * 98_000.0, (turning.latitude - 28.0) * 111_000.0)
        assertTrue(turningMeters < straightMeters)
    }

    @Test fun staleOrUnreliableFixIsNotExtrapolated() {
        assertNull(predict(age = 1_001L))
        assertNull(GpsDisplayMotionPredictor.predict(28.0, -16.0, 25f, 90f, 4f, 40f, 0L))
    }

    @Test fun missingBearingDoesNotInventDirection() {
        assertNull(GpsDisplayMotionPredictor.predict(28.0, -16.0, 25f, null, 4f, null, 0L))
    }
}
