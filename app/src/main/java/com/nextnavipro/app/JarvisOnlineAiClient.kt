package com.nextnavipro.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Optional online conversational layer. The app calls only a configured HTTPS backend;
 * provider credentials must remain on that server, never in the APK.
 *
 * Backend contract:
 * POST {"message":"...","language":"it-IT","app":"NEXT NAVI PRO"}
 * 200 {"reply":"..."}
 */
object JarvisOnlineAiClient {
    data class LiveLocation(val latitude: Double, val longitude: Double)

    private const val CONNECT_TIMEOUT_MS = 4000
    private const val READ_TIMEOUT_MS = 7000
    private const val MAX_REPLY_CHARS = 800

    private val endpoint: String
        get() = BuildConfig.JARVIS_AI_ENDPOINT.trim()

    fun isConfigured(): Boolean =
        endpoint.startsWith("https://", ignoreCase = true)

    fun ask(
        message: String,
        language: String = java.util.Locale.getDefault().toLanguageTag(),
        assistantName: String = "Jarvis",
        location: LiveLocation? = null,
        callback: (Result<String>) -> Unit
    ) {
        val target = endpoint
        if (!target.startsWith("https://", ignoreCase = true)) {
            callback(Result.failure(IllegalStateException("AI online non configurata")))
            return
        }
        val cleanMessage = message.trim().take(500)
        if (cleanMessage.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Richiesta vuota")))
            return
        }

        thread(name = "jarvis-online-ai") {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(target).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                val payload = JSONObject()
                    .put("message", cleanMessage)
                    .put("language", language.ifBlank { java.util.Locale.getDefault().toLanguageTag() })
                    .put("assistantName", assistantName.trim().take(30).ifBlank { "Jarvis" })
                    .put("app", "NEXT NAVI PRO")
                    .apply {
                        if (location != null &&
                            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
                            location.longitude.isFinite() && location.longitude in -180.0..180.0) {
                            put("location", JSONObject()
                                .put("latitude", location.latitude)
                                .put("longitude", location.longitude))
                        }
                    }
                    .toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText().take(4096)
                    }.orEmpty()
                    val backendError = try {
                        val json = JSONObject(errorBody)
                        val message = json.optString("error").take(160)
                        val providerStatus = json.optInt("providerStatus", 0)
                        buildString {
                            append("HTTP_").append(status)
                            if (message.isNotBlank()) append(": ").append(message)
                            if (providerStatus > 0) append(" (provider HTTP ").append(providerStatus).append(")")
                        }
                    } catch (_: Exception) {
                        "HTTP_$status"
                    }
                    throw IllegalStateException(backendError)
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(16_384) }
                val reply = JSONObject(response).optString("reply").trim().take(MAX_REPLY_CHARS)
                if (reply.isBlank()) throw IllegalStateException("Risposta AI vuota")
                callback(Result.success(reply))
            } catch (error: Exception) {
                callback(Result.failure(error))
            } finally {
                connection?.disconnect()
            }
        }
    }
}
