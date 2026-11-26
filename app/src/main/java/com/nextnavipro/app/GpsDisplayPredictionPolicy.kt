package com.nextnavipro.app

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Display-only dead reckoning for the map arrow between accepted GNSS fixes.
 * Its output must never be fed into currentLocation, route matching, speed limits,
 * radar checks, rerouting, GPX recording or navigation speech.
 */
internal object GpsDisplayPredictionPolicy {
    data class Position(val latitude: Double, val longitude: Double)

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_HORIZON_MILLIS = 1_500L
    private const val MAX_PREDICTION_METERS = 60.0
    private const val MAX_SPEED_MPS = 40.0
    private const val MAX_ACCURACY_METERS = 20.0
    private const val MAX_BEARING_ACCURACY_DEGREES = 25.0
    private const val MAX_HEADING_CHANGE_DEGREES = 25.0

    fun predict(
        latitude: Double,
        longitude: Double,
        speedMetersPerSecond: Float,
        bearingDegrees: Float,
        accuracyMeters: Float?,
        bearingAccuracyDegrees: Float?,
        headingChangeDegrees: Float,
        horizonMillis: Long
    ): Position? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0) return null
        val speed = speedMetersPerSecond.toDouble()
        val bearing = bearingDegrees.toDouble()
        val accuracy = accuracyMeters?.toDouble()
        val bearingAccuracy = bearingAccuracyDegrees?.toDouble()
        if (!speed.isFinite() || speed !in 3.0..MAX_SPEED_MPS ||
            !bearing.isFinite() || bearing !in 0.0..360.0 ||
            accuracy == null || !accuracy.isFinite() || accuracy > MAX_ACCURACY_METERS ||
            bearingAccuracy == null || !bearingAccuracy.isFinite() ||
            bearingAccuracy > MAX_BEARING_ACCURACY_DEGREES ||
            !headingChangeDegrees.isFinite() ||
            kotlin.math.abs(headingChangeDegrees) > MAX_HEADING_CHANGE_DEGREES ||
            horizonMillis !in 200L..MAX_HORIZON_MILLIS) {
            return null
        }

        val requestedDistance = speed * (horizonMillis / 1_000.0)
        val distance = requestedDistance.coerceAtMost(MAX_PREDICTION_METERS)
        if (distance <= 0.0) return null
        val angularDistance = distance / EARTH_RADIUS_METERS
        val bearingRadians = Math.toRadians(bearing)
        val latitudeRadians = Math.toRadians(latitude)
        val longitudeRadians = Math.toRadians(longitude)

        val destinationLatitude = asin(
            sin(latitudeRadians) * cos(angularDistance) +
                cos(latitudeRadians) * sin(angularDistance) * cos(bearingRadians)
        )
        val destinationLongitude = longitudeRadians + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latitudeRadians),
            cos(angularDistance) - sin(latitudeRadians) * sin(destinationLatitude)
        )
        val normalizedLongitude = (Math.toDegrees(destinationLongitude) + 540.0) % 360.0 - 180.0
        return Position(Math.toDegrees(destinationLatitude), normalizedLongitude)
    }
}
