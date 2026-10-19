package com.nextnavipro.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisIntentClassifierTest {
    @Test fun recognizesWeatherQuestionsInItalianSpanishAndEnglish() {
        assertTrue(JarvisIntentClassifier.isWeatherQuestion("Che condizioni meteo ci sono?"))
        assertTrue(JarvisIntentClassifier.isWeatherQuestion("Come sarà il tempo domani?"))
        assertTrue(JarvisIntentClassifier.isWeatherQuestion("Ci sarà vento forte e raffiche?"))
        assertTrue(JarvisIntentClassifier.isWeatherQuestion("¿Qué tiempo hace?"))
        assertTrue(JarvisIntentClassifier.isWeatherQuestion("Weather forecast and rain"))
    }

    @Test fun doesNotMistakeRouteDurationForWeather() {
        assertFalse(JarvisIntentClassifier.isWeatherQuestion("Quanto tempo manca all'arrivo?"))
        assertFalse(JarvisIntentClassifier.isWeatherQuestion("Portami a Guaza"))
        assertFalse(JarvisIntentClassifier.isWeatherQuestion(""))
    }
}
