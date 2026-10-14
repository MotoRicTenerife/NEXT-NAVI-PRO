package com.nextnavipro.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale

data class GpxTrack(val name: String, val points: List<NavPoint>, val distanceKm: Double)

object GpxRouteService {
    private const val MAX_POINTS = 100_000

    /** Imports GPX 1.0/1.1 route and track points. Invalid or tiny tracks are rejected. */
    fun read(input: InputStream): GpxTrack {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        val points = ArrayList<NavPoint>()
        var name = "Percorso GPX"
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase(Locale.ROOT)) {
                    "name" -> if (name == "Percorso GPX") {
                        val candidate = parser.nextText().trim()
                        if (candidate.isNotBlank()) name = candidate.take(120)
                    }
                    "trkpt", "rtept" -> {
                        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                            lat in -90.0..90.0 && lon in -180.0..180.0) {
                            if (points.size >= MAX_POINTS) throw IllegalArgumentException("Il GPX supera il limite di $MAX_POINTS punti.")
                            points.add(NavPoint(lat, lon))
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (points.size < 2) throw IllegalArgumentException("Il file GPX non contiene almeno due punti validi.")
        var meters = 0.0
        for (i in 1 until points.size) meters += distanceMeters(points[i - 1], points[i])
        if (!meters.isFinite() || meters <= 0.0) throw IllegalArgumentException("La traccia GPX non ha una distanza valida.")
        return GpxTrack(name, points, meters / 1000.0)
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
        serializer.startDocument("UTF-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        serializer.startTag("http://www.topografix.com/GPX/1/1", "gpx")
        serializer.attribute(null, "version", "1.1")
        serializer.attribute(null, "creator", "NEXT NAVI PRO")
        serializer.startTag(null, "metadata")
        serializer.startTag(null, "name")
        serializer.text(name.ifBlank { "Percorso NEXT NAVI PRO" }.take(120))
        serializer.endTag(null, "name")
        serializer.endTag(null, "metadata")
        serializer.startTag(null, "trk")
        serializer.startTag(null, "name")
        serializer.text(name.ifBlank { "Percorso NEXT NAVI PRO" }.take(120))
        serializer.endTag(null, "name")
        serializer.startTag(null, "trkseg")
        for (point in points) {
            serializer.startTag(null, "trkpt")
            serializer.attribute(null, "lat", String.format(Locale.US, "%.7f", point.latitude))
            serializer.attribute(null, "lon", String.format(Locale.US, "%.7f", point.longitude))
            serializer.endTag(null, "trkpt")
        }
        serializer.endTag(null, "trkseg")
        serializer.endTag(null, "trk")
        serializer.endTag("http://www.topografix.com/GPX/1/1", "gpx")
        serializer.endDocument()
        serializer.flush()
    }

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
