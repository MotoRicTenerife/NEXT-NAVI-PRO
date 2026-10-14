package com.nextnavipro.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GpsTraceDiagnosticsTest {
    @Test fun csvIncludesRawFixQualityAndRouteMatch() {
        val csv = GpsTraceDiagnostics.toCsv(listOf(
            GpsTraceSample(1_800_000_000_000L, 28.1234567, -16.7654321, 4.5f, 12.0f, 91f,
                18, 25, "GPS/GAL,SBAS", 42, 6.25f, "MATCHED")
        ))
        assertTrue(csv.startsWith("timestamp_epoch_ms,latitude,longitude"))
        assertTrue(csv.contains("28.1234567,-16.7654321,4.50,12.00,91.0,18,25,\"GPS/GAL,SBAS\",42,6.25,MATCHED"))
    }
}
