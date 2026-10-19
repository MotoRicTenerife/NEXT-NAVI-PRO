package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HereRouteMatchingServiceTest {
    @Test fun readsUnambiguousLimitFromLastMatchedLink() {
        val json = """{
          "response":{"route":[{"leg":[{"link":[
            {"attributes":{"SPEED_LIMITS_FC2":[{"FROM_REF_SPEED_LIMIT":80,"TO_REF_SPEED_LIMIT":80,"UNIT":"K"}]}},
            {"attributes":{"SPEED_LIMITS_FC2":[{"FROM_REF_SPEED_LIMIT":50,"TO_REF_SPEED_LIMIT":50,"UNIT":"K"}]}}
          ]}]}]}
        }"""
        assertEquals(50, HereRouteMatchingService.parseSpeedLimitFromResponse(json))
    }

    @Test fun convertsMilesPerHourWhenProviderMarksImperialUnits() {
        val json = """{"response":{"route":[{"leg":[{"link":[
          {"attributes":{"SPEED_LIMITS_FC1":[{"FROM_REF_SPEED_LIMIT":30,"TO_REF_SPEED_LIMIT":30,"UNIT":"M"}]}}
        ]}]}]}}"""
        assertEquals(48, HereRouteMatchingService.parseSpeedLimitFromResponse(json))
    }

    @Test fun rejectsConflictingDirectionalLimitsInsteadOfGuessing() {
        val json = """{"response":{"route":[{"leg":[{"link":[
          {"attributes":{"SPEED_LIMITS_FC2":[{"FROM_REF_SPEED_LIMIT":30,"TO_REF_SPEED_LIMIT":50,"UNIT":"K"}]}}
        ]}]}]}}"""
        assertNull(HereRouteMatchingService.parseSpeedLimitFromResponse(json))
    }

    @Test fun doesNotReuseAnEarlierLimitWhenLatestMatchedLinkHasNoLimit() {
        val json = """{"response":{"route":[{"leg":[{"link":[
          {"attributes":{"SPEED_LIMITS_FC2":[{"FROM_REF_SPEED_LIMIT":50,"TO_REF_SPEED_LIMIT":50,"UNIT":"K"}]}},
          {"attributes":{"LINK_ATTRIBUTE_FC2":[{"ISO_COUNTRY_CODE":"ESP"}]}}
        ]}]}]}}"""
        assertNull(HereRouteMatchingService.parseSpeedLimitFromResponse(json))
    }

    @Test fun missingOrEmptyResponseReturnsUnknown() {
        assertNull(HereRouteMatchingService.parseSpeedLimitFromResponse("""{"response":{"route":[]}}"""))
        assertNull(HereRouteMatchingService.parseSpeedLimitFromResponse("""{"response":{}}"""))
    }
}
