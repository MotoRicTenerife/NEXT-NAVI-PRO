package com.nextnavipro.app

/** Pure heading selection and circular-angle math; no UI or sensor lifecycle dependencies. */
internal data class HeadingDecision(val bearingDegrees: Float?, val source: String)

internal object DeviceHeadingFusion {
    private const val MIN_MOVING_GPS_SPEED_MPS = 2.5f
    private const val MAX_GPS_BEARING_ACCURACY_DEGREES = 35f
    const val MIN_COMPASS_ACCURACY_LEVEL = 2

    fun select(
        gpsBearingDegrees: Float?,
        speedMetersPerSecond: Float?,
        gpsBearingAccuracyDegrees: Float?,
        compassBearingDegrees: Float?,
        compassAccuracyLevel: Int
    ): HeadingDecision {
        val gps = gpsBearingDegrees?.takeIf { it.isFinite() }?.let(::normalize)
        val speed = speedMetersPerSecond?.takeIf { it.isFinite() } ?: 0f
        val gpsAccuracyOk = gpsBearingAccuracyDegrees == null ||
            (gpsBearingAccuracyDegrees.isFinite() && gpsBearingAccuracyDegrees <= MAX_GPS_BEARING_ACCURACY_DEGREES)
        if (gps != null && speed >= MIN_MOVING_GPS_SPEED_MPS && gpsAccuracyOk) {
            return HeadingDecision(gps, "GPS")
        }
        val compass = compassBearingDegrees?.takeIf { it.isFinite() }?.let(::normalize)
        if (compass != null && compassAccuracyLevel >= MIN_COMPASS_ACCURACY_LEVEL) {
            return HeadingDecision(compass, "COMPASS")
        }
        return if (gps != null) HeadingDecision(gps, "GPS_FALLBACK") else HeadingDecision(null, "NONE")
    }

    fun normalize(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        return ((degrees % 360f) + 360f) % 360f
    }

    /** Interpolate the shortest way around the 0/360-degree boundary. */
    fun interpolate(fromDegrees: Float, toDegrees: Float, fraction: Float): Float {
        val from = normalize(fromDegrees)
        val to = normalize(toDegrees)
        val delta = ((to - from + 540f) % 360f) - 180f
        return normalize(from + delta * fraction.coerceIn(0f, 1f))
    }
}
