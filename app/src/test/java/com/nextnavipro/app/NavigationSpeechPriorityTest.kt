package com.nextnavipro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSpeechPriorityTest {
    @Test fun safetyAlertInterruptsRadarAndJarvis() {
        assertTrue(NavigationSpeechPriority.mayInterrupt(true, NavigationSpeechPriority.RADAR, NavigationSpeechPriority.SAFETY_ALERT))
        assertTrue(NavigationSpeechPriority.mayInterrupt(true, NavigationSpeechPriority.JARVIS, NavigationSpeechPriority.SAFETY_ALERT))
    }

    @Test fun jarvisCannotCutOffAnImminentManeuver() {
        assertFalse(NavigationSpeechPriority.mayInterrupt(true, NavigationSpeechPriority.MANEUVER_IMMINENT, NavigationSpeechPriority.JARVIS))
    }

    @Test fun radarCannotCutOffManeuverExecution() {
        assertFalse(NavigationSpeechPriority.mayInterrupt(true, NavigationSpeechPriority.MANEUVER_EXECUTION, NavigationSpeechPriority.RADAR))
    }

    @Test fun newerInstructionAtSamePriorityCanReplaceStaleInstruction() {
        assertTrue(NavigationSpeechPriority.mayInterrupt(true, NavigationSpeechPriority.MANEUVER_PREPARATION, NavigationSpeechPriority.MANEUVER_PREPARATION))
    }

    @Test fun idleChannelAcceptsAnyPriority() {
        assertTrue(NavigationSpeechPriority.mayInterrupt(false, Int.MAX_VALUE, NavigationSpeechPriority.VOICE_TEST))
    }
}
