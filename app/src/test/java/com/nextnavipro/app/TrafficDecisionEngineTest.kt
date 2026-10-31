package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficDecisionEngineTest {
    private val now = 1_800_000_000_000L

    private fun route(
        id: String,
        eta: Int,
        confidence: Double = 0.9,
        closed: Boolean = false,
        vehicleAccessVerified: Boolean = false
    ) = TrafficDecisionEngine.RouteEstimate(
        id, eta, 20_000.0, confidence, now, closed, vehicleAccessVerified
    )

    private fun hazard(
        id: String = "incident-1",
        distance: Double = 2_000.0,
        severity: TrafficDecisionEngine.Severity = TrafficDecisionEngine.Severity.HEAVY,
        confidence: Double = 0.9,
        observed: Long = now,
        validUntil: Long = now + 120_000L,
        direction: Boolean = true,
        delay: Int = 600
    ) = TrafficDecisionEngine.Hazard(id, distance, severity, confidence, observed, validUntil, direction, delay)

    private fun context(
        hazards: List<TrafficDecisionEngine.Hazard> = listOf(hazard()),
        alternatives: List<TrafficDecisionEngine.RouteEstimate> = emptyList(),
        speed: Double? = 20.0
    ) = TrafficDecisionEngine.Context("active", "route-v1", route("active", 2_400), speed, now, hazards, alternatives)

    @Test fun warnsAboutFreshHazardAheadWithRouteDistanceAndTime() {
        val result = TrafficDecisionEngine.evaluate(context())!!
        assertEquals(TrafficDecisionEngine.Action.WARN_HAZARD, result.action)
        assertEquals(2_000, result.hazardDistanceMeters)
        assertEquals(100, result.timeToHazardSeconds)
        assertEquals("incident-1", result.hazardId)
    }

    @Test fun offersOnlyMaterialFreshAlternativeAndNeverChangesRouteItself() {
        val result = TrafficDecisionEngine.evaluate(context(
            alternatives = listOf(route("alternative", 1_900, vehicleAccessVerified = true))
        ))!!
        assertEquals(TrafficDecisionEngine.Action.OFFER_ALTERNATIVE, result.action)
        assertEquals("alternative", result.alternativeRouteId)
        assertEquals(500, result.estimatedSavingsSeconds)
    }

    @Test fun neverOffersAlternativeWithoutExplicitVehicleAccessValidation() {
        val unverified = route("unverified", 1_500)
        val result = TrafficDecisionEngine.evaluate(context(alternatives = listOf(unverified)))!!
        assertEquals(TrafficDecisionEngine.Action.WARN_HAZARD, result.action)
        assertNull(result.alternativeRouteId)
    }

    @Test fun offersAlternativeOnlyAfterVehicleAccessValidation() {
        val verified = route("verified-motorcycle-route", 1_900, vehicleAccessVerified = true)
        val result = TrafficDecisionEngine.evaluate(context(alternatives = listOf(verified)))!!
        assertEquals(TrafficDecisionEngine.Action.OFFER_ALTERNATIVE, result.action)
        assertEquals("verified-motorcycle-route", result.alternativeRouteId)
    }

    @Test fun rejectsAlternativeThatSavesTooLittleOrHasClosure() {
        assertEquals(
            TrafficDecisionEngine.Action.WARN_HAZARD,
            TrafficDecisionEngine.evaluate(context(alternatives = listOf(route("small-save", 2_250))))!!.action
        )
        assertEquals(
            TrafficDecisionEngine.Action.WARN_HAZARD,
            TrafficDecisionEngine.evaluate(context(alternatives = listOf(route("closed", 1_500, closed = true))))!!.action
        )
    }

    @Test fun confirmedClosureOnActiveRouteIsNeverSilentlyDiscarded() {
        val closedActiveRoute = route("active", 2_400, closed = true)
        val closure = hazard(
            id = "active-route-closure",
            distance = 500.0,
            severity = TrafficDecisionEngine.Severity.CLOSED
        )
        val result = TrafficDecisionEngine.evaluate(
            context(hazards = listOf(closure)).copy(activeRoute = closedActiveRoute)
        )!!
        assertEquals(TrafficDecisionEngine.Action.WARN_HAZARD, result.action)
        assertEquals("active-route-closure", result.hazardId)
        assertNull(result.alternativeRouteId)
    }

    @Test fun confirmedClosureDoesNotProduceUnvalidatedDetourRecommendations() {
        val closedActiveRoute = route("active", 2_400, closed = true)
        val closure = hazard(distance = 500.0, severity = TrafficDecisionEngine.Severity.CLOSED)
        val unvalidatedAlternative = route("alternative", 900)
        val result = TrafficDecisionEngine.evaluate(
            context(hazards = listOf(closure), alternatives = listOf(unvalidatedAlternative))
                .copy(activeRoute = closedActiveRoute)
        )!!
        assertEquals(TrafficDecisionEngine.Action.WARN_HAZARD, result.action)
        assertNull(result.alternativeRouteId)
    }

    @Test fun confirmedClosureRequiresAValidClosureObservation() {
        val closedActiveRoute = route("active", 2_400, closed = true)
        assertNull(
            TrafficDecisionEngine.evaluate(context(hazards = listOf(hazard())).copy(activeRoute = closedActiveRoute))
        )
    }

    @Test fun rejectsStaleLowConfidenceOrWrongDirectionHazards() {
        val stale = hazard(observed = now - 181_000L)
        val weak = hazard(confidence = 0.4)
        val opposite = hazard(direction = false)
        assertNull(TrafficDecisionEngine.evaluate(context(hazards = listOf(stale))))
        assertNull(TrafficDecisionEngine.evaluate(context(hazards = listOf(weak))))
        assertNull(TrafficDecisionEngine.evaluate(context(hazards = listOf(opposite))))
    }

    @Test fun doesNotWarnAboutDistantMinorSlowdownOrNoisySpeed() {
        val minor = hazard(distance = 9_000.0, severity = TrafficDecisionEngine.Severity.MODERATE, delay = 60)
        assertNull(TrafficDecisionEngine.evaluate(context(hazards = listOf(minor), speed = 20.0)))
        val farWithNoSpeed = hazard(distance = 2_000.0, severity = TrafficDecisionEngine.Severity.HEAVY)
        assertNull(TrafficDecisionEngine.evaluate(context(hazards = listOf(farWithNoSpeed), speed = null)))
    }

    @Test fun closedRoadCanWarnEvenWhenSpeedIsUnavailable() {
        val closure = hazard(distance = 8_000.0, severity = TrafficDecisionEngine.Severity.CLOSED)
        assertEquals(
            TrafficDecisionEngine.Action.WARN_HAZARD,
            TrafficDecisionEngine.evaluate(context(hazards = listOf(closure), speed = null))!!.action
        )
    }

    @Test fun advisoryExpiresAtTheEarlierOfPolicyAndSourceValidity() {
        val shortLived = hazard(validUntil = now + 20_000L)
        assertEquals(now + 20_000L, TrafficDecisionEngine.evaluate(context(hazards = listOf(shortLived)))!!.expiresAtEpochMs)
    }

    @Test fun duplicateAdvisoriesAreSuppressedButNewRouteVersionCanAnnounce() {
        val first = TrafficDecisionEngine.evaluate(context())!!
        val memory = TrafficAdvisoryMemory()
        assertTrue(memory.shouldAnnounce(first, now))
        assertFalse(memory.shouldAnnounce(first, now + 1_000L))
        val changed = first.copy(id = "route-v2|incident-1|WARN_HAZARD|")
        assertTrue(memory.shouldAnnounce(changed, now + 2_000L))
        assertFalse(memory.shouldAnnounce(changed, now + 2_001L))
        memory.reset()
        assertTrue(memory.shouldAnnounce(first, now + 3_000L))
    }

    @Test fun rejectsInvalidActiveRouteAndStaleAlternative() {
        val badContext = context().copy(activeRoute = route("other", 2_400))
        assertNull(TrafficDecisionEngine.evaluate(badContext))
        val staleAlternative = route("alt", 1_500).copy(observedAtEpochMs = now - 181_000L)
        assertEquals(
            TrafficDecisionEngine.Action.WARN_HAZARD,
            TrafficDecisionEngine.evaluate(context(alternatives = listOf(staleAlternative)))!!.action
        )
    }
}
