package com.nextnavipro.app

/**
 * Priorities for the single application speech channel.
 * Larger values may interrupt smaller ones; a lower-priority request is dropped
 * while a higher-priority utterance is active.
 */
internal object NavigationSpeechPriority {
    const val VOICE_TEST = 10
    const val JARVIS = 20
    const val RADAR = 40
    const val MANEUVER_PREPARATION = 70
    const val MANEUVER_IMMINENT = 80
    const val MANEUVER_EXECUTION = 90
    const val SAFETY_ALERT = 100

    fun mayInterrupt(isSpeaking: Boolean, activePriority: Int, incomingPriority: Int): Boolean {
        return !isSpeaking || incomingPriority >= activePriority
    }
}
