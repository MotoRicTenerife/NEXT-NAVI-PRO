package com.nextnavipro.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class GpsMotionEstimate(
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val speedInferred: Boolean,
    val bearingInferred: Boolean,
    val source: String
)

/** Fills missing/non-finite GNSS speed and course using consecutive accepted positions. */
internal class GpsMotionEstimator {
    private data class Fix(val latitude: Double, val longitude: Double, val elapsedRealtimeMs: Long)
    private var previous: Fix? = null

    fun reset() { previous = null }

    fun update(
        latitude: Double,
        longitude: Double,
        elapsedRealtimeMs: Long,
        reportedSpeedMetersPerSecond: Float?,
        reportedBearingDegrees: Float?
    ): GpsMotionEstimate {
        val reportedSpeed = reportedSpeedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..100f }
        val reportedBearing = reportedBearingDegrees?.takeIf { it.isFinite() && it >= 0f && it <= 360f }
            ?.let { if (it >= 360f) 0f else it }
        val validPosition = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 && elapsedRealtimeMs > 0L
        var derivedSpeed: Float? = null
        var derivedBearing: Float? = null

        if (validPosition) {
            val old = previous
            // Out-of-order fixes must not move the estimator's baseline backwards in time.
            // Otherwise the next sample could derive speed/course from a stale or future fix.
            val monotonic = old == null || elapsedRealtimeMs > old.elapsedRealtimeMs
            if (monotonic) {
                if (old != null) {
                    val dtMs = elapsedRealtimeMs - old.elapsedRealtimeMs
                    if (dtMs in 250L..10_000L) {
                        val distance = distanceMeters(old.latitude, old.longitude, latitude, longitude)
                        val dtSeconds = dtMs / 1000.0
                        val speed = distance / dtSeconds
                        // Only derive either speed or bearing from a physically plausible displacement.
                        if (distance.isFinite() && speed.isFinite() && speed in 0.0..80.0) {
                            if (distance >= 1.0) derivedSpeed = speed.toFloat()
                            if (distance >= 2.0) derivedBearing = bearingDegrees(old.latitude, old.longitude, latitude, longitude)
                        }
                    }
                }
                previous = Fix(latitude, longitude, elapsedRealtimeMs)
            }
        }

        val speed = reportedSpeed ?: derivedSpeed
        val bearing = reportedBearing ?: derivedBearing
        val speedInferred = reportedSpeed == null && derivedSpeed != null
        val bearingInferred = reportedBearing == null && derivedBearing != null
        val source = when {
            speedInferred || bearingInferred -> "POSITION_DIFFERENTIAL"
            reportedSpeed != null && reportedBearing != null -> "GNSS"
            reportedSpeed != null || reportedBearing != null -> "GNSS_PARTIAL"
            else -> "UNAVAILABLE"
        }
        return GpsMotionEstimate(speed, bearing, speedInferred, bearingInferred, source)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2.0) * sin(dLon / 2.0)
        return 2.0 * radius * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1.0 - a).coerceIn(0.0, 1.0)))
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
