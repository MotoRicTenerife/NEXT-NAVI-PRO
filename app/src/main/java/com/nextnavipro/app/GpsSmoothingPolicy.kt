package com.nextnavipro.app

import kotlin.math.exp

/**
 * Time-aware GPS position smoothing policy.
 *
 * The policy returns the fraction of the incoming fix to apply. It is deliberately
 * independent of Android Location so it can be exercised with deterministic tests.
 */
internal object GpsSmoothingPolicy {
    fun alpha(
        deltaSeconds: Float,
        displacementMeters: Float,
        accuracyMeters: Float?,
        speedMetersPerSecond: Float?,
        courseDeltaDegrees: Float?
    ): Float {
        val dt = deltaSeconds.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.1f, 2f) ?: 1f
        val distance = displacementMeters.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f } ?: 25f
        val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f }
        val courseDelta = courseDeltaDegrees?.takeIf { it.isFinite() } ?: 0f

        // Keep stronger low-pass filtering while stationary, but substantially reduce
        // accumulated lag at road speed. At 250 ms GNSS cadence, the former 0.38 s
        // time constant retained too much of the previous position on every fix.
        var timeConstant = when {
            speed == null -> 0.75f
            speed < 0.8f -> 1.4f
            speed < 2.5f -> 0.85f
            speed < 8f -> 0.55f
            else -> 0.18f
        }
        if (accuracy > 20f) timeConstant *= 1.15f

        var result = (1.0 - exp((-dt / timeConstant).toDouble())).toFloat()

        // Suppress small stationary wander, but do not freeze the marker when the
        // displacement is larger than the reported uncertainty envelope.
        if (speed != null && speed < 0.8f &&
            distance <= maxOf(4f, accuracy * 1.25f)) {
            result = minOf(result, 0.22f)
        }

        // At riding speed, do not allow the low-pass filter to create a persistent
        // visual trail. Keep this floor below the explicit turn/jump response below.
        if (speed != null && speed >= 8f) {
            result = maxOf(result, 0.82f)
        }

        // A clear displacement or course change is stronger evidence of real movement
        // than the low-pass prior: respond promptly instead of cutting a corner.
        if (distance > 12f || (speed != null && speed >= 2.5f && courseDelta >= 30f)) {
            result = maxOf(result, 0.88f)
        }

        return result.coerceIn(0.12f, 0.95f)
    }
}
