package com.nextnavipro.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Geometry-only projection. Temporal continuity and plausible progress are handled by
 * RouteProgressTracker so a single noisy fix cannot jump to a distant part of a loop.
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
        if (points.size < 2 || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val lastSegment = points.lastIndex - 1
        val start = startIndex.coerceIn(0, lastSegment)
        val end = endIndex.coerceIn(start, lastSegment)
        val metersPerDegreeLat = 111_132.0
        val metersPerDegreeLon = 111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        var best: Match? = null
        var bestScore = Double.POSITIVE_INFINITY
        val useBearing = bearingDegrees != null && bearingDegrees.isFinite()
        for (i in start..end) {
            val a = points[i]
            val b = points[i + 1]
            if (!a.latitude.isFinite() || !a.longitude.isFinite() || !b.latitude.isFinite() || !b.longitude.isFinite()) continue
            val ax = (a.longitude - longitude) * metersPerDegreeLon
            val ay = (a.latitude - latitude) * metersPerDegreeLat
            val bx = (b.longitude - longitude) * metersPerDegreeLon
            val by = (b.latitude - latitude) * metersPerDegreeLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared < 0.01) continue
            val fraction = (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val projectedX = ax + fraction * dx
            val projectedY = ay + fraction * dy
            val distance = sqrt(projectedX * projectedX + projectedY * projectedY)
            val segmentBearing = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
            val headingPenalty = if (useBearing) {
                val delta = kotlin.math.abs(((segmentBearing - bearingDegrees!! + 540.0) % 360.0) - 180.0)
                // Direction helps distinguish carriageways, but must not overpower position.
                delta * 0.25
            } else 0.0
            val score = distance + headingPenalty
            if (score < bestScore) {
                bestScore = score
                best = Match(i, distance, segmentBearing, fraction)
            }
        }
        return best
    }
}

/**
 * Stateful route matcher that limits each new fix to physically plausible progress from the
 * previous fix. Replaying the same timestamp/fix is idempotent, which is useful when guidance
 * and ETA both request a match for the same Android Location.
 */
internal class RouteProgressTracker {
    data class Match(val segmentIndex: Int, val distanceMeters: Double, val alongRouteMeters: Double)

    private var cachedPoints: List<NavPoint>? = null
    private var cumulativeMeters = DoubleArray(0)
    private var lastAlongMeters: Double? = null
    private var lastElapsedMs: Long = 0L
    private var lastLatitude = Double.NaN
    private var lastLongitude = Double.NaN
    private var lastBearing: Float? = null
    private var lastResult: Match? = null

    fun reset() {
        cachedPoints = null
        cumulativeMeters = DoubleArray(0)
        lastAlongMeters = null
        lastElapsedMs = 0L
        lastLatitude = Double.NaN
        lastLongitude = Double.NaN
        lastBearing = null
        lastResult = null
    }

    fun match(
        points: List<NavPoint>,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        bearingDegrees: Float?,
        speedMetersPerSecond: Double?,
        elapsedRealtimeMs: Long
    ): Match? {
        if (points.size < 2 || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracyMeters.isFinite() || accuracyMeters < 0.0) return null
        if (cachedPoints !== points) {
            cachedPoints = points
            cumulativeMeters = DoubleArray(points.size)
            for (i in 1 until points.size) {
                cumulativeMeters[i] = cumulativeMeters[i - 1] +
                    GpxRouteService.distanceMeters(points[i - 1], points[i])
            }
            lastAlongMeters = null
            lastElapsedMs = 0L
            lastResult = null
        }
        if (elapsedRealtimeMs == lastElapsedMs && latitude == lastLatitude && longitude == lastLongitude &&
            bearingDegrees == lastBearing && lastResult != null) return lastResult

        val lastSegment = points.lastIndex - 1
        val previousAlong = lastAlongMeters
        val dt = if (lastElapsedMs > 0L && elapsedRealtimeMs >= lastElapsedMs)
            ((elapsedRealtimeMs - lastElapsedMs) / 1000.0).coerceIn(0.0, 10.0) else 1.0
        val maxForward = if (previousAlong == null) Double.POSITIVE_INFINITY else {
            val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0.0 } ?: 8.0
            // Allow for imperfect speed estimates and missed callbacks, but reject impossible jumps.
            (speed * dt * 2.5 + accuracyMeters * 1.5 + 20.0).coerceIn(35.0, 250.0)
        }
        val minAlong = if (previousAlong == null) 0.0 else (previousAlong - 20.0).coerceAtLeast(0.0)
        val maxAlong = if (previousAlong == null) cumulativeMeters.last()
            else (previousAlong + maxForward).coerceAtMost(cumulativeMeters.last())
        var start = 0
        var end = lastSegment
        if (previousAlong != null) {
            start = cumulativeMeters.indexOfLast { it <= minAlong }.coerceIn(0, lastSegment)
            end = cumulativeMeters.indexOfFirst { it >= maxAlong }.let {
                if (it < 0) lastSegment else (it - 1).coerceAtLeast(start)
            }.coerceIn(start, lastSegment)
            // Ensure a tiny route or a route segment longer than the movement window remains searchable.
            if (end < start) end = start
        }
        val heading = bearingDegrees?.takeIf { it.isFinite() && (speedMetersPerSecond ?: 0.0) >= 3.0 }
        val geometric = RouteMapMatcher.match(points, latitude, longitude, start, end, heading) ?: return null
        val along = cumulativeMeters[geometric.segmentIndex] +
            (cumulativeMeters[geometric.segmentIndex + 1] - cumulativeMeters[geometric.segmentIndex]) * geometric.fraction
        if (previousAlong != null && along > previousAlong + maxForward + 1.0) return null
        val result = Match(geometric.segmentIndex, geometric.distanceMeters, along)
        lastAlongMeters = maxOf(previousAlong ?: along, along)
        lastElapsedMs = elapsedRealtimeMs
        lastLatitude = latitude
        lastLongitude = longitude
        lastBearing = bearingDegrees
        lastResult = result
        return result
    }
}
