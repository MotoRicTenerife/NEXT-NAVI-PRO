package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), LocationListener {
    companion object {
        private const val PREFS = "next_navi_settings"
        private const val PREF_NEGATIVE = "negative_theme"
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val PERMISSION_LOCATION = 100
        private const val SOURCE_USER = "next-user-position"
        private const val SOURCE_ROUTE = "next-active-route"
        private const val SOURCE_RADARS = "next-speed-cameras"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var root: LinearLayout
    private lateinit var speedText: TextView
    private lateinit var gpsText: TextView
    private lateinit var routeText: TextView
    private lateinit var radarText: TextView
    private lateinit var destinationInput: EditText
    private lateinit var searchButton: Button
    private lateinit var routeButton: Button
    private lateinit var modeButton: Button
    private lateinit var themeButton: Button
    private lateinit var limitValue: TextView
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var mapStyleReady = false
    private var dashboardStarted = false
    private var activityStarted = false
    private var activityResumed = false
    private var gpsUpdatesRequested = false
    private var carMode = false
    private var negativeTheme = true
    private var currentLocation: Location? = null
    private var destination: PlaceResult? = null
    private var routeResult: RouteResult? = null
    private var radarCameras: List<RadarCamera> = emptyList()
    private var lastRadarAttemptMs = 0L
    private var lastCameraLocation: Location? = null
    private var followGps = true
    private var isSearching = false
    private var isCalculatingRoute = false
    private val splashHandler = Handler(Looper.getMainLooper())
    private val yellow = Color.rgb(255, 212, 0)
    private val cyan = Color.rgb(0, 229, 255)
    private val white = Color.WHITE
    private val black = Color.BLACK
    private val positiveBackground = Color.rgb(235, 240, 242)
    private val positiveText = Color.rgb(18, 28, 35)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        negativeTheme = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_NEGATIVE, true)
        dashboardStarted = savedInstanceState?.getBoolean("dashboard_started") ?: false
        if (dashboardStarted) {
            buildDashboard(savedInstanceState)
            requestGps()
        } else {
            showSplash()
        }
    }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(black)
        }
        val brand = TextView(this).apply {
            text = "NEXT NAVI PRO"
            textSize = 34f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(yellow)
            gravity = Gravity.CENTER
            alpha = 0f
        }
        splash.addView(brand, params(-1, dp(76)))
        val tagline = TextView(this).apply {
            text = "LA STRADA, SOTTO CONTROLLO"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        splash.addView(tagline, params(-1, dp(44)))
        val start = Button(this).apply {
            text = "Avvia Navigazione"
            textSize = 16f
            isAllCaps = false
            setTextColor(white)
            setBackgroundColor(Color.rgb(26, 26, 26))
            setOnClickListener { launchDashboard() }
        }
        splash.addView(start, LinearLayout.LayoutParams(-1, dp(58)).apply {
            setMargins(dp(12), dp(28), dp(12), dp(12))
        })
        setContentView(splash)
        brand.startAnimation(android.view.animation.AlphaAnimation(0f, 1f).apply {
            duration = 1200
            fillAfter = true
        })
        splashHandler.postDelayed({ launchDashboard() }, 1800)
    }

    private fun launchDashboard() {
        if (dashboardStarted) return
        dashboardStarted = true
        splashHandler.removeCallbacksAndMessages(null)
        buildDashboard(null)
        requestGps()
    }

    private fun buildDashboard(savedState: Bundle?) {
        MapLibre.getInstance(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        destinationInput = EditText(this).apply {
            hint = "Cerca indirizzo o località"
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), 0, dp(8), 0)
            setTextColor(if (negativeTheme) white else positiveText)
            setHintTextColor(if (negativeTheme) Color.LTGRAY else Color.DKGRAY)
            setBackgroundColor(if (negativeTheme) Color.rgb(32, 36, 40) else Color.rgb(220, 227, 230))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchDestination()
                    true
                } else false
            }
        }
        searchRow.addView(destinationInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        searchButton = makeButton("CERCA") { searchDestination() }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(76), dp(46)).apply {
            setMargins(dp(4), 0, 0, 0)
        })
        val homeButton = makeButton("CASA") { showHomeOptions() }
        searchRow.addView(homeButton, LinearLayout.LayoutParams(dp(70), dp(46)).apply {
            setMargins(dp(4), 0, 0, 0)
        })
        root.addView(searchRow, params(-1, dp(50)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val infoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val brand = TextView(this).apply {
            text = "NEXT NAVI PRO"
            textSize = 12f
            letterSpacing = 0.04f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        infoColumn.addView(brand, params(-1, dp(34)))
        val limitTitle = TextView(this).apply {
            text = "LIMITE"
            textSize = 12f
            gravity = Gravity.CENTER
        }
        infoColumn.addView(limitTitle, params(-1, dp(28)))
        limitValue = TextView(this).apply {
            text = "—"
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(if (negativeTheme) Color.rgb(35, 35, 35) else Color.WHITE)
        }
        infoColumn.addView(limitValue, params(-1, dp(54)))
        gpsText = TextView(this).apply {
            text = "RICERCA GPS…"
            textSize = 10f
            gravity = Gravity.CENTER
        }
        infoColumn.addView(gpsText, params(-1, dp(38)))
        routeText = TextView(this).apply {
            text = "Nessun percorso attivo"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
        }
        infoColumn.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
        radarText = TextView(this).apply {
            text = "RADAR OSM"
            textSize = 10f
            gravity = Gravity.CENTER
        }
        infoColumn.addView(radarText, params(-1, dp(36)))

        val speedColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
        }
        speedText = TextView(this).apply {
            text = "—"
            textSize = 82f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(116)))
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 16f
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
        }
        speedColumn.addView(unit, params(-1, dp(30)))
        val speedCaption = TextView(this).apply {
            text = "VELOCITÀ GPS"
            textSize = 11f
            gravity = Gravity.CENTER
        }
        speedColumn.addView(speedCaption, params(-1, dp(28)))
        val centerSpacer = View(this)
        speedColumn.addView(centerSpacer, LinearLayout.LayoutParams(-1, 0, 1f))
        routeButton = makeButton("AVVIA PERCORSO") {
            if (routeResult != null) clearRoute() else calculateSelectedRoute()
        }
        speedColumn.addView(routeButton, params(-1, dp(48)))

        val mapColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val mapHeader = TextView(this).apply {
            text = "MAPPA"
            textSize = 12f
            gravity = Gravity.CENTER
        }
        mapColumn.addView(mapHeader, params(-1, dp(30)))
        val newMapView = MapView(this)
        mapView = newMapView
        newMapView.onCreate(savedState)
        mapColumn.addView(newMapView, LinearLayout.LayoutParams(-1, 0, 1f))
        val recenterButton = makeButton("CENTRA GPS") {
            followGps = true
            lastCameraLocation = null
            currentLocation?.let { updateMapForLocation(it, true) }
        }
        mapColumn.addView(recenterButton, params(-1, dp(42)))

        content.addView(infoColumn, LinearLayout.LayoutParams(0, -1, 0.29f))
        content.addView(speedColumn, LinearLayout.LayoutParams(0, -1, 0.32f))
        content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 0.39f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = makeButton(if (carMode) "AUTO" else "MOTO") {
            carMode = !carMode
            modeButton.text = if (carMode) "AUTO" else "MOTO"
        }
        themeButton = makeButton(if (negativeTheme) "NEGATIVO" else "POSITIVO") { toggleTheme() }
        controls.addView(modeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(2), dp(3), dp(3), dp(1))
        })
        controls.addView(themeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(3), dp(3), dp(2), dp(1))
        })
        root.addView(controls, params(-1, dp(48)))

        setContentView(root)
        applyTheme()
        setupMap(newMapView, savedState)
        if (activityStarted) newMapView.onStart()
        if (activityResumed) newMapView.onResume()
    }

    private fun setupMap(mapViewToSetup: MapView, savedState: Bundle?) {
        mapViewToSetup.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    followGps = false
                }
            }
            applyMapStyle(readyMap)
        }
    }

    private fun applyMapStyle(readyMap: MapLibreMap) {
        mapStyleReady = false
        val styleUrl = if (negativeTheme) "https://tiles.openfreemap.org/styles/positron" else STYLE_URL
        readyMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            if (negativeTheme) {
                style.layers.forEach { layer ->
                    when (layer) {
                        is SymbolLayer -> layer.setProperties(
                            PropertyFactory.textColor("#FFFFFF"),
                            PropertyFactory.textHaloColor("#000000"),
                            PropertyFactory.textHaloWidth(1.2f)
                        )
                        is LineLayer -> layer.setProperties(PropertyFactory.lineColor("#111111"))
                    }
                }
            }
            mapStyleReady = true
            style.addSource(GeoJsonSource(SOURCE_USER, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(CircleLayer("next-user-dot", SOURCE_USER).withProperties(
                PropertyFactory.circleColor("#FFD400"),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#000000"),
                PropertyFactory.circleStrokeWidth(2.5f)
            ))
            style.addSource(GeoJsonSource(SOURCE_ROUTE, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(LineLayer("next-route-line", SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(if (negativeTheme) "#00D6C9" else "#FFD400"),
                PropertyFactory.lineWidth(5f)
            ))
            style.addSource(GeoJsonSource(SOURCE_RADARS, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(CircleLayer("next-radar-circles", SOURCE_RADARS).withProperties(
                PropertyFactory.circleColor("#FF3B30"),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f)
            ))
            currentLocation?.let { updateMapForLocation(it, true) }
            drawRadars()
            routeResult?.let { drawRoute(it, false) }
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(if (negativeTheme) white else positiveText)
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
            setOnClickListener { action() }
        }

    private fun toggleTheme() {
        negativeTheme = !negativeTheme
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_NEGATIVE, negativeTheme).apply()
        themeButton.text = if (negativeTheme) "NEGATIVO" else "POSITIVO"
        applyTheme()
        map?.let { applyMapStyle(it) }
    }

    private fun applyTheme() {
        if (!::root.isInitialized) return
        root.setBackgroundColor(if (negativeTheme) black else positiveBackground)
        val textColor = if (negativeTheme) white else positiveText
        root.walkViews().forEach { view ->
            if (view is TextView && view !== destinationInput) {
                view.setTextColor(when {
                    view === speedText -> if ((speedText.text.toString().toIntOrNull() ?: 0) >= 120) Color.RED else if ((speedText.text.toString().toIntOrNull() ?: 0) >= 50) yellow else textColor
                    view === gpsText -> if (negativeTheme) yellow else Color.rgb(130, 85, 0)
                    view === routeText -> if (negativeTheme) cyan else Color.rgb(0, 90, 125)
                    view === radarText -> Color.rgb(255, 70, 55)
                    view.text.toString().contains("NEXT NAVI PRO") -> yellow
                    else -> textColor
                })
            }
            if (view is Button) {
                view.setTextColor(textColor)
                view.setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
            }
        }
        destinationInput.setTextColor(textColor)
        destinationInput.setHintTextColor(if (negativeTheme) Color.LTGRAY else Color.DKGRAY)
        destinationInput.setBackgroundColor(if (negativeTheme) Color.rgb(32, 36, 40) else Color.rgb(220, 227, 230))
    }

    private fun android.view.View.walkViews(): List<View> {
        val result = ArrayList<View>()
        fun visit(view: View) {
            result.add(view)
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(this)
        return result
    }

    private fun showHomeOptions() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedName = prefs.getString("home_name", null)
        val choices = if (savedName.isNullOrBlank()) {
            arrayOf("Salva la destinazione selezionata come Casa")
        } else {
            arrayOf("Vai a Casa: " + savedName, "Salva la destinazione selezionata come Casa", "Dimentica Casa")
        }
        AlertDialog.Builder(this)
            .setTitle(if (savedName.isNullOrBlank()) "Indirizzo di casa" else "Casa salvata: " + savedName)
            .setItems(choices) { _, which ->
                if (savedName.isNullOrBlank()) {
                    saveSelectedDestinationAsHome()
                } else when (which) {
                    0 -> navigateHome()
                    1 -> saveSelectedDestinationAsHome()
                    2 -> {
                        prefs.edit().remove("home_name").remove("home_description")
                            .remove("home_lat").remove("home_lon").apply()
                        Toast.makeText(this, "Indirizzo di casa rimosso.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun saveSelectedDestinationAsHome() {
        val selected = destination
        if (selected == null) {
            Toast.makeText(this, "Cerca e seleziona prima un indirizzo da salvare come Casa.", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("home_name", selected.name)
            .putString("home_description", selected.description)
            .putString("home_lat", selected.point.latitude.toString())
            .putString("home_lon", selected.point.longitude.toString())
            .apply()
        Toast.makeText(this, "Indirizzo di casa salvato: " + selected.name, Toast.LENGTH_LONG).show()
    }

    private fun navigateHome() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val name = prefs.getString("home_name", null) ?: return
        val lat = prefs.getString("home_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("home_lon", null)?.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, "Indirizzo di casa non valido: salvalo di nuovo.", Toast.LENGTH_LONG).show()
            return
        }
        destination = PlaceResult(name, NavPoint(lat, lon), prefs.getString("home_description", "").orEmpty())
        destinationInput.setText(name)
        routeText.text = "DESTINAZIONE: Casa\n" + name
        calculateSelectedRoute()
    }

    private fun searchDestination() {
        val query = destinationInput.text.toString().trim()
        if (query.length < 3) {
            Toast.makeText(this, "Inserisci almeno 3 caratteri per cercare.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isSearching) return
        isSearching = true
        searchButton.isEnabled = false
        routeText.text = "RICERCA DESTINAZIONE…"
        thread(name = "photon-search") {
            try {
                val results = NavigationServices.searchPlaces(query)
                runOnUiThread {
                    isSearching = false
                    searchButton.isEnabled = true
                    if (results.isEmpty()) {
                        routeText.text = "NESSUN RISULTATO • Prova con nome località o strada"
                        return@runOnUiThread
                    }
                    val labels = results.map {
                        if (it.description.isBlank()) it.name else it.name + "\n" + it.description
                    }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Scegli destinazione")
                        .setItems(labels) { _, which ->
                            destination = results[which]
                            routeResult = null
                            val routeSource = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
                            routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                            destinationInput.setText(results[which].name)
                            routeText.text = "DESTINAZIONE: " + results[which].name + "\nPremi AVVIA PERCORSO"
                            routeButton.text = "AVVIA PERCORSO"
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(results[which].point.latitude, results[which].point.longitude), 14.0
                            ), 700)
                        }
                        .setNegativeButton("Annulla", null)
                        .show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    isSearching = false
                    searchButton.isEnabled = true
                    routeText.text = "RICERCA NON DISPONIBILE • " + (error.message ?: "Errore di rete")
                }
            }
        }
    }

    private fun calculateSelectedRoute() {
        val target = destination ?: run {
            searchDestination()
            return
        }
        val location = currentLocation
        if (location == null || !location.hasAccuracy() || location.accuracy > 50f) {
            routeText.text = "ATTENDO UNA POSIZIONE GPS PRECISA"
            Toast.makeText(this, "Attendi GPS valido prima di calcolare il percorso.", Toast.LENGTH_LONG).show()
            return
        }
        if (isCalculatingRoute) return
        isCalculatingRoute = true
        routeButton.isEnabled = false
        routeText.text = "CALCOLO PERCORSO ONLINE…"
        val origin = NavPoint(location.latitude, location.longitude)
        thread(name = "valhalla-route") {
            try {
                val result = NavigationServices.calculateRoute(
                    origin, target.point, motorcycle = !carMode
                )
                runOnUiThread {
                    isCalculatingRoute = false
                    routeButton.isEnabled = true
                    routeResult = result
                    drawRoute(result, true)
                    val durationMinutes = (result.durationSeconds / 60.0).toInt()
                    routeText.text = String.format(
                        Locale.ITALY, "%.1f km  •  circa %d min\n%s",
                        result.distanceKm, durationMinutes, result.firstInstruction
                    )
                    routeButton.text = "ANNULLA PERCORSO"
                }
            } catch (error: Exception) {
                runOnUiThread {
                    isCalculatingRoute = false
                    routeButton.isEnabled = true
                    routeText.text = "PERCORSO NON DISPONIBILE • " + (error.message ?: "Errore")
                }
            }
        }
    }

    private fun drawRoute(result: RouteResult, fitCamera: Boolean) {
        if (!mapStyleReady) return
        val source = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource ?: return
        val points = result.points.map { Point.fromLngLat(it.longitude, it.latitude) }
        source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
        if (fitCamera && points.isNotEmpty()) {
            val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
            result.points.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
            try {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), dp(44)),
                    900
                )
            } catch (_: Exception) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(result.points.first().latitude, result.points.first().longitude), 13.0
                ), 700)
            }
        }
    }

    private fun clearRoute() {
        routeResult = null
        destination = null
        if (::routeText.isInitialized) routeText.text = "MAPPA ONLINE  •  Nessun percorso attivo"
        if (::routeButton.isInitialized) routeButton.text = "AVVIA PERCORSO"
        if (::destinationInput.isInitialized) destinationInput.text.clear()
        val source = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
        source?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    }

    private fun requestGps() {
        if (!dashboardStarted || gpsUpdatesRequested) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                PERMISSION_LOCATION
            )
            return
        }
        try {
            val providers = locationManager.getProviders(true)
            val provider = when {
                providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                gpsText.text = "LOCALIZZAZIONE DISATTIVATA"
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.requestLocationUpdates(provider, 800L, 0f, this, Looper.getMainLooper())
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestLocationUpdates(provider, 800L, 0f, this)
            }
            gpsUpdatesRequested = true
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null) onLocationChanged(last)
            else gpsText.text = "RICERCA GPS REALE…"
        } catch (_: SecurityException) {
            gpsText.text = "PERMESSO GPS NEGATO"
        } catch (_: Exception) {
            gpsText.text = "GPS NON DISPONIBILE"
        }
    }

    override fun onLocationChanged(location: Location) {
        val ageMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0)
        if (ageMs > 10000 || !location.hasAccuracy() || location.accuracy > 50f) {
            gpsText.text = "GPS INSUFFICIENTE"
            return
        }
        currentLocation = Location(location)
        val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
        speedText.text = if (location.hasSpeed()) speed.toInt().toString() else "—"
        speedText.setTextColor(when {
            speed >= 120f -> Color.RED
            speed >= 50f -> yellow
            else -> if (negativeTheme) white else positiveText
        })
        gpsText.text = "GPS OK  •  ±" + location.accuracy.toInt() + " m"
        gpsText.setTextColor(if (negativeTheme) yellow else Color.rgb(130, 85, 0))
        updateMapForLocation(location, lastCameraLocation == null)
        refreshRadarsIfNeeded(location)
    }

    private fun refreshRadarsIfNeeded(location: Location) {
        val center = NavPoint(location.latitude, location.longitude)
        radarCameras = RadarCache.loadNear(this, center)
        drawRadars()
        val now = System.currentTimeMillis()
        if (now - lastRadarAttemptMs < 15L * 60L * 1000L) {
            updateRadarLabel()
            return
        }
        if (!RadarCache.shouldRefresh(this, center, now)) {
            updateRadarLabel()
            return
        }
        lastRadarAttemptMs = now
        radarText.text = if (radarCameras.isEmpty()) "RADAR OSM • aggiornamento dati…" else "RADAR OSM: " + radarCameras.size + " • aggiornamento…"
        Thread({
            try {
                val fresh = RadarService.fetchNear(center)
                runOnUiThread {
                    radarCameras = fresh
                    RadarCache.save(this, center, fresh)
                    drawRadars()
                    updateRadarLabel()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    updateRadarLabel()
                    if (radarCameras.isEmpty()) radarText.text = "RADAR OSM • server non disponibile"
                }
            }
        }, "osm-radar-refresh").start()
    }

    private fun updateRadarLabel() {
        if (!::radarText.isInitialized) return
        radarText.text = if (radarCameras.isEmpty()) {
            "RADAR OSM • nessun dato disponibile"
        } else {
            "RADAR OSM: " + radarCameras.size + " • dati comunitari"
        }
        radarText.setTextColor(Color.rgb(255, 70, 55))
    }

    private fun drawRadars() {
        if (!mapStyleReady) return
        val source = map?.style?.getSource(SOURCE_RADARS) as? GeoJsonSource ?: return
        val features = radarCameras.map { camera ->
            Feature.fromGeometry(Point.fromLngLat(camera.point.longitude, camera.point.latitude))
        }
        source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    private fun updateMapForLocation(location: Location, forceCenter: Boolean) {
        if (!mapStyleReady) return
        val point = Point.fromLngLat(location.longitude, location.latitude)
        (map?.style?.getSource(SOURCE_USER) as? GeoJsonSource)?.setGeoJson(Feature.fromGeometry(point))
        val previous = lastCameraLocation
        val movedEnough = previous == null || previous.distanceTo(location) >= 8f
        if ((forceCenter || movedEnough) && (followGps || forceCenter)) {
            val oldBearing = map?.cameraPosition?.bearing ?: 0.0
            val bearing = if (location.hasBearing() && location.speed >= 3f) location.bearing.toDouble() else oldBearing
            val camera = CameraPosition.Builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (routeResult == null) 16.0 else 16.5)
                .bearing(bearing)
                .build()
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 650)
            lastCameraLocation = Location(location)
        }
    }

    override fun onProviderDisabled(provider: String) {
        gpsText.text = "LOCALIZZAZIONE DISATTIVATA"
    }

    override fun onProviderEnabled(provider: String) {
        gpsText.text = "RICERCA GPS REALE…"
        gpsUpdatesRequested = false
        requestGps()
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_LOCATION) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestGps()
            else {
                gpsText.text = "PERMESSO GPS NEGATO"
                Toast.makeText(this, "Concedi la localizzazione per usare GPS e navigazione.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        mapView?.onResume()
        if (dashboardStarted) requestGps()
    }

    override fun onPause() {
        activityResumed = false
        if (gpsUpdatesRequested) {
            locationManager.removeUpdates(this)
            gpsUpdatesRequested = false
        }
        mapView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        activityStarted = false
        mapView?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("dashboard_started", dashboardStarted)
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        splashHandler.removeCallbacksAndMessages(null)
        mapView?.onDestroy()
        mapView = null
        super.onDestroy()
    }

    private fun params(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
