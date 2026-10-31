package com.nextnavipro.app

/**
 * Deterministic, non-LLM traffic advisory policy.
 *
 * Geometry matching and provider I/O belong to adapters. This engine accepts only
 * already route-matched, direction-checked and timestamped evidence. It never changes
 * the active route; it produces an expiring recommendation for rider confirmation.
 */
internal object TrafficDecisionEngine {
    enum class Severity { MODERATE, HEAVY, CLOSED }
    enum class Action { WARN_HAZARD, OFFER_ALTERNATIVE }

    data class Hazard(
        val id: String,
        val distanceAheadMeters: Double,
        val severity: Severity,
        val confidence: Double,
        val observedAtEpochMs: Long,
        val validUntilEpochMs: Long,
        val directionMatches: Boolean,
        val estimatedDelaySeconds: Int
    )

    data class RouteEstimate(
        val routeId: String,
        val etaSeconds: Int,
        val distanceMeters: Double,
        val confidence: Double,
        val observedAtEpochMs: Long,
        val hasConfirmedClosure: Boolean
    )

    data class Context(
        val activeRouteId: String,
        val routeVersion: String,
        val activeRoute: RouteEstimate,
        val currentSpeedMetersPerSecond: Double?,
        val nowEpochMs: Long,
        val hazards: List<Hazard>,
        val alternatives: List<RouteEstimate>
    )

    data class Advisory(
        val id: String,
        val action: Action,
        val hazardId: String,
        val hazardDistanceMeters: Int,
        val timeToHazardSeconds: Int?,
        val severity: Severity,
        val expectedDelaySeconds: Int,
        val alternativeRouteId: String?,
        val alternativeEtaSeconds: Int?,
        val estimatedSavingsSeconds: Int,
        val expiresAtEpochMs: Long
    )

    private const val MAX_HAZARD_AGE_MS = 180_000L
    private const val MAX_ROUTE_ESTIMATE_AGE_MS = 180_000L
    private const val MAX_FUTURE_SKEW_MS = 30_000L
    private const val MIN_HAZARD_CONFIDENCE = 0.60
    private const val MIN_ROUTE_CONFIDENCE = 0.65
    private const val MAX_HAZARD_DISTANCE_METERS = 10_000.0
    private const val MAX_HAZARD_TIME_SECONDS = 8 * 60
    private const val MIN_DELAY_TO_WARN_SECONDS = 120
    private const val MIN_SAVINGS_SECONDS = 180
    private const val MIN_SAVINGS_RATIO = 0.08
    private const val ADVISORY_TTL_MS = 90_000L

    fun evaluate(context: Context): Advisory? {
        if (context.activeRouteId.isBlank() || context.routeVersion.isBlank() ||
            context.nowEpochMs <= 0L || !validRoute(context.activeRoute, context.nowEpochMs) ||
            context.activeRoute.routeId != context.activeRouteId ||
            context.activeRoute.hasConfirmedClosure
        ) return null

        val hazard = context.hazards.asSequence()
            .filter { validHazard(it, context.nowEpochMs) }
            .filter { it.distanceAheadMeters <= MAX_HAZARD_DISTANCE_METERS }
            .filter { hazardWithinDecisionWindow(it, context.currentSpeedMetersPerSecond) }
            .maxWithOrNull(compareBy<Hazard> { severityRank(it.severity) }
                .thenBy { it.estimatedDelaySeconds }
                .thenBy { -it.distanceAheadMeters }) ?: return null

        val bestAlternative = context.alternatives.asSequence()
            .filter { it.routeId.isNotBlank() && it.routeId != context.activeRouteId }
            .filter { validRoute(it, context.nowEpochMs) && !it.hasConfirmedClosure }
            .filter { it.confidence >= MIN_ROUTE_CONFIDENCE }
            .filter { it.observedAtEpochMs <= hazard.validUntilEpochMs }
            .filter {
                val savings = context.activeRoute.etaSeconds - it.etaSeconds
                savings >= MIN_SAVINGS_SECONDS &&
                    savings.toDouble() / context.activeRoute.etaSeconds.toDouble() >= MIN_SAVINGS_RATIO
            }
            .minByOrNull { it.etaSeconds }

        val action = if (bestAlternative != null) Action.OFFER_ALTERNATIVE else Action.WARN_HAZARD
        val savings = bestAlternative?.let { context.activeRoute.etaSeconds - it.etaSeconds } ?: 0
        val fingerprint = listOf(
            context.routeVersion, hazard.id, action.name, bestAlternative?.routeId.orEmpty()
        ).joinToString("|")
        return Advisory(
            id = fingerprint,
            action = action,
            hazardId = hazard.id,
            hazardDistanceMeters = hazard.distanceAheadMeters.toInt(),
            timeToHazardSeconds = timeToHazardSeconds(hazard.distanceAheadMeters, context.currentSpeedMetersPerSecond),
            severity = hazard.severity,
            expectedDelaySeconds = hazard.estimatedDelaySeconds,
            alternativeRouteId = bestAlternative?.routeId,
            alternativeEtaSeconds = bestAlternative?.etaSeconds,
            estimatedSavingsSeconds = savings,
            expiresAtEpochMs = minOf(
                context.nowEpochMs + ADVISORY_TTL_MS,
                hazard.validUntilEpochMs
            )
        )
    }

