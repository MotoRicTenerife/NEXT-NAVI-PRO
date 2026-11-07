package com.nextnavipro.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * Persistent route cache. Cached routing data is never presented as live data:
 * callers must state the snapshot time and freshness to the rider.
 */
data class CachedRouteInsight(
    val destinationName: String,
    val destination: NavPoint,
    val origin: NavPoint,
    val route: RouteResult,
    val fetchedAtEpochMs: Long
) {
    fun ageMs(now: Long = System.currentTimeMillis()): Long = (now - fetchedAtEpochMs).coerceAtLeast(0L)
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = ageMs(now) <= FRESH_FOR_MS

    companion object {
        const val FRESH_FOR_MS = 30L * 60L * 1000L
        const val MAX_REUSE_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

object RouteInsightCache {
    private const val PREFS = "next_navi_route_insight_cache"
    private const val KEY_PREFIX = "route_"

    fun save(
        context: Context,
        destinationName: String,
        destination: NavPoint,
        origin: NavPoint,
        route: RouteResult,
        now: Long = System.currentTimeMillis()
    ) {
        val json = JSONObject()
            .put("destinationName", destinationName.trim())
            .put("destination", pointJson(destination))
            .put("origin", pointJson(origin))
            .put("fetchedAt", now)
            .put("distanceKm", route.distanceKm)
            .put("durationSeconds", route.durationSeconds)
            .put("firstInstruction", route.firstInstruction)
            .put("points", JSONArray().apply { route.points.forEach { put(pointJson(it)) } })
            .put("maneuvers", JSONArray().apply {
                route.maneuvers.forEach { maneuver ->
                    put(JSONObject()
                        .put("pointIndex", maneuver.pointIndex)
                        .put("instruction", maneuver.instruction)
                        .put("type", maneuver.type)
                        .put("streetName", maneuver.streetName))
                }
            })
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(cacheKey(destinationName), json.toString()).apply()
    }

    fun loadForDestination(
        context: Context,
        name: String,
        currentOrigin: NavPoint?,
        now: Long = System.currentTimeMillis()
    ): CachedRouteInsight? {
        if (currentOrigin == null) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(cacheKey(name), null) ?: return null
        return try {
            val json = JSONObject(raw)
            if (!json.optString("destinationName").equals(name.trim(), ignoreCase = true)) return null
            val fetchedAt = json.optLong("fetchedAt", 0L)
            if (fetchedAt <= 0L || now - fetchedAt > CachedRouteInsight.MAX_REUSE_AGE_MS ||
                fetchedAt > now + 60_000L) return null
            val origin = parsePoint(json.optJSONObject("origin")) ?: return null
            val destination = parsePoint(json.optJSONObject("destination")) ?: return null
            if (distanceMeters(origin, currentOrigin) > 1500.0) return null
            val pointsArray = json.optJSONArray("points") ?: return null
            if (pointsArray.length() < 2) return null
            val points = (0 until pointsArray.length()).mapNotNull {
                parsePoint(pointsArray.optJSONObject(it))
            }
            if (points.size != pointsArray.length()) return null
            val maneuverArray = json.optJSONArray("maneuvers") ?: JSONArray()
            val maneuvers = (0 until maneuverArray.length()).mapNotNull { i ->
                val item = maneuverArray.optJSONObject(i) ?: return@mapNotNull null
                val index = item.optInt("pointIndex", -1)
                if (index !in points.indices) return@mapNotNull null
                RouteManeuver(index, item.optString("instruction"), item.optInt("type"),
                    item.optString("streetName"))
            }
            val route = RouteResult(points, json.optDouble("distanceKm", 0.0),
                json.optInt("durationSeconds", 0), json.optString("firstInstruction"), maneuvers)
            if (!route.distanceKm.isFinite() || route.distanceKm <= 0.0) return null
            CachedRouteInsight(json.optString("destinationName"), destination, origin, route, fetchedAt)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun cacheKey(name: String): String {
        val normalized = java.text.Normalizer.normalize(name.trim().lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val slug = normalized.replace(Regex("[^a-z0-9]+"), "_").trim('_').take(60)
        return KEY_PREFIX + if (slug.isBlank()) normalized.hashCode().toString() else slug
    }

    private fun pointJson(point: NavPoint) = JSONObject()
        .put("lat", point.latitude).put("lon", point.longitude)

    private fun parsePoint(json: JSONObject?): NavPoint? {
        if (json == null) return null
        val lat = json.optDouble("lat", Double.NaN)
        val lon = json.optDouble("lon", Double.NaN)
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 ||
            lon !in -180.0..180.0) return null
        return NavPoint(lat, lon)
    }

    private fun distanceMeters(a: NavPoint, b: NavPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 6371000.0 * 2.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
