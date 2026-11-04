package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficIncidentRouteMatcherTest {
    private val route = listOf(
        NavPoint(28.1000, -16.8000),
        NavPoint(28.1000, -16.7900),
        NavPoint(28.1000, -16.7800),
        NavPoint(28.1000, -16.7700)
    )

    private fun incident(
        type: String = "LineString",
        geometry: List<IncidentCoordinate> = listOf(
            IncidentCoordinate(28.10002, -16.7950),
            IncidentCoordinate(28.10002, -16.7920)
        )
    ) = TrafficIncidentObservation(
        id = "tt-1",
        category = "roadWorks",
        severity = TrafficIncidentSeverity.MODERATE,
        magnitudeOfDelay = "moderate",
        delaySeconds = 180,
        lengthMeters = 300.0,
        roadNumbers = listOf("TF-1"),
        geometryType = type,
        geometry = geometry,
        observedAtEpochMs = 1_800_000_000_000L,
        validUntilEpochMs = 1_800_000_300_000L,
        probabilityOfOccurrence = "certain",
        timeValidity = "present",
        startTimeEpochMs = null,
        endTimeEpochMs = null,
        from = null,
        to = null,
        description = "Road works"
    )

    @Test fun matchesIncidentAlongSameRoadAndCalculatesDistanceAhead() {
        val match = TrafficIncidentRouteMatcher.match(incident(), route, 100.0)
        assertNotNull(match)
        assertTrue(match!!.distanceAheadMeters > 0.0)
        assertTrue(match.crossTrackDistanceMeters < 5.0)
        assertTrue(match.geometryFitScore > 0.8)
        assertFalse(match.directionVerified)
    }

    @Test fun rejectsParallelRoadOutsideCorridor() {
        val adjacent = incident(geometry = listOf(
            IncidentCoordinate(28.1015, -16.7950),
            IncidentCoordinate(28.1015, -16.7920)
        ))
        assertNull(TrafficIncidentRouteMatcher.match(adjacent, route, 0.0))
    }

    @Test fun rejectsPerpendicularStreetCrossing() {
        val crossing = incident(geometry = listOf(
            IncidentCoordinate(28.0980, -16.7935),
            IncidentCoordinate(28.1020, -16.7935)
        ))
        assertNull(TrafficIncidentRouteMatcher.match(crossing, route, 0.0))
    }

    @Test fun acceptsPointIncidentNearRoute() {
        val point = incident(
            type = "Point",
            geometry = listOf(IncidentCoordinate(28.1001, -16.7940))
        )
        val match = TrafficIncidentRouteMatcher.match(point, route, 0.0)
        assertNotNull(match)
        assertEquals("tt-1", match!!.incidentId)
    }

    @Test fun rejectsIncidentAlreadyBehindRider() {
        val behind = incident(geometry = listOf(
            IncidentCoordinate(28.10002, -16.7990),
            IncidentCoordinate(28.10002, -16.7970)
        ))
        assertNull(TrafficIncidentRouteMatcher.match(behind, route, 500.0))
    }

    @Test fun acceptsOppositeGeometryOrderingButNeverClaimsDirection() {
        val reversed = incident(geometry = listOf(
            IncidentCoordinate(28.10002, -16.7920),
            IncidentCoordinate(28.10002, -16.7950)
        ))
        val match = TrafficIncidentRouteMatcher.match(reversed, route, 0.0)
        assertNotNull(match)
        assertFalse(match!!.directionVerified)
    }

    @Test fun rejectsInvalidRouteProgressAndInsufficientRoute() {
        assertNull(TrafficIncidentRouteMatcher.match(incident(), route, Double.NaN))
        assertNull(TrafficIncidentRouteMatcher.match(incident(), route.take(1), 0.0))
    }
}
