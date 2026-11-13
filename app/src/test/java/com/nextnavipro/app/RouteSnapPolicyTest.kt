package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSnapPolicyTest {
    @Test fun snapRadiusTracksAccuracyButNeverExceedsEightMeters() {
        assertEquals(3.0, RouteSnapPolicy.maxSnapDistance(2f), 0.001)
        assertEquals(5.0, RouteSnapPolicy.maxSnapDistance(5f), 0.001)
        assertEquals(8.0, RouteSnapPolicy.maxSnapDistance(20f), 0.001)
    }

    @Test fun invalidAccuracyUsesSmallConservativeRadius() {
        assertEquals(3.0, RouteSnapPolicy.maxSnapDistance(Float.NaN), 0.001)
    }

    @Test fun headingMustAgreeWithinThirtyDegrees() {
        assertTrue(RouteSnapPolicy.isHeadingCompatible(25f))
        assertFalse(RouteSnapPolicy.isHeadingCompatible(31f))
        assertFalse(RouteSnapPolicy.isHeadingCompatible(Float.NaN))
    }
}
