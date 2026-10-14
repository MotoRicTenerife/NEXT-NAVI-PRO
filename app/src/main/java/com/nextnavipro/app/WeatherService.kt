package com.nextnavipro.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherHour(
    val time: String,
    val temperatureC: Double,
    val precipitationProbability: Int,
    val windKmh: Double,
    val gustKmh: Double,
    val weatherCode: Int
)

data class WeatherSnapshot(
    val temperatureC: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val windDirectionDegrees: Int,
    val weatherCode: Int,
    val currentTime: String,
    val hourly: List<WeatherHour>
)

object WeatherService {
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 9000

    fun fetch(latitude: Double, longitude: Double): WeatherSnapshot {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m" +
                "&past_days=1&forecast_days=2&timezone=auto"
        )
        val connection = url.openConnection() as HttpURLConnection
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
            if (status !in 200..299) throw IllegalStateException("Meteo non disponibile (HTTP $status)")
            val root = JSONObject(body)
            val current = root.optJSONObject("current") ?: throw IllegalStateException("Dati meteo attuali assenti")
            val hourly = root.optJSONObject("hourly") ?: throw IllegalStateException("Previsioni orarie assenti")
            val times = hourly.optJSONArray("time") ?: throw IllegalStateException("Orari meteo assenti")
            val temperatures = hourly.optJSONArray("temperature_2m")
            val rain = hourly.optJSONArray("precipitation_probability")
            val codes = hourly.optJSONArray("weather_code")
            val winds = hourly.optJSONArray("wind_speed_10m")
            val gusts = hourly.optJSONArray("wind_gusts_10m")
            val items = ArrayList<WeatherHour>()
            for (i in 0 until times.length()) {
                val time = times.optString(i)
                val temp = temperatures?.optDouble(i, Double.NaN) ?: Double.NaN
                val wind = winds?.optDouble(i, Double.NaN) ?: Double.NaN
                val gust = gusts?.optDouble(i, Double.NaN) ?: Double.NaN
                val code = codes?.optInt(i, -1) ?: -1
                if (!time.contains("T") || time.length < 16 || !temp.isFinite() || !wind.isFinite() || !gust.isFinite() || code < 0) continue
                items.add(WeatherHour(time, temp, rain?.optInt(i, 0)?.coerceIn(0, 100) ?: 0, wind, gust, code))
            }
            return WeatherSnapshot(
                temperatureC = current.optDouble("temperature_2m", Double.NaN).also { require(it.isFinite()) },
                windKmh = current.optDouble("wind_speed_10m", Double.NaN).also { require(it.isFinite()) },
                gustKmh = current.optDouble("wind_gusts_10m", Double.NaN).also { require(it.isFinite()) },
                windDirectionDegrees = current.optInt("wind_direction_10m", 0).mod(360),
                weatherCode = current.optInt("weather_code", -1).also { require(it >= 0) },
                currentTime = current.optString("time"),
                hourly = items
            )
        } finally {
            connection.disconnect()
        }
    }

    fun description(code: Int): String = when (code) {
        0 -> "Sereno"
        1 -> "Prevalentemente sereno"
        2 -> "Parzialmente nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioviggine"
        56, 57 -> "Pioviggine gelata"
        61, 63, 65 -> "Pioggia"
        66, 67 -> "Pioggia gelata"
        71, 73, 75, 77 -> "Neve"
        80, 81, 82 -> "Rovesci"
        85, 86 -> "Rovesci di neve"
        95 -> "Temporale"
        96, 99 -> "Temporale con grandine"
        else -> "Condizione non disponibile"
    }

    fun icon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "—"
    }

    fun windDirection(degrees: Int): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return directions[((degrees + 22) / 45).mod(8)]
    }

    fun period(hour: Int): String = when (hour) {
        in 6..11 -> "Mattina"
        in 12..17 -> "Pomeriggio"
        else -> "Sera / notte"
    }

    fun format(value: Double): String = String.format(Locale.ITALY, "%.0f", value)
}
