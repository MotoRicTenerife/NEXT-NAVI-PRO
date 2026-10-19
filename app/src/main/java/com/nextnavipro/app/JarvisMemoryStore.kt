package com.nextnavipro.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Persistent text-only Jarvis memory. Microphone audio is never stored here. */
class JarvisMemoryStore(context: Context) {
    data class Message(val role: String, val text: String, val timestamp: Long)
    data class Stats(val bytes: Long, val messages: Int, val summaryChars: Int)
    data class CompactResult(val removed: Int)
    data class DeletePreview(val messages: Int)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
    private val messagesKey = "messages"
    private val summaryKey = "summary"
    private val keepRecentOnCompact = 40
    private val maxStoredMessages = 2000

    @Synchronized
    fun historyForRequest(): List<JarvisOnlineAiClient.HistoryMessage> =
        readMessages().takeLast(16).map { JarvisOnlineAiClient.HistoryMessage(it.role, it.text) }

    @Synchronized
    fun summaryForRequest(): String = prefs.getString(summaryKey, "").orEmpty().take(2500)

    @Synchronized
    fun addMessage(role: String, text: String) {
        if (role != "user" && role != "model") return
        val clean = text.trim().take(1200)
        if (clean.isBlank()) return
        val messages = readMessages().toMutableList()
        messages.add(Message(role, clean, System.currentTimeMillis()))
        while (messages.size > maxStoredMessages) messages.removeAt(0)
        writeMessages(messages)
    }

    @Synchronized
    fun stats(): Stats {
        val preferencesFile = File(appContext.filesDir.parentFile, "shared_prefs/jarvis_memory.xml")
        val bytes = if (preferencesFile.isFile) preferencesFile.length() else 0L
        return Stats(bytes, readMessages().size, summaryForRequest().length)
    }

    @Synchronized
    fun compact(): CompactResult {
        val all = readMessages()
        if (all.size <= keepRecentOnCompact) return CompactResult(0)
        val older = all.dropLast(keepRecentOnCompact)
        val recent = all.takeLast(keepRecentOnCompact)
        val compacted = buildSummary(older, summaryForRequest()).take(2500)
        prefs.edit().putString(summaryKey, compacted).apply()
        writeMessages(recent)
        return CompactResult(older.size)
    }

    @Synchronized
    fun previewDeleteBetween(from: String, to: String): DeletePreview? {
        val range = parseRange(from, to) ?: return null
        return DeletePreview(readMessages().count { it.timestamp in range.first..range.second })
    }

    @Synchronized
    fun deleteBetween(from: String, to: String): Int? {
        val range = parseRange(from, to) ?: return null
        val all = readMessages()
        val kept = all.filterNot { it.timestamp in range.first..range.second }
        val removed = all.size - kept.size
        writeMessages(kept)
        // A compacted summary has no per-message dates: rebuild it only from retained messages
        // so deleted information cannot remain hidden inside the summary.
        if (summaryForRequest().isNotBlank()) {
            prefs.edit().putString(summaryKey, buildSummary(kept, "").take(2500)).apply()
        }
        return removed
    }

    @Synchronized
    fun clearConversationKeepSummary() {
        prefs.edit().remove(messagesKey).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(messagesKey).remove(summaryKey).apply()
    }

    private fun parseRange(from: String, to: String): Pair<Long, Long>? = try {
        val start = LocalDate.parse(from).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        if (end < start) null else start to end
    } catch (_: Exception) { null }

    private fun buildSummary(older: List<Message>, prior: String): String {
        val candidates = ArrayList<String>()
        if (prior.isNotBlank()) candidates.add(prior)
        older.forEach { message ->
            val clean = message.text.replace(Regex("\\s+"), " ").trim()
            if (clean.isBlank()) return@forEach
            val important = Regex("(?i)\\b(ricorda|ricordati|preferisco|preferenza|abbiamo deciso|decisione|importante|problema|errore|da fare|prossimo|non dimenticare|il mio|la mia|voglio|devo|progetto|NEXT NAVI PRO)\\b").containsMatchIn(clean)
            if (important) candidates.add((if (message.role == "user") "Utente: " else "Jarvis: ") + clean.take(220))
        }
        return candidates.distinct().takeLast(18).joinToString("\n").take(2500)
    }

    private fun readMessages(): List<Message> = try {
        val array = JSONArray(prefs.getString(messagesKey, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val role = obj.optString("role")
            val text = obj.optString("text").trim()
            val timestamp = obj.optLong("timestamp", 0L)
            if ((role == "user" || role == "model") && text.isNotBlank() && timestamp > 0)
                Message(role, text, timestamp) else null
        }
    } catch (_: Exception) { emptyList() }

    private fun writeMessages(messages: List<Message>) {
        val array = JSONArray()
        messages.forEach { array.put(JSONObject().put("role", it.role).put("text", it.text).put("timestamp", it.timestamp)) }
        prefs.edit().putString(messagesKey, array.toString()).apply()
    }
}
