package com.nextnavipro.app

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RadarCamera(
    val osmId: Long,
    val point: NavPoint,
    val kind: String,
    val direction: String
)

object RadarService {
    // Public OSM Overpass endpoints: try a secondary instance if the primary is unavailable or rate-limited.
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )
    private const val RADIUS_METERS = 15000
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 20000

    fun fetchNear(center: NavPoint): List<RadarCamera> {
        var lastError: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                return fetchNearAtEndpoint(center, endpoint)
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Nessun servizio Overpass disponibile per i radar")
    }

    private fun fetchNearAtEndpoint(center: NavPoint, endpoint: String): List<RadarCamera> {
        val lat = center.latitude.toString()
        val lon = center.longitude.toString()
        val query = "[out:json][timeout:20];(" +
            "node[\"highway\"=\"speed_camera\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            "node[\"enforcement\"=\"maxspeed\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            ");out body;"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
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
            if (status !in 200..299) throw IllegalStateException("Overpass HTTP " + status)
            val elements = JSONObject(body).optJSONArray("elements") ?: JSONArray()
            val result = LinkedHashMap<Long, RadarCamera>()
            for (i in 0 until elements.length()) {
                val item = elements.optJSONObject(i) ?: continue
                if (item.optString("type") != "node") continue
                val id = item.optLong("id", -1L)
                val latitude = item.optDouble("lat", Double.NaN)
                val longitude = item.optDouble("lon", Double.NaN)
                if (id < 0L || !latitude.isFinite() || !longitude.isFinite()) continue
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                val tags = item.optJSONObject("tags") ?: JSONObject()
                val kind = when {
                    tags.optString("enforcement") == "maxspeed" -> "Controllo velocità"
                    tags.optString("maxspeed:type").isNotBlank() -> "Autovelox"
                    else -> "Autovelox OSM"
                }
                result[id] = RadarCamera(
                    id,
                    NavPoint(latitude, longitude),
                    kind,
                    tags.optString("direction")
                )
            }
            return result.values.toList()
        } finally {
            connection.disconnect()
        }
    }
}

object RadarCache {
    private const val PREFS = "next_navi_settings"
    private const val KEY_JSON = "radar_cache_json"
    private const val KEY_TIME = "radar_cache_timestamp"
    private const val KEY_LAT = "radar_cache_latitude"
    private const val KEY_LON = "radar_cache_longitude"
    private const val REFRESH_MIN_INTERVAL_MS = 30L * 60L * 1000L
    private const val MAX_CACHE_AGE_MS = 24L * 60L * 60L * 1000L
    private const val REFRESH_DISTANCE_METERS = 8000f
    private const val DISPLAY_CACHE_DISTANCE_METERS = 9000f

    fun loadNear(context: Context, location: NavPoint): List<RadarCamera> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val centerLat = prefs.getFloat(KEY_LAT, Float.NaN).toDouble()
        val centerLon = prefs.getFloat(KEY_LON, Float.NaN).toDouble()
        if (!centerLat.isFinite() || !centerLon.isFinite()) return emptyList()
        if (distanceMeters(location.latitude, location.longitude, centerLat, centerLon) > DISPLAY_CACHE_DISTANCE_METERS) {
            return emptyList()
        }
        return decode(prefs.getString(KEY_JSON, "[]").orEmpty())
    }

    fun shouldRefresh(context: Context, location: NavPoint, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_TIME, 0L)
        if (timestamp == 0L) return true
        val age = (now - timestamp).coerceAtLeast(0L)
        if (age < REFRESH_MIN_INTERVAL_MS) return false
        if (age >= MAX_CACHE_AGE_MS) return true
        val centerLat = prefs.getFloat(KEY_LAT, Float.NaN).toDouble()
        val centerLon = prefs.getFloat(KEY_LON, Float.NaN).toDouble()
        if (!centerLat.isFinite() || !centerLon.isFinite()) return true
        return distanceMeters(location.latitude, location.longitude, centerLat, centerLon) >= REFRESH_DISTANCE_METERS
    }

    fun save(context: Context, location: NavPoint, cameras: List<RadarCamera>, now: Long = System.currentTimeMillis()) {
        val array = JSONArray()
        cameras.forEach { camera ->
            array.put(JSONObject()
                .put("id", camera.osmId)
                .put("lat", camera.point.latitude)
                .put("lon", camera.point.longitude)
                .put("kind", camera.kind)
                .put("direction", camera.direction))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, array.toString())
            .putLong(KEY_TIME, now)
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LON, location.longitude.toFloat())
            .apply()
    }

    private fun decode(json: String): List<RadarCamera> {
        return try {
            val array = JSONArray(json)
            val result = ArrayList<RadarCamera>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                val id = item.optLong("id", -1L)
                if (id >= 0L && lat.isFinite() && lon.isFinite() &&
                    lat in -90.0..90.0 && lon in -180.0..180.0) {
                    result.add(RadarCamera(
                        id, NavPoint(lat, lon),
                        item.optString("kind", "Autovelox OSM"),
                        item.optString("direction", "")
                    ))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }
}
