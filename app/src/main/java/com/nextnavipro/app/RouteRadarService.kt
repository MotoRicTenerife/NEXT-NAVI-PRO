package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Route-wide OSM radar prefetch. The route is sampled into small overlapping circles;
 * each batch is bounded to keep public Overpass requests from becoming oversized.
 */
object RouteRadarService {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )
    private const val SAMPLE_RADIUS_METERS = 2600
    private const val CENTERS_PER_BATCH = 10

    fun fetchAlongRoute(route: List<NavPoint>): List<RadarCamera> {
        require(route.size >= 2) { "Percorso non valido per la ricerca radar" }
        val centers = RouteRadarPlanner.sample(route)
        val collected = LinkedHashMap<Long, RadarCamera>()
        val batches = centers.chunked(CENTERS_PER_BATCH)
        batches.forEachIndexed { index, batch ->
            val query = buildQuery(batch)
            var lastError: Exception? = null
            var loaded: List<RadarCamera>? = null
            for (endpoint in endpoints) {
                try {
                    loaded = request(endpoint, query)
                    break
                } catch (error: Exception) {
                    lastError = error
                    Log.w("NEXT_NAVI_RADAR", "Route Overpass batch ${index + 1}/${batches.size} failed at $endpoint", error)
                }
            }
            val cameras = loaded ?: throw (lastError ?: IllegalStateException("Servizio Overpass non disponibile"))
            cameras.forEach { collected[it.osmId] = it }
        }
        return RouteRadarPlanner.match(collected.values.toList(), route).map { it.camera }
    }

    private fun buildQuery(centers: List<NavPoint>): String {
        val selectors = centers.joinToString("") { p ->
            val lat = p.latitude.toString()
            val lon = p.longitude.toString()
            "(node[\"highway\"=\"speed_camera\"](around:$SAMPLE_RADIUS_METERS,$lat,$lon);" +
                "node[\"man_made\"=\"speed_camera\"](around:$SAMPLE_RADIUS_METERS,$lat,$lon);" +
                "node[\"enforcement\"~\"^(maxspeed|average_speed|traffic_signals)$\"](around:$SAMPLE_RADIUS_METERS,$lat,$lon);" +
                "relation[\"type\"=\"enforcement\"][\"enforcement\"~\"^(maxspeed|average_speed|traffic_signals)$\"](around:$SAMPLE_RADIUS_METERS,$lat,$lon););"
        }
        return "[out:json][timeout:20];($selectors);out body;>;out body qt;"
    }

    private fun request(endpoint: String, query: String): List<RadarCamera> {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 9000
            connection.readTimeout = 25000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3 (https://github.com/MotoRicTenerife/NEXT-NAVI-PRO)")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write("data=" + URLEncoder.encode(query, "UTF-8"))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Overpass HTTP $status: " + body.take(180))
            return RadarService.parseOverpassResponse(body)
        } finally {
            connection.disconnect()
        }
    }
}
