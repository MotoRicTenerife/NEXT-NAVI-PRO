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
 * Road matching based on the nearest mapped road segment, not on whichever road
 * happens to have a maxspeed tag nearby. A missing/ambiguous limit stays unknown.
 *
 * OSM fields are intentionally kept separate: name is the road name, ref is its
 * route number (for example TF-85), and maxspeed is an explicit mapped limit.
 */
data class RoadMatch(
    val name: String?,
    val ref: String?,
    val speedLimitKmh: Int?,
    val distanceMeters: Float,
    val roadBearing: Float
) {
    val displayName: String?
        get() = listOfNotNull(ref?.takeIf { it.isNotBlank() }, name?.takeIf { it.isNotBlank() })
            .distinct().joinToString(" · ").takeIf { it.isNotBlank() }
}

object SpeedLimitService {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val RADIUS_METERS = 80
    private const val MAX_MATCH_DISTANCE_METERS = 28f
    private const val MAX_HEADING_DIFFERENCE_DEGREES = 50f
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    /**
     * Finds the closest plausible road segment first, then reads that road's
     * name/ref/maxspeed. This prevents borrowing a limit from a parallel road
     * or a nearby slip road simply because it is the only tagged candidate.
     */
    fun findRoadNear(point: NavPoint, bearing: Float?): RoadMatch? {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
        val lat = point.latitude.toString()
        val lon = point.longitude.toString()
        val query = "[out:json][timeout:12];way[highway](around:$RADIUS_METERS,$lat,$lon);out tags geom;"
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
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Overpass HTTP $status")
            val elements = JSONObject(body).optJSONArray("elements") ?: return null

            var best: RoadMatch? = null
            var bestScore = Float.MAX_VALUE
            for (i in 0 until elements.length()) {
                val way = elements.optJSONObject(i) ?: continue
                val tags = way.optJSONObject("tags") ?: continue
                val geometry = way.optJSONArray("geometry") ?: continue
                if (geometry.length() < 2) continue

                var wayBestDistance = Float.MAX_VALUE
                var wayBestBearing = 0f
                for (j in 0 until geometry.length() - 1) {
                    val a = geometry.optJSONObject(j) ?: continue
                    val b = geometry.optJSONObject(j + 1) ?: continue
                    val aLat = a.optDouble("lat", Double.NaN)
                    val aLon = a.optDouble("lon", Double.NaN)
                    val bLat = b.optDouble("lat", Double.NaN)
                    val bLon = b.optDouble("lon", Double.NaN)
                    if (!aLat.isFinite() || !aLon.isFinite() || !bLat.isFinite() || !bLon.isFinite()) continue
                    if (aLat !in -90.0..90.0 || bLat !in -90.0..90.0 ||
                        aLon !in -180.0..180.0 || bLon !in -180.0..180.0) continue
                    val (distance, segmentBearing) = pointSegmentDistanceAndBearing(point, aLat, aLon, bLat, bLon)
                    if (distance < wayBestDistance) {
                        wayBestDistance = distance
                        wayBestBearing = segmentBearing
                    }
                }
                if (wayBestDistance > MAX_MATCH_DISTANCE_METERS) continue
                val headingDelta = bearing?.let { angularDifference(it, wayBestBearing) }
                if (headingDelta != null && wayBestDistance > 8f &&
                    headingDelta > MAX_HEADING_DIFFERENCE_DEGREES) continue

                // Distance dominates; heading helps disambiguate parallel/intersecting roads.
                val score = wayBestDistance + (headingDelta ?: 0f) * 0.12f
                if (score < bestScore) {
                    bestScore = score
                    best = RoadMatch(
                        name = tags.optString("name").takeIf { it.isNotBlank() && it != "null" },
                        ref = tags.optString("ref").takeIf { it.isNotBlank() && it != "null" },
                        speedLimitKmh = parseLimit(tags.optString("maxspeed")),
                        distanceMeters = wayBestDistance,
                        roadBearing = wayBestBearing
                    )
                }
            }
            return best
        } finally {
            connection.disconnect()
        }
    }

    /** Backwards-compatible helper for callers interested only in a numeric limit. */
    fun findNear(point: NavPoint, bearing: Float?): Int? =
        findRoadNear(point, bearing)?.speedLimitKmh

    /**
     * Accepts only explicit numeric OSM limits. Contextual/conditional values
     * such as ES:urban, signals, variable, none, or malformed values remain unknown.
     */
    internal fun parseLimit(raw: String): Int? {
        val value = raw.trim().lowercase(Locale.ROOT)
        val match = Regex("^(\\d{1,3})(?:\\s*(mph|km/h|kmh|kph|knots?))?$").matchEntire(value)
            ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        if (number !in 5..200) return null
        return when (match.groupValues[2]) {
            "mph" -> (number * 1.609344).roundToInt().takeIf { it in 5..200 }
            "knot", "knots" -> (number * 1.852).roundToInt().takeIf { it in 5..200 }
            else -> number
        }
    }

    private fun pointSegmentDistanceAndBearing(
        p: NavPoint, lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Pair<Float, Float> {
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
