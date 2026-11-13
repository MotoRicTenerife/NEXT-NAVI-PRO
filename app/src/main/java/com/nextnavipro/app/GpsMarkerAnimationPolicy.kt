package com.nextnavipro.app

/**
 * Keeps marker interpolation bounded by the high-rate GNSS update cadence.
 * Position filtering is performed before this policy; animation must not become a
 * second long low-pass filter that leaves the rendered marker behind the accepted fix.
 */
internal object GpsMarkerAnimationPolicy {
    fun durationMillis(movingTimeMs: Long, distanceMeters: Float, speedMetersPerSecond: Float): Long {
        val validInterval = movingTimeMs in 100L..5_000L
        val estimatedInterval = if (distanceMeters.isFinite() && distanceMeters >= 0f &&
            speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0f) {
            ((distanceMeters / maxOf(speedMetersPerSecond, 5f)) * 1_000f).toLong()
        } else {
            250L
        }
        val interval = if (validInterval) movingTimeMs else estimatedInterval
        return (interval * 0.8f).toLong().coerceIn(100L, 220L)
    }
}
