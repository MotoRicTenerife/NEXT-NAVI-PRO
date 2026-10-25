package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientTemperatureServiceTest {
    @Test fun parsesGlobalTemperatureAndModelTimestamp() {
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

        val result = AmbientTemperatureService.parseResponse(json)
        assertEquals(22.4, result.temperatureC, 0.001)
        assertEquals("2026-10-25T11:00:00Z", result.forecastTime)
        assertEquals("2026-10-25T09:00:00Z", result.modelUpdatedAt)
    }

    @Test fun rejectsMissingTemperature() {
        try {
            AmbientTemperatureService.parseResponse(
                """{"properties":{"timeseries":[{"time":"2026-10-25T11:00:00Z","data":{"instant":{"details":{}}}}]}}"""
            )
            throw AssertionError("Expected absent temperature to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Temperatura globale non valida"))
        }
    }
}
