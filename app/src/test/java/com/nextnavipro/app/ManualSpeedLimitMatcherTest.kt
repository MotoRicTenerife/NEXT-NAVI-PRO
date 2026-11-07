package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualSpeedLimitMatcherTest {
    private fun segment() = ManualSpeedLimitSegment(
        id = "test-road",
        roadKey = null,
        roadName = "Test road",
        geometry = listOf(
            NavPoint(28.0000, -16.0000),
            NavPoint(28.0010, -16.0000),
            NavPoint(28.0020, -16.0000)
        ),
        forwardLimitKmh = 50,
        reverseLimitKmh = 30,
        updatedAtEpochMs = 1L
    )

    @Test fun forwardCourseGetsForwardLimitNearPolyline() {
        val match = ManualSpeedLimitMatcher.find(listOf(segment()), 28.0008, -16.00002, 0f, 5f)
        assertEquals(50, match?.limitKmh)
    }

    @Test fun reverseCourseGetsReverseLimitNearPolyline() {
        val match = ManualSpeedLimitMatcher.find(listOf(segment()), 28.0008, -16.00002, 180f, 5f)
        assertEquals(30, match?.limitKmh)
    }

    @Test fun parallelRoadTooFarAwayDoesNotMatch() {
        val match = ManualSpeedLimitMatcher.find(listOf(segment()), 28.0008, -16.0010, 0f, 5f)
        assertNull(match)
    }

    @Test fun ambiguousCourseDoesNotMatch() {
        val match = ManualSpeedLimitMatcher.find(listOf(segment()), 28.0008, -16.00002, 90f, 5f)
        assertNull(match)
    }

    @Test fun missingCourseDoesNotMatch() {
        assertNull(ManualSpeedLimitMatcher.find(listOf(segment()), 28.0008, -16.00002, null, 5f))
    }

    @Test fun unconfirmedSegmentDoesNotMatch() {
        val pending = segment().copy(status = ManualSpeedLimitSegment.Status.PENDING)
        assertNull(ManualSpeedLimitMatcher.find(listOf(pending), 28.0008, -16.00002, 0f, 5f))
    }
}
