package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque

/**
 * Optional server-side HERE route-matching enrichment for speed limits.
 * The HERE credential stays on the backend and is never packaged into the APK.
 * OSM remains the baseline; missing or ambiguous data stays unknown.
 */
internal object HereRouteMatchingService {
    private data class TracePoint(val point: NavPoint, val timestampMs: Long)

    private const val TAG = "NEXT_NAVI_HERE"
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 10000
    // Collect trace points from every accepted GPS fix, not only when the slow
    // Overpass lookup happens. Otherwise a trace may take minutes to become usable.
    private const val MIN_TRACE_POINTS = 2
    private const val MIN_TRACE_DISTANCE_METERS = 100.0
    private const val MIN_REQUEST_INTERVAL_MS = 20_000L
    private const val MAX_TRACE_AGE_MS = 5 * 60_000L
    private const val MAX_TRACE_POINTS = 8

    private val lock = Any()
    private val trace = ArrayDeque<TracePoint>()
    private var lastRequestMs = 0L
    private var lastRequestPoint: NavPoint? = null

    /**
     * Record each accepted GPS fix independently of the OSM request cadence. This is
     * critical: Overpass can take many seconds, so using only its refresh callback
     * produced too few points and delayed/blocked HERE matching.
     */
    fun recordLocation(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        val now = System.currentTimeMillis()
        val point = NavPoint(location.latitude, location.longitude)
        synchronized(lock) {
            while (trace.isNotEmpty() && now - trace.peekFirst().timestampMs > MAX_TRACE_AGE_MS) {
                trace.removeFirst()
            }
            val last = trace.lastOrNull()
            if (last == null ||
                GpxRouteService.distanceMeters(last.point, point) >= 20.0 ||
                now - last.timestampMs >= 5_000L) {
                trace.addLast(TracePoint(point, now))
            }
            while (trace.size > MAX_TRACE_POINTS) trace.removeFirst()
        }
    }

    /** Returns null when disabled, unavailable, ambiguous, or lacking explicit data. */
    fun findLimit(location: Location, allowLookup: Boolean = true): Int? {
        val configuredEndpoint = BuildConfig.JARVIS_AI_ENDPOINT.trim()
        if (!configuredEndpoint.startsWith("https://") || !configuredEndpoint.endsWith("/jarvis")) {
            Log.w(TAG, "Speed-limit proxy not configured; check NEXT_NAVI_AI_ENDPOINT in the Android build")
            return null
        }
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null

        recordLocation(location)
        if (!allowLookup) return null
        val now = System.currentTimeMillis()
        val point = NavPoint(location.latitude, location.longitude)
        val snapshot = synchronized(lock) {
            val distanceFromLastRequest = lastRequestPoint?.let {
                GpxRouteService.distanceMeters(it, point)
            } ?: Double.POSITIVE_INFINITY
            if (trace.size < MIN_TRACE_POINTS ||
                now - lastRequestMs < MIN_REQUEST_INTERVAL_MS ||
                distanceFromLastRequest < MIN_TRACE_DISTANCE_METERS) {
                return@synchronized emptyList()
            }
            lastRequestMs = now
            lastRequestPoint = point
            trace.toList()
        }
        if (snapshot.isEmpty()) return null

        return try {
            query(configuredEndpoint.substringBeforeLast("/jarvis").trimEnd('/') + "/speed-limit", snapshot.map { it.point })
        } catch (error: Exception) {
            Log.w(TAG, "HERE route matching proxy unavailable; retaining OSM result", error)
            null
        }
    }

    private fun query(endpoint: String, points: List<NavPoint>): Int? {
        val payload = JSONObject().put("points", org.json.JSONArray().apply {
            points.forEach { point ->
                put(JSONObject().put("latitude", point.latitude).put("longitude", point.longitude))
            }
        })
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Speed-limit proxy HTTP $status")
            return parseProxyResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseProxyResponse(body: String): Int? {
        val root = JSONObject(body)
        if (!root.has("speedLimitKmh") || root.isNull("speedLimitKmh")) return null
        val value = root.optInt("speedLimitKmh", -1)
        return value.takeIf { it in 5..200 }
    }
}
