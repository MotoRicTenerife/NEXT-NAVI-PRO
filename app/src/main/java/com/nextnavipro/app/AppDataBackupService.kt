package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, versioned backup for the app's user settings and paired GPX/CSV archive.
 *
 * Only whitelisted data is included. Interrupted recording checkpoints and temporary
 * files are intentionally excluded because they are not complete, consistent trips.
 */
internal object AppDataBackupService {
    private const val SETTINGS_ENTRY = "settings.json"
    private const val SCHEMA_VERSION = 1
    private const val MAX_SETTINGS_BYTES = 2 * 1024 * 1024
    private const val MAX_FILE_BYTES = 50L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 250L * 1024L * 1024L
    private const val BUFFER_SIZE = 16 * 1024

    data class BackupSummary(val gpxCount: Int, val csvCount: Int, val settingsCount: Int)

    data class RestorePlan(
        val preferences: Map<String, Any>,
        val gpxFiles: List<File>,
        val csvFiles: List<File>
    )

    fun export(
        output: OutputStream,
        preferences: Map<String, *>,
        gpxDirectory: File,
        gpsDiagnosticsDirectory: File
    ): BackupSummary {
        val entries = JSONArray()
        preferences.toSortedMap().forEach { (key, value) ->
            if (key.isBlank() || key.length > 256 || value == null) return@forEach
            val item = JSONObject().put("key", key)
            when (value) {
                is String -> item.put("type", "string").put("value", value)
                is Boolean -> item.put("type", "boolean").put("value", value)
                is Int -> item.put("type", "int").put("value", value)
                is Long -> item.put("type", "long").put("value", value)
                is Float -> {
                    if (value.isFinite()) item.put("type", "float").put("value", value.toDouble())
                    else return@forEach
                }
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>()
                    if (strings.size != value.size) return@forEach
                    item.put("type", "string_set").put("value", JSONArray(strings.sorted()))
                }
                else -> return@forEach
            }
            entries.put(item)
        }
        val settings = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("entries", entries)
        val settingsBytes = settings.toString().toByteArray(Charsets.UTF_8)
        require(settingsBytes.size <= MAX_SETTINGS_BYTES) { "Le impostazioni superano il limite di backup." }

        var gpxCount = 0
        var csvCount = 0
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
            zip.write(settingsBytes)
            zip.closeEntry()

            gpxCount = addDirectory(zip, gpxDirectory, "gpx_routes", "gpx")
            csvCount = addDirectory(zip, gpsDiagnosticsDirectory, "gps_diagnostics", "csv")
            zip.finish()
        }
        return BackupSummary(gpxCount, csvCount, entries.length())
    }

    private fun addDirectory(zip: ZipOutputStream, directory: File, prefix: String, extension: String): Int {
        if (!directory.isDirectory) return 0
        var count = 0
        directory.listFiles()?.filter { file ->
            file.isFile &&
                file.extension.equals(extension, ignoreCase = true) &&
                !file.name.contains("in_progress", ignoreCase = true) &&
                !file.name.endsWith(".tmp", ignoreCase = true) &&
                isSafeFileName(file.name)
        }?.sortedBy { it.name }?.forEach { file ->
            require(file.length() <= MAX_FILE_BYTES) { "File troppo grande per il backup: ${file.name}" }
            zip.putNextEntry(ZipEntry("$prefix/${file.name}"))
            file.inputStream().buffered().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
            count++
        }
        return count
    }

    /**
     * Extracts to an isolated staging directory and returns a validated restore plan.
     * The caller must apply preferences and copy staged files only after this succeeds.
     */
    fun import(input: InputStream, stagingDirectory: File): RestorePlan {
        if (stagingDirectory.exists()) stagingDirectory.deleteRecursively()
        require(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) {
            "Impossibile creare l'area temporanea di ripristino."
        }
        val stagedGpx = File(stagingDirectory, "gpx_routes").apply { mkdirs() }
        val stagedCsv = File(stagingDirectory, "gps_diagnostics").apply { mkdirs() }
        var totalBytes = 0L
        var settingsBytes: ByteArray? = null
        val seenEntries = HashSet<String>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                require(seenEntries.add(entry.name)) { "Voce duplicata nel backup: ${entry.name}" }
                when {
                    entry.name == SETTINGS_ENTRY -> {
                        val bytes = readLimited(zip, MAX_SETTINGS_BYTES.toLong())
                        totalBytes += bytes.size
                        settingsBytes = bytes
                    }
                    entry.name.startsWith("gpx_routes/") -> {
                        val name = entry.name.removePrefix("gpx_routes/")
                        require(isSafeFileName(name) && name.substringAfterLast('.', "").equals("gpx", true)) {
                            "Nome GPX non valido nel backup."
                        }
                        val bytes = readLimited(zip, MAX_FILE_BYTES)
                        totalBytes += bytes.size
                        File(stagedGpx, name).writeBytes(bytes)
                    }
                    entry.name.startsWith("gps_diagnostics/") -> {
                        val name = entry.name.removePrefix("gps_diagnostics/")
                        require(isSafeFileName(name) && name.substringAfterLast('.', "").equals("csv", true) &&
                            !name.contains("in_progress", ignoreCase = true)) {
                            "Nome CSV non valido nel backup."
                        }
                        val bytes = readLimited(zip, MAX_FILE_BYTES)
                        totalBytes += bytes.size
                        File(stagedCsv, name).writeBytes(bytes)
                    }
                    else -> throw IllegalArgumentException("Il backup contiene una voce non consentita.")
                }
                require(totalBytes <= MAX_TOTAL_BYTES) { "Il backup supera il limite complessivo di 250 MB." }
                zip.closeEntry()
            }
        }

        val rawSettings = settingsBytes ?: throw IllegalArgumentException("Nel backup manca il file delle impostazioni.")
        val preferences = decodeSettings(rawSettings)
        return RestorePlan(
            preferences = preferences,
            gpxFiles = stagedGpx.listFiles()?.filter { it.isFile }.orEmpty(),
            csvFiles = stagedCsv.listFiles()?.filter { it.isFile }.orEmpty()
        )
    }

    private fun decodeSettings(bytes: ByteArray): Map<String, Any> {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) {
            "Versione del backup non supportata."
        }
        val entries = root.optJSONArray("entries") ?: throw IllegalArgumentException("Impostazioni del backup non valide.")
        val result = LinkedHashMap<String, Any>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: throw IllegalArgumentException("Voce impostazione non valida.")
            val key = item.optString("key")
            require(key.isNotBlank() && key.length <= 256 && !result.containsKey(key)) {
                "Chiave impostazione non valida o duplicata."
            }
            val value: Any = when (item.optString("type")) {
                "string" -> item.getString("value")
                "boolean" -> item.getBoolean("value")
                "int" -> item.getInt("value")
                "long" -> item.getLong("value")
                "float" -> item.getDouble("value").toFloat().also {
                    require(it.isFinite()) { "Valore numerico non valido." }
                }
                "string_set" -> {
                    val array = item.getJSONArray("value")
                    val set = LinkedHashSet<String>()
                    for (i in 0 until array.length()) set.add(array.getString(i))
                    set
                }
                else -> throw IllegalArgumentException("Tipo impostazione non supportato.")
            }
            result[key] = value
        }
        return result
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Una voce del backup supera il limite consentito." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name.length <= 180 &&
            name != "." && name != ".." &&
            '/' !in name && '\\' !in name &&
            !name.contains("..") &&
            name.matches(Regex("[A-Za-z0-9À-ÿ _().-]+\\.[A-Za-z0-9]{2,5}"))
}
