package com.nextnavipro.app

/**
 * Small, deterministic math helpers used by the frame-driven GPS marker renderer.
 * These functions contain no Android or map-SDK dependencies so their edge cases can be unit-tested.
 */
internal object MarkerMotionMath {
    /** Returns the signed shortest rotation from [fromDegrees] to [toDegrees], in [-180, 180). */
    fun shortestBearingDelta(fromDegrees: Float, toDegrees: Float): Float {
        val raw = (toDegrees - fromDegrees) % 360f
        return ((raw + 540f) % 360f) - 180f
    }
}
