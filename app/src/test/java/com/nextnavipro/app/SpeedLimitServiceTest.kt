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

    @Test fun roadMatchingIgnoresArbitraryDigitizedWayDirection() {
        assertEquals(0f, SpeedLimitService.headingAxisDifference(0f, 180f), 0.001f)
        assertEquals(10f, SpeedLimitService.headingAxisDifference(10f, 200f), 0.001f)
        assertEquals(90f, SpeedLimitService.headingAxisDifference(0f, 90f), 0.001f)
    }

    @Test fun selectsDirectionalLimitsOnlyWhenHeadingIsClear() {
        assertEquals(30, SpeedLimitService.selectLimit("50", "30", "70", 0f, 0f))
        assertEquals(70, SpeedLimitService.selectLimit("50", "30", "70", 180f, 0f))
        assertEquals(50, SpeedLimitService.selectLimit("50", "30", "70", 90f, 0f))
        assertEquals(30, SpeedLimitService.selectLimit("", "30", "30", null, null))
        assertNull(SpeedLimitService.selectLimit("", "30", "70", null, null))
    }

    @Test fun rejectsImplausibleNumericValues() {
        assertNull(SpeedLimitService.parseLimit("0"))
        assertNull(SpeedLimitService.parseLimit("250"))
        assertNull(SpeedLimitService.parseLimit("fast"))
    }
}
