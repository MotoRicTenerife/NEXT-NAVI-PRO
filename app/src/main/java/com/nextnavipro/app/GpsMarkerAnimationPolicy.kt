package com.nextnavipro.app

/**
 * Keeps marker interpolation shorter than normal GPS delivery intervals.
 * The GPS filter provides continuity; a long UI animation would become a second
 * low-pass filter and leave the rendered arrow behind the accepted fix.
 */
internal object GpsMarkerAnimationPolicy {
    private const val MIN_ANIMATION_MS = 100L
    private const val MAX_ANIMATION_MS = 220L
    private const val MAX_FIX_GAP_MS = 2_500L

    fun durationMillis(elapsedBetweenFixesMs: Long): Long? {
        if (elapsedBetweenFixesMs <= 0L || elapsedBetweenFixesMs > MAX_FIX_GAP_MS) return null
        return (elapsedBetweenFixesMs * 0.8f).toLong()
            .coerceIn(MIN_ANIMATION_MS, MAX_ANIMATION_MS)
    }
}
