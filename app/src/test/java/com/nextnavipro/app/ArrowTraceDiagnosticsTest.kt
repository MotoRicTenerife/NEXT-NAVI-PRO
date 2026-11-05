package com.nextnavipro.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowTraceDiagnosticsTest {
    @Test fun csvContainsRawFilteredAndRenderedPositionAndCourse() {
        val sample = ArrowTraceSample(
            timestampEpochMs = 1000L, elapsedRealtimeNanos = 2_000_000L,
            gpsFixEpochMs = 900L, gpsFixElapsedRealtimeNanos = 1_900_000L,
            rawLatitude = 28.1, rawLongitude = -16.2, rawBearingDegrees = 89f,
            filteredLatitude = 28.1001, filteredLongitude = -16.2001, filteredBearingDegrees = 90f,
            renderedLatitude = 28.1002, renderedLongitude = -16.2002, renderedBearingDegrees = 91.5f,
            gpsAccuracyMeters = 3f, bearingAccuracyDegrees = 4f, gpsSpeedMetersPerSecond = 8f,
            animationFraction = 0.5f, cameraLatitude = 28.1, cameraLongitude = -16.2,
            cameraBearingDegrees = 90.0, cameraZoom = 16.5
        )
        val csv = ArrowTraceDiagnostics.toCsv(listOf(sample))
        assertTrue(csv.contains("raw_bearing_deg,filtered_latitude,filtered_longitude,filtered_bearing_deg"))
        assertTrue(csv.contains("rendered_latitude,rendered_longitude,rendered_bearing_deg"))
        assertTrue(csv.contains("28.1002000,-16.2002000,91.5"))
        assertTrue(csv.contains("900,1900000"))
        assertTrue(csv.contains("4.0,8.00"))
    }

    @Test fun appendBatchOmitsRepeatedHeader() {
        val sample = ArrowTraceSample(1L, 2L, null, null, null, null, null, null,
            null, null, 1.0, 2.0, null, null, null, null, null, null, null, null)
        val csv = ArrowTraceDiagnostics.toCsv(listOf(sample), includeHeader = false)
        assertTrue(!csv.startsWith("timestamp_epoch_ms"))
        assertTrue(csv.startsWith("1,2,"))
    }
}
