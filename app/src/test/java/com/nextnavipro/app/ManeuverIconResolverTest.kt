package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverIconResolverTest {
    @Test fun roundaboutRequiresTypedRoundaboutManeuver() {
        assertTrue(ManeuverIconResolver.isRoundabout(26))
        assertTrue(ManeuverIconResolver.isRoundabout(27))
        assertFalse(ManeuverIconResolver.isRoundabout(23))
        assertFalse(ManeuverIconResolver.isRoundabout(24))
    }

    @Test fun typedForkDirectionWinsOverMisleadingInstructionText() {
        assertEquals("➡", ManeuverIconResolver.icon(23, "Prosegui nella rotonda"))
        assertEquals("⬅", ManeuverIconResolver.icon(24, "Prosegui nella rotatoria"))
    }

    @Test fun coversSharpLeftAndUTurnTypes() {
        assertEquals("⬅", ManeuverIconResolver.icon(14, "Svolta a sinistra"))
        assertEquals("↶", ManeuverIconResolver.icon(12, "Inversione"))
    }

    @Test fun usesInstructionTextOnlyWhenTypeIsUnknown() {
        assertEquals("➡", ManeuverIconResolver.icon(0, "Svolta a destra"))
        assertEquals("⬆", ManeuverIconResolver.icon(8, "Svolta a sinistra"))
    }
}
