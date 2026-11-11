package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsArrowVisualSpecTest {
    @Test fun bitmapDimensionsScaleWithDeviceDensity() {
        assertEquals(96, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.WIDTH_DP, 1f))
        assertEquals(192, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.WIDTH_DP, 2f))
        assertEquals(336, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.WIDTH_DP, 3.5f))
        assertEquals(112, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.HEIGHT_DP, 1f))
        assertEquals(392, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.HEIGHT_DP, 3.5f))
    }

    @Test fun invalidDensityFallsBackSafely() {
        assertEquals(96, GpsArrowVisualSpec.pixels(GpsArrowVisualSpec.WIDTH_DP, Float.NaN))
        assertEquals(1, GpsArrowVisualSpec.pixels(0f, 3.5f))
    }

    @Test fun iconScaleRemainsConstantAcrossMapZoom() {
        assertEquals(1.0f, GpsArrowVisualSpec.ICON_SIZE)
    }
}
