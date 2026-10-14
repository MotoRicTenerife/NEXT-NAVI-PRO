package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapMatcherTest {
    @Test fun projectsOntoSegmentBetweenSparseVertices() {
        val points = listOf(NavPoint(0.0, 0.0), NavPoint(0.0, 0.01))
        val match = RouteMapMatcher.match(points, 0.0001, 0.005)
        assertNotNull(match)
        assertTrue(match!!.distanceMeters < 12.0)
        assertEquals(0, match.segmentIndex)
        assertTrue(match.fraction in 0.45..0.55)
    }

    @Test fun headingCanPreferCorrectDirectionOnNearbyParallelSegments() {
        val points = listOf(
            NavPoint(0.0, 0.0), NavPoint(0.0, 0.01),
            NavPoint(0.00008, 0.0), NavPoint(0.00008, 0.01)
        )
        val match = RouteMapMatcher.match(points, 0.00008, 0.005, 2, 2, 90f)
        assertNotNull(match)
        assertEquals(2, match!!.segmentIndex)
        assertTrue(match.distanceMeters < 2.0)
    }

    @Test fun rejectsInvalidAndTooShortGeometry() {
        assertEquals(null, RouteMapMatcher.match(emptyList(), 0.0, 0.0))
        assertEquals(null, RouteMapMatcher.match(listOf(NavPoint(0.0, 0.0)), 0.0, 0.0))
        assertEquals(null, RouteMapMatcher.match(listOf(NavPoint(0.0, 0.0), NavPoint(0.0, 1.0)), Double.NaN, 0.0))
    }
}
