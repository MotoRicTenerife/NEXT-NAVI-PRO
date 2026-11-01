package com.nextnavipro.app

import java.text.Normalizer
import java.util.Locale

/** Deterministic routing of Jarvis weather questions to GPS, a named place, destination or route. */
sealed class JarvisWeatherRequest {
    object Current : JarvisWeatherRequest()
    object Destination : JarvisWeatherRequest()
    object Route : JarvisWeatherRequest()
    data class Place(val name: String) : JarvisWeatherRequest()

    companion object {
        fun parse(transcript: String): JarvisWeatherRequest {
            val normalized = normalize(transcript)
            if (normalized.isBlank()) return Current

            if (listOf(
                    "lungo il percorso", "sul percorso", "lungo la strada",
                    "sulla strada", "sul tragitto", "durante il viaggio",
                    "per tutto il percorso", "meteo del viaggio", "tempo lungo il tragitto"
                ).any { normalized.contains(it) }) return Route

            if (listOf(
                    "alla destinazione", "a destinazione", "dove sto andando",
                    "dove arrivero", "nel luogo di arrivo", "meteo all arrivo",
                    "tempo all arrivo"
                ).any { normalized.contains(it) }) return Destination

            val locationPattern = Regex(
                "(?iu)\\b(?:meteo|tempo|previsioni(?:\\s+del\\s+tempo|\\s+meteo)?|condizioni\\s+meteo|weather|forecast|clima)\\b.*?\\b(?:a|ad|al|alla|allo|all|in|di|del|della|per|at|for|de|en)\\s+(.+)$"
            )
            val rawPlace = locationPattern.find(transcript)?.groupValues?.getOrNull(1)
            val place = cleanPlace(rawPlace.orEmpty())
            return if (place.length >= 2) Place(place) else Current
        }

        private fun cleanPlace(value: String): String {
            var clean = value.trim().trim(',', '.', '!', '?', ';', ':')
            clean = clean.replace(
                Regex("(?iu)\\s+(?:per\\s+)?(?:oggi|domani|dopodomani|stasera|questa sera|stanotte|nel pomeriggio|in serata|questa notte)(?:\\b.*)?$"),
                ""
            )
            clean = clean.replace(Regex("(?iu)\\s+(?:per favore|per piacere)$"), "")
            return clean.trim().trim(',', '.', '!', '?', ';', ':')
        }

        private fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
