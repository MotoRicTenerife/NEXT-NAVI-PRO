package com.nextnavipro.app

import java.text.Normalizer
import java.util.Locale

/** Deterministic intent classification for live weather questions. */
object JarvisIntentClassifier {
    fun isWeatherQuestion(transcript: String): Boolean {
        val normalized = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return false
        val weatherTerms = Regex(
            "\\b(meteo|meteorolog\\w*|previsioni\\w*|pioggia|piove|piovera|piovendo|vento|ventoso|raffiche|temperatura|weather|forecast|rain|raining|wind|gusts|lluvia|llueve|viento|clima)\\b"
        )
        return weatherTerms.containsMatchIn(normalized) ||
            Regex("\\b(che|come|que|como)\\b.*\\b(tempo|tiempo)\\b").containsMatchIn(normalized) ||
            normalized.contains("come e il tempo") ||
            normalized.contains("fa caldo") ||
            normalized.contains("fa freddo") ||
            normalized.contains("el tiempo") ||
            normalized.contains("tiempo actual")
    }
}
