package com.nextnavipro.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

data class NavPoint(val latitude: Double, val longitude: Double, val timestampEpochMs: Long? = null)
data class PlaceResult(val name: String, val point: NavPoint, val description: String, val kind: String = "unknown")
data class RouteManeuver(
    val pointIndex: Int,
    val instruction: String,
    val type: Int,
    val streetName: String
)
data class RouteResult(
    val points: List<NavPoint>,
    val distanceKm: Double,
    val durationSeconds: Int,
    val firstInstruction: String,
    val maneuvers: List<RouteManeuver> = emptyList()
)

object NavigationServices {
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000
    private const val USER_AGENT = "NEXT-NAVI-PRO/0.3 (https://github.com/MotoRicTenerife/NEXT-NAVI-PRO)"

    internal fun placeQueryVariants(query: String): List<String> {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return emptyList()
        val variants = linkedSetOf(clean)
        // Speech recognition may double consonants in foreign proper names:
        // Spanish "Guaza" can be transcribed as "Guazza".
        val collapsed = clean.replace(Regex("(?i)([b-df-hj-np-tv-z])\\1+"), "$1")
        if (collapsed != clean && collapsed.length >= 3) variants.add(collapsed)
        val unaccented = java.text.Normalizer.normalize(clean, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        if (unaccented != clean) variants.add(unaccented)
        return variants.toList()
    }

    fun searchPlaces(query: String, near: NavPoint? = null): List<PlaceResult> {
        for (candidate in placeQueryVariants(query)) {
            val results = try {
                searchPlacesSingle(candidate, near)
            } catch (_: Exception) {
                // A failed fallback for one spelling must not prevent trying the next spelling.
                emptyList()
            }
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    private fun searchPlacesSingle(query: String, near: NavPoint?): List<PlaceResult> {
        val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        // Photon supports location-biased search. Supplying the current GPS fix helps
        // brand queries (e.g. "Mercadona") return nearby branches instead of distant matches.
        val proximity = near?.let {
            "&lat=" + it.latitude + "&lon=" + it.longitude + "&zoom=14"
        }.orEmpty()
        val language = Locale.getDefault().language.ifBlank { "en" }
        val connection = URL("https://photon.komoot.io/api/?q=" + encoded + "&limit=20&lang=" + URLEncoder.encode(language, "UTF-8") + proximity)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/geo+json, application/json")
            connection.setRequestProperty("Referer", "https://github.com/MotoRicTenerife/NEXT-NAVI-PRO")
            val body = readResponse(connection)
            val features = JSONObject(body).optJSONArray("features") ?: JSONArray()
            val results = ArrayList<PlaceResult>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val coordinates = geometry.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue
                val lon = coordinates.optDouble(0, Double.NaN)
                val lat = coordinates.optDouble(1, Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val name = properties.optString("name").takeIf { it.isNotBlank() }
                    ?: properties.optString("street").takeIf { it.isNotBlank() }
                    ?: "Luogo senza nome"
                val detailParts = listOf(
                    properties.optString("street").takeIf { it.isNotBlank() && it != name },
                    properties.optString("housenumber").takeIf { it.isNotBlank() },
                    properties.optString("city").takeIf { it.isNotBlank() },
                    properties.optString("state").takeIf { it.isNotBlank() },
                    properties.optString("country").takeIf { it.isNotBlank() }
                ).filterNotNull()
                val kind = listOf(properties.optString("type"), properties.optString("osm_value"), properties.optString("osm_key"))
                    .firstOrNull { it.isNotBlank() }?.lowercase() ?: "unknown"
                results.add(PlaceResult(name, NavPoint(lat, lon), detailParts.joinToString(", "), kind))
            }
            if (results.isNotEmpty()) return results
            // A zero-result Photon response is also a reason to try the fallback geocoder.
            return searchPlacesNominatim(cleanQuery, near)
        } catch (error: Exception) {
            // If Photon is unavailable, try Nominatim once as a fallback.
            // This is a search fallback only; it never fabricates a result.
            return searchPlacesNominatim(cleanQuery, near)
        } finally {
            connection.disconnect()
        }
    }

    private fun searchPlacesNominatim(query: String, near: NavPoint?): List<PlaceResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val viewbox = near?.let {
            "&viewbox=${it.longitude - 0.5},${it.latitude + 0.5},${it.longitude + 0.5},${it.latitude - 0.5}"
        }.orEmpty()
        val url = "https://nominatim.openstreetmap.org/search?q=" + encoded +
            "&format=jsonv2&limit=20&addressdetails=1&accept-language=" +
            URLEncoder.encode(Locale.getDefault().toLanguageTag(), "UTF-8") + viewbox
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val body = readResponse(connection)
            val items = JSONArray(body)
            val results = ArrayList<PlaceResult>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val address = item.optJSONObject("address") ?: JSONObject()
                val name = item.optString("name").takeIf { it.isNotBlank() }
                    ?: item.optString("display_name").substringBefore(",").takeIf { it.isNotBlank() }
                    ?: continue
                val detail = item.optString("display_name").removePrefix(name).trim().trimStart(',')
                val kind = listOf(item.optString("type"), item.optString("class"), address.optString("city"), address.optString("town"), address.optString("village"))
                    .firstOrNull { it.isNotBlank() }?.lowercase() ?: "unknown"
                results.add(PlaceResult(name, NavPoint(lat, lon), detail, kind))
            }
            return results
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Builds a Valhalla request for the selected vehicle. The motorcycle profile is
     * explicitly configured for paved-road touring (not adventure trails).
     */
    internal fun buildRouteRequest(origin: NavPoint, destination: NavPoint, motorcycle: Boolean): JSONObject {
        val request = JSONObject()
            .put("locations", JSONArray()
                .put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
                .put(JSONObject().put("lat", destination.latitude).put("lon", destination.longitude)))
            .put("costing", if (motorcycle) "motorcycle" else "auto")
            .put("units", "kilometers")
            .put("language", Locale.getDefault().toLanguageTag())
            .put("shape_format", "polyline6")
            .put("directions_options", JSONObject().put("units", "kilometers"))
        if (motorcycle) {
            request.put("costing_options", JSONObject().put("motorcycle",
                JSONObject().put("use_trails", 0.0)))
        }
        return request
    }

    fun calculateRoute(origin: NavPoint, destination: NavPoint, motorcycle: Boolean): RouteResult {
        val request = buildRouteRequest(origin, destination, motorcycle)
        val connection = URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("X-Client-Id", "NEXT-NAVI-PRO (github.com/MotoRicTenerife/NEXT-NAVI-PRO)")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(request.toString()) }
            val body = readResponse(connection)
            val root = JSONObject(body)
            val trip = root.optJSONObject("trip") ?: throw IllegalStateException(
                root.optJSONObject("error")?.optString("message") ?: "Il server non ha restituito un percorso."
            )
            val legs = trip.optJSONArray("legs") ?: throw IllegalStateException("Percorso senza tappe.")
            if (legs.length() == 0) throw IllegalStateException("Percorso vuoto.")
            val leg = legs.getJSONObject(0)
            val encodedShape = leg.optString("shape")
            val decoded = decodePolyline6(encodedShape)
            if (decoded.size < 2) throw IllegalStateException("Geometria del percorso non valida.")
            val summary = trip.optJSONObject("summary") ?: JSONObject()
            val legSummary = leg.optJSONObject("summary") ?: summary
            val distance = legSummary.optDouble("length", summary.optDouble("length", 0.0))
            val seconds = legSummary.optDouble("time", summary.optDouble("time", 0.0)).roundToInt()
            val maneuvers = leg.optJSONArray("maneuvers")
            val parsedManeuvers = ArrayList<RouteManeuver>()
            if (maneuvers != null) {
                for (i in 0 until maneuvers.length()) {
                    val item = maneuvers.optJSONObject(i) ?: continue
                    val instructionText = item.optString("instruction").ifBlank { "Prosegui" }
                    val pointIndex = item.optInt("begin_shape_index", 0).coerceIn(0, decoded.lastIndex)
                    val streetNames = item.optJSONArray("street_names")
                    val streetName = if (streetNames != null && streetNames.length() > 0) streetNames.optString(0) else ""
                    parsedManeuvers.add(RouteManeuver(pointIndex, instructionText, item.optInt("type", 0), streetName))
                }
            }
            val instruction = parsedManeuvers.firstOrNull()?.instruction ?: "Segui il percorso evidenziato."
            return RouteResult(decoded, distance, seconds, instruction, parsedManeuvers)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Converts an imported GPX track into a road-snapped route with server-derived maneuvers.
     * This is an online operation: if the trace cannot be matched, callers must keep the GPX
     * as a plain track and must not invent turn instructions.
     */
    fun calculateRouteFromTrace(points: List<NavPoint>, motorcycle: Boolean): RouteResult {
        val request = buildTraceRequest(points, motorcycle)
        val connection = URL("https://valhalla1.openstreetmap.de/trace_route").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("X-Client-Id", "NEXT-NAVI-PRO (github.com/MotoRicTenerife/NEXT-NAVI-PRO)")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(request.toString()) }
            val root = JSONObject(readResponse(connection))
            val trip = root.optJSONObject("trip") ?: throw IllegalStateException(
                root.optJSONObject("error")?.optString("message") ?: "La traccia GPX non è stata agganciata alla rete stradale.")
            val legs = trip.optJSONArray("legs") ?: throw IllegalStateException("Il percorso GPX non contiene tappe.")
            if (legs.length() == 0) throw IllegalStateException("Il percorso GPX è vuoto.")
            val leg = legs.getJSONObject(0)
            val decoded = decodePolyline6(leg.optString("shape"))
            if (decoded.size < 2) throw IllegalStateException("La geometria stradale del GPX non è valida.")
            val summary = trip.optJSONObject("summary") ?: JSONObject()
            val legSummary = leg.optJSONObject("summary") ?: summary
            val maneuvers = ArrayList<RouteManeuver>()
            val rawManeuvers = leg.optJSONArray("maneuvers")
            if (rawManeuvers != null) for (i in 0 until rawManeuvers.length()) {
                val item = rawManeuvers.optJSONObject(i) ?: continue
                val names = item.optJSONArray("street_names")
                maneuvers.add(RouteManeuver(
                    item.optInt("begin_shape_index", 0).coerceIn(0, decoded.lastIndex),
                    item.optString("instruction").ifBlank { "Prosegui" },
                    item.optInt("type", 0),
                    if (names != null && names.length() > 0) names.optString(0) else ""
                ))
            }
            return RouteResult(decoded, legSummary.optDouble("length", summary.optDouble("length", 0.0)),
                legSummary.optDouble("time", summary.optDouble("time", 0.0)).roundToInt(),
                maneuvers.firstOrNull()?.instruction ?: "Segui il percorso stradale evidenziato.", maneuvers)
        } finally { connection.disconnect() }
    }

    internal fun buildTraceRequest(points: List<NavPoint>, motorcycle: Boolean): JSONObject {
        require(points.size >= 2) { "Il GPX deve contenere almeno due punti." }
        require(points.all { it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 }) {
            "Il GPX contiene coordinate non valide."
        }
        // Keep the request bounded while preserving the shape of longer tracks.
        val sampled = ArrayList<NavPoint>()
        var previous: NavPoint? = null
        for ((index, point) in points.withIndex()) {
            val last = previous
            if (last == null || GpxRouteService.distanceMeters(last, point) >= 20.0 || index == points.lastIndex) {
                if (sampled.size < 999) sampled.add(point)
                previous = point
            }
        }
        if (sampled.lastOrNull() != points.last() && sampled.size < 1000) sampled.add(points.last())
        require(sampled.size >= 2) { "Non è possibile campionare la traccia GPX." }
        val shape = JSONArray()
        sampled.forEachIndexed { index, point -> shape.put(JSONObject().put("lat", point.latitude).put("lon", point.longitude).put("type", if (index == 0 || index == sampled.lastIndex) "break" else "via")) }
        val request = JSONObject()
            .put("shape", shape)
            .put("shape_match", "map_snap")
            .put("costing", if (motorcycle) "motorcycle" else "auto")
            .put("search_radius", 50)
            .put("units", "kilometers")
            .put("language", "it-IT")
            .put("shape_format", "polyline6")
            .put("directions_options", JSONObject().put("units", "kilometers"))
        if (motorcycle) request.put("costing_options", JSONObject().put("motorcycle", JSONObject().put("use_trails", 0.0)))
        return request
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }.orEmpty()
        if (status !in 200..299) {
            val detail = text.replace(Regex("\\s+"), " ").take(220)
            throw IllegalStateException(
                "Server cartografico HTTP " + status +
                    if (detail.isNotBlank()) ": " + detail else ". Riprova più tardi."
            )
        }
        return text
    }

    internal fun decodePolyline6(encoded: String): List<NavPoint> {
        if (encoded.isBlank()) return emptyList()
        val result = ArrayList<NavPoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            var shift = 0
            var value = 0L
            var chunk: Int
            do {
                if (index >= encoded.length) return emptyList()
                chunk = encoded[index++].code - 63
                if (chunk < 0 || chunk > 63) return emptyList()
                value = value or ((chunk and 0x1f).toLong() shl shift)
                shift += 5
                if (shift > 60) return emptyList()
            } while (chunk >= 0x20)
            val deltaLat = if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
            latitude += deltaLat

            shift = 0
            value = 0L
            do {
                if (index >= encoded.length) return emptyList()
                chunk = encoded[index++].code - 63
                if (chunk < 0 || chunk > 63) return emptyList()
                value = value or ((chunk and 0x1f).toLong() shl shift)
                shift += 5
                if (shift > 60) return emptyList()
            } while (chunk >= 0x20)
            val deltaLon = if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
            longitude += deltaLon
            val lat = latitude / 1_000_000.0
            val lon = longitude / 1_000_000.0
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return emptyList()
            result.add(NavPoint(lat, lon))
        }
        return result
    }
}
