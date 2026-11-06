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

    @Test fun rejectsNumericDirectionFacingOppositeToRoute() {
        val opposite = RadarCamera(8, NavPoint(28.1000, -16.6950), "Autovelox OSM", "270")
        assertTrue(RouteRadarPlanner.match(listOf(opposite), route).isEmpty())
    }

    @Test fun keepsNumericDirectionAlignedWithRoute() {
        val aligned = RadarCamera(9, NavPoint(28.1000, -16.6950), "Autovelox OSM", "90")
        assertEquals(1, RouteRadarPlanner.match(listOf(aligned), route).size)
    }

    @Test fun keepsTrafficSignalEnforcementAsDistinctKind() {
        val signal = RadarCamera(4, NavPoint(28.1000, -16.6950), "Controllo semaforo", "")
        val match = RouteRadarPlanner.match(listOf(signal), route).single()
        assertEquals("Controllo semaforo", match.camera.kind)
    }

    @Test fun computesCameraDistanceAheadUsingContinuousSegmentProjection() {
        val rider = NavPoint(28.1000, -16.6980)
        val camera = NavPoint(28.1000, -16.6920)
        val ahead = RouteRadarPlanner.distanceAhead(rider, camera, route)
        assertTrue(ahead != null && ahead in 590.0..610.0)
    }

    @Test fun rejectsCameraOnParallelCarriagewayEvenWhenItIsAhead() {
        val rider = NavPoint(28.1000, -16.6980)
        val parallelCamera = NavPoint(28.10035, -16.6920)
        assertEquals(null, RouteRadarPlanner.distanceAhead(rider, parallelCamera, route))
    }

    @Test fun rejectsRiderOutsideActiveRouteCorridor() {
        val riderOffRoute = NavPoint(28.1010, -16.6980)
        val camera = NavPoint(28.1000, -16.6920)
        assertEquals(null, RouteRadarPlanner.distanceAhead(riderOffRoute, camera, route))
    }

    @Test fun routeSamplingIncludesEndpointsAndIsBounded() {
        val samples = RouteRadarPlanner.sample(route, 500.0)
        assertEquals(route.first(), samples.first())
        assertEquals(route.last(), samples.last())
        assertTrue(samples.size > 2)
    }
}
