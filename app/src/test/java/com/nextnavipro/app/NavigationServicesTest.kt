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
    @Test
    fun carAndMotorcycleUseDistinctValhallaCostingProfiles() {
        val origin = NavPoint(28.0, -16.0)
        val destination = NavPoint(28.1, -16.1)
        val car = NavigationServices.buildRouteRequest(origin, destination, motorcycle = false)
        val motorcycle = NavigationServices.buildRouteRequest(origin, destination, motorcycle = true)

        assertEquals("auto", car.getString("costing"))
        assertEquals("motorcycle", motorcycle.getString("costing"))
        assertTrue(motorcycle.getJSONObject("costing_options")
            .getJSONObject("motorcycle").getDouble("use_trails") == 0.0)
    }
    @Test
    fun gpxTraceRequestUsesRoadSnapAndVehicleProfile() {
        val points = listOf(NavPoint(28.0, -16.0), NavPoint(28.0005, -16.0005), NavPoint(28.001, -16.001))
        val car = NavigationServices.buildTraceRequest(points, motorcycle = false)
        val moto = NavigationServices.buildTraceRequest(points, motorcycle = true)
        assertEquals("map_snap", car.getString("shape_match"))
        assertEquals("auto", car.getString("costing"))
        assertEquals("motorcycle", moto.getString("costing"))
        assertEquals(3, car.getJSONArray("shape").length())
        assertTrue("GPX accuracy must not be fabricated", !car.has("gps_accuracy"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun gpxTraceRequestRejectsInvalidCoordinates() {
        NavigationServices.buildTraceRequest(listOf(NavPoint(28.0, -16.0), NavPoint(Double.NaN, -16.0)), motorcycle = false)
    }
}
