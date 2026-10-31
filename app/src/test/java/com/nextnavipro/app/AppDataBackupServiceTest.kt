package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppDataBackupServiceTest {
    @Test fun roundTripsTypedPreferencesAndPairedTripFiles() {
        val root = Files.createTempDirectory("nnp-backup-test").toFile()
        try {
            val gpx = File(root, "gpx").apply { mkdirs() }
            val csv = File(root, "csv").apply { mkdirs() }
            File(gpx, "ride_01.gpx").writeText("<gpx/>")
            File(csv, "ride_01.csv").writeText("time,lat,lon\n1,2,3\n")
            File(gpx, "gpx_recording_in_progress.gpx").writeText("<partial/>")
            File(csv, "gps_trace_in_progress.csv").writeText("partial")

            val preferences = linkedMapOf<String, Any>(
                "theme" to "positive",
                "negative" to false,
                "offset" to 7,
                "last_seen" to 1234567890123L,
                "voice_rate" to 0.96f,
                "favorites" to setOf("Casa", "Lavoro")
            )
            val archive = ByteArrayOutputStream()
            val summary = AppDataBackupService.export(archive, preferences, gpx, csv)
            assertEquals(1, summary.gpxCount)
            assertEquals(1, summary.csvCount)
            assertEquals(preferences.size, summary.settingsCount)

            val staging = File(root, "staging")
            val plan = AppDataBackupService.import(ByteArrayInputStream(archive.toByteArray()), staging)
            assertEquals("positive", plan.preferences["theme"])
            assertEquals(false, plan.preferences["negative"])
            assertEquals(7, plan.preferences["offset"])
            assertEquals(1234567890123L, plan.preferences["last_seen"])
            assertEquals(0.96f, plan.preferences["voice_rate"])
            assertEquals(setOf("Casa", "Lavoro"), plan.preferences["favorites"])
            assertEquals(listOf("ride_01.gpx"), plan.gpxFiles.map { it.name })
            assertEquals(listOf("ride_01.csv"), plan.csvFiles.map { it.name })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsZipSlipAndUnknownEntries() {
        val root = Files.createTempDirectory("nnp-backup-slip").toFile()
        try {
            val archive = ByteArrayOutputStream()
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry("settings.json"))
                zip.write("""{"schemaVersion":1,"entries":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("gpx_routes/../../outside.gpx"))
                zip.write("<gpx/>".toByteArray())
                zip.closeEntry()
            }
            var rejected = false
            try {
                AppDataBackupService.import(ByteArrayInputStream(archive.toByteArray()), File(root, "stage"))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertFalse(File(root, "outside.gpx").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsUnsupportedBackupSchema() {
        val root = Files.createTempDirectory("nnp-backup-schema").toFile()
        try {
            val archive = ByteArrayOutputStream()
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry("settings.json"))
                zip.write("""{"schemaVersion":99,"entries":[]}""".toByteArray())
                zip.closeEntry()
            }
            var rejected = false
            try {
                AppDataBackupService.import(ByteArrayInputStream(archive.toByteArray()), File(root, "stage"))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            root.deleteRecursively()
        }
    }
}
