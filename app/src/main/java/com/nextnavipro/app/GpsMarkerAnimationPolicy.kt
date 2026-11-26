package com.nextnavipro.app

/**
 * Keeps the marker moving through almost the entire interval between accepted GPS fixes.
 *
 * A short fixed animation (100–220 ms) followed by a typical 700–900 ms pause makes
 * the arrow visibly advance in bursts when GNSS delivers roughly one fix per second.
 * We instead interpolate for most of the measured fix interval. The animation is
 * restarted from the currently displayed position when a newer fix arrives, so the
 * marker remains continuous without changing the measured location used by navigation.
 */
internal object GpsMarkerAnimationPolicy {
    private const val MIN_ANIMATION_MS = 80L
    private const val MAX_ANIMATION_MS = 2_200L
    private const val MAX_FIX_GAP_MS = 2_500L
    private const val INTERVAL_FRACTION = 0.92f

    fun durationMillis(elapsedBetweenFixesMs: Long): Long? {
        if (elapsedBetweenFixesMs <= 0L || elapsedBetweenFixesMs > MAX_FIX_GAP_MS) return null
        return (elapsedBetweenFixesMs * INTERVAL_FRACTION).toLong()
            .coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS)
    }
}
