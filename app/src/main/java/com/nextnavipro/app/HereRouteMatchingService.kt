package com.nextnavipro.app

import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * Optional HERE route-matching enrichment for speed limits.
 *
 * OSM remains the baseline and is never replaced by an invented value. HERE is
 * queried only when a key is configured and the OSM match has no usable limit.
 * Requests use a short, bounded trace and are throttled to conserve quota.
 */
internal object HereRouteMatchingService {
    private data class TracePoint(val point: NavPoint, val timestampMs: Long)

    private const val TAG = "NEXT_NAVI_HERE"
    private const val ENDPOINT = "https://routematching.hereapi.com/v8/match/routelinks"
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 10000
    private const val MIN_TRACE_POINTS = 3
    private const val MIN_TRACE_DISTANCE_METERS = 250.0
    private const val MIN_REQUEST_INTERVAL_MS = 60_000L
    private const val MAX_TRACE_AGE_MS = 5 * 60_000L
    private const val MAX_TRACE_POINTS = 8

    private val lock = Any()
    private val trace = ArrayDeque<TracePoint>()
    private var lastRequestMs = 0L
    private var lastRequestPoint: NavPoint? = null

    /** Returns null when disabled, unavailable, ambiguous, or lacking explicit data. */
    fun findLimit(location: Location, allowLookup: Boolean = true): Int? {
        val apiKey = BuildConfig.HERE_API_KEY.trim()
        if (apiKey.isEmpty()) return null
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null

        val now = System.currentTimeMillis()
        val point = NavPoint(location.latitude, location.longitude)
        val snapshot = synchronized(lock) {
            while (trace.isNotEmpty() && now - trace.peekFirst().timestampMs > MAX_TRACE_AGE_MS) {
                trace.removeFirst()
            }
            val last = trace.lastOrNull()
            if (last == null ||
                GpxRouteService.distanceMeters(last.point, point) >= 25.0 ||
                now - last.timestampMs >= 10_000L) {
                trace.addLast(TracePoint(point, now))
            }
            while (trace.size > MAX_TRACE_POINTS) trace.removeFirst()

            if (!allowLookup) return@synchronized emptyList()\n            val distanceFromLastRequest = lastRequestPoint?.let {
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
            query(apiKey, snapshot.map { it.point })
        } catch (error: Exception) {
            Log.w(TAG, "HERE route matching unavailable; retaining OSM result", error)
            null
        }
    }

    private fun query(apiKey: String, points: List<NavPoint>): Int? {
        val url = ENDPOINT +
            "?routeMatch=1" +
            "&mode=" + URLEncoder.encode("fastest;car;traffic:disabled", "UTF-8") +
            "&attributes=" + URLEncoder.encode(
                "SPEED_LIMITS_FCn(FROM_REF_SPEED_LIMIT,TO_REF_SPEED_LIMIT)",
                "UTF-8"
            ) +
            "&apiKey=" + URLEncoder.encode(apiKey, "UTF-8")
        val csv = buildString {
            append("LATITUDE,LONGITUDE\n")
            points.forEach {
                append(it.latitude).append(',').append(it.longitude).append('\n')
            }
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            connection.setRequestProperty("Content-Type", "text/csv; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(csv) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("HERE HTTP $status: " + body.take(180))
            }
            val limit = parseSpeedLimitFromResponse(body)
            if (limit == null) Log.i(TAG, "HERE response contained no unambiguous explicit speed limit")
            return limit
        } finally {
            connection.disconnect()
        }
    }

    /** Parses the last matched link only; opposing directional limits are never guessed. */
    internal fun parseSpeedLimitFromResponse(body: String): Int? {
        val root = JSONObject(body)
        val response = root.optJSONObject("response") ?: root
        val routes = response.optJSONArray("route") ?: return null
        var result: Int? = null
        for (routeIndex in 0 until routes.length()) {
            val route = routes.optJSONObject(routeIndex) ?: continue
            val legs = route.optJSONArray("leg") ?: continue
            for (legIndex in 0 until legs.length()) {
                val leg = legs.optJSONObject(legIndex) ?: continue
                val links = leg.optJSONArray("link") ?: continue
                for (linkIndex in 0 until links.length()) {
                    val link = links.optJSONObject(linkIndex) ?: continue
                    val attributes = link.optJSONObject("attributes") ?: continue
                    val candidate = parseDirectionalAttributes(attributes) ?: continue
                    result = candidate
                }
            }
        }
        return result
    }

    private fun parseDirectionalAttributes(attributes: JSONObject): Int? {
        val keys = attributes.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith("SPEED_LIMITS_FC")) continue
            val values = attributes.optJSONArray(key) ?: continue
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val from = readLimit(item, "FROM_REF_SPEED_LIMIT")
                val to = readLimit(item, "TO_REF_SPEED_LIMIT")
                val selected = when {
                    from != null && to != null && from == to -> from
                    from != null && to == null -> from
                    to != null && from == null -> to
                    else -> null
                }
                if (selected != null) return selected
            }
        }
        return null
    }

    private fun readLimit(item: JSONObject, field: String): Int? {
        val raw = item.opt(field)?.toString()?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true) ||
            raw.equals("unlimited", ignoreCase = true)) return null
        val numeric = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val unit = item.optString("UNIT", item.optString("SPEED_LIMIT_UNIT", "K"))
        val kmh = if (unit.equals("M", true) || unit.contains("mph", true)) numeric * 1.609344 else numeric
        return kmh.roundToInt().takeIf { it in 5..200 }
    }
}
