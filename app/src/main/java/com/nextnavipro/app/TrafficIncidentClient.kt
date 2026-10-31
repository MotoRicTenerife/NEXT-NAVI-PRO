package com.nextnavipro.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class MatchedRouteIncident(
    val id: String,
    val severity: TrafficIncidentSeverity,
    val delaySeconds: Int?,
    val distanceAheadMeters: Double,
    val road: String?,
    val description: String,
    val observedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val geometryFitScore: Double
)

data class TrafficRouteReport(
    val source: String,
    val observedAtEpochMs: Long,
    val cache: String,
    val incidents: List<MatchedRouteIncident>
)

/**
 * Fetches TomTom Orbis incidents through the Render proxy and matches their
 * geometry against the requested route. Provider keys never leave Render.
 *
 * The current route matcher intentionally does not verify carriageway direction.
 * This client preserves that limitation so Jarvis cannot call an opposite-direction
 * event a confirmed hazard on the rider's lane.
 */
object TrafficIncidentClient {
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_ROUTE_POINTS = 5_000

    fun fetchForRoute(
        route: List<NavPoint>,
        currentRouteDistanceMeters: Double
    ): TrafficRouteReport {
        val bbox = buildBbox(route)
        require(currentRouteDistanceMeters.isFinite() && currentRouteDistanceMeters >= 0.0) {
            "Distanza percorsa sul percorso non valida."
        }
        val base = BuildConfig.JARVIS_AI_ENDPOINT.trim().removeSuffix("/jarvis").trimEnd('/')
        require(base.startsWith("https://", ignoreCase = true)) {
            "Proxy traffico Render non configurato."
        }
        val target = URL(base + "/traffic/incidents?bbox=" + URLEncoder.encode(bbox, "UTF-8"))
        val connection = target.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2_000_000) }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IllegalStateException("TRAFFIC_HTTP_$status" + if (detail.isBlank()) "" else ": " + detail.take(120))
            }
            val root = JSONObject(body)
            val observedAt = root.optLong("observedAtEpochMs", System.currentTimeMillis())
            require(observedAt > 0L && observedAt <= System.currentTimeMillis() + 30_000L) {
                "Timestamp dati traffico non valido."
            }
            val parsed = TrafficIncidentParser.parse(body, observedAt)
            val byId = parsed.associateBy { it.id }
            val matched = parsed.mapNotNull { incident ->
                val match = TrafficIncidentRouteMatcher.match(
                    incident = incident,
                    route = route,
                    currentRouteDistanceMeters = currentRouteDistanceMeters,
                    corridorToleranceMeters = 35.0
                ) ?: return@mapNotNull null
                MatchedRouteIncident(
                    id = incident.id,
                    severity = incident.severity,
                    delaySeconds = incident.delaySeconds,
                    distanceAheadMeters = match.distanceAheadMeters,
                    road = incident.roadNumbers.joinToString(" / ").ifBlank { null },
                    description = incident.description,
                    observedAtEpochMs = incident.observedAtEpochMs,
                    validUntilEpochMs = incident.validUntilEpochMs,
                    geometryFitScore = match.geometryFitScore
                )
            }.sortedBy { it.distanceAheadMeters }.take(20)
            // Retain this reference to make it explicit that all emitted IDs came from
            // successfully parsed provider records, not from untrusted response fragments.
            check(matched.all { byId.containsKey(it.id) })
            return TrafficRouteReport(
                source = root.optString("source").ifBlank { "TomTom Orbis Traffic Incidents" },
                observedAtEpochMs = observedAt,
                cache = root.optString("cache").ifBlank { "UNKNOWN" },
                incidents = matched
            )
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildBbox(route: List<NavPoint>): String {
        require(route.size in 2..MAX_ROUTE_POINTS) { "Geometria percorso non valida." }
        require(route.all {
            it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                it.longitude.isFinite() && it.longitude in -180.0..180.0
        }) { "Coordinate percorso non valide." }
        val pad = 0.005
        val minLat = (route.minOf { it.latitude } - pad).coerceAtLeast(-90.0)
        val maxLat = (route.maxOf { it.latitude } + pad).coerceAtMost(90.0)
        val minLon = (route.minOf { it.longitude } - pad).coerceAtLeast(-180.0)
        val maxLon = (route.maxOf { it.longitude } + pad).coerceAtMost(180.0)
        val heightKm = (maxLat - minLat) * 111.32
        val widthKm = (maxLon - minLon) * 111.32 *
            kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.01)
        require(heightKm * widthKm in 0.0..10_000.0) { "Area percorso troppo estesa per una singola richiesta traffico." }
        return listOf(minLon, minLat, maxLon, maxLat)
            .joinToString(",") { String.format(Locale.US, "%.6f", it) }
    }
}
