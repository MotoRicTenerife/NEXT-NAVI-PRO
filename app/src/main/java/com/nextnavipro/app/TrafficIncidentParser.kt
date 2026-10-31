package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class TrafficIncidentSeverity { MODERATE, HEAVY, CLOSED }

data class IncidentCoordinate(val latitude: Double, val longitude: Double)

data class TrafficIncidentObservation(
    val id: String,
    val category: String,
    val severity: TrafficIncidentSeverity,
    val magnitudeOfDelay: String,
    val delaySeconds: Int?,
    val lengthMeters: Double?,
    val roadNumbers: List<String>,
    val geometryType: String,
    val geometry: List<IncidentCoordinate>,
    val observedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val probabilityOfOccurrence: String,
    val timeValidity: String,
    val startTimeEpochMs: Long?,
    val endTimeEpochMs: Long?,
    val from: String?,
    val to: String?,
    val description: String
)

/**
 * Normalizes the documented TomTom Orbis Traffic Incident Details v2 GeoJSON response.
 *
 * This deliberately does NOT map an incident to a route-relative hazard: route matching,
 * direction validation, distance-along-route and confidence are separate required steps.
 */
object TrafficIncidentParser {
    const val DEFAULT_MAX_AGE_MS = 5 * 60 * 1000L

    fun parse(
        response: String,
        observedAtEpochMs: Long,
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS
    ): List<TrafficIncidentObservation> {
        require(observedAtEpochMs > 0L) { "Timestamp di acquisizione non valido." }
        require(maxAgeMs in 1_000L..30 * 60 * 1000L) { "TTL incidenti fuori intervallo." }
        val root = JSONObject(response)
        val incidents = root.optJSONArray("incidents") ?: return emptyList()
        val result = ArrayList<TrafficIncidentObservation>()
        for (index in 0 until minOf(incidents.length(), 1000)) {
            val item = incidents.optJSONObject(index) ?: continue
            parseIncident(item, observedAtEpochMs, maxAgeMs)?.let(result::add)
        }
        return result
    }

    internal fun parseIncident(
        item: JSONObject,
        observedAtEpochMs: Long,
        maxAgeMs: Long
    ): TrafficIncidentObservation? {
        val properties = item.optJSONObject("properties") ?: return null
        val id = properties.optString("id").trim()
        if (id.isBlank()) return null

        val category = properties.optString("iconCategory").trim()
        val magnitude = properties.optString("magnitudeOfDelay").ifBlank { "unknown" }
        val severity = severityFor(category, magnitude) ?: return null
        val timeValidity = properties.optString("timeValidity").ifBlank { "present" }
        if (timeValidity != "present") return null

        val startTime = parseEpochMs(properties.optString("startTime"))
        val endTime = parseEpochMs(properties.optString("endTime"))
        if (startTime != null && startTime > observedAtEpochMs) return null
        if (endTime != null && endTime <= observedAtEpochMs) return null

        val geometryObject = item.optJSONObject("geometry") ?: return null
        val geometryType = geometryObject.optString("type")
        val geometry = parseGeometry(geometryType, geometryObject.optJSONArray("coordinates")) ?: return null
        if (geometry.isEmpty()) return null

        val roads = ArrayList<String>()
        val roadNumbers = properties.optJSONArray("roadNumbers") ?: JSONArray()
        for (i in 0 until roadNumbers.length()) {
            roadNumbers.optString(i).trim().takeIf { it.isNotBlank() }?.let(roads::add)
        }
        val events = properties.optJSONArray("events") ?: JSONArray()
        val descriptions = (0 until minOf(events.length(), 8)).mapNotNull { i ->
            events.optJSONObject(i)?.optString("description")?.trim()?.takeIf { it.isNotBlank() }
        }

        val maximumExpiry = observedAtEpochMs + maxAgeMs
        val expiry = endTime?.coerceAtMost(maximumExpiry) ?: maximumExpiry
        if (expiry <= observedAtEpochMs) return null

        return TrafficIncidentObservation(
            id = id,
            category = category,
            severity = severity,
            magnitudeOfDelay = magnitude,
            delaySeconds = properties.optInt("delayInSeconds", -1).takeIf { it >= 0 },
            lengthMeters = properties.optDouble("lengthInMeters", Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 },
            roadNumbers = roads.distinct(),
            geometryType = geometryType,
            geometry = geometry,
            observedAtEpochMs = observedAtEpochMs,
            validUntilEpochMs = expiry,
            probabilityOfOccurrence = properties.optString("probabilityOfOccurrence").ifBlank { "unknown" },
            timeValidity = timeValidity,
            startTimeEpochMs = startTime,
            endTimeEpochMs = endTime,
            from = properties.optString("from").takeIf { it.isNotBlank() },
            to = properties.optString("to").takeIf { it.isNotBlank() },
            description = descriptions.joinToString(". ")
        )
    }

    private fun severityFor(category: String, magnitude: String): TrafficIncidentSeverity? {
        if (category == "roadClosed") return TrafficIncidentSeverity.CLOSED
        val major = magnitude == "major"
        return when (category) {
            "jam", "laneClosed", "accident", "fog", "dangerousConditions", "rain",
            "ice", "roadWorks", "wind", "flooding", "brokenDownVehicle" ->
                if (major) TrafficIncidentSeverity.HEAVY else TrafficIncidentSeverity.MODERATE
            else -> null
        }
    }

    private fun parseGeometry(type: String, coordinates: JSONArray?): List<IncidentCoordinate>? {
        coordinates ?: return null
        val pairs = when (type) {
            "Point" -> listOf(coordinates)
            "LineString" -> (0 until coordinates.length()).mapNotNull { coordinates.optJSONArray(it) }
            else -> return null
        }
        if (pairs.isEmpty() || pairs.size > 20_000) return null
        val points = ArrayList<IncidentCoordinate>(pairs.size)
        for (pair in pairs) {
            if (pair.length() < 2) return null
            val longitude = pair.optDouble(0, Double.NaN)
            val latitude = pair.optDouble(1, Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) return null
            points.add(IncidentCoordinate(latitude, longitude))
        }
        return points
    }

    private fun parseEpochMs(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
