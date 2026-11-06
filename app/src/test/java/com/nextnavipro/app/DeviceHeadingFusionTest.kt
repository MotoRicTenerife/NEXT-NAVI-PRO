package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceHeadingFusionTest {
    @Test fun reliableMovingGpsCourseTakesPriorityOverCompass() {
        val result = DeviceHeadingFusion.select(91f, 12f, 8f, 180f, 3)
        assertEquals("GPS", result.source)
        assertEquals(91f, result.bearingDegrees!!, 0.01f)
    }

    @Test fun compassControlsHeadingAtLowSpeed() {
        val result = DeviceHeadingFusion.select(270f, 0.4f, null, 45f, 3)
        assertEquals("COMPASS", result.source)
        assertEquals(45f, result.bearingDegrees!!, 0.01f)
    }

    @Test fun unreliableCompassIsRejectedAndGpsIsOnlyFallback() {
        val result = DeviceHeadingFusion.select(270f, 0.2f, null, 45f, 1)
        assertEquals("GPS_FALLBACK", result.source)
        assertEquals(270f, result.bearingDegrees!!, 0.01f)
        val none = DeviceHeadingFusion.select(null, 0f, null, 45f, 1)
        assertNull(none.bearingDegrees)
    }

    @Test fun circularInterpolationCrossesNorthByShortestPath() {
        assertEquals(0f, DeviceHeadingFusion.interpolate(359f, 1f, 0.5f), 0.01f)
        assertEquals(180f, DeviceHeadingFusion.interpolate(181f, 179f, 0.5f), 0.01f)
    }
}
