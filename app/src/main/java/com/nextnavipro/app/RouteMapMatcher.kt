package com.nextnavipro.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Projects a GNSS fix onto route segments rather than only comparing it with sampled vertices.
 * Coordinates are projected to a local tangent plane; suitable for short road segments.
 */
internal object RouteMapMatcher {
    data class Match(val segmentIndex: Int, val distanceMeters: Double, val bearingDegrees: Double, val fraction: Double)

    fun match(
        points: List<NavPoint>,
        latitude: Double,
        longitude: Double,
        startIndex: Int = 0,
        endIndex: Int = points.lastIndex - 1,
        bearingDegrees: Float? = null
    ): Match? {
        if (points.size < 2 || !latitude.isFinite() || !longitude.isFinite()) return null
        val lastSegment = points.lastIndex - 1
        val start = startIndex.coerceIn(0, lastSegment)
        val end = endIndex.coerceIn(start, lastSegment)
        val metersPerDegreeLat = 111_132.0
        val metersPerDegreeLon = 111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val px = (longitude - longitude) * metersPerDegreeLon
        val py = (latitude - latitude) * metersPerDegreeLat
        var best: Match? = null
        var bestScore = Double.POSITIVE_INFINITY
        val useBearing = bearingDegrees != null && bearingDegrees.isFinite()
        for (i in start..end) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.longitude - longitude) * metersPerDegreeLon
            val ay = (a.latitude - latitude) * metersPerDegreeLat
            val bx = (b.longitude - longitude) * metersPerDegreeLon
            val by = (b.latitude - latitude) * metersPerDegreeLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared < 0.01) continue
            val fraction = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val projectedX = ax + fraction * dx
            val projectedY = ay + fraction * dy
            val distance = sqrt(projectedX * projectedX + projectedY * projectedY)
            val segmentBearing = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
            val angle = if (useBearing) {
                val delta = kotlin.math.abs(((segmentBearing - bearingDegrees!! + 540.0) % 360.0) - 180.0)
                // Heading is a soft discriminator, never enough to outweigh a grossly closer road.
                min(delta, 100.0) * if (delta > 100.0) 0.8 else 0.9
            } else 0.0
            val score = distance + angle
            if (score < bestScore) {
                bestScore = score
                best = Match(i, distance, segmentBearing, fraction)
            }
        }
        return best
    }
}
