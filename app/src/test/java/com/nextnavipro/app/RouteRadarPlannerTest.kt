package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRadarPlannerTest {
    private val route = listOf(NavPoint(28.1000, -16.7000), NavPoint(28.1000, -16.6900))

    @Test fun includesCameraOnRouteAndOrdersByDistance() {
        val nearEnd = RadarCamera(2, NavPoint(28.1000, -16.6910), "Controllo semaforo", "")
        val nearStart = RadarCamera(1, NavPoint(28.1000, -16.6990), "Autovelox OSM", "")
        val matches = RouteRadarPlanner.match(listOf(nearEnd, nearStart), route)
        assertEquals(listOf(1L, 2L), matches.map { it.camera.osmId })
        assertTrue(matches[0].distanceFromStartMeters < matches[1].distanceFromStartMeters)
    }

    @Test fun rejectsCameraOnParallelRoadOutsideCorridor() {
        val parallel = RadarCamera(3, NavPoint(28.1020, -16.6950), "Autovelox OSM", "")
        assertTrue(RouteRadarPlanner.match(listOf(parallel), route).isEmpty())
    }

    @Test fun keepsTrafficSignalEnforcementAsDistinctKind() {
        val signal = RadarCamera(4, NavPoint(28.1000, -16.6950), "Controllo semaforo", "")
        val match = RouteRadarPlanner.match(listOf(signal), route).single()
        assertEquals("Controllo semaforo", match.camera.kind)
    }

    @Test fun routeSamplingIncludesEndpointsAndIsBounded() {
        val samples = RouteRadarPlanner.sample(route, 500.0)
        assertEquals(route.first(), samples.first())
        assertEquals(route.last(), samples.last())
        assertTrue(samples.size > 2)
    }
}
