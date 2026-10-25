package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/**
 * Free worldwide ambient-temperature resolver:
 * 1) recent measured METAR from the nearest suitable reporting airport;
 * 2) global MET Norway model estimate when no suitable observation is available.
 * The result explicitly identifies which kind of value was returned.
 */
data class AmbientTemperatureSnapshot(
    val temperatureC: Double,
    val source: String,
    val stationId: String? = null,
    val distanceKm: Double? = null,
    val observationTime: String? = null,
    val isStationObservation: Boolean = false,
    val modelUpdatedAt: String? = null
)

object AmbientTemperatureService {
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 10000
    private const val MAX_STATION_DISTANCE_KM = 100.0
    private const val MAX_OBSERVATION_AGE_MS = 2L * 60L * 60L * 1000L
    private const val USER_AGENT =
        "NEXT-NAVI-PRO/0.3 (https://github.com/MotoRicTenerife/NEXT-NAVI-PRO)"

    fun fetch(latitude: Double, longitude: Double, altitudeM: Double? = null): AmbientTemperatureSnapshot {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val observation = try { fetchNearestMetar(latitude, longitude, altitudeM) } catch (error: Exception) {
            android.util.Log.w("NEXT_NAVI_WEATHER", "Worldwide station observation unavailable; using global model fallback", error)
            null
        }
        return observation ?: fetchGlobalModel(latitude, longitude)
    }

    private fun fetchNearestMetar(latitude: Double, longitude: Double, altitudeM: Double?): AmbientTemperatureSnapshot? {
        // Query a compact box around the current GPS fix. At high latitudes, widen longitude
        // while keeping the actual selection constrained by geodesic distance.
        val latSpan = 0.75
        val lonSpan = min(5.0, 0.75 / max(cos(Math.toRadians(latitude)), 0.15))
        val lat0 = max(-90.0, latitude - latSpan)
        val lat1 = min(90.0, latitude + latSpan)
        val lon0 = normalizeLongitude(longitude - lonSpan)
        val lon1 = normalizeLongitude(longitude + lonSpan)
        val bbox = "$lat0,$lon0,$lat1,$lon1"
        val url = URL("https://aviationweather.gov/api/data/metar?bbox=$bbox&hours=2&format=json")
        val body = requestBody(url, allowNoContent = true) ?: return null
        return parseMetarResponse(body, latitude, longitude, altitudeM)
    }

    private fun fetchGlobalModel(latitude: Double, longitude: Double): AmbientTemperatureSnapshot {
        val url = URL(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=$latitude&lon=$longitude"
        )
        val body = requestBody(url) ?: throw IllegalStateException("Modello globale meteo vuoto")
        return parseGlobalModelResponse(body)
    }

    internal fun parseGlobalModelResponse(body: String): AmbientTemperatureSnapshot {
        val root = JSONObject(body)
        val properties = root.optJSONObject("properties")
            ?: throw IllegalStateException("Metadati meteo globali assenti")
        val series = properties.optJSONArray("timeseries")
            ?: throw IllegalStateException("Serie temporale meteo assente")
        if (series.length() == 0) throw IllegalStateException("Serie temporale meteo vuota")
        val first = series.optJSONObject(0)
            ?: throw IllegalStateException("Prima previsione meteo assente")
        val details = first.optJSONObject("data")?.optJSONObject("instant")?.optJSONObject("details")
            ?: throw IllegalStateException("Dettagli istantanei meteo assenti")
        val temperature = details.optDouble("air_temperature", Double.NaN)
        require(temperature.isFinite() && temperature in -90.0..65.0) { "Temperatura globale non valida" }
        val updatedAt = properties.optJSONObject("meta")?.optString("updated_at")
            ?.takeIf { it.isNotBlank() && it != "null" }
        return AmbientTemperatureSnapshot(
            temperatureC = temperature,
            source = "MET Norway • modello globale",
            isStationObservation = false,
            modelUpdatedAt = updatedAt
        )

    private fun requestBody(url: URL, allowNoContent: Boolean = false): String? {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (allowNoContent && status == HttpURLConnection.HTTP_NO_CONTENT) return null
            if (status !in 200..299) throw IllegalStateException("Servizio temperatura non disponibile (HTTP $status)")
            return (connection.inputStream ?: return null).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseMetarResponse(
        body: String,
        latitude: Double,
        longitude: Double,
        altitudeM: Double? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AmbientTemperatureSnapshot? {
        val rows = JSONArray(body)
        data class Candidate(val result: AmbientTemperatureSnapshot, val score: Double)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val temperature = row.optDouble("temp", Double.NaN)
            val stationLat = row.optDouble("lat", Double.NaN)
            val stationLon = row.optDouble("lon", Double.NaN)
            if (!temperature.isFinite() || temperature !in -90.0..65.0 ||
                !stationLat.isFinite() || stationLat !in -90.0..90.0 ||
                !stationLon.isFinite() || stationLon !in -180.0..180.0) continue
            val observationMs = parseObservationTime(row.opt("obsTime")) ?: continue
            val ageMs = nowMs - observationMs
            if (ageMs < -10L * 60L * 1000L || ageMs > MAX_OBSERVATION_AGE_MS) continue
            val distanceKm = distanceKm(latitude, longitude, stationLat, stationLon)
            if (distanceKm > MAX_STATION_DISTANCE_KM) continue
            val stationElevation = row.optDouble("elev", Double.NaN)
            val altitudePenalty = if (altitudeM != null && altitudeM.isFinite() &&
                stationElevation.isFinite() && stationElevation in -500.0..9000.0) {
                abs(altitudeM - stationElevation) / 1000.0 * 4.0
            } else 0.0
            val stationId = row.optString("icaoId").takeIf { it.isNotBlank() && it != "null" }
            val time = formatUtc(observationMs)
            val result = AmbientTemperatureSnapshot(
                temperatureC = temperature,
                source = "METAR • osservazione reale",
                stationId = stationId,
                distanceKm = distanceKm,
                observationTime = time,
                isStationObservation = true
            )
            // Prefer a close, fresh station at a comparable elevation; reject stale reports above.
            val agePenalty = ageMs.coerceAtLeast(0L) / 60_000.0 * 0.025
            candidates.add(Candidate(result, distanceKm + altitudePenalty + agePenalty))
        }
        return candidates.minByOrNull { it.score }?.result
    }

    private fun parseObservationTime(value: Any?): Long? {
        if (value == null || value == JSONObject.NULL) return null
        if (value is Number) {
            val raw = value.toLong()
            return if (raw > 10_000_000_000L) raw else raw * 1000L
        }
        val text = value.toString()
        text.toLongOrNull()?.let { return if (it > 10_000_000_000L) it else it * 1000L }
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        for (pattern in formats) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                parser.parse(text)?.let { return it.time }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun formatUtc(timeMs: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timeMs))

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return earthRadiusKm * 2.0 * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1.0 - a).coerceAtLeast(0.0)))
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var value = longitude
        while (value < -180.0) value += 360.0
        while (value > 180.0) value -= 360.0
        return value
    }
}
