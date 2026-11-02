package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherServiceTest {
    @Test fun parsesVerifiedCurrentWeatherAndHourlyForecastFields() {
        val json = """
            {
              "current": {
                "temperature_2m": 24.6,
                "weather_code": 2,
                "wind_speed_10m": 7.2,
                "wind_direction_10m": 270,
                "wind_gusts_10m": 14.0,
                "time": "2026-10-19T12:15"
              },
              "hourly": {
                "time": ["2026-10-19T12:00", "2026-10-19T13:00"],
                "temperature_2m": [24.0, 25.0],
                "precipitation_probability": [10, 20],
                "weather_code": [2, 3],
                "wind_speed_10m": [7.0, 8.0],
                "wind_gusts_10m": [13.0, 15.0]
              }
            }
        """.trimIndent()

        val result = WeatherService.parseResponse(json)
        assertEquals(24.6, result.temperatureC, 0.001)
        assertEquals(7.2, result.windKmh, 0.001)
        assertEquals(14.0, result.gustKmh, 0.001)
        assertEquals(270, result.windDirectionDegrees)
        assertEquals(2, result.weatherCode)
        assertEquals("2026-10-19T12:15", result.currentTime)
        assertEquals(2, result.hourly.size)
        assertEquals(20, result.hourly[1].precipitationProbability)
    }

    @Test fun rejectsWeatherResponseWithoutCurrentData() {
        try {
            WeatherService.parseResponse("""{"hourly":{"time":[]}}""")
            throw AssertionError("Expected missing current data to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Dati meteo attuali assenti"))
        }
    }
}
