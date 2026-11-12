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
    data class PreviewRoute(val destination: String) : JarvisCommand()
    data class TrafficPreview(val destination: String) : JarvisCommand()
    data class TrafficVia(val roadName: String) : JarvisCommand()
    object BriefRoute : JarvisCommand()
    object AssistantName : JarvisCommand()
    object CancelNavigation : JarvisCommand()
    data class ChangeDestination(val destination: String? = null) : JarvisCommand()
    object WhereAmI : JarvisCommand()
    object Remaining : JarvisCommand()
    object Repeat : JarvisCommand()
    object Silence : JarvisCommand()
    object Help : JarvisCommand()
    object ShowResults : JarvisCommand()
    object Unknown : JarvisCommand()
}

object JarvisCommandParser {
    /** Remove a spoken assistant-name prefix before deterministic intent parsing. */
    fun stripAssistantName(transcript: String, assistantName: String = "Jarvis"): String {
        val names = listOf(assistantName.trim(), "Jarvis")
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString("|") { Regex.escape(it) }
        val prefix = Regex("(?iu)^\\s*(?:$names)(?:\\s*[,.:;!?-]\\s*|\\s+)")
        val stripped = transcript.replaceFirst(prefix, "").trim()
        return stripped.ifBlank { transcript.trim() }
    }

