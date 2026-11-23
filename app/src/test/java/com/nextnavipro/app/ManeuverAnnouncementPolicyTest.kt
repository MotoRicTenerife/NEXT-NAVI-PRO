package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverAnnouncementPolicyTest {
    @Test fun leadDistancesGrowWithSpeedForThirtyFiftyAndEightyKmh() {
        val a = ManeuverAnnouncementPolicy.thresholds(30f / 3.6f, 5f, false)
        val b = ManeuverAnnouncementPolicy.thresholds(50f / 3.6f, 5f, false)
        val c = ManeuverAnnouncementPolicy.thresholds(80f / 3.6f, 5f, false)
        assertTrue(a.advanceMeters < b.advanceMeters && b.advanceMeters < c.advanceMeters)
        assertTrue(a.prepareMeters < b.prepareMeters && b.prepareMeters < c.prepareMeters)
        assertTrue(a.imminentMeters < b.imminentMeters && b.imminentMeters < c.imminentMeters)
    }

    @Test fun roundaboutsGetMoreLeadTimeAndThresholdsStayOrdered() {
        val normal = ManeuverAnnouncementPolicy.thresholds(80f / 3.6f, 5f, false)
        val roundabout = ManeuverAnnouncementPolicy.thresholds(80f / 3.6f, 5f, true)
        assertTrue(normal.advanceMeters > normal.prepareMeters)
        assertTrue(normal.prepareMeters > normal.imminentMeters)
        assertTrue(roundabout.advanceMeters > normal.advanceMeters)
        assertTrue(roundabout.prepareMeters > normal.prepareMeters)
        assertTrue(roundabout.imminentMeters > normal.imminentMeters)
    }

    @Test fun stagesAdvanceAsTheTurnApproaches() {
        val x = ManeuverAnnouncementPolicy.thresholds(50f / 3.6f, 5f, false)
        assertEquals(0, ManeuverAnnouncementPolicy.stage(x.advanceMeters + 1.0, x))
        assertEquals(1, ManeuverAnnouncementPolicy.stage(x.advanceMeters.toDouble(), x))
        assertEquals(2, ManeuverAnnouncementPolicy.stage(x.prepareMeters.toDouble(), x))
        assertEquals(3, ManeuverAnnouncementPolicy.stage(x.imminentMeters.toDouble(), x))
        assertEquals(0, ManeuverAnnouncementPolicy.stage(-1.0, x))
    }

    @Test fun stationaryApproachUsesShorterDistances() {
        val stopped = ManeuverAnnouncementPolicy.thresholds(0f, 5f, false)
        val moving = ManeuverAnnouncementPolicy.thresholds(80f / 3.6f, 5f, false)
        assertTrue(stopped.advanceMeters < moving.advanceMeters)
        assertTrue(stopped.imminentMeters < moving.imminentMeters)
    }

    @Test fun guidanceFixOlderThanThreeAndAHalfSecondsIsRejected() {
        assertTrue(ManeuverAnnouncementPolicy.isFixFresh(3_500L))
        assertFalse(ManeuverAnnouncementPolicy.isFixFresh(3_501L))
        assertFalse(ManeuverAnnouncementPolicy.isFixFresh(-1L))
    }
}
