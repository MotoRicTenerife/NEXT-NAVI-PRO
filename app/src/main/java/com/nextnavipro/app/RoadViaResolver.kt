package com.nextnavipro.app

import kotlin.math.*

data class ResolvedRoadWaypoint(
    val roadName: String,
    val osmId: Long,
    val point: NavPoint,
    val distanceToTripMidpointMeters: Double
)

/**
 * Resolves a named OSM road geometry to a safe intermediate point for a route
 * comparison. It never starts navigation and refuses distant/degenerate matches.
 */
object RoadViaResolver {
    fun selectWaypoint(
        geometries: List<RoadGeometryResult>,
        origin: NavPoint,
        destination: NavPoint
    ): ResolvedRoadWaypoint? {
        if (!valid(origin) || !valid(destination)) return null
        val directDistance = distanceMeters(origin, destination)
        if (!directDistance.isFinite() || directDistance < 1_000.0) return null
        val midpoint = NavPoint(
            (origin.latitude + destination.latitude) / 2.0,
            (origin.longitude + destination.longitude) / 2.0
        )
        val maxMidpointDistance = max(2_000.0, directDistance * 0.65)
        val minEndpointDistance = min(800.0, directDistance * 0.12)
        return geometries.asSequence()
            .filter { it.osmId > 0 && it.geometry.size >= 2 }
            .flatMap { road ->
                road.geometry.asSequence()
                    .filter(::valid)
                    .map { point -> Triple(road, point, distanceMeters(midpoint, point)) }
            }
            .filter { (_, point, midpointDistance) ->
                midpointDistance <= maxMidpointDistance &&
                    distanceMeters(origin, point) >= minEndpointDistance &&
                    distanceMeters(destination, point) >= minEndpointDistance
            }
            .minByOrNull { it.third }
            ?.let { (road, point, distance) ->
                ResolvedRoadWaypoint(
                    roadName = road.name?.takeIf { it.isNotBlank() } ?: "strada selezionata",
                    osmId = road.osmId,
                    point = point,
                    distanceToTripMidpointMeters = distance
                )
            }
    }

    fun distanceToRouteMeters(point: NavPoint, route: List<NavPoint>): Double {
        if (!valid(point) || route.size < 2 || route.any { !valid(it) }) return Double.POSITIVE_INFINITY
        val latitudeScale = 111_320.0
        val longitudeScale = 111_320.0 * cos(Math.toRadians(point.latitude))
        var best = Double.POSITIVE_INFINITY
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val ax = (a.longitude - point.longitude) * longitudeScale
            val ay = (a.latitude - point.latitude) * latitudeScale
            val bx = (b.longitude - point.longitude) * longitudeScale
            val by = (b.latitude - point.latitude) * latitudeScale
            val dx = bx - ax
            val dy = by - ay
            val denominator = dx * dx + dy * dy
            val t = if (denominator <= 1e-9) 0.0 else ((-(ax * dx + ay * dy)) / denominator).coerceIn(0.0, 1.0)
            val distance = hypot(ax + t * dx, ay + t * dy)
            if (distance < best) best = distance
        }
        return best
    }

    private fun valid(point: NavPoint): Boolean =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private fun distanceMeters(a: NavPoint, b: NavPoint): Double {
        val radius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return radius * 2 * atan2(sqrt(h), sqrt((1 - h).coerceAtLeast(0.0)))
    }
}
