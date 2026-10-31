package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverLifecycleTest {
    private fun obs(
        distance: Double,
        speed: Double = 10.0,
        matched: Double = 100.0,
        target: Double = 100.0,
        confidence: Double = 0.9,
        sampleTime: Long = 1L,
        roundabout: Boolean = false,
        id: String = "m-1"
    ) = ManeuverLifecycle.Observation(id, distance, speed, matched, target, confidence, roundabout)

    @Test fun advancesThroughApproachPrepareImminentAndExecuting() {
        val lifecycle = ManeuverLifecycle()
        assertEquals(ManeuverLifecycle.State.APPROACH, lifecycle.update(obs(500.0))!!.current)
        assertEquals(ManeuverLifecycle.State.PREPARE, lifecycle.update(obs(300.0, sampleTime = 2L))!!.current)
        assertEquals(ManeuverLifecycle.State.IMMINENT, lifecycle.update(obs(80.0, sampleTime = 3L))!!.current)
        assertEquals(ManeuverLifecycle.State.EXECUTING, lifecycle.update(obs(10.0, sampleTime = 4L))!!.current)
    }

    @Test fun roundaboutGetsLongerPreparationWindow() {
        val normal = ManeuverLifecycle()
        val roundabout = ManeuverLifecycle()
        assertEquals(ManeuverLifecycle.State.APPROACH, normal.update(obs(420.0, roundabout = false, sampleTime = 1L))!!.current)
        assertEquals(ManeuverLifecycle.State.PREPARE, roundabout.update(obs(420.0, roundabout = true, sampleTime = 1L))!!.current)
    }

    @Test fun oneNoisyFixCannotCompleteManeuver() {
        val lifecycle = ManeuverLifecycle()
        lifecycle.update(obs(8.0))
        val firstPassedFix = lifecycle.update(obs(20.0, matched = 135.0, sampleTime = 2L))
        assertEquals(ManeuverLifecycle.State.EXECUTING, firstPassedFix!!.current)
        assertFalse(firstPassedFix.changed)
        val secondPassedFix = lifecycle.update(obs(18.0, matched = 136.0, sampleTime = 3L))
        assertEquals(ManeuverLifecycle.State.COMPLETED, secondPassedFix!!.current)
        assertTrue(secondPassedFix.changed)
    }

    @Test fun lowConfidenceFixCannotCompleteOrMarkManeuverMissed() {
        val lifecycle = ManeuverLifecycle()
        lifecycle.update(obs(8.0))
        repeat(5) { lifecycle.update(obs(100.0, matched = 250.0, confidence = 0.2, sampleTime = it + 2L)) }
        assertEquals(ManeuverLifecycle.State.EXECUTING, lifecycle.currentState())
    }

    @Test fun newManeuverResetsLifecycleAndInvalidInputIsIgnored() {
        val lifecycle = ManeuverLifecycle()
        lifecycle.update(obs(8.0))
        assertNull(lifecycle.update(obs(Double.NaN)))
        val next = lifecycle.update(obs(500.0, id = "m-2", sampleTime = 2L))
        assertEquals(ManeuverLifecycle.State.APPROACH, next!!.current)
        assertTrue(next.changed)
    }

    @Test fun missedManeuverRequiresRepeatedTrustedEvidence() {
        val lifecycle = ManeuverLifecycle()
        lifecycle.update(obs(8.0))
        repeat(2) { lifecycle.update(obs(100.0, matched = 190.0, sampleTime = it + 2L)) }
        assertEquals(ManeuverLifecycle.State.EXECUTING, lifecycle.currentState())
        lifecycle.update(obs(100.0, matched = 190.0))
        assertEquals(ManeuverLifecycle.State.MISSED, lifecycle.currentState())
    }
}
