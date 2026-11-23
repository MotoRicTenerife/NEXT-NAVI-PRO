package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSpeechQueuePolicyTest {
    private fun event(key: String, id: String, priority: Int, sequence: Long, expiresAt: Long = 10_000L) =
        NavigationSpeechEvent(key, id, id, priority, 0L, expiresAt, sequence, 0.96f)

    @Test fun newestPendingEventReplacesOlderEventForSameManeuver() {
        val old = event("turn-1", "turn-1-stage-1", 60, 1)
        val fresh = event("turn-1", "turn-1-stage-3", 60, 2)
        val merged = NavigationSpeechQueuePolicy.merge(listOf(old), fresh, 100L, 5)
        assertEquals(1, merged.size)
        assertEquals("turn-1-stage-3", merged.single().eventId)
    }

    @Test fun expiredEventsAreRemovedBeforeSelection() {
        val expired = event("radar-1", "radar-1", 80, 1, 100L)
        val fresh = event("turn-2", "turn-2-stage-1", 60, 2, 1_000L)
        val (next, remaining) = NavigationSpeechQueuePolicy.takeNext(listOf(expired, fresh), 100L)
        assertEquals("turn-2-stage-1", next?.eventId)
        assertTrue(remaining.isEmpty())
    }

    @Test fun higherPriorityEventsAreSelectedBeforeOlderGuidance() {
        val guidance = event("turn-1", "turn-1", 60, 1)
        val overspeed = event("overspeed", "overspeed", 100, 2)
        val (next, remaining) = NavigationSpeechQueuePolicy.takeNext(listOf(guidance, overspeed), 0L)
        assertEquals("overspeed", next?.eventId)
        assertEquals("turn-1", remaining.single().eventId)
    }

    @Test fun samePriorityEventsKeepFifoOrder() {
        val first = event("radar-1", "radar-1", 80, 1)
        val second = event("radar-2", "radar-2", 80, 2)
        val (next, _) = NavigationSpeechQueuePolicy.takeNext(listOf(second, first), 0L)
        assertEquals("radar-1", next?.eventId)
    }

    @Test fun boundedQueueDropsLowerPriorityEvents() {
        val low = event("start", "start", 20, 1)
        val normal = event("turn", "turn", 60, 2)
        val urgent = event("overspeed", "overspeed", 100, 3)
        val merged = NavigationSpeechQueuePolicy.merge(listOf(low, normal), urgent, 0L, 2)
        assertEquals(2, merged.size)
        assertNull(merged.find { it.key == "start" })
        assertTrue(merged.any { it.key == "overspeed" })
    }
}
