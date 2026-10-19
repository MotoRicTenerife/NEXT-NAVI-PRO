package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HereRouteMatchingServiceTest {
    @Test fun acceptsExplicitKilometersPerHourLimitFromProxy() {
        assertEquals(50, HereRouteMatchingService.parseProxyResponse("""{"speedLimitKmh":50}"""))
    }

    @Test fun rejectsOutOfRangeOrMalformedLimits() {
        assertNull(HereRouteMatchingService.parseProxyResponse("""{"speedLimitKmh":250}"""))
        assertNull(HereRouteMatchingService.parseProxyResponse("""{"speedLimitKmh":"unknown"}"""))
        assertNull(HereRouteMatchingService.parseProxyResponse("""{"error":"provider unavailable"}"""))
    }

    @Test fun missingOrNullLimitStaysUnknown() {
        assertNull(HereRouteMatchingService.parseProxyResponse("""{"speedLimitKmh":null}"""))
        assertNull(HereRouteMatchingService.parseProxyResponse("""{}"""))
    }
}
