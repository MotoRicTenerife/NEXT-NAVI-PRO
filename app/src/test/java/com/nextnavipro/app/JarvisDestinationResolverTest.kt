package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisDestinationResolverTest {
    @Test fun resolvesPhoneticallyCorrectedGuazaNearGps() {
        val guazaTenerife = PlaceResult("Guaza", NavPoint(28.046, -16.654), "Arona, Santa Cruz de Tenerife", "village")
        val guazaElsewhere = PlaceResult("Guaza", NavPoint(42.5, -8.7), "Galicia, Spain", "village")

        assertTrue(JarvisDestinationResolver.isExactLocalityMatch(guazaTenerife, "Guazza"))
        assertEquals(
            guazaTenerife,
            JarvisDestinationResolver.uniqueNearbyExactLocality(
                listOf(guazaElsewhere, guazaTenerife),
                "Guazza",
                NavPoint(28.1, -16.7)
            )
        )
    }

    @Test fun duplicateGeocoderRecordsForSamePlaceDoNotCreateFalseAmbiguity() {
        val first = PlaceResult("Guaza", NavPoint(28.0460, -16.6540), "Tenerife", "village")
        val duplicate = PlaceResult("Guaza", NavPoint(28.0465, -16.6542), "Tenerife", "village")
        val selected = JarvisDestinationResolver.uniqueNearbyExactLocality(
            listOf(first, duplicate), "Guazza", NavPoint(28.1, -16.7)
        )
        assertTrue(selected == first || selected == duplicate)
    }

    @Test fun refusesToGuessBetweenDistinctNearbyLocalities() {
        val first = PlaceResult("Guaza", NavPoint(28.046, -16.654), "Tenerife", "village")
        val second = PlaceResult("Guaza", NavPoint(28.12, -16.58), "Tenerife", "village")
        assertNull(JarvisDestinationResolver.uniqueNearbyExactLocality(
            listOf(first, second), "Guazza", NavPoint(28.1, -16.7)
        ))
    }

    @Test fun doesNotTreatBusinessesAsLocalities() {
        val business = PlaceResult("Guaza", NavPoint(28.046, -16.654), "shop", "commercial")
        assertTrue(!JarvisDestinationResolver.isExactLocalityMatch(business, "Guazza"))
    }
}
