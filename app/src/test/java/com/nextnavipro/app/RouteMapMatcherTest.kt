package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun rejectsInvalidAndTooShortGeometry() {
        assertNull(RouteMapMatcher.match(emptyList(), 0.0, 0.0))
        assertNull(RouteMapMatcher.match(listOf(NavPoint(0.0, 0.0)), 0.0, 0.0))
        assertNull(RouteMapMatcher.match(listOf(NavPoint(0.0, 0.0), NavPoint(0.0, 1.0)), Double.NaN, 0.0))
        assertNull(RouteMapMatcher.match(listOf(NavPoint(0.0, 0.0), NavPoint(0.0, 1.0)), 95.0, 0.0))
    }

    @Test fun replayedNoisyTraceProgressesSmoothlyAndSameFixIsIdempotent() {
        val route = (0..100).map { NavPoint(28.0, -16.0 + it * 0.00001) }
        val tracker = RouteProgressTracker()
        var previousAlong = -1.0
        for (i in 0..20) {
            val longitude = -16.0 + i * 0.00005
            val latitudeNoise = if (i % 2 == 0) 0.000015 else -0.000012
            val fix = tracker.match(route, 28.0 + latitudeNoise, longitude, 6.0, 90f, 8.0, 1_000L + i * 1_000L)
            assertNotNull("Trace sample $i should match", fix)
            assertTrue("Progress must not move backwards", fix!!.alongRouteMeters >= previousAlong)
            val duplicate = tracker.match(route, 28.0 + latitudeNoise, longitude, 6.0, 90f, 8.0, 1_000L + i * 1_000L)
            assertEquals(fix, duplicate)
            previousAlong = fix.alongRouteMeters
        }
    }

    @Test fun singleOutlierCannotJumpToDistantPartOfLoop() {
        val route = listOf(
            NavPoint(28.0, -16.0),
            NavPoint(28.0, -15.999),
            NavPoint(28.001, -15.999),
            NavPoint(28.001, -16.0),
            NavPoint(28.0, -16.0)
        )
        val tracker = RouteProgressTracker()
        val first = tracker.match(route, 28.0, -15.9999, 5.0, 90f, 8.0, 10_000L)
        assertNotNull(first)
        val outlier = tracker.match(route, 28.001, -15.9995, 5.0, 270f, 8.0, 10_250L)
        assertNotNull(outlier)
        assertTrue("Outlier must not skip an implausible distance", outlier!!.alongRouteMeters - first!!.alongRouteMeters < 40.0)
    }

    @Test fun rejectsInvalidFixAndKeepsRouteProgressWhenNoCandidateExists() {
        val route = listOf(NavPoint(28.0, -16.0), NavPoint(28.0, -15.999))
        val tracker = RouteProgressTracker()
        assertNull(tracker.match(route, Double.NaN, -16.0, 5.0, null, 0.0, 1L))
        val valid = tracker.match(route, 28.0, -15.9999, 5.0, 90f, 5.0, 1_000L)
        assertNotNull(valid)
        assertNull(tracker.match(route, 28.1, -15.9, 5.0, 90f, 5.0, 2_000L))
    }
}
