package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenderTomTomSpeedLimitServiceTest {
    @Test fun retryAfterHeaderWinsOverBody() {
        assertEquals(40, RenderTomTomSpeedLimitService.parseRetryAfter("40", """{"retryAfterSeconds":12}"""))
    }

    @Test fun retryAfterFallsBackToTheJsonBody() {
        assertEquals(12, RenderTomTomSpeedLimitService.parseRetryAfter(null, """{"retryAfterSeconds":12}"""))
    }

    @Test fun missingOrImplausibleRetryAfterIsUnknown() {
        assertNull(RenderTomTomSpeedLimitService.parseRetryAfter(null, "{}"))
        assertNull(RenderTomTomSpeedLimitService.parseRetryAfter("soon", "not json"))
        assertNull(RenderTomTomSpeedLimitService.parseRetryAfter("0", """{"retryAfterSeconds":999999}"""))
    }

    @Test fun httpStatusesAreClassified() {
        assertEquals(TomTomLookupStatus.RATE_LIMITED, RenderTomTomSpeedLimitService.statusForHttp(429))
        assertEquals(TomTomLookupStatus.HTTP_ERROR, RenderTomTomSpeedLimitService.statusForHttp(502))
        assertEquals(TomTomLookupStatus.HTTP_ERROR, RenderTomTomSpeedLimitService.statusForHttp(400))
    }

    @Test fun resultWithoutLimitStaysUnknownByDefault() {
        val result = TomTomSpeedLimitResult(null, null, null, null, "no_matched_road_segments")
        assertNull(result.speedLimitKmh)
        assertEquals(TomTomLookupStatus.NO_LIMIT, result.status)
    }
}
