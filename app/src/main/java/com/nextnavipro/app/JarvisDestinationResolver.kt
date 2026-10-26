package com.nextnavipro.app

import java.text.Normalizer
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Conservative resolver for voice destinations that name a whole locality. */
object JarvisDestinationResolver {
    private val localityKinds = setOf(
        "city", "town", "village", "hamlet", "municipality", "suburb",
        "quarter", "neighbourhood", "neighborhood", "district", "locality", "borough"
    )

    fun isExactLocalityMatch(place: PlaceResult, query: String): Boolean {
        if (place.kind.lowercase(Locale.ROOT) !in localityKinds) return false
        val name = normalize(place.name)
        return NavigationServices.placeQueryVariants(query).any { normalize(it) == name }
    }

    fun hasExactLocalityMatch(results: List<PlaceResult>, query: String): Boolean =
        results.any { isExactLocalityMatch(it, query) }

    /**
     * Auto-select only one distinct exact locality close to a reliable GPS origin.
     * Duplicate geocoder records within 250 m count as one place; multiple distinct
     * nearby localities remain ambiguous and must be shown to the user.
     */
    fun uniqueNearbyExactLocality(
        results: List<PlaceResult>,
        query: String,
        origin: NavPoint?,
        maxDistanceMeters: Double = 30_000.0
    ): PlaceResult? {
        if (origin == null) return null
        val candidates = results.asSequence()
            .filter { isExactLocalityMatch(it, query) }
            .map { it to distanceMeters(origin, it.point) }
            .filter { it.second <= maxDistanceMeters }
            .sortedBy { it.second }
            .toList()
        if (candidates.isEmpty()) return null
        val distinct = ArrayList<Pair<PlaceResult, Double>>()
        for (candidate in candidates) {
            if (distinct.none { distanceMeters(it.first.point, candidate.first.point) < 250.0 }) {
                distinct.add(candidate)
            }
        }
        return distinct.singleOrNull()?.first
    }

    internal fun distanceMeters(a: NavPoint, b: NavPoint): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = Math.toRadians(b.latitude - a.latitude)
        val deltaLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return earthRadius * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1.0 - h).coerceIn(0.0, 1.0)))
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
