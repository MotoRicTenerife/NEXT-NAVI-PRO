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
    val direction: String,
    val maxSpeed: String = ""
)

object RadarService {
    // Public OSM Overpass endpoints: try a secondary instance if the primary is unavailable or rate-limited.
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )
    // A 10 km window keeps the response useful at road speed without requesting a
    // needlessly large relation graph on every refresh.
    private const val RADIUS_METERS = 10000
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 18000

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
        // Include standalone camera nodes and enforcement relations. The latter are
        // important for devices mapped beside the road, average-speed checks, and cameras
        // whose direction is encoded by relation members rather than node tags.
        val query = "[out:json][timeout:15];(" +
            "node[\"highway\"=\"speed_camera\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            // Compatibility with occasional non-standard but real-world OSM tagging.
            "node[\"man_made\"=\"speed_camera\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            "node[\"enforcement\"~\"^(maxspeed|average_speed|traffic_signals)$\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            "relation[\"type\"=\"enforcement\"][\"enforcement\"~\"^(maxspeed|average_speed|traffic_signals)$\"](around:" + RADIUS_METERS + "," + lat + "," + lon + ");" +
            ");out body;>;out body qt;"
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
            return parseOverpassResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Parse camera nodes and device members of OSM enforcement relations. */
    internal fun parseOverpassResponse(body: String): List<RadarCamera> {
        val root = JSONObject(body)
        // Overpass can return HTTP 200 with a JSON "remark" for a timed-out or
        // otherwise failed query. Treating that as an empty result poisons the cache.
        val remark = root.optString("remark").trim()
        if (remark.isNotEmpty()) throw IllegalStateException("Overpass query failed: " + remark.take(300))
        val elements = root.optJSONArray("elements")
            ?: throw IllegalStateException("Risposta Overpass senza elenco elements")
        val nodes = LinkedHashMap<Long, JSONObject>()
        val deviceNodeIds = HashSet<Long>()
        val relationKinds = HashMap<Long, String>()
        val relationDirections = HashMap<Long, String>()
        val relationMaxSpeeds = HashMap<Long, String>()

        for (i in 0 until elements.length()) {
            val item = elements.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "node" -> {
                    val id = item.optLong("id", -1L)
                    if (id >= 0L) {
                        val previous = nodes[id]
                        val currentTags = item.optJSONObject("tags")
                        val previousTags = previous?.optJSONObject("tags")
                        // Recursion may return a node more than once. Preserve richer tags.
                        nodes[id] = if (previousTags != null && previousTags.length() > (currentTags?.length() ?: 0)) previous else item
                    }
                }
                "relation" -> {
                    val tags = item.optJSONObject("tags") ?: JSONObject()
                    val enforcement = tags.optString("enforcement")
                    if (tags.optString("type") != "enforcement" ||
                        enforcement !in setOf("maxspeed", "average_speed", "traffic_signals")) continue
                    val members = item.optJSONArray("members") ?: continue
                    val kind = when (enforcement) {
                        "average_speed" -> "Controllo velocità media"
                        "traffic_signals" -> "Controllo semaforo"
                        else -> "Controllo velocità"
                    }
                    for (j in 0 until members.length()) {
                        val member = members.optJSONObject(j) ?: continue
                        if (member.optString("type") != "node" || member.optString("role") != "device") continue
                        val nodeId = member.optLong("ref", -1L)
                        if (nodeId < 0L) continue
                        deviceNodeIds.add(nodeId)
                        relationKinds[nodeId] = kind
                        val direction = tags.optString("direction")
                        if (direction.isNotBlank()) relationDirections[nodeId] = direction
                        val maxSpeed = tags.optString("maxspeed")
                        if (maxSpeed.isNotBlank()) relationMaxSpeeds[nodeId] = maxSpeed
                    }
                }
            }
        }

        val result = LinkedHashMap<Long, RadarCamera>()
        for ((id, item) in nodes) {
            val tags = item.optJSONObject("tags") ?: JSONObject()
            val isTaggedCamera = tags.optString("highway") == "speed_camera" ||
                tags.optString("man_made") == "speed_camera" ||
                tags.optString("enforcement") in setOf("maxspeed", "average_speed", "traffic_signals")
            if (!isTaggedCamera && id !in deviceNodeIds) continue
            val latitude = item.optDouble("lat", Double.NaN)
            val longitude = item.optDouble("lon", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
            val kind = relationKinds[id] ?: when (tags.optString("enforcement")) {
                "average_speed" -> "Controllo velocità media"
                "traffic_signals" -> "Controllo semaforo"
                "maxspeed" -> "Controllo velocità"
                else -> "Autovelox OSM"
            }
            result[id] = RadarCamera(
                id,
                NavPoint(latitude, longitude),
                kind,
                relationDirections[id] ?: tags.optString("direction"),
                relationMaxSpeeds[id] ?: tags.optString("maxspeed")
            )
        }
        return result.values.toList()
    }
}

object RadarCache {
    private const val PREFS = "next_navi_settings"
    private const val KEY_JSON = "radar_cache_json"
    private const val KEY_TIME = "radar_cache_timestamp"
    private const val KEY_LAT = "radar_cache_latitude"
    private const val KEY_LON = "radar_cache_longitude"
    private const val REFRESH_MIN_INTERVAL_MS = 10L * 60L * 1000L
    private const val MAX_CACHE_AGE_MS = 30L * 60L * 1000L
    private const val REFRESH_DISTANCE_METERS = 4000f
    private const val DISPLAY_CACHE_DISTANCE_METERS = 8000f

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
        val centerLat = prefs.getFloat(KEY_LAT, Float.NaN).toDouble()
        val centerLon = prefs.getFloat(KEY_LON, Float.NaN).toDouble()
        if (!centerLat.isFinite() || !centerLon.isFinite()) return true
        // Movement takes priority over cache age; a new area must be queried promptly.
        if (distanceMeters(location.latitude, location.longitude, centerLat, centerLon) >= REFRESH_DISTANCE_METERS) return true
        if (age >= MAX_CACHE_AGE_MS) return true
        if (age < REFRESH_MIN_INTERVAL_MS) return false
        return false
    }

    fun lastUpdatedAt(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_TIME, 0L)

    fun save(context: Context, location: NavPoint, cameras: List<RadarCamera>, now: Long = System.currentTimeMillis()) {
        val array = JSONArray()
        cameras.forEach { camera ->
            array.put(JSONObject()
                .put("id", camera.osmId)
                .put("lat", camera.point.latitude)
                .put("lon", camera.point.longitude)
                .put("kind", camera.kind)
                .put("direction", camera.direction)
                .put("maxSpeed", camera.maxSpeed))
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
                        item.optString("direction", ""),
                        item.optString("maxSpeed", "")
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
