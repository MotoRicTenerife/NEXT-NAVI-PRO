package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedLimitServiceTest {
    @Test fun parsesPlainKmhValue() {
        assertEquals(50, SpeedLimitService.parseLimit("50"))
    }

    @Test fun parsesKmhSuffixes() {
        assertEquals(80, SpeedLimitService.parseLimit("80 km/h"))
        assertEquals(90, SpeedLimitService.parseLimit("90kph"))
        assertEquals(70, SpeedLimitService.parseLimit("70 kmh"))
    }

    @Test fun convertsImperialAndKnotsToKmh() {
        assertEquals(80, SpeedLimitService.parseLimit("50 mph"))
        assertEquals(19, SpeedLimitService.parseLimit("10 knots"))
    }

    @Test fun refusesUnknownOrConditionalValuesRatherThanGuessing() {
        assertNull(SpeedLimitService.parseLimit(""))
        assertNull(SpeedLimitService.parseLimit("ES:urban"))
        assertNull(SpeedLimitService.parseLimit("signals"))
        assertNull(SpeedLimitService.parseLimit("none"))
        assertNull(SpeedLimitService.parseLimit("variable"))
        assertNull(SpeedLimitService.parseLimit("30 @ (school_hours)"))
    }

    @Test fun rejectsImplausibleNumericValues() {
        assertNull(SpeedLimitService.parseLimit("0"))
        assertNull(SpeedLimitService.parseLimit("250"))
        assertNull(SpeedLimitService.parseLimit("fast"))
    }
}
