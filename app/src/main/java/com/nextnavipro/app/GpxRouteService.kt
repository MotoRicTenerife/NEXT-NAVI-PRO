package com.nextnavipro.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GpxTrack(
    val name: String,
    val points: List<NavPoint>,
    val distanceKm: Double,
    val segmentCount: Int = 1,
    val hasDiscontinuities: Boolean = false
)

object GpxRouteService {
    private const val MAX_POINTS = 100_000
    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    private const val DISCONTINUITY_METERS = 5_000.0
    private const val SEGMENT_GAP_MS = 10_000L

    /** Imports GPX 1.0/1.1 track and route points while preserving timestamps and segment breaks. */
    fun read(input: InputStream): GpxTrack {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        val points = ArrayList<NavPoint>()
        val segmentStarts = linkedSetOf(0)
        var name = "Percorso GPX"
        var pendingSegmentStart = false
        var activePointIndex: Int? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.substringAfter(':').lowercase(Locale.ROOT)) {
                    "name" -> if (name == "Percorso GPX") {
                        val candidate = parser.nextText().trim()
                        if (candidate.isNotBlank()) name = candidate.take(120)
                    }
                    "trkseg" -> pendingSegmentStart = true
                    "trkpt", "rtept" -> {
                        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                            lat in -90.0..90.0 && lon in -180.0..180.0) {
                            if (points.size >= MAX_POINTS) throw IllegalArgumentException("Il GPX supera il limite di $MAX_POINTS punti.")
                            if (pendingSegmentStart && points.isNotEmpty()) segmentStarts.add(points.size)
                            pendingSegmentStart = false
                            points.add(NavPoint(lat, lon))
                            activePointIndex = points.lastIndex
                        } else {
                            activePointIndex = null
                        }
                    }
                    "time" -> {
                        val pointIndex = activePointIndex
                        if (pointIndex != null) {
                            val parsed = parseGpxTime(parser.nextText().trim())
                            if (parsed != null) points[pointIndex] = points[pointIndex].copy(timestampEpochMs = parsed)
                        }
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name.substringAfter(':').lowercase(Locale.ROOT)) {
                    "trkpt", "rtept" -> activePointIndex = null
                }
            }
            event = parser.next()
        }
        if (points.size < 2) throw IllegalArgumentException("Il file GPX non contiene almeno due punti validi.")
        var meters = 0.0
        var hasDiscontinuities = segmentStarts.size > 1
        for (i in 1 until points.size) {
            val distance = distanceMeters(points[i - 1], points[i])
            val timeGap = points[i - 1].timestampEpochMs?.let { previous ->
                points[i].timestampEpochMs?.let { current -> current - previous }
            }
            if (segmentStarts.contains(i) || distance > DISCONTINUITY_METERS ||
                (timeGap != null && timeGap > SEGMENT_GAP_MS)) {
                hasDiscontinuities = true
                continue
            }
            meters += distance
        }
        if (!meters.isFinite() || meters <= 0.0) throw IllegalArgumentException("La traccia GPX non ha una distanza continua valida.")
        return GpxTrack(name, points, meters / 1000.0, segmentStarts.size, hasDiscontinuities)
    }

    fun write(output: OutputStream, name: String, points: List<NavPoint>) {
        require(points.size >= 2) { "Non ci sono abbastanza punti per esportare un GPX." }
        require(points.size <= MAX_POINTS) { "La traccia supera il limite di $MAX_POINTS punti." }
        points.forEach {
            require(it.latitude.isFinite() && it.latitude in -90.0..90.0)
            require(it.longitude.isFinite() && it.longitude in -180.0..180.0)
        }
        val serializer = Xml.newSerializer()
        serializer.setOutput(OutputStreamWriter(output, Charsets.UTF_8))
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        serializer.setPrefix("", GPX_NS)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(GPX_NS, "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "NEXT NAVI PRO")
        serializer.startTag(GPX_NS, "metadata")
        serializer.startTag(GPX_NS, "name")
        serializer.text(name.ifBlank { "Percorso NEXT NAVI PRO" }.take(120))
        serializer.endTag(GPX_NS, "name")
        serializer.endTag(GPX_NS, "metadata")
        serializer.startTag(GPX_NS, "trk")
        serializer.startTag(GPX_NS, "name")
        serializer.text(name.ifBlank { "Percorso NEXT NAVI PRO" }.take(120))
        serializer.endTag(GPX_NS, "name")

        var segmentOpen = false
        var previous: NavPoint? = null
        for (point in points) {
            val prior = previous
            val timeGap = prior?.timestampEpochMs?.let { old ->
                point.timestampEpochMs?.let { current -> current - old }
            }
            val newSegment = !segmentOpen || (prior != null &&
                (distanceMeters(prior, point) > DISCONTINUITY_METERS ||
                    (timeGap != null && timeGap > SEGMENT_GAP_MS)))
            if (newSegment) {
                if (segmentOpen) serializer.endTag(GPX_NS, "trkseg")
                serializer.startTag(GPX_NS, "trkseg")
                segmentOpen = true
            }
            serializer.startTag(GPX_NS, "trkpt")
            serializer.attribute(null, "lat", String.format(Locale.US, "%.7f", point.latitude))
            serializer.attribute(null, "lon", String.format(Locale.US, "%.7f", point.longitude))
            point.timestampEpochMs?.let { timestamp ->
                serializer.startTag(GPX_NS, "time")
                serializer.text(formatGpxTime(timestamp))
                serializer.endTag(GPX_NS, "time")
            }
            serializer.endTag(GPX_NS, "trkpt")
            previous = point
        }
        if (segmentOpen) serializer.endTag(GPX_NS, "trkseg")
        serializer.endTag(GPX_NS, "trk")
        serializer.endTag(GPX_NS, "gpx")
        serializer.endDocument()
        serializer.flush()
    }

    private fun parseGpxTime(value: String): Long? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed.time
        }
        return null
    }

    private fun formatGpxTime(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))

    internal fun distanceMeters(a: NavPoint, b: NavPoint): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }
}
