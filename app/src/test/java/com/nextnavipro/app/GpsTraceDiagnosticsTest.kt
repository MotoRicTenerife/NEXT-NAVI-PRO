package com.nextnavipro.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GpsTraceDiagnosticsTest {
    @Test fun csvIncludesRawFixQualityAndRouteMatch() {
        val csv = GpsTraceDiagnostics.toCsv(listOf(
            GpsTraceSample(
                timestampEpochMs = 1_800_000_000_000L,
                latitude = 28.1234567,
                longitude = -16.7654321,
                smoothedLatitude = 28.1234500,
                smoothedLongitude = -16.7654200,
                gpxPointStatus = "FILTERED_NEAR_POINT",
                accuracyMeters = 4.5f,
                speedMetersPerSecond = 12.0f,
                bearingDegrees = 91f,
                satellitesUsed = 18,
                satellitesVisible = 25,
                constellations = "GPS/GAL,SBAS",
                routeSegmentIndex = 42,
                routeDistanceMeters = 6.25f,
                matchStatus = "MATCHED",
                reportedSpeedMetersPerSecond = 12.0f,
                reportedBearingDegrees = 91f,
                motionSource = "GNSS"
            )
        ))
        assertTrue(csv.startsWith("timestamp_epoch_ms,latitude,longitude,smoothed_latitude,smoothed_longitude,gpx_point_status"))
        assertTrue(csv.contains("28.1234567,-16.7654321,28.1234500,-16.7654200,FILTERED_NEAR_POINT,4.50,12.00,91.0,12.00,91.0,GNSS,18,25,\"GPS/GAL,SBAS\",42,6.25,MATCHED"))
    }
}
