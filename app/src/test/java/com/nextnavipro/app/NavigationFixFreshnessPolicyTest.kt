package com.nextnavipro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFixFreshnessPolicyTest {
    @Test fun highSpeedFixMustBeVeryRecent() {
        assertTrue(NavigationFixFreshnessPolicy.isFresh(1_800L, 8.5f))
        assertFalse(NavigationFixFreshnessPolicy.isFresh(1_801L, 8.5f))
    }

    @Test fun mediumSpeedFixHasAnIntermediateAgeLimit() {
        assertTrue(NavigationFixFreshnessPolicy.isFresh(2_500L, 3f))
        assertFalse(NavigationFixFreshnessPolicy.isFresh(2_501L, 3f))
    }

    @Test fun stationaryFixMayBeOlderButNeverNegativeOrOverThreeAndAHalfSeconds() {
        assertTrue(NavigationFixFreshnessPolicy.isFresh(3_500L, 0f))
        assertFalse(NavigationFixFreshnessPolicy.isFresh(3_501L, 0f))
        assertFalse(NavigationFixFreshnessPolicy.isFresh(-1L, 0f))
    }
}
