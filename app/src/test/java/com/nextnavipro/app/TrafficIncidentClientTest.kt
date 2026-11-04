package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrafficIncidentClientTest {
    @Test
    fun buildsTomTomBboxAroundRouteInLongitudeLatitudeOrder() {
        val bbox = TrafficIncidentClient.buildBbox(
            listOf(
                NavPoint(28.0500, -16.7200),
                NavPoint(28.1000, -16.6500),
                NavPoint(28.1500, -16.6000)
            )
        )
        val values = bbox.split(",").map(String::toDouble)
        assertEquals(4, values.size)
        assert(values[0] < -16.7200)
        assert(values[1] < 28.0500)
        assert(values[2] > -16.6000)
        assert(values[3] > 28.1500)
    }

    @Test
    fun rejectsInvalidOrOversizedRouteGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            TrafficIncidentClient.buildBbox(listOf(NavPoint(0.0, 0.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrafficIncidentClient.buildBbox(listOf(NavPoint(Double.NaN, 0.0), NavPoint(1.0, 1.0)))
        }
    }
}
