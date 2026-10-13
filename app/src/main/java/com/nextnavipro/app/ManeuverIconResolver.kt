package com.nextnavipro.app

import java.util.Locale

/**
 * Valhalla maneuver type values from its turn-by-turn API reference.
 * Text is only a fallback when the API did not provide a recognized type:
 * a word such as "roundabout" must not override a typed fork/keep-left maneuver.
 */
internal object ManeuverIconResolver {
    fun isRoundabout(type: Int): Boolean = type == 26 || type == 27

    fun icon(type: Int, instruction: String): String {
        val lower = instruction.lowercase(Locale.ITALIAN)
        return when {
            isRoundabout(type) -> "⟳"
            type in setOf(3, 14, 15, 16, 19, 21, 24, 38) -> "⬅"
            type in setOf(2, 9, 10, 11, 18, 20, 23, 37) -> "➡"
            type == 12 || type == 13 -> "↶"
            type == 0 && (lower.contains("inversione") || lower.contains("u-turn")) -> "↶"
            type == 0 && lower.contains("sinistra") -> "⬅"
            type == 0 && lower.contains("destra") -> "➡"
            else -> "⬆"
        }
    }
}
