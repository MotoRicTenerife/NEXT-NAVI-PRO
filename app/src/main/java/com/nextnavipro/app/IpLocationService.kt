package com.nextnavipro.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Approximate network geolocation only. These coordinates are never suitable for
 * turn-by-turn guidance, road matching, speed limits or radar proximity.
 */
data class ApproximateLocation(
    val latitude: Double,
    val longitude: Double,
    val areaLabel: String
)

object IpLocationService {
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 6000

    fun fetch(): ApproximateLocation {
        val connection = URL("https://ipapi.co/json/").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3 (https://github.com/MotoRicTenerife/NEXT-NAVI-PRO)")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Geolocalizzazione IP non disponibile (HTTP $status)")
            val json = JSONObject(body)
            if (json.optBoolean("error", false)) throw IllegalStateException("Il servizio IP non ha restituito una posizione")
            val latitude = json.optDouble("latitude", Double.NaN)
            val longitude = json.optDouble("longitude", Double.NaN)
            require(latitude.isFinite() && latitude in -90.0..90.0)
            require(longitude.isFinite() && longitude in -180.0..180.0)
            val city = json.optString("city").takeIf { it.isNotBlank() && it != "null" }
            val region = json.optString("region").takeIf { it.isNotBlank() && it != "null" }
            val country = json.optString("country_name").takeIf { it.isNotBlank() && it != "null" }
            val label = listOfNotNull(city, region, country).distinct().joinToString(", ")
                .ifBlank { "zona approssimativa" }
            return ApproximateLocation(latitude, longitude, label)
        } finally {
            connection.disconnect()
        }
    }
}
