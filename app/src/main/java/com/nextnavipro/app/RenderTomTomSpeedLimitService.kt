package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Secure Render proxy client for TomTom Snap to Roads. The TomTom API key stays server-side. */
data class TomTomSpeedLimitResult(
    val speedLimitKmh: Int?,
    val road: String?,
    val ref: String?,
    val highwayType: String?,
    val reason: String?
)

object RenderTomTomSpeedLimitService {
    private const val TAG = "NEXT_NAVI_SPEED_LIMIT"

    fun findLimit(points: List<Location>): TomTomSpeedLimitResult? {
        val configured = BuildConfig.JARVIS_AI_ENDPOINT.trim()
        if (!configured.startsWith("https://") || !configured.endsWith("/jarvis")) return null
        val valid = points.filter { it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }.takeLast(8)
        if (valid.size < 2) {
            Log.i(TAG, "TomTom trace not ready: fewer than two GPS fixes")
            return null
        }
        val base = configured.substringBeforeLast("/jarvis").trimEnd('/')
        val payloadPoints = JSONArray()
        valid.forEach { location ->
            val point = JSONObject()
                .put("latitude", location.latitude)
                .put("longitude", location.longitude)
            if (location.hasBearing() && location.hasSpeed() && location.speed >= 2.5f &&
                location.bearing.isFinite()) {
                point.put("bearing", location.bearing.toDouble())
            }
            payloadPoints.put(point)
        }
        val connection = URL(base + "/speed-limit").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 4000
            connection.readTimeout = 9000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use {
                it.write(JSONObject().put("points", payloadPoints).toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                Log.w(TAG, "TomTom Render proxy HTTP " + status + ": " + body.take(180))
                return null
            }
            val root = JSONObject(body)
            val limit = if (root.has("speedLimitKmh") && !root.isNull("speedLimitKmh"))
                root.optInt("speedLimitKmh", -1).takeIf { it in 5..200 } else null
            val result = TomTomSpeedLimitResult(
                speedLimitKmh = limit,
                road = root.optString("road").takeIf { it.isNotBlank() && it != "null" },
                ref = root.optString("ref").takeIf { it.isNotBlank() && it != "null" },
                highwayType = root.optString("highwayType").takeIf { it.isNotBlank() && it != "null" },
                reason = root.optString("reason").takeIf { it.isNotBlank() && it != "null" }
            )
            Log.i(TAG, if (limit != null) "TomTom returned explicit maximum " + limit + " km/h from " + valid.size + " GPS points"
                else "TomTom returned no explicit maximum; reason=" + (result.reason ?: "not provided"))
            return result
        } catch (error: Exception) {
            Log.w(TAG, "TomTom Render proxy request failed", error)
            return null
        } finally {
            connection.disconnect()
        }
    }
}
