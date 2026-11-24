package com.nextnavipro.app

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Coordinates projected for the map arrow only; never use as a navigation fix. */
internal data class GpsDisplayPoint(val latitude: Double, val longitude: Double)

/**
 * Bounded dead-reckoning used only by the map arrow.
 * Route matching, speed limits, radar checks, guidance and trip logs keep using measured fixes.
 */
internal object GpsDisplayMotionPredictor {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_FIX_AGE_MS = 1_000L
    private const val MAX_PREDICTION_MS = 650L

    fun predict(
        latitude: Double,
        longitude: Double,
        speedMetersPerSecond: Float?,
        bearingDegrees: Float?,
        accuracyMeters: Float?,
        bearingAccuracyDegrees: Float?,
        fixAgeMs: Long,
        courseDeltaDegrees: Float? = null
    ): GpsDisplayPoint? {
        val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 2.5f } ?: return null
        val bearing = bearingDegrees?.takeIf { it.isFinite() } ?: return null
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f } ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() || accuracy > 20f) return null
        if (bearingAccuracyDegrees != null && (!bearingAccuracyDegrees.isFinite() || bearingAccuracyDegrees > 35f)) return null
        if (fixAgeMs !in 0L..MAX_FIX_AGE_MS) return null

        val turnDelta = courseDeltaDegrees?.takeIf { it.isFinite() } ?: 0f
        val turnFactor = when {
            turnDelta >= 35f -> 0.25f
            turnDelta >= 20f -> 0.55f
            else -> 1f
        }
        val leadMs = if (speed >= 8f) 220L else 120L
        val horizonMs = (fixAgeMs + (leadMs * turnFactor).toLong())
            .coerceAtMost(MAX_PREDICTION_MS)
        if (horizonMs < 40L) return null

        val distanceMeters = speed.toDouble() * horizonMs / 1_000.0
        val bearingRadians = bearing * PI / 180.0
        val latitude1 = latitude * PI / 180.0
        val longitude1 = longitude * PI / 180.0
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val latitude2 = asin(
            sin(latitude1) * cos(angularDistance) +
                cos(latitude1) * sin(angularDistance) * cos(bearingRadians)
        )
        val longitude2 = longitude1 + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latitude1),
            cos(angularDistance) - sin(latitude1) * sin(latitude2)
        )
        return GpsDisplayPoint(
            latitude2 * 180.0 / PI,
            ((longitude2 * 180.0 / PI + 540.0) % 360.0) - 180.0
        )
    }
}
