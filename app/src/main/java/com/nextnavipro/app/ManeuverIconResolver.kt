package com.nextnavipro.app

import java.util.Locale

/**
 * Valhalla maneuver type values from its turn-by-turn API reference.
 * Text is only a fallback when the API did not provide a recognized type:
 * a word such as "roundabout" must not override a typed fork/keep-left maneuver.
 */
internal object ManeuverIconResolver {
    fun isRoundabout(type: Int): Boolean = type == 26 || type == 27

    private val leftTypes = setOf(3, 14, 15, 16, 19, 21, 24, 38)
    private val rightTypes = setOf(2, 9, 10, 11, 18, 20, 23, 37)

    fun icon(type: Int, instruction: String): String {
        val lower = instruction.lowercase(Locale.ITALIAN)
        return when {
            isRoundabout(type) -> "⟳"
            type == 12 -> "↶"
            type == 13 -> "↷"
            type in leftTypes -> "⬅"
            type in rightTypes -> "➡"
            type == 0 && (lower.contains("inversione") || lower.contains("u-turn")) -> "↶"
            type == 0 && (lower.contains("sinistra") || lower.contains("left")) -> "⬅"
            type == 0 && (lower.contains("destra") || lower.contains("right")) -> "➡"
            else -> "⬆"
        }
    }

    /**
     * Valhalla's typed maneuver is the canonical direction. If its generated text
     * contradicts that type, repair only the directional words; do not guess from
     * route geometry or silently swap all instructions.
     */
    fun correctedInstruction(type: Int, instruction: String): String {
        val expected = when {
            type in leftTypes || type == 13 -> "left"
            type in rightTypes || type == 12 -> "right"
            else -> return instruction
        }
        val lower = instruction.lowercase(Locale.ITALIAN)
        val hasLeft = Regex("\\b(sinistra|sinistro|sinistre|sinistri|left)\\b").containsMatchIn(lower)
        val hasRight = Regex("\\b(destra|destro|destre|destri|right)\\b").containsMatchIn(lower)
        if (!hasLeft && !hasRight) return instruction
        if (expected == "left" && hasLeft && !hasRight) return instruction
        if (expected == "right" && hasRight && !hasLeft) return instruction
        if (hasLeft && hasRight) return instruction
        return if (expected == "left") {
            Regex("(?i)\\b(destra|destro|destre|destri|right)\\b").replace(instruction) { match ->
                when (match.value.lowercase(Locale.ITALIAN)) {
                    "right" -> "left"
                    "destro" -> "sinistro"
                    "destre" -> "sinistre"
                    "destri" -> "sinistri"
                    else -> "sinistra"
                }
            }
        } else {
            Regex("(?i)\\b(sinistra|sinistro|sinistre|sinistri|left)\\b").replace(instruction) { match ->
                when (match.value.lowercase(Locale.ITALIAN)) {
                    "left" -> "right"
                    "sinistro" -> "destro"
                    "sinistre" -> "destre"
                    "sinistri" -> "destri"
                    else -> "destra"
                }
            }
        }
    }
}
