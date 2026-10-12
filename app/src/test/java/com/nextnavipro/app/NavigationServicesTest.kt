package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationServicesTest {
    @Test
    fun polylineDecoderRejectsEmptyInput() {
        assertTrue(NavigationServices.decodePolyline6("").isEmpty())
    }

    @Test
    fun polylineDecoderDecodesKnownTwoPointPolyline6() {
        // Encoded polyline6 for (38.5, -120.2) -> (40.7, -120.95)
        val decoded = NavigationServices.decodePolyline6("_izlhA~rlgdF_{geC~ywl@")
        assertEquals(2, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 0.00001)
        assertEquals(-120.2, decoded[0].longitude, 0.00001)
        assertEquals(40.7, decoded[1].latitude, 0.00001)
        assertEquals(-120.95, decoded[1].longitude, 0.00001)
    }

    @Test
    fun polylineDecoderRejectsTruncatedInput() {
        assertTrue(NavigationServices.decodePolyline6("_").isEmpty())
    }
}
