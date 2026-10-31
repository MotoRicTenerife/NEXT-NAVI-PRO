package com.nextnavipro.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Private, on-device transcript memory for Jarvis. Audio is never stored.
 * Older messages can be compacted into date-scoped summaries so deleting a date
 * range also removes the summaries for those dates instead of retaining erased data.
 */
class JarvisMemoryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    data class Message(val id: Long, val timestamp: Long, val role: String, val content: String)
    data class Stats(val bytes: Long, val messages: Int, val summaryDays: Int)

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getDefault()
    }

    @Synchronized
    fun addMessage(role: String, content: String, timestamp: Long = System.currentTimeMillis()) {
        val cleanRole = role.takeIf { it == "user" || it == "assistant" } ?: return
        val clean = content.trim().take(MAX_MESSAGE_CHARS)
        if (clean.isBlank()) return
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("role", cleanRole)
            put("content", clean)
        }
        writableDatabase.insert("messages", null, values)
    }

    @Synchronized
    fun recent(limit: Int = 12): List<Message> {
        val rows = ArrayList<Message>()
        readableDatabase.rawQuery(
            "SELECT id,timestamp,role,content FROM messages ORDER BY timestamp DESC,id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 40).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) rows.add(
                Message(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3))
            )
        }
        return rows.asReversed()
    }

    @Synchronized
    fun summary(maxChars: Int = 5000): String {
        val parts = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT day,summary FROM summaries ORDER BY day DESC LIMIT 45", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val text = cursor.getString(1).orEmpty().trim()
                if (text.isNotBlank()) parts.add(0, cursor.getString(0) + ": " + text)
            }
        }
        return parts.joinToString("\n").takeLast(maxChars.coerceIn(500, 8000))
    }

    /**
     * Creates a consistent standalone SQLite snapshot after checkpointing the WAL.
     * The destination is an app-private temporary file chosen by the caller.
     */
    @Synchronized
    fun exportDatabaseSnapshot(destination: java.io.File) {
        val db = writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            while (cursor.moveToNext()) { /* consume checkpoint status */ }
        }
        val source = java.io.File(db.path)
        require(source.isFile && source.length() > 0L) { "Archivio memoria Jarvis non disponibile." }
        source.copyTo(destination, overwrite = true)
    }

    /**
     * Validates and replaces the private transcript database, then reopens it.
     * Existing WAL/journal sidecars are removed only after the helper is closed.
     */
    @Synchronized
    fun restoreDatabaseSnapshot(snapshot: java.io.File) {
        require(snapshot.isFile && snapshot.length() in 1L..(50L * 1024L * 1024L)) {
            "Backup memoria Jarvis non valido o troppo grande."
        }
        val probe = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = probe.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            require(integrity == "ok") { "Il database memoria Jarvis non ha superato il controllo d'integrità." }
        } finally {
            probe.close()
        }

        val target = java.io.File(databaseNameForPath())
        val replacement = java.io.File(target.parentFile, target.name + ".restore")
        snapshot.copyTo(replacement, overwrite = true)
        close()
        if (!replacement.renameTo(target)) replacement.copyTo(target, overwrite = true)
        java.io.File(target.path + "-wal").delete()
        java.io.File(target.path + "-shm").delete()
        java.io.File(target.path + "-journal").delete()
        writableDatabase // Reopen immediately so subsequent Jarvis calls use the restored database.
    }

    @Synchronized
    fun stats(): Stats {
        val db = readableDatabase
        val messages = scalarCount(db, "messages")
        val days = scalarCount(db, "summaries")
        val file = contextDatabaseFile()
        val bytes = listOf(file, java.io.File(file.path + "-wal"), java.io.File(file.path + "-shm"))
            .filter { it.exists() }.sumOf { it.length() }
        return Stats(bytes, messages, days)
    }

    /**
     * Compact old transcript rows into concise per-day extractive summaries.
     * Recent turns remain verbatim. Priority is given to decisions, preferences,
     * unresolved questions and explicit remember/never/always statements.
     */
    @Synchronized
    fun compact(keepRecent: Int = 30): Stats {
        val db = writableDatabase
        val all = ArrayList<Message>()
        db.rawQuery("SELECT id,timestamp,role,content FROM messages ORDER BY timestamp ASC,id ASC", null).use { c ->
            while (c.moveToNext()) all.add(Message(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)))
        }
        if (all.size <= keepRecent.coerceAtLeast(10)) return stats()
        val older = all.dropLast(keepRecent.coerceAtLeast(10))
        val byDay = older.groupBy { dayFormat.format(Date(it.timestamp)) }
        db.beginTransaction()
        try {
            for ((day, entries) in byDay) {
                val generated = summarizeDay(entries)
                if (generated.isBlank()) continue
                val previous = db.rawQuery("SELECT summary FROM summaries WHERE day=?", arrayOf(day)).use { c ->
                    if (c.moveToFirst()) c.getString(0).orEmpty() else ""
                }
                val merged = listOf(previous, generated).filter { it.isNotBlank() }
                    .joinToString(" ").takeLast(MAX_SUMMARY_CHARS)
                val values = ContentValues().apply { put("day", day); put("summary", merged) }
                db.insertWithOnConflict("summaries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (older.isNotEmpty()) {
                // Delete exactly the rows compacted above; timestamp ordering and
                // insertion IDs need not be identical after a restored/imported record.
                older.chunked(800).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    db.delete("messages", "id IN ($placeholders)", chunk.map { it.id.toString() }.toTypedArray())
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        reclaimSpace(db)
        return stats()
    }

    @Synchronized
    fun countRange(startInclusiveMs: Long, endExclusiveMs: Long): Int {
        if (endExclusiveMs <= startInclusiveMs) return 0
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE timestamp>=? AND timestamp<?",
            arrayOf(startInclusiveMs.toString(), endExclusiveMs.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    @Synchronized
    fun deleteRange(startInclusiveMs: Long, endExclusiveMs: Long): Int {
        if (endExclusiveMs <= startInclusiveMs) return 0
        val db = writableDatabase
        val startDay = dayFormat.format(Date(startInclusiveMs))
        val lastDay = dayFormat.format(Date(endExclusiveMs - 1L))
        db.beginTransaction()
        val deleted: Int
        try {
            deleted = db.delete("messages", "timestamp>=? AND timestamp<?",
                arrayOf(startInclusiveMs.toString(), endExclusiveMs.toString()))
            db.delete("summaries", "day>=? AND day<=?", arrayOf(startDay, lastDay))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        reclaimSpace(db)
        return deleted
    }

    @Synchronized
    fun clearCurrentConversation() {
        val db = writableDatabase
        db.delete("messages", null, null)
        // Compacted long-term summaries are deliberately retained for a new chat.
        reclaimSpace(db)
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("messages", null, null)
            writableDatabase.delete("summaries", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        reclaimSpace(writableDatabase)
    }

    private fun reclaimSpace(db: SQLiteDatabase) {
        // Run only after the transaction has ended. VACUUM physically returns
        // unused pages to the app's filesystem instead of merely marking them free.
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { /* consume checkpoint result */ }
        db.execSQL("VACUUM")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL)")
        db.execSQL("CREATE INDEX messages_timestamp_idx ON messages(timestamp,id)")
        db.execSQL("CREATE TABLE summaries (day TEXT PRIMARY KEY NOT NULL, summary TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun scalarCount(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun contextDatabaseFile(): java.io.File = java.io.File(
        // SQLiteOpenHelper stores this file in the app-private database directory.
        databaseNameForPath()
    )

    private fun databaseNameForPath(): String {
        // The standard path is stable across app restarts and remains private to this app.
        return cachedDatabasePath ?: throw IllegalStateException("Database path not initialized")
    }

    private var cachedDatabasePath: String? = null

    override fun getWritableDatabase(): SQLiteDatabase {
        val db = super.getWritableDatabase()
        cachedDatabasePath = db.path
        return db
    }

    override fun getReadableDatabase(): SQLiteDatabase {
        val db = super.getReadableDatabase()
        cachedDatabasePath = db.path
        return db
    }

    private fun summarizeDay(entries: List<Message>): String {
        val candidates = entries.flatMap { entry ->
            entry.content.split(Regex("(?<=[.!?])\\s+|\\n+"))
                .map { sentence -> sentence.trim().take(360) }
                .filter { it.length >= 18 }
                .map { sentence ->
                    val lower = sentence.lowercase(Locale.ROOT)
                    var score = if (entry.role == "user") 2 else 0
                    listOf("prefer", "preferisco", "voglio", "importante", "deciso", "decisione",
                        "ricorda", "ricordare", "sempre", "mai ", "non deve", "deve ", "problema",
                        "errore", "manca", "serve", "scelto", "agreed", "prefer", "remember",
                        "always", "never", "decided", "important", "issue", "problem", "todo")
                        .forEach { if (lower.contains(it)) score += 3 }
                    if (sentence.endsWith("?")) score += 1
                    Triple(score, entry.timestamp, sentence)
                }
        }.distinctBy { it.third.lowercase(Locale.ROOT) }
        val chosen = candidates.sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }
            .thenByDescending { it.second }).take(8).sortedBy { it.second }
        return chosen.joinToString(" • ") { it.third }.take(MAX_SUMMARY_CHARS)
    }

    companion object {
        private const val DATABASE_NAME = "jarvis_memory.db"
        private const val DATABASE_VERSION = 1
        private const val MAX_MESSAGE_CHARS = 4000
        private const val MAX_SUMMARY_CHARS = 1800
    }
}
