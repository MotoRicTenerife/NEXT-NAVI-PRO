package com.nextnavipro.app

import java.util.Locale

internal data class GpsTraceSample(
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val smoothedLatitude: Double?,
    val smoothedLongitude: Double?,
    val gpxPointStatus: String,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    val constellations: String,
    val routeSegmentIndex: Int?,
    val routeDistanceMeters: Float?,
    val matchStatus: String,
    val reportedSpeedMetersPerSecond: Float? = null,
    val reportedBearingDegrees: Float? = null,
    val motionSource: String = "UNKNOWN",
    val elapsedRealtimeMs: Long? = null
)

internal object GpsTraceDiagnostics {
    fun toCsv(samples: List<GpsTraceSample>): String = buildString {
        appendLine("timestamp_epoch_ms,elapsed_realtime_ms,latitude,longitude,smoothed_latitude,smoothed_longitude,gpx_point_status,accuracy_m,speed_mps,bearing_deg,reported_speed_mps,reported_bearing_deg,motion_source,satellites_used,satellites_visible,constellations,route_segment_index,route_distance_m,match_status")
        for (s in samples) {
            append(s.timestampEpochMs).append(',')
            append(s.elapsedRealtimeMs?.toString() ?: "").append(',')
            append(String.format(Locale.US, "%.7f", s.latitude)).append(',')
            append(String.format(Locale.US, "%.7f", s.longitude)).append(',')
            append(s.smoothedLatitude?.let { String.format(Locale.US, "%.7f", it) } ?: "").append(',')
            append(s.smoothedLongitude?.let { String.format(Locale.US, "%.7f", it) } ?: "").append(',')
            append(csv(s.gpxPointStatus)).append(',')
            append(s.accuracyMeters?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(',')
            append(s.speedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..100f }?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(',')
            append(s.bearingDegrees?.takeIf { it.isFinite() && it in 0f..360f }?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(',')
            append(s.reportedSpeedMetersPerSecond?.takeIf { it.isFinite() && it in 0f..100f }?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(',')
            append(s.reportedBearingDegrees?.takeIf { it.isFinite() && it in 0f..360f }?.let { String.format(Locale.US, "%.1f", it) } ?: "").append(',')
            append(csv(s.motionSource)).append(',')
            append(s.satellitesUsed).append(',')
            append(s.satellitesVisible).append(',')
            append(csv(s.constellations)).append(',')
            append(s.routeSegmentIndex?.toString() ?: "").append(',')
            append(s.routeDistanceMeters?.let { String.format(Locale.US, "%.2f", it) } ?: "").append(',')
            appendLine(csv(s.matchStatus))
        }
    }

    private fun csv(value: String): String {
        if (!value.contains(',') && !value.contains('"') && !value.contains('\n') && !value.contains('\r')) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
