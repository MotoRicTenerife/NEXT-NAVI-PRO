package com.nextnavipro.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficIncidentParserTest {
    private val now = 1_800_000_000_000L

    private fun response(
        category: String = "roadClosed",
        magnitude: String = "major",
        timeValidity: String = "present",
        startTime: String = "2026-10-01T10:00:00Z",
        endTime: String = "2027-01-20T00:00:00Z",
        geometry: String = """{"type":"LineString","coordinates":[[-16.75,28.12],[-16.74,28.13]]}"""
    ) = """{
      "incidents":[{
        "type":"Feature",
        "properties":{
          "id":"incident-1","iconCategory":"$category","magnitudeOfDelay":"$magnitude",
          "delayInSeconds":420,"lengthInMeters":238.5,"roadNumbers":["TF-1","TF-1"],
          "timeValidity":"$timeValidity","probabilityOfOccurrence":"certain",
          "startTime":"$startTime","endTime":"$endTime",
          "from":"Adeje","to":"Guaza",
          "events":[{"description":"Road closed"},{"description":"Police activity"}]
        },
        "geometry":$geometry
      }]
    }"""

    @Test fun parsesClosureAndGeoJsonCoordinatesInLatLonOrder() {
        val item = TrafficIncidentParser.parse(response(), now).single()
        assertEquals("incident-1", item.id)
        assertEquals(TrafficIncidentSeverity.CLOSED, item.severity)
        assertEquals(2, item.geometry.size)
        assertEquals(28.12, item.geometry.first().latitude, 0.000001)
        assertEquals(-16.75, item.geometry.first().longitude, 0.000001)
        assertEquals(listOf("TF-1"), item.roadNumbers)
        assertEquals(420, item.delaySeconds)
        assertEquals("Road closed. Police activity", item.description)
    }

    @Test fun unknownCategoryIsNotPromotedIntoAUsableHazard() {
        assertTrue(TrafficIncidentParser.parse(response(category = "unknown"), now).isEmpty())
    }

    @Test fun futureAndExpiredIncidentsAreExcluded() {
        assertTrue(TrafficIncidentParser.parse(response(timeValidity = "future"), now).isEmpty())
        assertTrue(TrafficIncidentParser.parse(
            response(endTime = "2026-01-01T00:00:00Z"), now
        ).isEmpty())
    }

    @Test fun expiryNeverExceedsLocalFreshnessTtl() {
        val item = TrafficIncidentParser.parse(
            response(endTime = "2030-01-01T00:00:00Z"), now, 60_000L
        ).single()
        assertEquals(now + 60_000L, item.validUntilEpochMs)
    }

    @Test fun pointGeometryAndModerateJamAreSupported() {
        val item = TrafficIncidentParser.parse(
            response(category = "jam", geometry = """{"type":"Point","coordinates":[-16.75,28.12]}"""),
            now
        ).single()
        assertEquals(TrafficIncidentSeverity.HEAVY, item.severity)
        assertEquals(1, item.geometry.size)
    }

    @Test fun malformedCoordinatesAreRejected() {
        val malformed = response(geometry = """{"type":"Point","coordinates":[999,28.12]}""")
        assertTrue(TrafficIncidentParser.parse(malformed, now).isEmpty())
    }

    @Test fun unknownOrUndefinedMagnitudeDoesNotBecomeModerate() {
        assertTrue(TrafficIncidentParser.parse(
            response(category = "jam", magnitude = "unknown"), now
        ).isEmpty())
        assertTrue(TrafficIncidentParser.parse(
            response(category = "roadWorks", magnitude = "undefined"), now
        ).isEmpty())
    }

    @Test fun invalidProviderTimestampsRejectTheIncident() {
        assertTrue(TrafficIncidentParser.parse(
            response(startTime = "not-an-iso-date"), now
        ).isEmpty())
        assertTrue(TrafficIncidentParser.parse(
            response(endTime = "not-an-iso-date"), now
        ).isEmpty())
    }

    @Test fun minorMagnitudeIsNotReportedAsHeavy() {
        val item = TrafficIncidentParser.parse(
            response(category = "jam", magnitude = "minor"), now
        ).single()
        assertEquals(TrafficIncidentSeverity.MODERATE, item.severity)
    }

    @Test fun missingTimeValidityIsNotAssumedToBePresent() {
        val missing = response().replace("\"timeValidity\":\"present\",", "")
        assertTrue(TrafficIncidentParser.parse(missing, now).isEmpty())
    }

    @Test fun malformedLineStringVertexRejectsTheWholeIncident() {
        val malformed = response(
            geometry = """{"type":"LineString","coordinates":[[-16.75,28.12],null]}"""
        )
        assertTrue(TrafficIncidentParser.parse(malformed, now).isEmpty())
    }

    @Test fun lineStringRequiresAtLeastTwoVertices() {
        val incomplete = response(
            geometry = """{"type":"LineString","coordinates":[[-16.75,28.12]]}"""
        )
        assertTrue(TrafficIncidentParser.parse(incomplete, now).isEmpty())
    }

    @Test fun missingIdentityOrGeometryIsRejected() {
        val missingGeometry = response().replace(
            """"geometry":{"type":"LineString","coordinates":[[-16.75,28.12],[-16.74,28.13]]}""", ""
        )
        assertTrue(TrafficIncidentParser.parse(missingGeometry, now).isEmpty())
        assertNull(TrafficIncidentParser.parse("""{"incidents":[{"properties":{}}]}""", now).firstOrNull())
    }
}
