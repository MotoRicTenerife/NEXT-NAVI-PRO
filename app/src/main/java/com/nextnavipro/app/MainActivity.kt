package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.speech.tts.TextToSpeech
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
    private lateinit var maneuverIcon: TextView
    private lateinit var radarText: TextView
    private lateinit var destinationInput: EditText
    private lateinit var searchButton: Button
    private lateinit var routeButton: Button
    private lateinit var modeButton: Button
    private lateinit var themeButton: Button
    private lateinit var limitValue: TextView
    private var mapView: MapView? = null
    private var mapOriginalParent: android.view.ViewGroup? = null
    private var mapOriginalIndex: Int = -1
    private var mapOriginalParams: android.view.ViewGroup.LayoutParams? = null
    private var fullScreenRoot: LinearLayout? = null
    private var fullScreenMap = false
    private var selectionMode = false
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
    private var voiceGuide: TextToSpeech? = null
    private var voiceGuideReady = false
    private var announcedManeuverIndex = -1
    private var announcedStage = 0
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
    private val positiveBackground = Color.BLACK
    private val positiveText = Color.WHITE
    private val negativeBackground = Color.rgb(220, 224, 227)
    private val inactiveDigitColor = Color.rgb(105, 112, 118)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        voiceGuide = TextToSpeech(this) { status ->
            voiceGuideReady = status == TextToSpeech.SUCCESS &&
                (voiceGuide?.setLanguage(Locale.ITALIAN) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        }
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
        val logo = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.next_navi_logo)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "NEXT NAVI PRO"
        }
        splash.addView(logo, LinearLayout.LayoutParams(-1, 0, 1f))
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
        logo.startAnimation(android.view.animation.AlphaAnimation(0f, 1f).apply {
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
            textSize = 12f
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
        val historyButton = makeButton("RECENTI") { showSearchHistory() }
        searchRow.addView(historyButton, LinearLayout.LayoutParams(dp(78), dp(46)).apply {
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
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
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
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(2), dp(3), dp(3))
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
        }
        infoColumn.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
        radarText = TextView(this).apply {
            text = "RADAR OSM"
            textSize = 10f
            gravity = Gravity.CENTER
        }
        infoColumn.addView(radarText, params(-1, dp(36)))

        maneuverIcon = TextView(this).apply {
            text = "↑"
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (negativeTheme) Color.rgb(25, 30, 34) else white)
        }

        val speedColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
        }
        speedText = TextView(this).apply {
            text = "000"
            textSize = 108f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        speedText.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(150)))
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 13f
            letterSpacing = 0.03f
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        speedColumn.addView(unit, params(-1, dp(46)))
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
        val mapStatus = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        gpsText.textSize = 9f
        gpsText.maxLines = 1
        gpsText.setPadding(dp(2), 0, dp(2), 0)
        radarText.textSize = 9f
        radarText.maxLines = 1
        radarText.setPadding(dp(2), 0, dp(2), 0)
        infoColumn.removeView(gpsText)
        infoColumn.removeView(radarText)
        mapStatus.addView(gpsText, LinearLayout.LayoutParams(0, dp(22), 1f))
        mapStatus.addView(radarText, LinearLayout.LayoutParams(0, dp(22), 1f))
        mapColumn.addView(mapStatus, params(-1, dp(22)))
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

        // Move navigation and speed-limit views into their orientation-specific thirds.
        listOf(brand, limitTitle, limitValue, gpsText, routeText, radarText).forEach { infoColumn.removeView(it) }
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            content.orientation = LinearLayout.VERTICAL
            val upper = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val navHalf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            navHalf.addView(maneuverIcon, params(-1, dp(42)))
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            limitHalf.addView(limitTitle, params(-1, dp(24)))
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(-1, 0, 1f))
            upper.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            upper.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(upper, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(speedColumn, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 1f))
            speedText.textSize = 104f
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val navHalf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            limitHalf.addView(limitTitle, params(-1, dp(24)))
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(-1, 0, 1f))
            left.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            left.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(speedColumn, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 1f))
        }
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
        updateSpeedDisplay(currentLocation?.let { if (it.hasSpeed()) (it.speed * 3.6f).toInt() else null })
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
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.addOnMapClickListener { point ->
                if (fullScreenMap && selectionMode) {
                    val picked = NavPoint(point.latitude, point.longitude)
                    showPickedPointDialog(picked)
                    true
                } else {
                    enterMapFullScreen()
                    true
                }
            }
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
        val styleUrl = if (negativeTheme) STYLE_URL else "https://tiles.openfreemap.org/styles/positron"
        readyMap.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            if (!negativeTheme) {
                style.layers.forEach { layer ->
                    when (layer) {
                        is SymbolLayer -> layer.setProperties(
                            PropertyFactory.textColor("#111111"),
                            PropertyFactory.textHaloColor("#FFFFFF"),
                            PropertyFactory.textHaloWidth(1.2f)
                        )
                        is LineLayer -> layer.setProperties(PropertyFactory.lineColor("#111111"))
                    }
                }
            }
            mapStyleReady = true
            style.addSource(GeoJsonSource(SOURCE_USER, """{"type":"FeatureCollection","features":[]}"""))
            // A high-contrast arrow stays upright on screen while the map rotates with travel heading.
            style.addLayer(SymbolLayer("next-user-arrow", SOURCE_USER).withProperties(
                PropertyFactory.textField("▲"),
                PropertyFactory.textColor("#FFD400"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(2.5f),
                PropertyFactory.textSize(25f),
                PropertyFactory.textRotationAlignment("viewport")
            ))
            style.addSource(GeoJsonSource(SOURCE_ROUTE, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(LineLayer("next-route-line", SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(if (negativeTheme) "#FFD400" else "#00D6C9"),
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

    private fun updateSpeedDisplay(speed: Int?) {
        val value = (speed ?: 0).coerceIn(0, 999)
        val digits = String.format(Locale.US, "%03d", value)
        val styled = SpannableString(digits)
        val activeColor = if (negativeTheme) Color.rgb(25, 30, 34) else white
        val leadingZeros = if (speed == null) 3 else digits.indexOfFirst { it != '0' }.let { if (it < 0) 2 else it }
        for (i in 0 until leadingZeros) {
            styled.setSpan(ForegroundColorSpan(inactiveDigitColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (i in leadingZeros until digits.length) {
            styled.setSpan(ForegroundColorSpan(activeColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        speedText.text = styled
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
        root.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        val panelBackground = if (negativeTheme) negativeBackground else positiveBackground
        limitValue.setBackgroundColor(panelBackground)
        routeText.setBackgroundColor(panelBackground)
        speedText.setBackgroundColor(panelBackground)
        val textColor = if (negativeTheme) white else positiveText
        root.walkViews().forEach { view ->
            if (view is TextView && view !== destinationInput) {
                view.setTextColor(when {
                    view === speedText -> textColor
                    view === gpsText -> if (negativeTheme) yellow else Color.rgb(130, 85, 0)
                    view === routeText -> if (negativeTheme) Color.rgb(0, 85, 105) else cyan
                    view === radarText -> Color.rgb(255, 70, 55)
                    view.text.toString().contains("NEXT NAVI PRO") -> yellow
                    else -> textColor
                })
            }
            if (view is Button) {
                view.setTextColor(textColor)
                view.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(35, 35, 35))
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
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(destinationInput.windowToken, 0)
        destinationInput.clearFocus()
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
                val origin = currentLocation
                val sortedResults = if (origin != null) results.sortedBy { result ->
                    val distance = FloatArray(1)
                    Location.distanceBetween(origin.latitude, origin.longitude, result.point.latitude, result.point.longitude, distance)
                    distance[0]
                } else results
                runOnUiThread {
                    isSearching = false
                    searchButton.isEnabled = true
                    if (sortedResults.isEmpty()) {
                        routeText.text = "NESSUN RISULTATO • Prova con nome località o strada"
                        return@runOnUiThread
                    }
                    rememberSearch(query)
                    val labels = sortedResults.map {
                        if (it.description.isBlank()) it.name else it.name + "\n" + it.description
                    }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Scegli destinazione")
                        .setItems(labels) { _, which ->
                            destination = sortedResults[which]
                            routeResult = null
                            val routeSource = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
                            routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
                            destinationInput.setText(sortedResults[which].name)
                            routeText.text = "DESTINAZIONE: " + sortedResults[which].name + "\nPremi AVVIA PERCORSO"
                            routeButton.text = "AVVIA PERCORSO"
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(sortedResults[which].point.latitude, sortedResults[which].point.longitude), 14.0
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
                    announcedManeuverIndex = -1
                    announcedStage = 0
                    drawRoute(result, true)
                    updateTurnGuidance(currentLocation)
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
        announcedManeuverIndex = -1
        announcedStage = 0
        if (::maneuverIcon.isInitialized) maneuverIcon.text = "↑"
        destination = null
        if (::routeText.isInitialized) routeText.text = "MAPPA ONLINE  •  Nessun percorso attivo"
        if (::routeButton.isInitialized) routeButton.text = "AVVIA PERCORSO"
        if (::destinationInput.isInitialized) destinationInput.text.clear()
        val source = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
        source?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
    }

    private fun showSearchHistory() {
        val entries = getSharedPreferences(PREFS, MODE_PRIVATE).getString("search_history", "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
        if (entries.isEmpty()) {
            Toast.makeText(this, "Nessuna ricerca recente.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Ricerche recenti")
            .setItems(entries.toTypedArray()) { _, which ->
                destinationInput.setText(entries[which])
                searchDestination()
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun rememberSearch(query: String) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val previous = prefs.getString("search_history", "").orEmpty().split("\n").filter { it.isNotBlank() }
        val latest = (listOf(query.trim()) + previous.filterNot { it.equals(query.trim(), true) }).take(10)
        prefs.edit().putString("search_history", latest.joinToString("\n")).apply()
    }

    private fun enterMapFullScreen() {
        if (fullScreenMap) return
        val view = mapView ?: return
        val parent = view.parent as? android.view.ViewGroup ?: return
        mapOriginalParent = parent
        mapOriginalIndex = parent.indexOfChild(view)
        mapOriginalParams = view.layoutParams
        parent.removeView(view)
        fullScreenMap = true
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(black) }
        fullScreenRoot = page
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(makeButton("CRUSCOTTO") { exitMapFullScreen() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(makeButton("LUOGHI") { showSavedPois() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val flagButton = makeButton(if (selectionMode) "FLAG ATTIVO" else "SELEZIONA PUNTI") { }
        flagButton.setOnClickListener {
            selectionMode = !selectionMode
            flagButton.text = if (selectionMode) "FLAG ATTIVO" else "SELEZIONA PUNTI"
        }
        bar.addView(flagButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(bar, params(-1, dp(48)))
        page.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun exitMapFullScreen() {
        val view = mapView ?: return
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        val parent = mapOriginalParent
        if (parent != null) {
            parent.addView(view, mapOriginalIndex.coerceIn(0, parent.childCount), mapOriginalParams)
        }
        fullScreenMap = false
        fullScreenRoot = null
        setContentView(root)
    }

    private fun showSavedPois() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).all
            .filterKeys { it.startsWith("poi_") }
            .mapNotNull { (key, value) ->
                val coords = (value as? String)?.split(",") ?: return@mapNotNull null
                val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                Triple(key.removePrefix("poi_"), lat, lon)
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
        if (saved.isEmpty()) {
            Toast.makeText(this, "Non hai ancora salvato luoghi di interesse.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Luoghi salvati")
            .setItems(saved.map { it.first }.toTypedArray()) { _, which ->
                val poi = saved[which]
                destination = PlaceResult(poi.first, NavPoint(poi.second, poi.third), "Luogo salvato")
                destinationInput.setText(poi.first)
                routeText.text = "DESTINAZIONE: " + poi.first + "\\nPremi AVVIA PERCORSO"
                exitMapFullScreen()
                calculateSelectedRoute()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showPickedPointDialog(point: NavPoint) {
        AlertDialog.Builder(this).setTitle("Punto sulla mappa")
            .setItems(arrayOf("Usa come destinazione", "Salva come luogo di interesse")) { _, which ->
                if (which == 0) {
                    destination = PlaceResult("Punto sulla mappa", point, "Punto selezionato sulla mappa")
                    destinationInput.setText("Punto sulla mappa")
                    exitMapFullScreen()
                    calculateSelectedRoute()
                } else {
                    val input = EditText(this).apply { hint = "Nome del luogo" }
                    AlertDialog.Builder(this).setTitle("Salva luogo di interesse").setView(input)
                        .setPositiveButton("Salva") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotBlank()) {
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString("poi_" + name, point.latitude.toString() + "," + point.longitude).apply()
                                Toast.makeText(this, "Luogo salvato: " + name, Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton("Annulla", null).show()
                }
            }.setNegativeButton("Annulla", null).show()
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
        updateSpeedDisplay(if (location.hasSpeed()) speed.toInt() else null)
        gpsText.text = "GPS OK  •  ±" + location.accuracy.toInt() + " m"
        gpsText.setTextColor(if (negativeTheme) yellow else Color.rgb(130, 85, 0))
        updateMapForLocation(location, lastCameraLocation == null)
        updateTurnGuidance(location)
        refreshRadarsIfNeeded(location)
    }

    private fun updateTurnGuidance(location: Location?) {
        val route = routeResult ?: return
        val here = location ?: return
        if (route.points.size < 2 || route.maneuvers.isEmpty()) return
        var nearestIndex = 0
        var nearestMeters = Float.MAX_VALUE
        for (i in route.points.indices) {
            val point = route.points[i]
            val distance = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, point.latitude, point.longitude, distance)
            if (distance[0] < nearestMeters) {
                nearestMeters = distance[0]
                nearestIndex = i
            }
        }
        val nextPair = route.maneuvers.withIndex().firstOrNull { (_, maneuver) ->
            maneuver.pointIndex > nearestIndex + 2
        } ?: route.maneuvers.withIndex().lastOrNull()
        if (nextPair == null) return
        val maneuverIndex = nextPair.index
        val maneuver = nextPair.value
        var distanceToTurn = 0.0
        val to = maneuver.pointIndex.coerceAtLeast(nearestIndex).coerceAtMost(route.points.lastIndex)
        for (i in nearestIndex.coerceAtMost(to) until to) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val segment = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, segment)
            distanceToTurn += segment[0]
        }
        val instruction = maneuver.instruction
        val lower = instruction.lowercase(Locale.ITALIAN)
        val roundabout = lower.contains("rotonda") || lower.contains("rotatoria") ||
            lower.contains("uscita") || maneuver.type == 26 || maneuver.type == 27
        val icon = when {
            roundabout -> "↻"
            lower.contains("sinistra") -> "↰"
            lower.contains("destra") -> "↱"
            lower.contains("inversione") || lower.contains("u-turn") -> "↶"
            else -> "↑"
        }
        if (::maneuverIcon.isInitialized) {
            maneuverIcon.text = icon
            maneuverIcon.setTextColor(if (negativeTheme) Color.rgb(25, 30, 34) else white)
        }
        val shortInstruction = if (roundabout && !lower.contains("rotonda") && !lower.contains("rotatoria")) {
            "Rotonda: " + instruction
        } else instruction
        if (::routeText.isInitialized) {
            val distanceLabel = when {
                distanceToTurn < 10.0 -> "ORA"
                distanceToTurn < 100.0 -> distanceToTurn.toInt().toString() + " m"
                else -> ((distanceToTurn / 10.0).toInt() * 10).toString() + " m"
            }
            routeText.text = shortInstruction + "\n" + distanceLabel
        }
        val speedMps = if (here.hasSpeed()) here.speed.coerceAtLeast(0f) else 0f
        val earlyThreshold = (speedMps * 18f).coerceIn(150f, 500f)
        val mediumThreshold = (speedMps * 7f).coerceIn(60f, 180f)
        val stage = when {
            distanceToTurn <= 10.0 -> 4
            distanceToTurn <= 50.0 -> 3
            distanceToTurn <= mediumThreshold -> 2
            distanceToTurn <= earlyThreshold -> 1
            else -> 0
        }
        if (maneuverIndex != announcedManeuverIndex) {
            announcedManeuverIndex = maneuverIndex
            announcedStage = 0
        }
        if (stage > announcedStage) {
            announcedStage = stage
            val spoken = when (stage) {
                1 -> "Tra poco, " + instruction
                2 -> "Tra circa " + distanceToTurn.toInt() + " metri, " + instruction
                3 -> "Tra cinquanta metri, " + instruction
                4 -> instruction
                else -> ""
            }
            if (spoken.isNotBlank() && voiceGuideReady) {
                voiceGuide?.speak(spoken, TextToSpeech.QUEUE_ADD, null, "turn-" + maneuverIndex + "-stage-" + stage)
            }
        }
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
        voiceGuide?.stop()
        voiceGuide?.shutdown()
        voiceGuide = null
        super.onDestroy()
    }

    private fun params(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
