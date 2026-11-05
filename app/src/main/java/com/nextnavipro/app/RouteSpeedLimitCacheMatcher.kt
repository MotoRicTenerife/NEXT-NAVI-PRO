package com.nextnavipro.app

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Selects a TomTom route-prefetch sample only when the current fix is confidently on
 * the planned geometry, has a compatible travel direction, and the sample is close
 * behind the matched progress along that route. Euclidean distance to a sparse sample
 * is deliberately not used: it can select a parallel carriageway's limit.
 */
internal object RouteSpeedLimitCacheMatcher {
    fun findEligibleSample(
        routePoints: List<NavPoint>,
        samples: List<TomTomRouteLimitSample>,
        progressIndex: Int,
        progressDistanceMeters: Float,
        accuracyMeters: Float?,
        bearingDegrees: Float?,
        speedMetersPerSecond: Float?,
        bearingAccuracyDegrees: Float? = null,
        maxSampleGapMeters: Double = 220.0
    ): TomTomRouteLimitSample? {
        if (routePoints.size < 2 || samples.isEmpty() ||
            progressIndex !in 0 until routePoints.lastIndex ||
            !progressDistanceMeters.isFinite() || accuracyMeters == null ||
            !accuracyMeters.isFinite() || accuracyMeters !in 0f..15f) return null

        val allowedLateralError = maxOf(8.0, minOf(18.0, accuracyMeters * 1.5))
        if (progressDistanceMeters.toDouble() > allowedLateralError) return null

        val moving = speedMetersPerSecond != null && speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= 3f
        if (moving) {
            if (bearingDegrees == null || !bearingDegrees.isFinite() ||
                (bearingAccuracyDegrees != null && (!bearingAccuracyDegrees.isFinite() || bearingAccuracyDegrees > 35f))) return null
            val routeBearing = bearing(routePoints[progressIndex], routePoints[progressIndex + 1])
            val delta = abs(((bearingDegrees - routeBearing + 540f) % 360f) - 180f)
            if (delta > 35f) return null
        }

        var best: TomTomRouteLimitSample? = null
        var bestGap = Double.POSITIVE_INFINITY
        for (sample in samples) {
            if (sample.speedLimitKmh == null || sample.routeIndex !in 0..progressIndex) continue
            var gap = 0.0
            for (i in sample.routeIndex until progressIndex) {
                gap += distanceMeters(routePoints[i], routePoints[i + 1])
                if (gap > maxSampleGapMeters) break
            }
            if (gap <= maxSampleGapMeters && gap < bestGap) {
                best = sample
                bestGap = gap
            }
        }
        return best
    }

    private fun bearing(a: NavPoint, b: NavPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun distanceMeters(a: NavPoint, b: NavPoint): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }
}
