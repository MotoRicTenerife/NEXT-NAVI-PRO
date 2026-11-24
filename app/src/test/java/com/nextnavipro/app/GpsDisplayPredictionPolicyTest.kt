package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDisplayPredictionPolicyTest {
    private fun predict(speed: Float = 25f, bearing: Float = 0f, accuracy: Float? = 5f,
                        bearingAccuracy: Float? = 5f, headingChange: Float = 0f) =
        GpsDisplayPredictionPolicy.predict(
            latitude = 28.0, longitude = -16.0,
            speedMetersPerSecond = speed, bearingDegrees = bearing,
            accuracyMeters = accuracy, bearingAccuracyDegrees = bearingAccuracy,
            headingChangeDegrees = headingChange
        )

    @Test fun projectsForwardByAboutFourAndHalfMetersAtNinetyKmh() {
        val point = predict()!!
        val northMeters = (point.latitude - 28.0) * 111_320.0
        assertEquals(4.5, northMeters, 0.15)
        assertEquals(-16.0, point.longitude, 0.00001)
    }

    @Test fun rejectsPoorAccuracyOrUnreliableHeading() {
        assertNull(predict(accuracy = 25f))
        assertNull(predict(bearingAccuracy = 40f))
        assertNull(predict(headingChange = 35f))
        assertNull(predict(speed = 2f))
    }

    @Test fun projectionIsBoundedAtHighSpeed() {
        val point = predict(speed = 100f) // above the policy's maximum accepted speed
        assertNull(point)
        val capped = GpsDisplayPredictionPolicy.predict(
            28.0, -16.0, 45f, 0f, 5f, 5f, 0f
        )!!
        val northMeters = (capped.latitude - 28.0) * 111_320.0
        assertTrue(northMeters <= 6.1)
    }

    @Test fun handlesLongitudeWrapAtDateLine() {
        val point = GpsDisplayPredictionPolicy.predict(
            0.0, 179.99999, 10f, 90f, 5f, 5f, 0f
        )!!
        assertTrue(point.longitude in -180.0..180.0)
    }
}
