package com.nextnavipro.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.*

/**
 * Reads explicitly mapped OSM maxspeed values from nearby road ways.
 * It intentionally returns null when the road/value cannot be matched with enough confidence.
 */
object SpeedLimitService {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val RADIUS_METERS = 100
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    fun findNear(point: NavPoint, bearing: Float?): Int? {
        val lat = point.latitude.toString()
        val lon = point.longitude.toString()
        val query = "[out:json][timeout:12];way[highway][maxspeed](around:$RADIUS_METERS,$lat,$lon);out tags geom;"
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
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
            val body = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() } }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Overpass HTTP $status")
            val elements = JSONObject(body).optJSONArray("elements") ?: return null
            var bestDistance = Float.MAX_VALUE
            var bestLimit: Int? = null
            for (i in 0 until elements.length()) {
                val way = elements.optJSONObject(i) ?: continue
                val tags = way.optJSONObject("tags") ?: continue
                val value = tags.optString("maxspeed")
                val limit = parseLimit(value) ?: continue
                val geometry = way.optJSONArray("geometry") ?: continue
                if (geometry.length() < 2) continue
                for (j in 0 until geometry.length() - 1) {
                    val a = geometry.optJSONObject(j) ?: continue
                    val b = geometry.optJSONObject(j + 1) ?: continue
                    val aLat = a.optDouble("lat", Double.NaN)
                    val aLon = a.optDouble("lon", Double.NaN)
                    val bLat = b.optDouble("lat", Double.NaN)
                    val bLon = b.optDouble("lon", Double.NaN)
                    if (!aLat.isFinite() || !aLon.isFinite() || !bLat.isFinite() || !bLon.isFinite()) continue
                    val (distance, segmentBearing) = pointSegmentDistanceAndBearing(point, aLat, aLon, bLat, bLon)
                    if (distance > 45f) continue
                    if (bearing != null && distance > 12f && angularDifference(bearing, segmentBearing) > 65f) continue
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestLimit = limit
                    }
                }
            }
            return bestLimit
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLimit(raw: String): Int? {
        val value = raw.trim().lowercase()
        val match = Regex("^(\\d{1,3})(?:\\s*(mph|km/h|kph))?$").matchEntire(value) ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        if (number !in 5..200) return null
        return if (match.groupValues[2] == "mph") (number * 1.609344).roundToInt() else number
    }

    private fun pointSegmentDistanceAndBearing(p: NavPoint, lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Float, Float> {
        val metersPerLat = 111_320.0
        val metersPerLon = 111_320.0 * cos(Math.toRadians(p.latitude)).coerceAtLeast(0.01)
        val ax = (lon1 - p.longitude) * metersPerLon
        val ay = (lat1 - p.latitude) * metersPerLat
        val bx = (lon2 - p.longitude) * metersPerLon
        val by = (lat2 - p.latitude) * metersPerLat
        val dx = bx - ax
        val dy = by - ay
        val denom = dx * dx + dy * dy
        val t = if (denom <= 0.001) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        val distance = hypot(closestX, closestY).toFloat()
        val bearing = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
        return distance to bearing.toFloat()
    }

    private fun angularDifference(a: Float, b: Float): Float {
        val diff = abs((a - b + 540f) % 360f - 180f)
        return min(diff, 360f - diff)
    }
}
