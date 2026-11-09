package com.nextnavipro.app

/**
 * Keeps marker animation aligned with the measured interval between accepted GPS fixes.
 *
 * A short floor avoids zero-length animations when providers deliver very fast updates.
 * A long-gap ceiling deliberately snaps to the latest accepted fix instead of drawing
 * a misleading multi-second interpolation after GPS has gone stale.
 */
internal object GpsMarkerAnimationPolicy {
    private const val MIN_ANIMATION_MS = 120L
    private const val MAX_ANIMATION_MS = 2_500L

    fun durationMillis(elapsedBetweenFixesMs: Long): Long? {
        if (elapsedBetweenFixesMs <= 0L || elapsedBetweenFixesMs > MAX_ANIMATION_MS) return null
        return elapsedBetweenFixesMs.coerceAtLeast(MIN_ANIMATION_MS)
    }
}
