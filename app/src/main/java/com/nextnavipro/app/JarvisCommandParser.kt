package com.nextnavipro.app

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic, offline-first command parser for the user-activated Jarvis microphone.
 * It never invents a destination or navigation value: destinations are passed to the
 * existing geocoder and require the normal confirmation flow.
 */
sealed class JarvisCommand {
    data class Navigate(val destination: String) : JarvisCommand()
    object WhereAmI : JarvisCommand()
    object Remaining : JarvisCommand()
    object Repeat : JarvisCommand()
    object Silence : JarvisCommand()
    object Help : JarvisCommand()
    object ShowResults : JarvisCommand()
    object Unknown : JarvisCommand()
}

object JarvisCommandParser {
    fun parse(transcript: String): JarvisCommand {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return JarvisCommand.Unknown

        if (matchesAny(normalized, "silenzio", "zitto", "basta parlare", "ferma la voce", "smetti di parlare", "stop voce")) {
            return JarvisCommand.Silence
        }
        if (matchesAny(normalized, "ripeti", "ripeti istruzione", "ripeti indicazione", "cosa devo fare")) {
            return JarvisCommand.Repeat
        }
        if (matchesAny(normalized, "dove sono", "la mia posizione", "posizione attuale", "come mi trovo")) {
            return JarvisCommand.WhereAmI
        }
        if (matchesAny(normalized, "quanto manca", "quanta strada manca", "quanto manca all arrivo", "tempo all arrivo", "distanza rimanente")) {
            return JarvisCommand.Remaining
        }
        if (matchesAny(normalized, "mostra risultati", "mostra le alternative", "risultati destinazione")) {
            return JarvisCommand.ShowResults
        }
        if (matchesAny(normalized, "aiuto", "cosa sai fare", "comandi disponibili", "cosa puoi fare")) {
            return JarvisCommand.Help
        }

        val homePhrases = listOf("vai a casa", "portami a casa", "naviga verso casa", "andiamo a casa", "guidami a casa")
        if (homePhrases.any { normalized == it }) return JarvisCommand.Navigate("casa")

        val prefixes = listOf(
            "portami a ", "portami verso ", "naviga verso ", "naviga a ",
            "guidami a ", "guidami verso ", "vai a ", "vai verso ",
            "andiamo a ", "vorrei andare a ", "imposta destinazione ",
            "cerca ", "destinazione "
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith(it) }
        if (prefix != null) {
            val destination = transcript.trim().let { original ->
                val normalizedOriginal = normalize(original)
                val matchedPrefix = prefixes.firstOrNull { normalizedOriginal.startsWith(it) } ?: prefix
                original.drop(matchedPrefix.length).trim().trim(',', '.', '!', '?', ' ')
            }
            if (destination.length >= 2 && normalize(destination) !in setOf("casa", "qui", "qua")) {
                return JarvisCommand.Navigate(destination)
            }
            if (normalize(destination) == "casa") return JarvisCommand.Navigate("casa")
        }
        return JarvisCommand.Unknown
    }

    private fun matchesAny(value: String, vararg phrases: String): Boolean =
        phrases.any { value == normalize(it) }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
