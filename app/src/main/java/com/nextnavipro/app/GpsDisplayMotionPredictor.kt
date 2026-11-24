package com.nextnavipro.app

import android.location.Location
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bounded dead-reckoning used only by the map arrow.
 *
 * Navigation, road matching, speed limits, radar checks and trip logs must continue
 * to consume the accepted/filtered GPS fix, never this predicted display coordinate.
 */
internal object GpsDisplayMotionPredictor {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_FIX_AGE_MS = 1_000L
    private const val MAX_PREDICTION_MS = 650L

    fun predict(
        measured: Location,
        fixAgeMs: Long,
        courseDeltaDegrees: Float? = null
    ): Location {
        val result = Location(measured)
        if (!measured.hasSpeed() || !measured.hasBearing() || !measured.hasAccuracy()) return result
        if (fixAgeMs !in 0L..MAX_FIX_AGE_MS || measured.accuracy > 20f) return result
        if (!measured.speed.isFinite() || measured.speed < 2.5f) return result
        if (!measured.bearing.isFinite()) return result
        if (measured.hasBearingAccuracy() && measured.bearingAccuracyDegrees > 35f) return result

        val turnDelta = courseDeltaDegrees?.takeIf { it.isFinite() } ?: 0f
        val turnFactor = when {
            turnDelta >= 35f -> 0.25f
            turnDelta >= 20f -> 0.55f
            else -> 1f
        }
        val leadMs = if (measured.speed >= 8f) 220L else 120L
        val horizonMs = (fixAgeMs + (leadMs * turnFactor).toLong())
            .coerceAtMost(MAX_PREDICTION_MS)
        if (horizonMs < 40L) return result

        val distanceMeters = measured.speed.toDouble() * horizonMs / 1_000.0
        val bearingRadians = measured.bearing * PI / 180.0
        val latitude1 = measured.latitude * PI / 180.0
        val longitude1 = measured.longitude * PI / 180.0
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS

        val latitude2 = asin(
            sin(latitude1) * cos(angularDistance) +
                cos(latitude1) * sin(angularDistance) * cos(bearingRadians)
        )
        val longitude2 = longitude1 + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latitude1),
            cos(angularDistance) - sin(latitude1) * sin(latitude2)
        )
        result.latitude = latitude2 * 180.0 / PI
        result.longitude = ((longitude2 * 180.0 / PI + 540.0) % 360.0) - 180.0
        return result
    }
}
