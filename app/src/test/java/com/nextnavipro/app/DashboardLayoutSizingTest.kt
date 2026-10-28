package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutSizingTest {
    @Test fun standardPortraitKeepsDesignDimensions() {
        assertEquals(1f, DashboardLayoutSizing.visualScale(800, true), 0.001f)
        assertEquals(184, DashboardLayoutSizing.visualDp(184, 800, true))
    }

    @Test fun compactLandscapeScalesVisualDimensionsWithoutGoingBelowFloor() {
        assertEquals(0.82f, DashboardLayoutSizing.visualScale(300, false), 0.001f)
        assertEquals(150, DashboardLayoutSizing.visualDp(184, 300, false))
    }

    @Test fun compactPortraitScalesOnlyWhenHeightIsBelowReference() {
        assertEquals(700f / 760f, DashboardLayoutSizing.visualScale(700, true), 0.001f)
        assertEquals(644, DashboardLayoutSizing.visualDp(700, 700, true))
    }

    @Test fun invalidHeightFallsBackToUnscaledDimensions() {
        assertEquals(1f, DashboardLayoutSizing.visualScale(0, false), 0.001f)
    }
}
