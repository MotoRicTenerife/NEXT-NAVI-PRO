package com.nextnavipro.app

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDisplayMotionPredictorTest {
    private fun fix(speed: Float = 25f, bearing: Float = 90f, accuracy: Float = 4f): Location =
        Location("test").apply {
            latitude = 28.0
            longitude = -16.0
            this.speed = speed
            this.bearing = bearing
            this.accuracy = accuracy
            setSpeed(speed)
            setBearing(bearing)
            setAccuracy(accuracy)
            setBearingAccuracyDegrees(8f)
        }

    @Test fun predictsForwardBySpeedAndBearingOnlyForDisplay() {
        val measured = fix()
        val predicted = GpsDisplayMotionPredictor.predict(measured, 0L)
        assertEquals(measured.latitude, predicted.latitude, 0.000001)
        assertTrue(predicted.longitude > measured.longitude)
        assertTrue(measured.distanceTo(predicted) in 5.0f..6.0f)
    }

    @Test fun stationaryOrPoorAccuracyFixIsNotPredicted() {
        val stationary = fix(speed = 0.2f)
        val inaccurate = fix(accuracy = 30f)
        assertEquals(0f, stationary.distanceTo(GpsDisplayMotionPredictor.predict(stationary, 0L)), 0f)
        assertEquals(0f, inaccurate.distanceTo(GpsDisplayMotionPredictor.predict(inaccurate, 0L)), 0f)
    }

    @Test fun sharpCourseChangeReducesPredictionDistance() {
        val measured = fix()
        val straight = GpsDisplayMotionPredictor.predict(measured, 0L, 0f)
        val turning = GpsDisplayMotionPredictor.predict(measured, 0L, 45f)
        assertTrue(turning.distanceTo(measured) < straight.distanceTo(measured))
    }

    @Test fun staleFixIsNotExtrapolatedWithoutBound() {
        val measured = fix()
        val predicted = GpsDisplayMotionPredictor.predict(measured, 1_001L)
        assertEquals(0f, measured.distanceTo(predicted), 0f)
    }

    @Test fun predictionNeverChangesTheMeasuredLocation() {
        val measured = fix()
        val originalLatitude = measured.latitude
        val originalLongitude = measured.longitude
        GpsDisplayMotionPredictor.predict(measured, 100L)
        assertEquals(originalLatitude, measured.latitude, 0.0)
        assertEquals(originalLongitude, measured.longitude, 0.0)
    }
}
