package com.nextnavipro.app

import android.location.Location
import kotlin.math.*

data class RouteRadarMatch(
    val camera: RadarCamera,
    val distanceFromStartMeters: Double,
    val distanceFromRouteMeters: Double
)

/** Projects mapped enforcement devices onto the active route, rejecting nearby parallel roads. */
object RouteRadarPlanner {
    fun match(cameras: List<RadarCamera>, route: List<NavPoint>, corridorMeters: Double = 90.0): List<RouteRadarMatch> {
        if (route.size < 2 || cameras.isEmpty()) return emptyList()
        val cumulative = DoubleArray(route.size)
        for (i in 1 until route.size) cumulative[i] = cumulative[i - 1] + distance(route[i - 1], route[i])
        val matches = LinkedHashMap<Long, RouteRadarMatch>()
        for (camera in cameras) {
            var bestDistance = Double.POSITIVE_INFINITY
            var bestAlong = 0.0
            for (i in 0 until route.lastIndex) {
                val projection = project(camera.point, route[i], route[i + 1])
                if (projection.distanceMeters < bestDistance) {
                    bestDistance = projection.distanceMeters
                    bestAlong = cumulative[i] + distance(route[i], route[i + 1]) * projection.fraction
                }
            }
            if (bestDistance <= corridorMeters) {
                val candidate = RouteRadarMatch(camera, bestAlong, bestDistance)
                val previous = matches[camera.osmId]
                if (previous == null || candidate.distanceFromStartMeters < previous.distanceFromStartMeters) {
                    matches[camera.osmId] = candidate
                }
            }
        }
        return matches.values.sortedBy { it.distanceFromStartMeters }
    }

    /** Samples the route at a bounded interval; used to form small Overpass query batches. */
    fun sample(route: List<NavPoint>, intervalMeters: Double = 1800.0): List<NavPoint> {
        if (route.isEmpty()) return emptyList()
        if (route.size == 1) return route
        val interval = intervalMeters.coerceAtLeast(500.0)
        val result = ArrayList<NavPoint>()
        result.add(route.first())
        var sinceLast = 0.0
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            val segment = distance(a, b)
            if (segment <= 0.1) continue
            var offset = interval - sinceLast
            while (offset < segment) {
                result.add(interpolate(a, b, offset / segment))
                offset += interval
            }
            sinceLast = (sinceLast + segment) % interval
        }
        if (result.last() != route.last()) result.add(route.last())
        return result
    }

    private data class Projection(val fraction: Double, val distanceMeters: Double)

    private fun project(p: NavPoint, a: NavPoint, b: NavPoint): Projection {
        val lat0 = Math.toRadians((a.latitude + b.latitude + p.latitude) / 3.0)
        val sx = 111320.0 * cos(lat0)
        val sy = 110540.0
        val ax = 0.0; val ay = 0.0
        val bx = (b.longitude - a.longitude) * sx
        val by = (b.latitude - a.latitude) * sy
        val px = (p.longitude - a.longitude) * sx
        val py = (p.latitude - a.latitude) * sy
        val dx = bx - ax; val dy = by - ay
        val denominator = dx * dx + dy * dy
        val t = if (denominator <= 1e-9) 0.0 else ((px * dx + py * dy) / denominator).coerceIn(0.0, 1.0)
        return Projection(t, hypot(px - t * dx, py - t * dy))
    }

    private fun distance(a: NavPoint, b: NavPoint): Double {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0].toDouble()
    }

    private fun interpolate(a: NavPoint, b: NavPoint, t: Double) =
        NavPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
}
