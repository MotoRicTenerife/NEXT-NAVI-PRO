package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarServiceTest {
    @Test fun parsesStandaloneCameraNodesAndEnforcementDeviceMembers() {
        val json = """
            {
              "elements": [
                {"type":"node","id":10,"lat":28.1001,"lon":-16.7001,"tags":{"highway":"speed_camera","direction":"90"}},
                {"type":"relation","id":90,"tags":{"type":"enforcement","enforcement":"maxspeed","direction":"180"},
                 "members":[
                   {"type":"node","ref":20,"role":"device"},
                   {"type":"node","ref":21,"role":"from"},
                   {"type":"node","ref":22,"role":"to"}
                 ]},
                {"type":"node","id":20,"lat":28.1002,"lon":-16.7002,"tags":{}},
                {"type":"node","id":21,"lat":28.1003,"lon":-16.7003,"tags":{}},
                {"type":"node","id":22,"lat":28.1004,"lon":-16.7004,"tags":{}},
                {"type":"node","id":30,"lat":28.1005,"lon":-16.7005,"tags":{"amenity":"cafe"}}
              ]
            }
        """.trimIndent()

        val cameras = RadarService.parseOverpassResponse(json)

        assertEquals(setOf(10L, 20L), cameras.map { it.osmId }.toSet())
        assertEquals("90", cameras.first { it.osmId == 10L }.direction)
        assertEquals("Controllo velocità", cameras.first { it.osmId == 20L }.kind)
        assertEquals("180", cameras.first { it.osmId == 20L }.direction)
    }

    @Test fun parsesAverageSpeedEnforcementAsSeparateDeviceType() {
        val json = """
            {
              "elements": [
                {"type":"relation","id":91,"tags":{"type":"enforcement","enforcement":"average_speed"},
                 "members":[{"type":"node","ref":40,"role":"device"}]},
                {"type":"node","id":40,"lat":28.2,"lon":-16.8,"tags":{}}
              ]
            }
        """.trimIndent()

        val cameras = RadarService.parseOverpassResponse(json)

        assertEquals(1, cameras.size)
        assertEquals("Controllo velocità media", cameras.single().kind)
        assertTrue(cameras.single().point.latitude in 28.0..29.0)
    }
}
