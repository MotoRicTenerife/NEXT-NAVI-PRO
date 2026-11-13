package com.nextnavipro.app

/** Conservative visual-only snapping limits for closely spaced roads. */
internal object RouteSnapPolicy {
    fun maxSnapDistance(accuracyMeters: Float): Double {
        val accuracy = accuracyMeters.takeIf { it.isFinite() && it > 0f } ?: 3f
        return minOf(8.0, maxOf(3.0, accuracy.toDouble()))
    }

    fun isHeadingCompatible(deltaDegrees: Float): Boolean =
        deltaDegrees.isFinite() && deltaDegrees <= 30f
}
