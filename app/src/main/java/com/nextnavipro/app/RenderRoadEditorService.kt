package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RoadSearchResult(
    val osmId: Long,
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val roadType: String?,
    val tags: JSONObject
)

data class RoadGeometryResult(
    val osmId: Long,
    val name: String?,
    val tags: JSONObject,
    val geometry: List<NavPoint>,
    val source: String
)

data class ProviderSpeedLimit(
    val speedLimitKmh: Int?,
    val source: String?,
    val reason: String?
)

/** Road-editor lookup via the Render backend. No provider credentials are shipped in the APK. */
object RenderRoadEditorService {
    private const val TAG = "NEXT_NAVI_ROAD_EDITOR"

    private fun baseUrl(): String? {
        val configured = BuildConfig.JARVIS_AI_ENDPOINT.trim()
        if (!configured.startsWith("https://") || !configured.endsWith("/jarvis")) return null
        return configured.substringBeforeLast("/jarvis").trimEnd('/')
    }

    private fun getJson(path: String): JSONObject {
        val base = baseUrl() ?: throw IllegalStateException("Backend Render non configurato")
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Backend HTTP $status")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun searchRoads(query: String): List<RoadSearchResult> {
        val q = query.trim()
        require(q.length in 2..120) { "Inserisci almeno 2 caratteri." }
        val json = getJson("/roads/search?q=" + URLEncoder.encode(q, "UTF-8"))
        val roads = json.optJSONArray("roads") ?: JSONArray()
        return (0 until roads.length()).mapNotNull { index ->
            val item = roads.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("osmId", -1L)
            val lat = item.optDouble("latitude", Double.NaN)
            val lon = item.optDouble("longitude", Double.NaN)
            if (id <= 0 || !lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            RoadSearchResult(
                osmId = id,
                name = item.optString("name", "Strada senza nome"),
                displayName = item.optString("displayName", ""),
                latitude = lat,
                longitude = lon,
                roadType = item.optString("roadType").takeIf { it.isNotBlank() && it != "null" },
                tags = item.optJSONObject("tags") ?: JSONObject()
            )
        }
    }

    fun fetchGeometry(road: RoadSearchResult): RoadGeometryResult {
        val json = getJson("/roads/geometry?type=way&id=" + road.osmId)
        val points = json.optJSONArray("geometry") ?: throw IllegalStateException("Geometria OSM assente")
        val geometry = (0 until points.length()).mapNotNull { index ->
            val p = points.optJSONObject(index) ?: return@mapNotNull null
            val lat = p.optDouble("latitude", Double.NaN)
            val lon = p.optDouble("longitude", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) null else NavPoint(lat, lon)
        }
        if (geometry.size < 2) throw IllegalStateException("Geometria OSM non valida")
        return RoadGeometryResult(
            osmId = json.optLong("osmId", road.osmId),
            name = json.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: road.name,
            tags = json.optJSONObject("tags") ?: JSONObject(),
            geometry = geometry,
            source = json.optString("source", "OpenStreetMap")
        )
    }

    /** TomTom is queried with samples from the selected real OSM way; null means no explicit limit. */
    fun proposeSpeedLimit(geometry: List<NavPoint>): ProviderSpeedLimit {
        val base = baseUrl() ?: return ProviderSpeedLimit(null, null, "backend_not_configured")
        if (geometry.size < 2) return ProviderSpeedLimit(null, null, "geometry_too_short")
        val indices = (0 until minOf(8, geometry.size)).map { i ->
            if (minOf(8, geometry.size) == 1) 0 else (i * (geometry.lastIndex).toDouble() / (minOf(8, geometry.size) - 1)).toInt()
        }.distinct()
        val points = JSONArray()
        indices.forEach { index ->
            val point = geometry[index]
            points.put(JSONObject().put("latitude", point.latitude).put("longitude", point.longitude))
        }
        val connection = URL(base + "/speed-limit").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 12000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val body = JSONObject().put("points", points).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) return ProviderSpeedLimit(null, null, "provider_http_$status")
            val json = JSONObject(response)
            val limit = if (json.isNull("speedLimitKmh")) null else json.optInt("speedLimitKmh", -1).takeIf { it in 5..200 }
            return ProviderSpeedLimit(limit, json.optString("source").takeIf { it.isNotBlank() && it != "null" }, json.optString("reason").takeIf { it.isNotBlank() && it != "null" })
        } catch (error: Exception) {
            Log.w(TAG, "Provider speed limit lookup failed", error)
            return ProviderSpeedLimit(null, null, "lookup_failed")
        } finally {
            connection.disconnect()
        }
    }
}
