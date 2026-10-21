package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * A user-confirmed speed limit attached to a directed road section.
 * Coordinates are WGS84. Geometry follows the mapped road, not a straight chord
 * between the two flags, so curves and junctions can be represented safely.
 */
data class ManualSpeedLimitSegment(
    val id: String,
    val roadKey: String?,
    val roadName: String?,
    val geometry: List<NavPoint>,
    val forwardLimitKmh: Int?,
    val reverseLimitKmh: Int?,
    val updatedAtEpochMs: Long,
    val revision: Long = 1L,
    val status: Status = Status.CONFIRMED
) {
    enum class Status { PENDING, CONFIRMED, CONTESTED }

    init {
        require(id.isNotBlank())
        require(geometry.size >= 2)
        require(geometry.all { it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 })
        require(forwardLimitKmh == null || forwardLimitKmh in 5..200)
        require(reverseLimitKmh == null || reverseLimitKmh in 5..200)
        require(forwardLimitKmh != null || reverseLimitKmh != null)
        require(revision >= 1)
    }

    fun limitForDirection(vehicleBearingDegrees: Float?, minDirectionConfidence: Float = 0.70f): Int? {
        if (vehicleBearingDegrees == null || !vehicleBearingDegrees.isFinite() ||
            minDirectionConfidence !in 0f..1f || status != Status.CONFIRMED) return null
        val first = geometry.first()
        val last = geometry.last()
        val roadBearing = bearing(first, last)
        val delta = abs(((vehicleBearingDegrees - roadBearing + 540f) % 360f) - 180f)
        // A direction within 60 degrees of the digitized geometry is forward;
        // within 60 degrees of the opposite direction is reverse. Junction-like
        // cross angles remain ambiguous and never inherit a directional limit.
        return when {
            delta <= 60f -> forwardLimitKmh
            delta >= 120f -> reverseLimitKmh
            else -> null
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("roadKey", roadKey ?: JSONObject.NULL)
        put("roadName", roadName ?: JSONObject.NULL)
        put("geometry", JSONArray().apply {
            geometry.forEach { point -> put(JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)) }
        })
        put("forwardLimitKmh", forwardLimitKmh ?: JSONObject.NULL)
        put("reverseLimitKmh", reverseLimitKmh ?: JSONObject.NULL)
        put("updatedAtEpochMs", updatedAtEpochMs)
        put("revision", revision)
        put("status", status.name)
        put("schemaVersion", 1)
    }

    companion object {
        fun fromJson(json: JSONObject): ManualSpeedLimitSegment {
            require(json.optInt("schemaVersion", 0) == 1) { "Unsupported manual segment schema" }
            val points = json.optJSONArray("geometry") ?: throw IllegalArgumentException("Missing geometry")
            val geometry = (0 until points.length()).map { index ->
                val point = points.getJSONObject(index)
                NavPoint(point.getDouble("latitude"), point.getDouble("longitude"))
            }
            fun nullableLimit(key: String): Int? =
                if (json.isNull(key) || !json.has(key)) null else json.optInt(key, -1).takeIf { it in 5..200 }
                    ?: throw IllegalArgumentException("Invalid $key")
            val status = runCatching { Status.valueOf(json.optString("status", "PENDING")) }.getOrDefault(Status.PENDING)
            return ManualSpeedLimitSegment(
                id = json.getString("id"),
                roadKey = json.optString("roadKey").takeIf { it.isNotBlank() && it != "null" },
                roadName = json.optString("roadName").takeIf { it.isNotBlank() && it != "null" },
                geometry = geometry,
                forwardLimitKmh = nullableLimit("forwardLimitKmh"),
                reverseLimitKmh = nullableLimit("reverseLimitKmh"),
                updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
                revision = json.optLong("revision", 1L),
                status = status
            )
        }

        private fun bearing(a: NavPoint, b: NavPoint): Float {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        }
    }
}
