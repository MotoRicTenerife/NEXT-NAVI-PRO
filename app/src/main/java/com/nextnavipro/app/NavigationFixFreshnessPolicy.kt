package com.nextnavipro.app

/**
 * Rejects old position/speed samples before they can drive time-critical decisions.
 * At road speed a stale fix represents a much larger physical displacement.
 */
internal object NavigationFixFreshnessPolicy {
    const val MAX_LOW_SPEED_AGE_MS = 3_500L

    fun isFresh(ageMillis: Long, speedMetersPerSecond: Float = 0f): Boolean {
        if (ageMillis < 0L) return false
        val speed = speedMetersPerSecond.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val maxAge = when {
            speed >= 8f -> 1_800L
            speed >= 2.5f -> 2_500L
            else -> MAX_LOW_SPEED_AGE_MS
        }
        return ageMillis <= maxAge
    }
}