    private fun validHazard(hazard: Hazard, now: Long): Boolean =
        hazard.id.isNotBlank() &&
            hazard.distanceAheadMeters.isFinite() && hazard.distanceAheadMeters >= 0.0 &&
            hazard.confidence.isFinite() && hazard.confidence >= MIN_HAZARD_CONFIDENCE &&
            hazard.directionMatches &&
            hazard.observedAtEpochMs > 0L &&
            hazard.observedAtEpochMs <= now + MAX_FUTURE_SKEW_MS &&
            now - hazard.observedAtEpochMs <= MAX_HAZARD_AGE_MS &&
            hazard.validUntilEpochMs >= now &&
            hazard.estimatedDelaySeconds >= 0

    private fun validRoute(route: RouteEstimate, now: Long): Boolean =
        route.routeId.isNotBlank() &&
            route.etaSeconds > 0 &&
            route.distanceMeters.isFinite() && route.distanceMeters > 0.0 &&
            route.confidence.isFinite() && route.confidence in 0.0..1.0 &&
            route.observedAtEpochMs > 0L &&
            route.observedAtEpochMs <= now + MAX_FUTURE_SKEW_MS &&
            now - route.observedAtEpochMs <= MAX_ROUTE_ESTIMATE_AGE_MS

    private fun hazardWithinDecisionWindow(hazard: Hazard, speed: Double?): Boolean {
        if (hazard.severity == Severity.CLOSED) return true
        if (hazard.estimatedDelaySeconds < MIN_DELAY_TO_WARN_SECONDS &&
            hazard.severity == Severity.MODERATE) return false
        val time = timeToHazardSeconds(hazard.distanceAheadMeters, speed)
        return if (time != null) time <= MAX_HAZARD_TIME_SECONDS
            else hazard.distanceAheadMeters <= 1_200.0
    }

    private fun timeToHazardSeconds(distanceMeters: Double, speed: Double?): Int? {
        val validSpeed = speed?.takeIf { it.isFinite() && it >= 1.0 } ?: return null
        return (distanceMeters / validSpeed).toInt().coerceAtLeast(0)
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.MODERATE -> 1
        Severity.HEAVY -> 2
        Severity.CLOSED -> 3
    }
}

/** Suppresses repeated prompts for the same route/hazard/alternative decision. */
internal class TrafficAdvisoryMemory(
    private val cooldownMs: Long = 180_000L
) {
    private var lastAdvisoryId: String? = null
    private var lastAnnouncedAtEpochMs: Long = Long.MIN_VALUE

    fun shouldAnnounce(advisory: TrafficDecisionEngine.Advisory, nowEpochMs: Long): Boolean {
        if (nowEpochMs <= 0L || advisory.id.isBlank() || advisory.expiresAtEpochMs < nowEpochMs) return false
        val duplicate = advisory.id == lastAdvisoryId &&
            nowEpochMs >= lastAnnouncedAtEpochMs &&
            nowEpochMs - lastAnnouncedAtEpochMs < cooldownMs
        if (duplicate) return false
        lastAdvisoryId = advisory.id
        lastAnnouncedAtEpochMs = nowEpochMs
        return true
    }

    fun reset() {
        lastAdvisoryId = null
        lastAnnouncedAtEpochMs = Long.MIN_VALUE
    }
}
