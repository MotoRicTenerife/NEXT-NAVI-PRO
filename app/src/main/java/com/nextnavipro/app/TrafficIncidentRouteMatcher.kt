package com.nextnavipro.app

import kotlin.math.*

/**
 * Matches TomTom incident geometry to the active route corridor.
 *
 * This establishes geometric overlap only. TomTom incident geometry does not prove
 * travel direction, lane, legal access, or route-relative confidence. The returned
 * directionVerified flag is therefore always false and must not be passed to the
 * decision engine as directionMatches=true without a separate directional check.
 */
internal object TrafficIncidentRouteMatcher {
    data class Match(
        val incidentId: String,
        val routeStartDistanceMeters: Double,
        val routeEndDistanceMeters: Double,
        val distanceAheadMeters: Double,
        val crossTrackDistanceMeters: Double,
        val geometryFitScore: Double,
        val matchedSampleRatio: Double,
        val directionVerified: Boolean = false
    )

    private data class Projection(
        val distanceMeters: Double,
        val chainageMeters: Double,
        val routeSegmentBearing: Double
    )

    private data class Sample(val point: IncidentCoordinate, val bearing: Double?)

    fun match(
        incident: TrafficIncidentObservation,
        route: List<NavPoint>,
        currentRouteDistanceMeters: Double,
        corridorToleranceMeters: Double = 35.0
    ): Match? {
        if (incident.id.isBlank() || route.size < 2 ||
            !currentRouteDistanceMeters.isFinite() || currentRouteDistanceMeters < 0.0 ||
            !corridorToleranceMeters.isFinite() || corridorToleranceMeters !in 5.0..100.0 ||
            incident.geometry.isEmpty()
        ) return null

        val cumulative = DoubleArray(route.size)
        for (i in 1 until route.size) {
            val segment = distanceMeters(
                route[i - 1].latitude, route[i - 1].longitude,
                route[i].latitude, route[i].longitude
            )
            if (!segment.isFinite() || segment < 0.01) return null
            cumulative[i] = cumulative[i - 1] + segment
        }

        val samples = sampleIncident(incident) ?: return null
        if (samples.isEmpty()) return null
        val matches = ArrayList<Projection>()
        for (sample in samples) {
            val projection = nearestRouteProjection(sample.point, sample.bearing, route, cumulative)
                ?: continue
            if (projection.distanceMeters <= corridorToleranceMeters) matches.add(projection)
        }

        val requiredMatches = if (incident.geometryType == "Point") 1
            else max(2, ceil(samples.size * 0.60).toInt())
        if (matches.size < requiredMatches) return null

        val ordered = matches.sortedBy { it.chainageMeters }
        val start = ordered.first().chainageMeters
        val end = ordered.last().chainageMeters
        if (incident.geometryType == "LineString" && end - start < 8.0) return null

        val ahead = max(0.0, start - currentRouteDistanceMeters)
        if (end < currentRouteDistanceMeters - 10.0) return null

        val medianCrossTrack = ordered.map { it.distanceMeters }.sorted()
            .let { it[it.size / 2] }
        val ratio = matches.size.toDouble() / samples.size.toDouble()
        val fit = (ratio * (1.0 - medianCrossTrack / corridorToleranceMeters)).coerceIn(0.0, 1.0)

        return Match(
            incidentId = incident.id,
            routeStartDistanceMeters = start,
            routeEndDistanceMeters = end,
            distanceAheadMeters = ahead,
            crossTrackDistanceMeters = medianCrossTrack,
            geometryFitScore = fit,
            matchedSampleRatio = ratio,
            directionVerified = false
        )
    }

    private fun sampleIncident(incident: TrafficIncidentObservation): List<Sample>? {
        return when (incident.geometryType) {
            "Point" -> incident.geometry.singleOrNull()?.let { listOf(Sample(it, null)) }
            "LineString" -> {
                if (incident.geometry.size < 2) return null
                val samples = ArrayList<Sample>()
                for (i in 0 until incident.geometry.lastIndex) {
                    val a = incident.geometry[i]
                    val b = incident.geometry[i + 1]
                    val length = distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                    if (!length.isFinite() || length < 0.01) continue
                    val bearing = bearingRadians(a.latitude, a.longitude, b.latitude, b.longitude)
                    val count = ceil(length / 15.0).toInt().coerceIn(1, 2000)
                    for (step in 0 until count) {
                        val fraction = step.toDouble() / count.toDouble()
                        samples.add(
                            Sample(
                                IncidentCoordinate(
                                    a.latitude + (b.latitude - a.latitude) * fraction,
                                    a.longitude + (b.longitude - a.longitude) * fraction
                                ),
                                bearing
                            )
                        )
                        if (samples.size >= 20_000) return samples
                    }
                }
                val last = incident.geometry.last()
                samples.add(Sample(last, null))
                samples
            }
            else -> null
        }
    }

    private fun nearestRouteProjection(
        point: IncidentCoordinate,
        incidentBearing: Double?,
        route: List<NavPoint>,
        cumulative: DoubleArray
    ): Projection? {
        var best: Projection? = null
        for (i in 0 until route.lastIndex) {
            val a = route[i]
            val b = route[i + 1]
            val routeBearing = bearingRadians(a.latitude, a.longitude, b.latitude, b.longitude)
            if (incidentBearing != null) {
                val difference = abs(normalizeRadians(routeBearing - incidentBearing))
                // Geometry alignment is direction-agnostic: opposite line ordering is valid.
                if (abs(cos(difference)) < 0.5) continue
            }

            val meanLat = Math.toRadians(point.latitude)
            val scaleX = EARTH_RADIUS_METERS * cos(meanLat)
            val scaleY = EARTH_RADIUS_METERS
            val ax = Math.toRadians(a.longitude - point.longitude) * scaleX
            val ay = Math.toRadians(a.latitude - point.latitude) * scaleY
            val bx = Math.toRadians(b.longitude - point.longitude) * scaleX
            val by = Math.toRadians(b.latitude - point.latitude) * scaleY
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared < 0.0001) continue
            val t = (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val crossTrack = hypot(px, py)
            val chainage = cumulative[i] + sqrt(lengthSquared) * t
            if (best == null || crossTrack < best.distanceMeters) {
                best = Projection(crossTrack, chainage, routeBearing)
            }
        }
        return best
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (!lat1.isFinite() || !lon1.isFinite() || !lat2.isFinite() || !lon2.isFinite() ||
            lat1 !in -90.0..90.0 || lat2 !in -90.0..90.0 ||
            lon1 !in -180.0..180.0 || lon2 !in -180.0..180.0
        ) return Double.NaN
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun bearingRadians(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        return atan2(
            sin(dLon) * cos(p2),
            cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        )
    }

    private fun normalizeRadians(value: Double): Double {
        var result = value % (2.0 * Math.PI)
        if (result > Math.PI) result -= 2.0 * Math.PI
        if (result < -Math.PI) result += 2.0 * Math.PI
        return result
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
