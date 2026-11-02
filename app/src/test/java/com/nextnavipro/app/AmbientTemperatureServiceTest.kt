package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientTemperatureServiceTest {
    @Test fun prefersRecentNearbyRealStationObservation() {
        val now = 1_793_000_000_000L
        val recent = now / 1000L - 10L * 60L
        val stale = now / 1000L - 4L * 60L * 60L
        val json = """
            [
              {"icaoId":"NEAR","obsTime":$recent,"temp":21.0,"lat":28.10,"lon":-16.70,"elev":120},
              {"icaoId":"STALE","obsTime":$stale,"temp":25.0,"lat":28.11,"lon":-16.71,"elev":100},
              {"icaoId":"FAR","obsTime":$recent,"temp":29.0,"lat":29.10,"lon":-16.70,"elev":120}
            ]
        """.trimIndent()

        val result = AmbientTemperatureService.parseMetarResponse(json, 28.10, -16.70, 125.0, now)
        assertNotNull(result)
        assertEquals(21.0, result!!.temperatureC, 0.001)
        assertEquals("NEAR", result.stationId)
        assertTrue(result.isStationObservation)
        assertTrue(result.distanceKm!! < 1.0)
        assertNotNull(result.observationTime)
    }

    @Test fun rejectsStaleOrDistantStationReports() {
        val now = 1_793_000_000_000L
        val stale = now / 1000L - 4L * 60L * 60L
        val json = """[{"icaoId":"OLD","obsTime":$stale,"temp":18.0,"lat":28.10,"lon":-16.70}]"""
        assertNull(AmbientTemperatureService.parseMetarResponse(json, 28.10, -16.70, null, now))
    }

    @Test fun parsesGlobalModelFallbackAndMarksItAsEstimated() {
        val json = """
            {
              "properties": {
                "meta": { "updated_at": "2026-10-25T09:00:00Z" },
                "timeseries": [{
                  "time": "2026-10-25T11:00:00Z",
                  "data": {
                    "instant": {
                      "details": { "air_temperature": 22.4 }
                    }
                  }
                }]
              }
            }
        """.trimIndent()
        val result = AmbientTemperatureService.parseGlobalModelResponse(json)
        assertEquals(22.4, result.temperatureC, 0.001)
        assertEquals("MET Norway • modello globale", result.source)
        assertNotNull(result.modelUpdatedAt)
        assertTrue(!result.isStationObservation)
    }
}
