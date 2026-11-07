package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Server-side OSM lookup; keeps road metadata with the limit for motorway alerts and diagnostics. */
data class RenderSpeedLimitResult(
    val speedLimitKmh: Int?,
    val road: String?,
    val ref: String?,
    val highwayType: String?,
    val distanceMeters: Float?
)

object RenderSpeedLimitService {
    private const val TAG = "NEXT_NAVI_SPEED_LIMIT"
    fun findRoadMatch(location: Location): RenderSpeedLimitResult? {
        val configured = BuildConfig.JARVIS_AI_ENDPOINT.trim()
        if (!configured.startsWith("https://") || !configured.endsWith("/jarvis")) return null
        if (!location.latitude.isFinite() || !location.longitude.isFinite() || location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null
        val base = configured.substringBeforeLast("/jarvis").trimEnd('/')
        val query = "lat=" + URLEncoder.encode(location.latitude.toString(), "UTF-8") + "&lon=" + URLEncoder.encode(location.longitude.toString(), "UTF-8")
        val connection = URL("$base/speed-limit?$query").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 2500
            connection.readTimeout = 4500
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() } }.orEmpty()
            if (status !in 200..299) {
                Log.w(TAG, "Render OSM fallback HTTP $status: " + body.take(180))
                return null
            }
            val root = JSONObject(body)
            val limit = parseResponse(body)
            val result = RenderSpeedLimitResult(
                speedLimitKmh = limit,
                road = root.optString("road").takeIf { it.isNotBlank() && it != "null" },
                ref = root.optString("ref").takeIf { it.isNotBlank() && it != "null" },
                highwayType = root.optString("highwayType").takeIf { it.isNotBlank() && it != "null" },
                distanceMeters = root.optDouble("distanceMeters", Double.NaN).takeIf { it.isFinite() && it >= 0.0 }?.toFloat()
            )
            if (limit == null) Log.i(TAG, "Render OSM lookup: no explicit numeric limit; road=" + (result.road ?: "unknown"))
            else Log.i(TAG, "Render OSM lookup returned $limit km/h for " + (result.road ?: "unnamed"))
            return result
        } catch (error: Exception) {
            Log.w(TAG, "Render OSM fallback request failed", error)
            return null
        } finally { connection.disconnect() }
    }

    internal fun parseResponse(body: String): Int? {
        val root = JSONObject(body)
        if (!root.has("speedLimitKmh") || root.isNull("speedLimitKmh")) return null
        return root.optInt("speedLimitKmh", -1).takeIf { it in 5..200 }
    }
}
