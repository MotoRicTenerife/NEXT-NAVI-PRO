package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkerMotionMathTest {
    @Test
    fun bearingWrapsAcrossNorthUsingShortestDirection() {
        assertEquals(20f, MarkerMotionMath.shortestBearingDelta(350f, 10f), 0.001f)
        assertEquals(-20f, MarkerMotionMath.shortestBearingDelta(10f, 350f), 0.001f)
    }

    @Test
    fun bearingHandlesFullTurnsAndNegativeInputs() {
        assertEquals(0f, MarkerMotionMath.shortestBearingDelta(360f, 0f), 0.001f)
        assertEquals(20f, MarkerMotionMath.shortestBearingDelta(-10f, 10f), 0.001f)
    }

    @Test
    fun oppositeBearingUsesBoundedHalfTurn() {
        assertEquals(-180f, MarkerMotionMath.shortestBearingDelta(0f, 180f), 0.001f)
    }
}
