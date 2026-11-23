package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAnnouncementPolicyTest {
    @Test fun ordinaryTurnHasUsefulLeadAtThirtyKmh() {
        val t = NavigationAnnouncementPolicy.thresholds(30f / 3.6f, false)
        assertEquals(183.3f, t.advanceMeters, 0.5f)
        assertEquals(41.7f, t.confirmationMeters, 0.5f)
    }

    @Test fun ordinaryTurnScalesAtFiftyAndEightyKmh() {
        val at50 = NavigationAnnouncementPolicy.thresholds(50f / 3.6f, false)
        val at80 = NavigationAnnouncementPolicy.thresholds(80f / 3.6f, false)
        assertEquals(305.6f, at50.advanceMeters, 0.5f)
        assertEquals(69.4f, at50.confirmationMeters, 0.5f)
        assertEquals(488.9f, at80.advanceMeters, 0.5f)
        assertEquals(111.1f, at80.confirmationMeters, 0.5f)
    }

    @Test fun roundaboutGetsMoreAdvanceAndConfirmationTime() {
        val normal = NavigationAnnouncementPolicy.thresholds(50f / 3.6f, false)
        val roundabout = NavigationAnnouncementPolicy.thresholds(50f / 3.6f, true)
        assertEquals(361.1f, roundabout.advanceMeters, 0.5f)
        assertEquals(97.2f, roundabout.confirmationMeters, 0.5f)
        assertTrue(roundabout.advanceMeters > normal.advanceMeters)
        assertTrue(roundabout.confirmationMeters > normal.confirmationMeters)
    }

    @Test fun invalidSpeedAndExtremeSpeedAreBounded() {
        val invalid = NavigationAnnouncementPolicy.thresholds(Float.NaN, false)
        assertEquals(150f, invalid.advanceMeters, 0f)
        assertEquals(30f, invalid.confirmationMeters, 0f)
        val fast = NavigationAnnouncementPolicy.thresholds(100f, true)
        assertEquals(600f, fast.advanceMeters, 0f)
        assertEquals(180f, fast.confirmationMeters, 0f)
    }
}
