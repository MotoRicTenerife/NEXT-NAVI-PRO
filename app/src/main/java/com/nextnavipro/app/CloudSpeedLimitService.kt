package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Render-backed shared speed-limit archive. No Firebase credentials are stored in the APK. */
object CloudSpeedLimitService {
    private fun baseUrl(): String {
        val configured = BuildConfig.JARVIS_AI_ENDPOINT.trim()
        require(configured.startsWith("https://") && configured.endsWith("/jarvis")) {
            "Backend Render non configurato"
        }
        return configured.substringBeforeLast("/jarvis").trimEnd('/')
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(baseUrl() + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NEXT-NAVI-PRO/0.3")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Archivio cloud HTTP $status")
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun list(): List<ManualSpeedLimitSegment> {
        val result = request("/speed-limit-segments")
        val array = result.optJSONArray("segments") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            runCatching { ManualSpeedLimitSegment.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }

    fun upsert(segment: ManualSpeedLimitSegment) {
        request("/speed-limit-segments/upsert", "POST", JSONObject().put("segment", segment.toJson()))
    }

    fun delete(id: String) {
        request("/speed-limit-segments?id=" + URLEncoder.encode(id, "UTF-8"), "DELETE")
    }
}
