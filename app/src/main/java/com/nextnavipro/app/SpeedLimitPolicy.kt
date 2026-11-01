package com.nextnavipro.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A GPS fix reduced to what the speed-limit policy needs. [bearingDeg] is null when the course is
 * not reliable (slow or unknown), so a missing course never counts as "straight ahead".
 */
data class SpeedLimitFix(
    val latitude: Double,
    val longitude: Double,
    val bearingDeg: Float?,
    val timeMs: Long
)

/**
 * Pure decision rules for provider speed-limit lookups (no Android dependencies, unit-tested).
 *
 * Two questions are answered here:
 *  1. [shouldQuery]: is it time to ask the provider again? (keeps quota use proportional to the
 *     distance actually travelled instead of asking every few seconds);
 *  2. [isLimitApplicable]: may a limit that was verified at [SpeedLimitFix] still be shown (or a late
 *     answer be applied) at the current position? A verified limit is only carried forward while the
 *     vehicle is still on the same line of travel and within a bounded distance and age; anything
 *     else becomes "unknown", never a default value.
 */
object SpeedLimitPolicy {
    const val MIN_QUERY_SPACING_MS = 3_000L
    const val REFRESH_DISTANCE_M = 250.0
    const val REFRESH_INTERVAL_MS = 20_000L
    const val STATIONARY_RADIUS_M = 30.0
    const val VALID_DISTANCE_M = 450.0
    const val VALID_AGE_MS = 45_000L
    const val SLOW_VALID_DISTANCE_M = 60.0
    const val CROSS_TRACK_TOLERANCE_M = 25.0
    const val BACKWARD_TOLERANCE_M = 15.0
    const val MAX_BEARING_DELTA_DEG = 60.0
    const val MIN_TRACE_DISTANCE_M = 12.0

    private const val METERS_PER_DEGREE = 111_320.0

    /** North/east offset in metres from [from] to [to] (equirectangular, fine below ~1 km). */
    private fun offsetMeters(from: SpeedLimitFix, to: SpeedLimitFix): Pair<Double, Double> {
        val north = (to.latitude - from.latitude) * METERS_PER_DEGREE
        val east = (to.longitude - from.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(from.latitude))
        return Pair(north, east)
    }

    fun distanceMeters(from: SpeedLimitFix, to: SpeedLimitFix): Double {
        val offset = offsetMeters(from, to)
        return hypot(offset.first, offset.second)
    }

    /**
     * TomTom Snap-to-Roads needs a short movement trace, not a duplicated first GPS fix.
     * Defer the initial lookup until two accepted fixes span a meaningful distance; callers
     * must not count a deferred lookup against the provider refresh cadence.
     */
    fun hasUsableTrace(points: List<SpeedLimitFix>): Boolean {
        if (points.size < 2) return false
        return distanceMeters(points.first(), points.last()) >= MIN_TRACE_DISTANCE_M
    }

    private fun bearingDelta(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    /**
     * Whether a limit verified at [query] still describes the road at [now].
     * Stationary vehicles keep the limit (same road). Moving ones keep it only within
     * [VALID_DISTANCE_M] / [VALID_AGE_MS], laterally within [CROSS_TRACK_TOLERANCE_M] of the line of
     * travel at verification time (rules out parallel roads) and without a turn above
     * [MAX_BEARING_DELTA_DEG].
     */
    fun isLimitApplicable(query: SpeedLimitFix, now: SpeedLimitFix): Boolean {
        val offset = offsetMeters(query, now)
        val north = offset.first
        val east = offset.second
        val distance = hypot(north, east)
        if (distance <= STATIONARY_RADIUS_M) return true
        if (now.timeMs - query.timeMs > VALID_AGE_MS) return false
        if (distance > VALID_DISTANCE_M) return false
        val queryBearing = query.bearingDeg ?: return distance <= SLOW_VALID_DISTANCE_M
        val radians = Math.toRadians(queryBearing.toDouble())
        val along = north * cos(radians) + east * sin(radians)
        val cross = east * cos(radians) - north * sin(radians)
        if (along < -BACKWARD_TOLERANCE_M || abs(cross) > CROSS_TRACK_TOLERANCE_M) return false
        val nowBearing = now.bearingDeg
        if (nowBearing != null && bearingDelta(queryBearing.toDouble(), nowBearing.toDouble()) > MAX_BEARING_DELTA_DEG) {
            return false
        }
        return true
    }

    /**
     * Whether the provider chain should run again. [last] is the fix of the previous lookup (null if
     * none yet). Asks again after [REFRESH_DISTANCE_M] of travel, after [REFRESH_INTERVAL_MS] if the
     * vehicle is actually moving, or at once when the previous result no longer applies (turn onto a
     * different road), never faster than [MIN_QUERY_SPACING_MS].
     */
    fun shouldQuery(last: SpeedLimitFix?, now: SpeedLimitFix): Boolean {
        if (last == null) return true
        val age = now.timeMs - last.timeMs
        if (age < MIN_QUERY_SPACING_MS) return false
        val distance = distanceMeters(last, now)
        if (distance >= REFRESH_DISTANCE_M) return true
        if (age >= REFRESH_INTERVAL_MS && distance > STATIONARY_RADIUS_M) return true
        return !isLimitApplicable(last, now)
    }
}

/**
 * Backoff for the paid provider path. A 429 honours the server's Retry-After; timeouts and network
 * errors back off 8, 16, 32, then 60 seconds. While blocked the app skips TomTom rather than
 * hammering a service that already said no; automatic limits remain unknown until verified again.
 */
class ProviderBackoff {
    private var failures = 0
    private var blockedUntilMs = 0L

    @Synchronized
    fun isBlocked(nowMs: Long): Boolean = nowMs < blockedUntilMs

    @Synchronized
    fun remainingMs(nowMs: Long): Long = (blockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized
    fun onSuccess() {
        failures = 0
        blockedUntilMs = 0L
    }

    @Synchronized
    fun onRateLimited(retryAfterSec: Int?, nowMs: Long) {
        failures += 1
        val seconds = (retryAfterSec ?: defaultDelaySeconds(failures)).coerceIn(5, 600)
        blockedUntilMs = nowMs + seconds * 1000L
    }

    @Synchronized
    fun onTransientFailure(nowMs: Long) {
        failures += 1
        blockedUntilMs = nowMs + defaultDelaySeconds(failures) * 1000L
    }

    private fun defaultDelaySeconds(failureCount: Int): Int =
        minOf(60, 8 shl (failureCount - 1).coerceIn(0, 3))
}
