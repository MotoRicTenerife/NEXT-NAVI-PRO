package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisWeatherRequestTest {
    @Test fun usesNamedPlaceInsteadOfDeviceGps() {
        assertEquals(JarvisWeatherRequest.Place("Los Cristianos"), JarvisWeatherRequest.parse("Che tempo fa a Los Cristianos?"))
        assertEquals(JarvisWeatherRequest.Place("Puerto de la Cruz"), JarvisWeatherRequest.parse("Vorrei sapere le condizioni meteo di Puerto de la Cruz per domani"))
        assertEquals(JarvisWeatherRequest.Place("Madrid"), JarvisWeatherRequest.parse("Previsioni meteo per Madrid"))
    }

    @Test fun recognizesDestinationAndRouteWeather() {
        assertEquals(JarvisWeatherRequest.Destination, JarvisWeatherRequest.parse("Che meteo troverò alla destinazione?"))
        assertEquals(JarvisWeatherRequest.Route, JarvisWeatherRequest.parse("Che tempo troverò lungo il percorso?"))
        assertEquals(JarvisWeatherRequest.Route, JarvisWeatherRequest.parse("Controlla il meteo durante il viaggio"))
    }

    @Test fun keepsUnspecifiedWeatherLocalToGps() {
        assertEquals(JarvisWeatherRequest.Current, JarvisWeatherRequest.parse("Che tempo fa?"))
        assertEquals(JarvisWeatherRequest.Current, JarvisWeatherRequest.parse("Ci sarà vento forte domani?"))
    }
}
