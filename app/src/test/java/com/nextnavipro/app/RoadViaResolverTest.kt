package com.nextnavipro.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoadViaResolverTest {
    @Test
    fun selectsRoadGeometryNearTheMiddleOfTheTrip() {
        val origin = NavPoint(28.05, -16.75)
        val destination = NavPoint(28.05, -16.55)
        val road = RoadGeometryResult(
            osmId = 123L,
            name = "TF-1",
            tags = JSONObject(),
            geometry = listOf(
                NavPoint(28.049, -16.66),
                NavPoint(28.05, -16.65),
                NavPoint(28.051, -16.64)
            ),
            source = "OpenStreetMap"
        )
        val selected = RoadViaResolver.selectWaypoint(listOf(road), origin, destination)
        assertNotNull(selected)
        assertEquals("TF-1", selected!!.roadName)
        assertEquals(123L, selected.osmId)
    }

    @Test
    fun refusesRoadGeometryFarFromTheTripCorridor() {
        val road = RoadGeometryResult(
            osmId = 124L,
            name = "TF-1",
            tags = JSONObject(),
            geometry = listOf(NavPoint(28.20, -16.90), NavPoint(28.21, -16.89)),
            source = "OpenStreetMap"
        )
        assertNull(
            RoadViaResolver.selectWaypoint(
                listOf(road),
                NavPoint(28.05, -16.75),
                NavPoint(28.05, -16.55)
            )
        )
    }

    @Test
    fun refusesVeryShortTrips() {
        val road = RoadGeometryResult(
            osmId = 125L,
            name = "TF-1",
            tags = JSONObject(),
            geometry = listOf(NavPoint(28.05, -16.70), NavPoint(28.051, -16.699)),
            source = "OpenStreetMap"
        )
        assertNull(RoadViaResolver.selectWaypoint(
            listOf(road), NavPoint(28.05, -16.70), NavPoint(28.051, -16.699)
        ))
    }
}
