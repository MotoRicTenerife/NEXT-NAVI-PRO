package com.nextnavipro.app

/**
 * Uses a short correction animation when prediction is unavailable.
 * With a reliable display-only prediction, the animation spans that predicted horizon
 * so the marker advances continuously at the estimated vehicle speed between fixes.
 */
internal object GpsMarkerAnimationPolicy {
    private const val MIN_CORRECTION_ANIMATION_MS = 100L
    private const val MAX_CORRECTION_ANIMATION_MS = 220L
    private const val MAX_FIX_GAP_MS = 2_500L
    private const val MAX_PREDICTION_ANIMATION_MS = 1_500L

    fun durationMillis(elapsedBetweenFixesMs: Long, predictionHorizonMs: Long? = null): Long? {
        if (elapsedBetweenFixesMs <= 0L || elapsedBetweenFixesMs > MAX_FIX_GAP_MS) return null
        if (predictionHorizonMs != null) {
            if (predictionHorizonMs !in 200L..MAX_PREDICTION_ANIMATION_MS) return null
            return predictionHorizonMs
        }
        return (elapsedBetweenFixesMs * 0.8f).toLong()
            .coerceIn(MIN_CORRECTION_ANIMATION_MS, MAX_CORRECTION_ANIMATION_MS)
    }
}
