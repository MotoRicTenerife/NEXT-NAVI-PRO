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
    @Test
    fun routeRequestCanAskForBoundedAlternatives() {
        val origin = NavPoint(28.0, -16.0)
        val destination = NavPoint(28.1, -16.1)
        val request = NavigationServices.buildRouteRequest(origin, destination, motorcycle = true, alternatives = 2)
        assertEquals(2, request.getInt("alternates"))
        assertEquals("motorcycle", request.getString("costing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun routeRequestRejectsUnboundedAlternatives() {
        NavigationServices.buildRouteRequest(
            NavPoint(28.0, -16.0), NavPoint(28.1, -16.1),
            motorcycle = true, alternatives = 4
        )
    }

    @Test
    fun routeRequestUsesThroughWaypointWithoutChangingMotorcycleCosting() {
        val request = NavigationServices.buildRouteRequest(
            NavPoint(28.0, -16.0),
            NavPoint(28.1, -16.1),
            motorcycle = true,
            viaPoints = listOf(NavPoint(28.05, -16.05))
        )
        val locations = request.getJSONArray("locations")
        assertEquals(3, locations.length())
        assertEquals("through", locations.getJSONObject(1).getString("type"))
        assertEquals("motorcycle", request.getString("costing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun routeRequestRejectsInvalidThroughWaypoint() {
        NavigationServices.buildRouteRequest(
            NavPoint(28.0, -16.0),
            NavPoint(28.1, -16.1),
            motorcycle = true,
            viaPoints = listOf(NavPoint(Double.NaN, -16.05))
        )
    }

    @Test
    fun routeResponseParsesPrimaryAndAlternateTrips() {
        val trip = """{"legs":[{"shape":"_izlhA~rlgdF_{geC~ywl@","summary":{"length":12.5,"time":600},"maneuvers":[{"begin_shape_index":0,"instruction":"Prosegui","type":1,"street_names":["TF-1"]}]}],"summary":{"length":12.5,"time":600}}"""
        val alternate = """{"legs":[{"shape":"_izlhA~rlgdF_{geC~ywl@","summary":{"length":13.0,"time":720},"maneuvers":[{"begin_shape_index":0,"instruction":"Svolta a destra","type":10,"street_names":["TF-82"]}]}],"summary":{"length":13.0,"time":720}}"""
        val root = org.json.JSONObject()
            .put("trip", org.json.JSONObject(trip))
            .put("alternates", org.json.JSONArray().put(
                org.json.JSONObject().put("trip", org.json.JSONObject(alternate))
            ).put(org.json.JSONObject().put("unexpected", true)))
        val options = NavigationServices.parseRouteOptions(root)
        assertEquals(12.5, options.primary.distanceKm, 0.001)
        assertEquals(600, options.primary.durationSeconds)
        assertEquals(1, options.alternatives.size)
        assertEquals(13.0, options.alternatives.single().distanceKm, 0.001)
        assertEquals(720, options.alternatives.single().durationSeconds)
    }

}