    fun parse(transcript: String): JarvisCommand {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return JarvisCommand.Unknown
        if (matchesAny(normalized, "come ti chiami", "come ti chiami tu", "tu come ti chiami", "qual e il tuo nome", "dimmi il tuo nome", "come ti devo chiamare",
            "chi sei", "what is your name", "what are you called", "who are you",
            "como te llamas", "cual es tu nombre", "comment tu t appelles", "wie heisst du",
            "como te chamas", "hoe heet je")) return JarvisCommand.AssistantName

        val viaPatterns = listOf(
            Regex("(?iu)^\\s*(?:e\\s+)?passando\\s+(?:dalla|dal|dallo|da|per|sulla|sul)\\s+(.+?)[?.!,]*$"),
            Regex("(?iu)^\\s*(?:e\\s+)?se\\s+prendo\\s+(?:la|il|lo)\\s+(.+?)[?.!,]*$")
        )
        val viaRoad = viaPatterns.firstNotNullOfOrNull { it.find(transcript.trim())?.groupValues?.getOrNull(1)?.trim() }
        if (!viaRoad.isNullOrBlank() && viaRoad.length in 2..60) {
            return JarvisCommand.TrafficVia(viaRoad)
        }

        val trafficSuffixes = listOf(
            "mi dici se c e traffico", "puoi dirmi se c e traffico",
            "come e il traffico", "com e il traffico",
            "ci sono code", "ci sono incidenti", "c e traffico", "traffico"
        )
        val trafficSuffix = trafficSuffixes.firstOrNull { normalized.endsWith(it) }
        if (trafficSuffix != null) {
            val cutAt = normalized.lastIndexOf(trafficSuffix).coerceAtLeast(0)
            val original = transcript.trim().replace(Regex("\\s+"), " ")
            val destinationText = original.take(cutAt).trim().trimEnd(',', '.', '!', '?', ' ')
            val normalizedDestination = normalize(destinationText)
            val prefixes = listOf(
                "devo andare alla ", "devo andare al ", "devo andare ad ", "devo andare a ",
                "vorrei andare alla ", "vorrei andare al ", "vorrei andare ad ", "vorrei andare a ",
                "voglio andare alla ", "voglio andare al ", "voglio andare ad ", "voglio andare a ",
                "devo raggiungere ", "voglio raggiungere ", "raggiungere ",
                "andare alla ", "andare al ", "andare ad ", "andare a "
            ).sortedByDescending { it.length }
            val prefix = prefixes.firstOrNull { normalizedDestination.startsWith(it) }
            val actual = if (prefix != null) destinationText.drop(prefix.length).trim() else destinationText
            if (actual.length >= 2) return JarvisCommand.TrafficPreview(actual.trim(',', '.', '!', '?', ' '))
        }

        val previewSuffixes = listOf(
            "cosa mi aspetta per la strada", "cosa mi aspetta sulla strada",
            "cosa mi aspetta lungo la strada", "cosa mi aspetta per il percorso",
            "cosa trovo lungo la strada", "fammi un briefing del percorso", "analizza il percorso",
            "what should i expect on the road", "what awaits me on the way",
            "que me espera por el camino", "que me espera en la carretera"
        )
        val suffix = previewSuffixes.firstOrNull { normalized.endsWith(it) }
        if (suffix != null) {
            val cutAt = normalized.lastIndexOf(suffix).coerceAtLeast(0)
            // Collapse whitespace before slicing so offsets in the normalized text do
            // not truncate the destination when speech recognition emits repeated spaces.
            val original = transcript.trim().replace(Regex("\\s+"), " ")
            val destinationText = original.take(cutAt).trim().trimEnd(',', '.', '!', '?', ' ')
            val normalizedDestination = normalize(destinationText)
            val prefixes = listOf(
                "voglio andare a ", "vorrei andare a ", "voglio andare al ",
                "voglio andare alla ", "voglio raggiungere ", "portami a ",
                "portami al ", "portami alla ", "vai a ", "vai al ",
                "vai alla ", "naviga verso ", "guidami a ", "take me to ",
                "go to ", "navigate to "
            ).sortedByDescending { it.length }
            val prefix = prefixes.firstOrNull { normalizedDestination.startsWith(it) }
            val actual = if (prefix != null) destinationText.drop(prefix.length).trim() else destinationText
            if (actual.length >= 2) return JarvisCommand.PreviewRoute(actual.trim(',', '.', '!', '?', ' '))
        }
        if (matchesAny(normalized, "cosa mi aspetta", "analizza il percorso", "briefing del percorso",
            "cosa c e lungo il percorso", "cosa trovo lungo la strada", "what should i expect on the road")) {
            return JarvisCommand.BriefRoute
        }

        if (matchesAny(normalized,
            "annulla navigazione", "annulla la navigazione", "annulla il percorso", "annulla percorso",
            "termina navigazione", "termina la navigazione", "termina il percorso", "ferma navigazione",
            "ferma la navigazione", "ferma il navigatore", "interrompi navigazione", "interrompi la navigazione",
            "esci dalla navigazione", "smetti di navigare", "cancella percorso", "cancella il percorso",
            "stop navigazione", "stop navigation", "cancel navigation", "cancel the navigation",
            "end navigation", "stop navigation", "cancel route", "cancel the route",
            "cancela la navegacion", "deten la navegacion", "terminer la navigation",
            "navigation annuler", "navigation beenden", "navigatie annuleren", "cancela navegacao")) {
            return JarvisCommand.CancelNavigation
        }
        val changeDestinationPrefixes = listOf(
            "cambia destinazione a ", "cambia la destinazione a ", "cambia destinazione in ",
            "cambia la destinazione in ", "cambia rotta per ", "portami invece a ", "vai invece a ",
            "imposta nuova destinazione ", "imposta destinazione a ", "imposta la destinazione a ",
            "nuova destinazione ", "change destination to ", "change the destination to ",
            "set new destination to ", "cambia destino a ", "cambia la destinacion a ",
            "changer la destination pour ", "ziel andern zu ", "verander bestemming naar "
        ).sortedByDescending { it.length }
        val changePrefix = changeDestinationPrefixes.firstOrNull { normalized.startsWith(it) }
        if (changePrefix != null) {
            val destination = transcript.trim().drop(changePrefix.length).trim().trim(',', '.', '!', '?', ' ')
            if (destination.length >= 2) return JarvisCommand.ChangeDestination(destination)
        }
        if (matchesAny(normalized, "cambia destinazione", "cambia la destinazione", "modifica destinazione",
            "modifica la destinazione", "voglio cambiare destinazione", "nuova destinazione",
            "change destination", "change the destination", "set a new destination",
            "cambiar destino", "cambiar la destinacion", "changer de destination",
            "ziel andern", "bestemming wijzigen")) {
            return JarvisCommand.ChangeDestination()
        }

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
            "mi porti ad ", "mi porti a ", "mi porti al ", "mi porti alla ", "mi porti allo ",
            "puoi portarmi ad ", "portami a ", "portami ad ", "portami al ", "portami alla ",
            "portami allo ", "portami all ", "portami in ", "portami verso ",
            "portami dal ", "portami dalla ",
            "llevame a ", "llevame al ", "llevame a la ",
            "llevame hacia ", "llevame hasta ", "llevarme a ",
            "take me to ", "navigate to ", "go to ", "drive to ", "head to ",
            "emmene moi a ", "emmene moi au ", "conduis moi a ",
            "bring mich nach ", "bring mich zu ", "fahr mich nach ", "navigiere zu ",
            "breng me naar ", "rij naar ", "navigeer naar ",
            "leva me a ", "leva me ao ", "navega para ",
            "voglio andare ad ", "voglio andare a ", "voglio andare al ", "voglio andare alla ",
            "vorrei andare ad ", "vorrei andare a ", "vorrei raggiungere ", "raggiungimi ad ", "raggiungimi a ",
            "naviga verso ", "naviga a ", "guidami a ", "guidami verso ",
            "vai a ", "vai al ", "vai alla ", "vai allo ", "vai verso ",
            "andiamo a ", "imposta destinazione "
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
