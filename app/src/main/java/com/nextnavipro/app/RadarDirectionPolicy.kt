package com.nextnavipro.app

import kotlin.math.abs
import kotlin.math.sin

/** Conservative direction/corridor checks shared by radar alert logic and unit tests. */
internal object RadarDirectionPolicy {
    fun parseBearing(direction: String): Float? {
        val value = direction.trim().uppercase()
            .replace("°", "")
            .replace("DEG", "")
            .trim()
        val numeric = value.toFloatOrNull()
        if (numeric != null && numeric.isFinite() && numeric in 0f..360f) {
            return if (numeric >= 360f) 0f else numeric
        }
        return when (value) {
            "N", "NORTH", "NORTHBOUND" -> 0f
            "NE", "NORTHEAST" -> 45f
            "E", "EAST", "EASTBOUND" -> 90f
            "SE", "SOUTHEAST" -> 135f
            "S", "SOUTH", "SOUTHBOUND" -> 180f
            "SW", "SOUTHWEST" -> 225f
            "W", "WEST", "WESTBOUND" -> 270f
            "NW", "NORTHWEST" -> 315f
            else -> null
        }
    }

    fun isDirectionCompatible(direction: String, travelBearingDegrees: Float, toleranceDegrees: Float = 65f): Boolean {
        val cameraBearing = parseBearing(direction) ?: return true
        if (!travelBearingDegrees.isFinite()) return true
        val delta = abs(((cameraBearing - travelBearingDegrees + 540f) % 360f) - 180f)
        return delta <= toleranceDegrees
    }

    fun isInsideForwardCorridor(
        distanceMeters: Float,
        bearingDeltaDegrees: Float,
        maxDistanceMeters: Float,
        maxCrossTrackMeters: Float,
        maxBearingDeltaDegrees: Float = 40f
    ): Boolean {
        if (!distanceMeters.isFinite() || !bearingDeltaDegrees.isFinite() ||
            distanceMeters < 0f || distanceMeters > maxDistanceMeters ||
            bearingDeltaDegrees < 0f || bearingDeltaDegrees > maxBearingDeltaDegrees ||
            maxCrossTrackMeters < 0f) return false
        val lateralDistance = distanceMeters * sin(Math.toRadians(bearingDeltaDegrees.toDouble())).toFloat()
        return lateralDistance.isFinite() && lateralDistance <= maxCrossTrackMeters
    }
}
