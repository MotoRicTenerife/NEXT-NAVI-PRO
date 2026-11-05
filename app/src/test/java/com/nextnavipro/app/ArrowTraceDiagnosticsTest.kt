package com.nextnavipro.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowTraceDiagnosticsTest {
    @Test fun csvContainsExactRendererCoordinatesAndBearing() {
        val sample = ArrowTraceSample(1000L, 2_000_000L, 28.1, -16.2, 28.1001, -16.2001,
            28.1002, -16.2002, 91.5f, 3f, 8f, 0.5f, 28.1, -16.2, 90.0, 16.5)
        val csv = ArrowTraceDiagnostics.toCsv(listOf(sample))
        assertTrue(csv.contains("rendered_latitude,rendered_longitude,rendered_bearing_deg"))
        assertTrue(csv.contains("28.1002000,-16.2002000,91.5"))
        assertTrue(csv.contains("2000000"))
    }

    @Test fun appendBatchOmitsRepeatedHeader() {
        val sample = ArrowTraceSample(1L, 2L, null, null, null, null, 1.0, 2.0,
            null, null, null, null, null, null, null, null)
        val csv = ArrowTraceDiagnostics.toCsv(listOf(sample), includeHeader = false)
        assertTrue(!csv.startsWith("timestamp_epoch_ms"))
        assertTrue(csv.startsWith("1,2,"))
    }
}
