package com.nextnavipro.app

import java.util.Locale

internal data class ArrowTraceSample(
    val timestampEpochMs: Long, val elapsedRealtimeNanos: Long,
    val gpsFixEpochMs: Long?, val gpsFixElapsedRealtimeNanos: Long?,
    val rawLatitude: Double?, val rawLongitude: Double?, val rawBearingDegrees: Float?,
    val filteredLatitude: Double?, val filteredLongitude: Double?, val filteredBearingDegrees: Float?,
    val renderedLatitude: Double, val renderedLongitude: Double,
    val renderedBearingDegrees: Float?, val gpsAccuracyMeters: Float?, val bearingAccuracyDegrees: Float?,
    val gpsSpeedMetersPerSecond: Float?, val animationFraction: Float?,
    val cameraLatitude: Double?, val cameraLongitude: Double?,
    val cameraBearingDegrees: Double?, val cameraZoom: Double?
)

internal object ArrowTraceDiagnostics {
    private const val HEADER = "timestamp_epoch_ms,elapsed_realtime_ns,gps_fix_epoch_ms,gps_fix_elapsed_realtime_ns,raw_latitude,raw_longitude,raw_bearing_deg,filtered_latitude,filtered_longitude,filtered_bearing_deg,rendered_latitude,rendered_longitude,rendered_bearing_deg,gps_accuracy_m,gps_bearing_accuracy_deg,gps_speed_mps,animation_fraction,camera_latitude,camera_longitude,camera_bearing_deg,camera_zoom"
    fun toCsv(samples: List<ArrowTraceSample>, includeHeader: Boolean = true): String = buildString {
        if (includeHeader) appendLine(HEADER)
        for (s in samples) {
            append(s.timestampEpochMs).append(',').append(s.elapsedRealtimeNanos).append(',')
            append(s.gpsFixEpochMs?.toString() ?: "").append(',').append(s.gpsFixElapsedRealtimeNanos?.toString() ?: "").append(',')
            append(n(s.rawLatitude,7)).append(',').append(n(s.rawLongitude,7)).append(',')
            append(n(s.rawBearingDegrees?.toDouble(),1)).append(',')
            append(n(s.filteredLatitude,7)).append(',').append(n(s.filteredLongitude,7)).append(',')
            append(n(s.filteredBearingDegrees?.toDouble(),1)).append(',')
            append(n(s.renderedLatitude,7)).append(',').append(n(s.renderedLongitude,7)).append(',')
            append(n(s.renderedBearingDegrees?.toDouble(),1)).append(',')
            append(n(s.gpsAccuracyMeters?.toDouble(),2)).append(',')
            append(n(s.bearingAccuracyDegrees?.toDouble(),1)).append(',')
            append(n(s.gpsSpeedMetersPerSecond?.toDouble(),2)).append(',')
            append(n(s.animationFraction?.toDouble(),4)).append(',')
            append(n(s.cameraLatitude,7)).append(',').append(n(s.cameraLongitude,7)).append(',')
            append(n(s.cameraBearingDegrees,2)).append(',').appendLine(n(s.cameraZoom,3))
        }
    }
    private fun n(value: Double?, decimals: Int): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: ""
}
