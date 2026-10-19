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

        if (matchesAny(normalized, "silenzio", "zitto", "basta parlare", "ferma la voce", "smetti di parlare", "stop voce",
            "silencio", "calla", "deja de hablar", "stop voice", "be quiet", "silence", "tais toi", "arrête de parler",
            "stille", "sei still", "hör auf zu sprechen", "stilte", "zwijg", "para de falar")) {
            return JarvisCommand.Silence
        }
        if (matchesAny(normalized, "ripeti", "ripeti istruzione", "ripeti indicazione", "ripeti l indicazione", "ripeti l istruzione", "cosa devo fare",
            "repite", "repite la indicacion", "repeat", "repeat instruction", "repete", "wiederholen", "herhaal")) {
            return JarvisCommand.Repeat
        }
        if (matchesAny(normalized, "dove sono", "la mia posizione", "posizione attuale", "come mi trovo",
            "donde estoy", "mi ubicacion", "where am i", "my location", "ou suis je", "wo bin ich", "waar ben ik", "onde estou")) {
            return JarvisCommand.WhereAmI
        }
        if (matchesAny(normalized, "quanto manca", "quanta strada manca", "quanto manca all arrivo", "tempo all arrivo", "distanza rimanente",
            "cuanto falta", "distancia restante", "how much longer", "remaining distance", "combien reste t il",
            "wie weit noch", "hoe ver nog", "quanto falta para chegar")) {
            return JarvisCommand.Remaining
        }
        if (matchesAny(normalized, "mostra risultati", "mostra le alternative", "risultati destinazione",
            "mostrar resultados", "show results", "afficher les resultats", "ergebnisse anzeigen", "toon resultaten")) {
            return JarvisCommand.ShowResults
        }
        if (matchesAny(normalized, "aiuto", "cosa sai fare", "comandi disponibili", "cosa puoi fare",
            "ayuda", "que puedes hacer", "help", "what can you do", "aide", "was kannst du", "ajuda")) {
            return JarvisCommand.Help
        }

        val homePhrases = listOf("vai a casa", "portami a casa", "naviga verso casa", "andiamo a casa", "guidami a casa",
            "llevame a casa", "llevame al domicilio", "take me home", "go home", "bring me home",
            "emmene moi a la maison", "bring mich nach hause", "breng me naar huis", "leva me para casa")
        if (homePhrases.any { normalized == it }) return JarvisCommand.Navigate("casa")

        // Cover common spontaneous Italian speech variants. These are deterministic
        // navigation intents; they must not fall through to the conversational AI.
        val prefixes = listOf(
            "puoi portarmi a ", "puoi portarmi al ", "puoi portarmi alla ",
            "mi porti a ", "mi porti al ", "mi porti alla ", "mi porti allo ",
            "portami a ", "portami ad ", "portami al ", "portami alla ",
            "portami allo ", "portami all ", "portami in ", "portami verso ",
            "portami dal ", "portami dalla ",
            "llevame a ", "llevame al ", "llevame a la ",
            "llevame hacia ", "llevame hasta ", "llevarme a ",
            "take me to ", "navigate to ", "go to ", "drive to ", "head to ",
            "emmene moi a ", "emmene moi au ", "conduis moi a ",
            "bring mich nach ", "bring mich zu ", "fahr mich nach ", "navigiere zu ",
            "breng me naar ", "rij naar ", "navigeer naar ",
            "leva me a ", "leva me ao ", "navega para ",
            "voglio andare a ", "voglio andare al ", "voglio andare alla ",
            "vorrei andare a ", "vorrei raggiungere ", "raggiungimi a ",
            "naviga verso ", "naviga a ", "guidami a ", "guidami verso ",
            "vai a ", "vai al ", "vai alla ", "vai allo ", "vai verso ",
            "andiamo a ", "imposta destinazione ", "cerca ", "destinazione "
        )
        // Match the longest grammatical prefix first (e.g. "portami allo " before "portami a ").
        val orderedPrefixes = prefixes.sortedByDescending { it.length }
        val prefix = orderedPrefixes.firstOrNull { normalized.startsWith(it) }
        if (prefix != null) {
            val destination = transcript.trim().let { original ->
                val normalizedOriginal = normalize(original)
                val matchedPrefix = orderedPrefixes.firstOrNull { normalizedOriginal.startsWith(it) } ?: prefix
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
