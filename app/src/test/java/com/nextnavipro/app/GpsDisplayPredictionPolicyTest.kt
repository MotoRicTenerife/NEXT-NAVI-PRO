package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDisplayPredictionPolicyTest {
    private fun predict(
        speed: Float = 25f,
        bearing: Float = 0f,
        accuracy: Float? = 5f,
        bearingAccuracy: Float? = 5f,
        headingChange: Float = 0f,
        horizon: Long = 1_000L
    ) = GpsDisplayPredictionPolicy.predict(
        latitude = 28.0,
        longitude = -16.0,
        speedMetersPerSecond = speed,
        bearingDegrees = bearing,
        accuracyMeters = accuracy,
        bearingAccuracyDegrees = bearingAccuracy,
        headingChangeDegrees = headingChange,
        horizonMillis = horizon
    )

    @Test fun projectsAtNinetyKmhAcrossOneSecondForDisplayOnly() {
        val point = predict()!!
        val northMeters = (point.latitude - 28.0) * 111_320.0
        assertEquals(25.0, northMeters, 0.2)
        assertEquals(-16.0, point.longitude, 0.00001)
    }

    @Test fun rejectsPoorAccuracyOrUnreliableHeading() {
        assertNull(predict(accuracy = 25f))
        assertNull(predict(bearingAccuracy = 40f))
        assertNull(predict(headingChange = 35f))
        assertNull(predict(speed = 2f))
        assertNull(predict(horizon = 1_501L))
    }

    @Test fun projectionDistanceIsBoundedAtHighSpeed() {
        val point = predict(speed = 40f, horizon = 1_500L)!!
        val northMeters = (point.latitude - 28.0) * 111_320.0
        assertTrue(northMeters <= 60.2)
    }

    @Test fun handlesLongitudeWrapAtDateLine() {
        val point = predict(
            speed = 10f, bearing = 90f, horizon = 1_000L
        )
        assertTrue(point!!.longitude in -180.0..180.0)
    }
}
