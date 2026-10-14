package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxRouteServiceTest {
    @Test
    fun identicalCoordinatesHaveZeroDistance() {
        val point = NavPoint(28.0, -16.0)
        assertEquals(0.0, GpxRouteService.distanceMeters(point, point), 0.001)
    }

    @Test
    fun oneDegreeLatitudeIsApproximately111Kilometres() {
        val distance = GpxRouteService.distanceMeters(NavPoint(0.0, 0.0), NavPoint(1.0, 0.0))
        assertTrue("Distance was $distance", distance in 111_000.0..111_300.0)
    }

    @Test
    fun distanceIsFiniteAcrossAntimeridian() {
        val distance = GpxRouteService.distanceMeters(NavPoint(0.0, 179.9), NavPoint(0.0, -179.9))
        assertTrue(distance.isFinite())
        assertTrue(distance in 22_000.0..22_300.0)
    }
}
