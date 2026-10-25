package com.nextnavipro.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Global free temperature estimate from MET Norway's Locationforecast API.
 * This is model-based, not a direct observation from the nearest physical station.
 */
data class AmbientTemperatureSnapshot(
    val temperatureC: Double,
    val forecastTime: String,
    val modelUpdatedAt: String?
)

object AmbientTemperatureService {
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 10000
    private const val USER_AGENT =
        "NEXT-NAVI-PRO/0.3 (https://github.com/MotoRicTenerife/NEXT-NAVI-PRO)"

    fun fetch(latitude: Double, longitude: Double): AmbientTemperatureSnapshot {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val url = URL(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=$latitude&lon=$longitude"
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Temperatura globale non disponibile (HTTP $status)")
            }
            val body = connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }
            return parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResponse(body: String): AmbientTemperatureSnapshot {
        val root = JSONObject(body)
        val properties = root.optJSONObject("properties")
            ?: throw IllegalStateException("Metadati meteo globali assenti")
        val series = properties.optJSONArray("timeseries")
            ?: throw IllegalStateException("Serie temporale meteo assente")
        if (series.length() == 0) throw IllegalStateException("Serie temporale meteo vuota")
        val first = series.optJSONObject(0)
            ?: throw IllegalStateException("Prima previsione meteo assente")
        val data = first.optJSONObject("data")
            ?: throw IllegalStateException("Dati istantanei meteo assenti")
        val instant = data.optJSONObject("instant")
            ?: throw IllegalStateException("Dettagli istantanei meteo assenti")
        val details = instant.optJSONObject("details")
            ?: throw IllegalStateException("Temperatura globale assente")
        val temperature = details.optDouble("air_temperature", Double.NaN)
        require(temperature.isFinite() && temperature in -90.0..65.0) {
            "Temperatura globale non valida"
        }
        val time = first.optString("time").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Ora della previsione assente")
        val updatedAt = properties.optJSONObject("meta")?.optString("updated_at")
            ?.takeIf { it.isNotBlank() && it != "null" }
        return AmbientTemperatureSnapshot(temperature, time, updatedAt)
    }
}
