package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteManeuverProgressTest {
    @Test
    fun choosesTheNextTurnByDistanceInsteadOfSkippingTwoSparseVertices() {
        val route = listOf(
            NavPoint(28.0, -16.0),
            NavPoint(28.0, -15.999),
            NavPoint(28.0, -15.998),
            NavPoint(28.0, -15.997)
        )
        val tracker = RouteProgressTracker()
        val match = tracker.match(
            points = route,
            latitude = 28.00002,
            longitude = -15.9999,
            accuracyMeters = 5.0,
            bearingDegrees = 90f,
            speedMetersPerSecond = 8.0,
            elapsedRealtimeMs = 1_000L
        )
        assertNotNull(match)
        val next = RouteManeuverProgress.next(
            routePoints = route,
            maneuvers = listOf(
                RouteManeuver(1, "Turn left", 0, ""),
                RouteManeuver(3, "Continue to destination", 0, "")
            ),
            currentAlongRouteMeters = match!!.alongRouteMeters,
            progressTracker = tracker
        )
        assertNotNull(next)
        assertEquals("The first upcoming maneuver must not be skipped", 0, next!!.index)
        assertTrue(next.distanceMeters in 80.0..110.0)
    }

    @Test
    fun skipsOnlyManeuversAlreadyPassedBeyondTolerance() {
        val route = listOf(
            NavPoint(28.0, -16.0),
            NavPoint(28.0, -15.999),
            NavPoint(28.0, -15.998),
            NavPoint(28.0, -15.997)
        )
        val tracker = RouteProgressTracker()
        val length = tracker.routeLengthMeters(route)
        val next = RouteManeuverProgress.next(
            routePoints = route,
            maneuvers = listOf(
                RouteManeuver(1, "Turn left", 0, ""),
                RouteManeuver(3, "Continue to destination", 0, "")
            ),
            currentAlongRouteMeters = length * 0.5,
            progressTracker = tracker
        )
        assertNotNull(next)
        assertEquals(1, next!!.index)
    }
}
