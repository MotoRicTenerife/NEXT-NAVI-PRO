package com.nextnavipro.app

import android.location.Location
import kotlin.math.*

/**
 * Conservative matching for user-drawn speed-limit segments.
 * A segment only matches when the fix is close to its polyline and the course
 * agrees with the local direction of that polyline. No straight-line chord is used.
 */
object ManualSpeedLimitMatcher {
    data class Match(val segment: ManualSpeedLimitSegment, val limitKmh: Int, val distanceMeters: Double)

    fun find(
        segments: List<ManualSpeedLimitSegment>,
        latitude: Double,
        longitude: Double,
        bearingDegrees: Float?,
        accuracyMeters: Float? = null
    ): Match? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            bearingDegrees == null || !bearingDegrees.isFinite()) return null
        val maxDistance = max(12.0, min(28.0, (accuracyMeters ?: 12f).toDouble() * 1.25))
        var best: Match? = null
        for (segment in segments) {
            if (segment.status != ManualSpeedLimitSegment.Status.CONFIRMED) continue
            for (i in 0 until segment.geometry.lastIndex) {
                val a = segment.geometry[i]
                val b = segment.geometry[i + 1]
                val projected = projectToSegment(latitude, longitude, a.latitude, a.longitude, b.latitude, b.longitude)
                if (projected.distanceMeters > maxDistance) continue
                val roadBearing = bearing(a.latitude, a.longitude, b.latitude, b.longitude)
                val delta = abs(((bearingDegrees - roadBearing + 540f) % 360f) - 180f)
                val limit = when {
                    delta <= 45f -> segment.forwardLimitKmh
                    delta >= 135f -> segment.reverseLimitKmh
                    else -> null
                } ?: continue
                val candidate = Match(segment, limit, projected.distanceMeters)
                if (best == null || candidate.distanceMeters < best.distanceMeters) best = candidate
            }
        }
        return best
    }

    private data class Projection(val distanceMeters: Double)

    private fun projectToSegment(lat: Double, lon: Double, lat1: Double, lon1: Double, lat2: Double, lon2: Double): Projection {
        val meanLat = Math.toRadians((lat1 + lat2 + lat) / 3.0)
        val scaleX = 111320.0 * cos(meanLat)
        val scaleY = 110540.0
        val ax = (lon1 - lon) * scaleX
        val ay = (lat1 - lat) * scaleY
        val bx = (lon2 - lon) * scaleX
        val by = (lat2 - lat) * scaleY
        val dx = bx - ax
        val dy = by - ay
        val denom = dx * dx + dy * dy
        val t = if (denom <= 1e-9) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
        return Projection(hypot(ax + t * dx, ay + t * dy))
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
