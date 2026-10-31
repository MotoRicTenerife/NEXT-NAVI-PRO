package com.nextnavipro.app

/**
 * Geometry-only projection. Temporal continuity and plausible progress are handled by
 * RouteProgressTracker so a single noisy fix cannot jump to a distant part of a loop.
 */
internal object RouteMapMatcher {
    data class Match(val segmentIndex: Int, val distanceMeters: Double, val bearingDegrees: Double, val fraction: Double, val score: Double)

    /** O(1) route-chainage lookup after geometry has been prepared by the matcher. */
    fun alongRouteDistance(points: List<NavPoint>, pointIndex: Int): Double {
        if (points.size < 2) return 0.0
        ensureGeometry(points)
        return cumulativeMeters[pointIndex.coerceIn(0, points.lastIndex)]
    }

    private fun ensureGeometry(points: List<NavPoint>) {
        if (cachedPoints === points) return
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

    fun match(
        points: List<NavPoint>, latitude: Double, longitude: Double,
        startIndex: Int = 0, endIndex: Int = points.lastIndex - 1, bearingDegrees: Float? = null
    ): Match? = candidates(points, latitude, longitude, startIndex, endIndex, bearingDegrees).minByOrNull { it.score }

    /** Returns every valid segment candidate so temporal evidence can disambiguate nearby roads. */
    fun candidates(
        points: List<NavPoint>, latitude: Double, longitude: Double,
        startIndex: Int = 0, endIndex: Int = points.lastIndex - 1, bearingDegrees: Float? = null
    ): List<Match> {
        if (points.size < 2 || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return emptyList()
        val lastSegment = points.lastIndex - 1
        val start = startIndex.coerceIn(0, lastSegment)
        val end = endIndex.coerceIn(start, lastSegment)
        val metersPerDegreeLat = 111_132.0
        val metersPerDegreeLon = 111_320.0 * kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val useBearing = bearingDegrees != null && bearingDegrees.isFinite()
        val result = ArrayList<Match>()
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
            val distance = kotlin.math.sqrt(projectedX * projectedX + projectedY * projectedY)
            val segmentBearing = (Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0) % 360.0
            val headingPenalty = if (useBearing) {
                val delta = kotlin.math.abs(((segmentBearing - bearingDegrees!! + 540.0) % 360.0) - 180.0)
                delta * 0.25
            } else 0.0
            result.add(Match(i, distance, segmentBearing, fraction, distance + headingPenalty))
        }
        return result
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
        ensureGeometry(points)
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
        val candidates = RouteMapMatcher.candidates(points, latitude, longitude, start, end, heading)
        if (candidates.isEmpty()) return null
        val maximumSnapDistance = minOf(30.0, maxOf(12.0, accuracyMeters * 1.5))
        val observedDistance = if (previousAlong != null && lastLatitude.isFinite() && lastLongitude.isFinite())
            GpxRouteService.distanceMeters(NavPoint(lastLatitude, lastLongitude), NavPoint(latitude, longitude)) else 0.0
        val emissionSigma = maxOf(5.0, accuracyMeters)
        val transitionSigma = maxOf(12.0, accuracyMeters * 1.5)
        var selected: Match? = null
        var selectedScore = Double.POSITIVE_INFINITY
        for (candidate in candidates) {
            if (candidate.distanceMeters > maximumSnapDistance) continue
            val along = cumulativeMeters[candidate.segmentIndex] +
                (cumulativeMeters[candidate.segmentIndex + 1] - cumulativeMeters[candidate.segmentIndex]) * candidate.fraction
            val progressDelta = if (previousAlong == null) 0.0 else along - previousAlong
            if (previousAlong != null && progressDelta < -20.0) continue
            if (previousAlong != null && progressDelta > maxForward + 1.0) continue
            // Gaussian emission likelihood plus a transition penalty comparing route progress
            // with observed displacement. This is an online probabilistic score, not a claim
            // of full-window HMM inference; recorded traces are still required for calibration.
            val normalizedDistance = candidate.distanceMeters / emissionSigma
            val emissionCost = 0.5 * normalizedDistance * normalizedDistance
            val headingCost = (candidate.score - candidate.distanceMeters) * 0.5
            val transitionCost = if (previousAlong == null) 0.0
                else kotlin.math.abs(progressDelta - observedDistance) / transitionSigma
            val score = emissionCost + headingCost + transitionCost
            if (score < selectedScore) {
                selectedScore = score
                selected = Match(candidate.segmentIndex, candidate.distanceMeters, along)
            }
        }
        val result = selected ?: return null
        lastAlongMeters = maxOf(previousAlong ?: result.alongRouteMeters, result.alongRouteMeters)
        lastElapsedMs = elapsedRealtimeMs
        lastLatitude = latitude
        lastLongitude = longitude
        lastBearing = bearingDegrees
        lastResult = result
        return result
    }
}
