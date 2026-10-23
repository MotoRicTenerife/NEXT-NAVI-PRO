package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Outcome category of one Render/TomTom lookup, so callers can back off instead of retrying blindly. */
enum class TomTomLookupStatus { LIMIT, NO_LIMIT, RATE_LIMITED, TIMEOUT, NETWORK_ERROR, HTTP_ERROR }

/** Secure Render proxy client for TomTom Snap to Roads. The TomTom API key stays server-side. */
data class TomTomSpeedLimitResult(
    val speedLimitKmh: Int?,
    val road: String?,
    val ref: String?,
    val highwayType: String?,
    val reason: String?,
    val status: TomTomLookupStatus = TomTomLookupStatus.NO_LIMIT,
    val retryAfterSec: Int? = null
)

object RenderTomTomSpeedLimitService {
    private const val TAG = "NEXT_NAVI_SPEED_LIMIT"

    /** Seconds from the Retry-After header, else from the JSON body; null if absent or implausible. */
    internal fun parseRetryAfter(header: String?, body: String): Int? {
        val fromHeader = header?.trim()?.toIntOrNull()?.takeIf { it in 1..3600 }
        if (fromHeader != null) return fromHeader
        return try {
            JSONObject(body).optInt("retryAfterSeconds", -1).takeIf { it in 1..3600 }
        } catch (ignored: Exception) {
            null
        }
    }

    internal fun statusForHttp(code: Int): TomTomLookupStatus =
        if (code == 429) TomTomLookupStatus.RATE_LIMITED else TomTomLookupStatus.HTTP_ERROR

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
                val reason = try {
                    JSONObject(body).optString("reason").takeIf { it.isNotBlank() } ?: ("http_" + status)
                } catch (ignored: Exception) {
                    "http_" + status
                }
                return TomTomSpeedLimitResult(
                    speedLimitKmh = null, road = null, ref = null, highwayType = null, reason = reason,
                    status = statusForHttp(status),
                    retryAfterSec = if (status == 429) parseRetryAfter(connection.getHeaderField("Retry-After"), body) else null
                )
            }
            val root = JSONObject(body)
            val limit = if (root.has("speedLimitKmh") && !root.isNull("speedLimitKmh"))
                root.optInt("speedLimitKmh", -1).takeIf { it in 5..200 } else null
            val result = TomTomSpeedLimitResult(
                speedLimitKmh = limit,
                road = root.optString("road").takeIf { it.isNotBlank() && it != "null" },
                ref = root.optString("ref").takeIf { it.isNotBlank() && it != "null" },
                highwayType = root.optString("highwayType").takeIf { it.isNotBlank() && it != "null" },
                reason = root.optString("reason").takeIf { it.isNotBlank() && it != "null" },
                status = if (limit != null) TomTomLookupStatus.LIMIT else TomTomLookupStatus.NO_LIMIT
            )
            Log.i(TAG, if (limit != null) "TomTom returned explicit maximum " + limit + " km/h from " + valid.size + " GPS points"
                else "TomTom returned no explicit maximum; reason=" + (result.reason ?: "not provided"))
            return result
        } catch (error: SocketTimeoutException) {
            Log.w(TAG, "TomTom Render proxy timed out", error)
            return TomTomSpeedLimitResult(null, null, null, null, "timeout", TomTomLookupStatus.TIMEOUT)
        } catch (error: IOException) {
            Log.w(TAG, "TomTom Render proxy network error", error)
            return TomTomSpeedLimitResult(null, null, null, null, "network_error", TomTomLookupStatus.NETWORK_ERROR)
        } catch (error: Exception) {
            Log.w(TAG, "TomTom Render proxy request failed", error)
            return TomTomSpeedLimitResult(null, null, null, null, "bad_response", TomTomLookupStatus.HTTP_ERROR)
        } finally {
            connection.disconnect()
        }
    }
}
