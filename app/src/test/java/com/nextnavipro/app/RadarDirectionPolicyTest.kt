package com.nextnavipro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarDirectionPolicyTest {
    @Test fun parsesNumericAndCompassDirectionMetadata() {
        assertTrue(RadarDirectionPolicy.isDirectionCompatible("90", 95f))
        assertFalse(RadarDirectionPolicy.isDirectionCompatible("90", 270f))
        assertTrue(RadarDirectionPolicy.isDirectionCompatible("N", 5f))
        assertFalse(RadarDirectionPolicy.isDirectionCompatible("S", 5f))
    }

    @Test fun rejectsCameraAcrossParallelCarriagewayUsingLateralOffset() {
        assertTrue(RadarDirectionPolicy.isInsideForwardCorridor(80f, 5f, 250f, 15f))
        assertFalse(RadarDirectionPolicy.isInsideForwardCorridor(80f, 25f, 250f, 15f))
        assertFalse(RadarDirectionPolicy.isInsideForwardCorridor(260f, 0f, 250f, 15f))
    }

    @Test fun unknownDirectionTagsDoNotCreateFalseDirectionMismatch() {
        assertTrue(RadarDirectionPolicy.isDirectionCompatible("forward", 270f))
        assertTrue(RadarDirectionPolicy.isDirectionCompatible("", 270f))
    }
}
