package com.nextnavipro.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ManualSpeedLimitSegmentTest {
    private fun segment(status: ManualSpeedLimitSegment.Status = ManualSpeedLimitSegment.Status.CONFIRMED) =
        ManualSpeedLimitSegment(
            id = "test-road-1",
            roadKey = "osm-way:123",
            roadName = "Test road",
            geometry = listOf(NavPoint(28.0, -16.0), NavPoint(28.01, -16.0)),
            forwardLimitKmh = 50,
            reverseLimitKmh = 70,
            updatedAtEpochMs = 1234L,
            status = status
        )

    @Test fun directionSelectsIndependentLimits() {
        assertEquals(50, segment().limitForDirection(0f))
        assertEquals(70, segment().limitForDirection(180f))
    }

    @Test fun ambiguousCrossDirectionDoesNotApplyLimit() {
        assertNull(segment().limitForDirection(90f))
    }

    @Test fun missingBearingNeverGuessesDirection() {
        assertNull(segment().limitForDirection(null))
    }

    @Test fun unconfirmedSegmentIsNotUsedForDriving() {
        assertNull(segment(ManualSpeedLimitSegment.Status.PENDING).limitForDirection(0f))
        assertNull(segment(ManualSpeedLimitSegment.Status.CONTESTED).limitForDirection(0f))
    }

    @Test fun jsonRoundTripPreservesGeometryDirectionsAndRevision() {
        val original = segment().copy(revision = 4L, geometry = listOf(
            NavPoint(28.0, -16.0), NavPoint(28.005, -16.001), NavPoint(28.01, -16.0)
        ))
        val restored = ManualSpeedLimitSegment.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLimit() {
        segment().copy(forwardLimitKmh = 201)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCoordinates() {
        segment().copy(geometry = listOf(NavPoint(100.0, 0.0), NavPoint(101.0, 1.0)))
    }
}
