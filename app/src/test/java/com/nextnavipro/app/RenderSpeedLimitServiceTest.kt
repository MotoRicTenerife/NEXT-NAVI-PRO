package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderSpeedLimitServiceTest {
    @Test fun parsesExplicitNumericLimit() {
        assertEquals(50, RenderSpeedLimitService.parseResponse("""{"speedLimitKmh":50}"""))
    }
    @Test fun missingOrUnknownLimitStaysUnknown() {
        assertNull(RenderSpeedLimitService.parseResponse("""{"speedLimitKmh":null,"reason":"no_explicit_limit"}"""))
        assertNull(RenderSpeedLimitService.parseResponse("""{"road":"Calle Example"}"""))
    }
    @Test fun rejectsInvalidLimits() {
        assertNull(RenderSpeedLimitService.parseResponse("""{"speedLimitKmh":0}"""))
        assertNull(RenderSpeedLimitService.parseResponse("""{"speedLimitKmh":250}"""))
        assertNull(RenderSpeedLimitService.parseResponse("""{"speedLimitKmh":"fifty"}"""))
    }
}
