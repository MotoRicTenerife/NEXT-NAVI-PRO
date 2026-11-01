package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class SpeedLimitPolicyTest {
    private val baseLat = 28.1100
    private val baseLon = -16.7300
    private val metersPerDegree = 111_320.0

    /** A fix [northM]/[eastM] metres from the origin, at [timeMs]. */
    private fun fix(northM: Double, eastM: Double, bearing: Float?, timeMs: Long) = SpeedLimitFix(
        baseLat + northM / metersPerDegree,
        baseLon + eastM / (metersPerDegree * cos(Math.toRadians(baseLat))),
        bearing,
        timeMs
    )

    private val query = fix(0.0, 0.0, 0f, 0L) // heading north

    // ---- applicability: same road / parallel road / road change / expiry --------------------------

    @Test fun sameRoadStraightAheadKeepsTheVerifiedLimit() {
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(150.0, 3.0, 2f, 8_000L)))
    }

    @Test fun lateAnswerAfterMoreThan180MetresIsStillAppliedOnTheSameRoad() {
        // 7 s at ~28 m/s = ~196 m: the old "> 180 m" rule discarded this answer.
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(196.0, 2.0, 0f, 7_000L)))
    }

    @Test fun parallelRoadIsNotAccepted() {
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(200.0, 40.0, 0f, 9_000L)))
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(200.0, 10.0, 0f, 9_000L)))
    }

    @Test fun turnOntoAnotherRoadIsNotAccepted() {
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(80.0, 100.0, 90f, 6_000L)))
        // same spot but only the course changed by more than 60 degrees
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(100.0, 5.0, 90f, 6_000L)))
    }

    @Test fun uTurnOrReverseIsNotAccepted() {
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(-60.0, 0.0, 180f, 8_000L)))
    }

    @Test fun limitExpiresByAge() {
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(100.0, 0.0, 0f, 44_000L)))
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(100.0, 0.0, 0f, 46_000L)))
    }

    @Test fun limitExpiresByDistance() {
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(440.0, 0.0, 0f, 20_000L)))
        assertFalse(SpeedLimitPolicy.isLimitApplicable(query, fix(460.0, 0.0, 0f, 20_000L)))
    }

    @Test fun stationaryVehicleKeepsTheLimitOfTheSameRoad() {
        assertTrue(SpeedLimitPolicy.isLimitApplicable(query, fix(8.0, 2.0, null, 10 * 60_000L)))
    }

    @Test fun withoutAReliableCourseOnlyAShortDistanceIsAccepted() {
        val slow = fix(0.0, 0.0, null, 0L)
        assertTrue(SpeedLimitPolicy.isLimitApplicable(slow, fix(50.0, 0.0, null, 5_000L)))
        assertFalse(SpeedLimitPolicy.isLimitApplicable(slow, fix(70.0, 0.0, null, 5_000L)))
    }

    // ---- when to ask again ------------------------------------------------------------------------

    @Test fun firstFixAlwaysQueries() {
        assertTrue(SpeedLimitPolicy.shouldQuery(null, query))
    }

    @Test fun tomTomTraceWaitsForTwoDistinctGpsFixes() {
        assertFalse(SpeedLimitPolicy.hasUsableTrace(emptyList()))
        assertFalse(SpeedLimitPolicy.hasUsableTrace(listOf(query)))
        assertFalse(SpeedLimitPolicy.hasUsableTrace(listOf(query, fix(0.0, 0.0, 0f, 2_500L))))
        assertFalse(SpeedLimitPolicy.hasUsableTrace(listOf(query, fix(0.0, 11.0, 0f, 2_500L))))
        assertTrue(SpeedLimitPolicy.hasUsableTrace(listOf(query, fix(0.0, 13.0, 0f, 2_500L))))
    }

    @Test fun neverFasterThanTheMinimumSpacingEvenAfterATurn() {
        assertFalse(SpeedLimitPolicy.shouldQuery(query, fix(30.0, 100.0, 90f, 2_000L)))
    }

    @Test fun roadChangeTriggersAnImmediateNewQuery() {
        assertTrue(SpeedLimitPolicy.shouldQuery(query, fix(60.0, 100.0, 90f, 5_000L)))
    }

    @Test fun movingFurtherThanTheRefreshDistanceQueriesAgain() {
        assertFalse(SpeedLimitPolicy.shouldQuery(query, fix(120.0, 0.0, 0f, 6_000L)))
        assertTrue(SpeedLimitPolicy.shouldQuery(query, fix(260.0, 0.0, 0f, 9_000L)))
    }

    @Test fun movingSlowlyQueriesByAgeButAStoppedVehicleDoesNot() {
        assertTrue(SpeedLimitPolicy.shouldQuery(query, fix(60.0, 0.0, 0f, 21_000L)))
        assertFalse(SpeedLimitPolicy.shouldQuery(query, fix(5.0, 0.0, null, 10 * 60_000L)))
    }

    // ---- backoff: 429, timeouts, network errors, recovery -----------------------------------------

    @Test fun rateLimitedHonoursRetryAfterAndThenResumes() {
        val backoff = ProviderBackoff()
        backoff.onRateLimited(40, 1_000L)
        assertTrue(backoff.isBlocked(1_000L + 39_000L))
        assertFalse(backoff.isBlocked(1_000L + 40_000L))
    }

    @Test fun retryAfterIsClampedToASaneRange() {
        val backoff = ProviderBackoff()
        backoff.onRateLimited(1, 0L)
        assertEquals(5_000L, backoff.remainingMs(0L))
        backoff.onRateLimited(99_999, 0L)
        assertEquals(600_000L, backoff.remainingMs(0L))
    }

    @Test fun transientFailuresBackOffProgressivelyAndSuccessResets() {
        val backoff = ProviderBackoff()
        backoff.onTransientFailure(0L); assertEquals(8_000L, backoff.remainingMs(0L))
        backoff.onTransientFailure(0L); assertEquals(16_000L, backoff.remainingMs(0L))
        backoff.onTransientFailure(0L); assertEquals(32_000L, backoff.remainingMs(0L))
        backoff.onTransientFailure(0L); assertEquals(60_000L, backoff.remainingMs(0L))
        backoff.onTransientFailure(0L); assertEquals(60_000L, backoff.remainingMs(0L))
        backoff.onSuccess()
        assertFalse(backoff.isBlocked(0L))
        backoff.onTransientFailure(0L); assertEquals(8_000L, backoff.remainingMs(0L))
    }

    @Test fun rateLimitedWithoutRetryAfterUsesTheDefaultDelay() {
        val backoff = ProviderBackoff()
        backoff.onRateLimited(null, 0L)
        assertEquals(8_000L, backoff.remainingMs(0L))
    }

    // ---- replay of a 619 s ride against the backend budget ----------------------------------------

    private data class Sample(val timeMs: Long, val latitude: Double, val longitude: Double, val speedMps: Double, val bearing: Double)

    /** NEXT_NAVI_TRACE_CSV = a recorded "Viaggio registrato" CSV; otherwise a deterministic synthetic ride. */
    private fun loadTrace(): List<Sample> {
        val path = System.getenv("NEXT_NAVI_TRACE_CSV")
        if (path != null && File(path).exists()) {
            val lines = File(path).readLines().filter { it.isNotBlank() }
            val header = lines.first().split(",")
            val time = header.indexOf("timestamp_epoch_ms")
            val lat = header.indexOf("latitude")
            val lon = header.indexOf("longitude")
            val speed = header.indexOf("speed_mps")
            val bearing = header.indexOf("bearing_deg")
            return lines.drop(1).map { line ->
                val c = line.split(",")
                Sample(c[time].toLong(), c[lat].toDouble(), c[lon].toDouble(), c[speed].toDouble(), c[bearing].toDouble())
            }
        }
        val out = ArrayList<Sample>()
        var lat = 28.1117
        var lon = -16.7345
        for (i in 0 until 620) {
            val speed = when {
                i < 20 -> 3.7 + (28.0 - 3.7) * i / 20.0
                i > 600 -> 33.0 - (33.0 - 8.0) * (i - 600) / 20.0
                else -> 28.0 + 5.0 * sin(i / 40.0)
            }
            val bearing = 165.0 + 12.0 * sin(i / 90.0)
            val rad = Math.toRadians(bearing)
            lat += speed * cos(rad) / 111_320.0
            lon += speed * sin(rad) / (111_320.0 * cos(Math.toRadians(lat)))
            out.add(Sample(1_790_000_000_000L + i * 1000L, lat, lon, speed, bearing))
        }
        return out
    }

    private fun fixOf(s: Sample) = SpeedLimitFix(
        s.latitude, s.longitude, if (s.speedMps >= 2.5) s.bearing.toFloat() else null, s.timeMs
    )

    private fun requestTimes(trace: List<Sample>, gate: (SpeedLimitFix?, SpeedLimitFix) -> Boolean): List<Long> {
        val times = ArrayList<Long>()
        var last: SpeedLimitFix? = null
        for (sample in trace) {
            val now = fixOf(sample)
            if (gate(last, now)) { times.add(now.timeMs); last = now }
        }
        return times
    }

    private val legacyGate: (SpeedLimitFix?, SpeedLimitFix) -> Boolean = { last, now ->
        last == null || SpeedLimitPolicy.distanceMeters(last, now) >= 150.0 || now.timeMs - last.timeMs >= 8_000L
    }

    @Test fun legacyCadenceExceedsTheOldTwentyPerTenMinutesLimitButTheNewOneFitsTheBudget() {
        val trace = loadTrace()
        val duration = (trace.last().timeMs - trace.first().timeMs) / 1000
        assertTrue("trace should last about 619 s, was $duration", duration in 618..621)

        val legacy = requestTimes(trace, legacyGate)
        val first = legacy.first()
        assertTrue("old cadence would exceed 20 requests in 10 minutes", legacy.count { it < first + 600_000L } > 20)

        val current = requestTimes(trace) { last, now -> SpeedLimitPolicy.shouldQuery(last, now) }
        assertTrue("fewer lookups than before", current.size < legacy.size)

        // Backend admission model: burst 30, one token every 5 s (speedLimitGuard defaults).
        var tokens = 30.0
        var updated = current.first()
        var refused = 0
        for (t in current) {
            tokens = minOf(30.0, tokens + (t - updated) / 5_000.0)
            updated = t
            if (tokens < 1.0) refused++ else tokens -= 1.0
        }
        assertEquals("no lookup of a normal ride may be refused", 0, refused)
    }
}
