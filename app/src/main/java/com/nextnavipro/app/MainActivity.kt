package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.TextPaint
import kotlin.math.roundToInt
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
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
import org.maplibre.android.style.expressions.Expression
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), LocationListener, SensorEventListener {
    companion object {
        private const val PREFS = "next_navi_settings"
        private const val PREF_NEGATIVE = "negative_theme"
        private const val PREF_THEME_MODE = "theme_mode"
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val PERMISSION_LOCATION = 100
        private const val SOURCE_USER = "next-user-position"
        private const val SOURCE_SAVED_POIS = "next-saved-pois"
        private const val SOURCE_DESTINATION = "next-destination"
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
    private lateinit var themeButton: Button
    private lateinit var menuButton: Button
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var themeMode: String = "negative"
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
    private var gpsMarkerAnimator: ValueAnimator? = null
    private var displayedMarkerLocation: Location? = null
    private var dashboardStarted = false
    private var activityStarted = false
    private var activityResumed = false
    private var gpsUpdatesRequested = false
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
    private var lastSpeedLimitQueryLocation: Location? = null
    private var lastSpeedLimitQueryMs = 0L
    private var currentSpeedLimit: Int? = null
    private var currentRoadMatch: RoadMatch? = null
    private var lastDisplayedSpeed: Int? = null
    private var speedAlertActive = false
    private var speedAlertFlashRed = false
    private var speedAlertFlashesRemaining = 0
    private val speedAlertHandler = Handler(Looper.getMainLooper())
    private val speedAlertRunnable = object : Runnable {
        override fun run() {
            if (!isMotorwaySpeeding()) {
                speedAlertActive = false
                speedAlertFlashRed = false
                speedAlertFlashesRemaining = 0
                renderSpeedDigits(lastDisplayedSpeed)
                return
            }
            if (speedAlertFlashesRemaining == 0) {
                speedAlertFlashesRemaining = 6
                speedAlertFlashRed = true
            }
            renderSpeedDigits(lastDisplayedSpeed)
            speedAlertFlashesRemaining--
            if (speedAlertFlashesRemaining > 0) {
                speedAlertFlashRed = !speedAlertFlashRed
                speedAlertHandler.postDelayed(this, 250L)
            } else {
                speedAlertFlashRed = false
                renderSpeedDigits(lastDisplayedSpeed)
                speedAlertHandler.postDelayed(this, 3500L)
            }
        }
    }
    private var lastRadarAlertId: Long = -1L
    private var lastRadarAlertMs = 0L
    private var lastRerouteAttemptMs = 0L
    private var offRouteFixes = 0
    private var lastRawGpsLocation: Location? = null
    private var smoothedGpsLocation: Location? = null
    private val mapFollowHandler = Handler(Looper.getMainLooper())
    private val mapFollowRunnable = object : Runnable {
        override fun run() {
            if (dashboardStarted && activityResumed && fullScreenMap && followGps) {
                currentLocation?.let { updateMapForLocation(it, true) }
            }
            mapFollowHandler.postDelayed(this, 10_000L)
        }
    }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!dashboardStarted) return

        // Keep route, destination and latest GPS fix in this Activity while rotating.
        // Recreate only the view tree and MapView so portrait/landscape layouts are rebuilt.
        gpsMarkerAnimator?.cancel()
        gpsMarkerAnimator = null
        displayedMarkerLocation = null
        val oldMapView = mapView
        try {
            oldMapView?.onPause()
            oldMapView?.onStop()
            oldMapView?.onDestroy()
        } catch (_: Exception) {
            // A partially initialized map must not prevent navigation from being restored.
        }
        mapView = null
        map = null
        mapStyleReady = false
        fullScreenMap = false
        fullScreenRoot = null
        mapOriginalParent = null
        mapOriginalIndex = -1
        mapOriginalParams = null
        lastCameraLocation = null
        buildDashboard(null)
        currentLocation?.let { updateMapForLocation(it, true) }
        routeResult?.let { drawRoute(it, false); updateTurnGuidance(currentLocation ?: return) }
        if (activityResumed) requestGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        voiceGuide = TextToSpeech(this) { status ->
            voiceGuideReady = status == TextToSpeech.SUCCESS &&
                (voiceGuide?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        }
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        themeMode = preferences.getString(PREF_THEME_MODE, if (preferences.getBoolean(PREF_NEGATIVE, true)) "negative" else "positive") ?: "negative"
        negativeTheme = when (themeMode) { "positive" -> false; "negative" -> true; else -> preferences.getBoolean(PREF_NEGATIVE, true) }
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
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
        menuButton = makeButton("MENU") { showMainMenu() }
        searchRow.addView(menuButton, LinearLayout.LayoutParams(dp(76), dp(46)).apply {
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
            // Oversized, heavy navigation symbol; the container below is enlarged too
            // so the glyph is not clipped on portrait screens.
            textSize = 160f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(if (negativeTheme) Color.rgb(25, 30, 34) else white)
        }
        routeText.apply {
            textSize = 18f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 3
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        val speedColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), 0, dp(2), 0)
        }
        speedText = TextView(this).apply {
            text = "000"
            textSize = 132f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        speedText.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        speedColumn.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(184)))
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
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
        // The route button is placed in the lower control row in landscape and
        // between the speed display and map in portrait, so it never covers the map.

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
        root.addView(mapStatus, params(-1, dp(22)))
        val newMapView = MapView(this)
        mapView = newMapView
        newMapView.onCreate(savedState)
        mapColumn.addView(newMapView, LinearLayout.LayoutParams(-1, 0, 1f))
        val recenterButton = makeButton("CENTRA GPS") {
            followGps = true
            lastCameraLocation = null
            currentLocation?.let { updateMapForLocation(it, true) }
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mapColumn.addView(recenterButton, params(-1, dp(36)))
        }

        // Keep the map in the dashboard in both orientations. The speedometer stays
        // in the central column/band; the map is the right-hand (landscape) or lower
        // (portrait) pane rather than being used to position the speedometer.
        listOf(brand, limitTitle, limitValue, gpsText, routeText, radarText).forEach { infoColumn.removeView(it) }
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            content.orientation = LinearLayout.VERTICAL
            val upper = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val navHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            maneuverIcon.textSize = 104f
            navHalf.addView(maneuverIcon, params(-1, dp(112)))
            routeText.translationY = 0f
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitHalf.addView(limitTitle, params(-1, dp(24)))
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(-1, 0, 1f))
            upper.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            upper.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.85f))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(-1, 0, 1.15f))
            content.addView(routeButton, params(-1, dp(44)))
            content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val navHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            navHalf.addView(View(this), LinearLayout.LayoutParams(-1, 0, 0.2f))
            maneuverIcon.textSize = 132f
            navHalf.addView(maneuverIcon, params(-1, dp(132)))
            routeText.translationY = 0f
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitHalf.addView(limitTitle, params(-1, dp(24)))
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(-1, 0, 1f))
            left.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            left.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(0, -1, 1.2f))
            content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        themeButton = makeButton(when (themeMode) { "auto" -> "AUTO LUCE"; "positive" -> "POSITIVO"; else -> "NEGATIVO" }) { toggleTheme() }
        val navigatorButton = makeButton("NAVIGATORE") { enterMapFullScreen() }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controls.addView(themeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(1))
            })
            controls.addView(navigatorButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(1))
            })
            controls.addView(routeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(1))
            })
        } else {
            controls.addView(themeButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(1))
            })
            controls.addView(navigatorButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(1))
            })
        }
        root.addView(controls, params(-1, dp(48)))

        setContentView(root)
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        mapFollowHandler.postDelayed(mapFollowRunnable, 10_000L)
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
                    // Let the user inspect the map, then resume GPS-follow after ten seconds.
                    followGps = false
                    mapFollowHandler.removeCallbacks(mapFollowRunnable)
                    mapFollowHandler.postDelayed({
                        followGps = true
                        currentLocation?.let { updateMapForLocation(it, true) }
                        mapFollowHandler.removeCallbacks(mapFollowRunnable)
                        mapFollowHandler.postDelayed(mapFollowRunnable, 10_000L)
                    }, 10_000L)
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
            style.addImage("next-user-arrow-icon", createGpsArrowBitmap())
            style.addImage("next-place-flag-icon", createPlaceFlagBitmap())
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
            // GPS position is represented by the bitmap arrow alone; no dot underneath.
            style.addSource(GeoJsonSource(SOURCE_USER, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(SymbolLayer("next-user-arrow", SOURCE_USER).withProperties(
                PropertyFactory.iconImage("next-user-arrow-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment("map"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            ))
            // Saved places and destination use a native bitmap flag, not text glyphs or circles.
            style.addSource(GeoJsonSource(SOURCE_SAVED_POIS, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(SymbolLayer("next-saved-poi-flags", SOURCE_SAVED_POIS).withProperties(
                PropertyFactory.iconImage("next-place-flag-icon"),
                PropertyFactory.iconSize(0.85f),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            ))
            style.addSource(GeoJsonSource(SOURCE_DESTINATION, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(SymbolLayer("next-destination-flag", SOURCE_DESTINATION).withProperties(
                PropertyFactory.iconImage("next-place-flag-icon"),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            ))
            refreshSavedPoiMarkers()
            refreshDestinationMarker()
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
        lastDisplayedSpeed = speed
        renderSpeedDigits(speed)
        updateSpeedGaugeBorder(speed)
        updateSpeedingAlertState()
    }

    private fun renderSpeedDigits(speed: Int?) {
        if (!::speedText.isInitialized) return
        val value = (speed ?: 0).coerceIn(0, 999)
        val digits = String.format(Locale.US, "%03d", value)
        val styled = SpannableString(digits)
        val activeColor = if (speedAlertFlashRed) Color.rgb(255, 55, 45)
            else if (negativeTheme) Color.rgb(25, 30, 34) else white
        val leadingZeros = if (speed == null) 3 else digits.indexOfFirst { it != '0' }.let { if (it < 0) 2 else it }
        if (speedAlertFlashRed) {
            for (i in digits.indices) {
                styled.setSpan(ForegroundColorSpan(activeColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            for (i in 0 until leadingZeros) {
                styled.setSpan(ForegroundColorSpan(inactiveDigitColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            for (i in leadingZeros until digits.length) {
                styled.setSpan(ForegroundColorSpan(activeColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val outlineColor = speedGaugeColor(speed)
        for (i in digits.indices) {
            styled.setSpan(SpeedDigitOutlineSpan(outlineColor, dp(2).toFloat()), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        speedText.text = styled
    }

    private fun speedGaugeColor(speed: Int?): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val greenMax = prefs.getInt("speed_green_max", 50).coerceIn(0, 250)
        val yellowMax = prefs.getInt("speed_yellow_max", 120).coerceIn(greenMax + 1, 300)
        return when {
            speed == null -> Color.GRAY
            speed <= greenMax -> Color.rgb(0, 205, 90)
            speed <= yellowMax -> Color.rgb(255, 190, 0)
            else -> Color.rgb(255, 55, 45)
        }
    }

    private class SpeedDigitOutlineSpan(
        private val outlineColor: Int,
        private val outlineWidth: Float
    ) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            paint.measureText(text, start, end).roundToInt()

        override fun draw(
            canvas: Canvas, text: CharSequence, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            val glyph = text.subSequence(start, end).toString()
            val outline = TextPaint(paint).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeWidth = outlineWidth
                color = outlineColor
            }
            canvas.drawText(glyph, x, y, outline)
            canvas.drawText(glyph, x, y, paint)
        }
    }

    private fun updateSpeedGaugeBorder(speed: Int?) {
        if (!::speedText.isInitialized) return
        // The gauge color outlines the glyphs themselves; no rectangular frame.
        speedText.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        renderSpeedDigits(speed)
    }

    private fun isMotorwaySpeeding(): Boolean {
        val road = currentRoadMatch ?: return false
        if (road.highwayType != "motorway") return false
        val location = currentLocation ?: return false
        if (!location.hasSpeed()) return false
        // A mapped legal limit takes priority. If missing, this is only the user's
        // configurable alarm threshold; the legal-limit display remains unknown.
        val threshold = currentSpeedLimit
            ?: getSharedPreferences(PREFS, MODE_PRIVATE).getInt("motorway_alert_speed", 120)
        return location.speed * 3.6f > threshold + 3f
    }

    private fun updateSpeedingAlertState() {
        if (isMotorwaySpeeding()) {
            if (!speedAlertActive) {
                speedAlertActive = true
                speedAlertFlashesRemaining = 0
                speedAlertFlashRed = false
                speedAlertHandler.removeCallbacks(speedAlertRunnable)
                speedAlertHandler.post(speedAlertRunnable)
            }
        } else if (speedAlertActive || speedAlertFlashRed) {
            speedAlertHandler.removeCallbacks(speedAlertRunnable)
            speedAlertActive = false
            speedAlertFlashRed = false
            speedAlertFlashesRemaining = 0
            renderSpeedDigits(lastDisplayedSpeed)
        }
    }

    private fun showSpeedSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val greenInput = EditText(this).apply {
            hint = "Soglia verde fino a (km/h)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("speed_green_max", 50).toString())
        }
        val yellowInput = EditText(this).apply {
            hint = "Soglia gialla fino a (km/h)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("speed_yellow_max", 120).toString())
        }
        val motorwayInput = EditText(this).apply {
            hint = "Allarme autostrada se limite ignoto (km/h)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("motorway_alert_speed", 120).toString())
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(greenInput, params(-1, dp(52)))
            addView(yellowInput, params(-1, dp(52)))
            addView(motorwayInput, params(-1, dp(52)))
        }
        AlertDialog.Builder(this)
            .setTitle("Soglie tachimetro e avvisi")
            .setView(fields)
            .setPositiveButton("Salva") { _, _ ->
                val green = greenInput.text.toString().toIntOrNull()
                val yellow = yellowInput.text.toString().toIntOrNull()
                val motorway = motorwayInput.text.toString().toIntOrNull()
                if (green == null || yellow == null || motorway == null ||
                    green !in 0..250 || yellow !in (green + 1)..300 || motorway !in 30..250) {
                    Toast.makeText(this, "Valori non validi: verde 0–250, giallo maggiore del verde e allarme 30–250.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putInt("speed_green_max", green)
                    .putInt("speed_yellow_max", yellow)
                    .putInt("motorway_alert_speed", motorway)
                    .apply()
                updateSpeedGaugeBorder(lastDisplayedSpeed)
                updateSpeedingAlertState()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun toggleTheme() {
        val modes = arrayOf("Automatico (sensore lux)", "Positivo", "Negativo")
        val values = arrayOf("auto", "positive", "negative")
        AlertDialog.Builder(this).setTitle("Modalità schermo")
            .setSingleChoiceItems(modes, values.indexOf(themeMode).coerceAtLeast(0)) { dialog, which ->
                setThemeMode(values[which])
                dialog.dismiss()
            }.setNegativeButton("Annulla", null).show()
    }

    private fun setThemeMode(mode: String) {
        themeMode = mode
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_THEME_MODE, mode).apply()
        when (mode) {
            "positive" -> applyThemeMode(false)
            "negative" -> applyThemeMode(true)
            else -> {
                val sensor = lightSensor
                if (sensor != null) sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                else Toast.makeText(this, "Sensore lux non disponibile: mantengo la modalità corrente.", Toast.LENGTH_LONG).show()
            }
        }
        if (::themeButton.isInitialized) themeButton.text = when (mode) { "auto" -> "AUTO LUCE"; "positive" -> "NEGATIVO"; else -> "POSITIVO" }
    }

    private fun applyThemeMode(isNegative: Boolean) {
        if (negativeTheme == isNegative) return
        negativeTheme = isNegative
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_NEGATIVE, negativeTheme).apply()
        applyTheme()
        map?.let { applyMapStyle(it) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (themeMode != "auto" || event.sensor.type != Sensor.TYPE_LIGHT || event.values.isEmpty()) return
        val lux = event.values[0]
        // Bright sunlight: light panel with dark text; ordinary/dim light: dark panel.
        if (lux >= 6000f && negativeTheme) applyThemeMode(false)
        else if (lux < 6000f && !negativeTheme) applyThemeMode(true)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun showMainMenu() {
        val choices = arrayOf("Indirizzi preferiti", "Salva destinazione come preferita", "Elimina preferiti", "Prova voce navigatore", "Modalità schermo", "Ricerche recenti", "Soglie tachimetro e avvisi")
        AlertDialog.Builder(this).setTitle("NEXT NAVI PRO • Menu")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> showFavoriteAddresses()
                    1 -> saveFavoriteAddress()
                    2 -> deleteFavoriteAddress()
                    3 -> testNavigationVoice()
                    4 -> toggleTheme()
                    5 -> showSearchHistory()
                    6 -> showSpeedSettings()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun saveFavoriteAddress() {
        val selected = destination
        if (selected == null) {
            Toast.makeText(this, "Cerca prima un indirizzo da salvare.", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply { setText(selected.name); hint = "Nome preferito" }
        AlertDialog.Builder(this).setTitle("Salva indirizzo preferito").setView(input)
            .setPositiveButton("Salva") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("favorite_" + name, selected.point.latitude.toString() + "," + selected.point.longitude)
                        .apply()
                    Toast.makeText(this, "Preferito salvato.", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Annulla", null).show()
    }

    private fun readFavoriteAddresses(): List<Triple<String, Double, Double>> =
        getSharedPreferences(PREFS, MODE_PRIVATE).all.filterKeys { it.startsWith("favorite_") }
            .mapNotNull { (key, value) ->
                val coords = (value as? String)?.split(",") ?: return@mapNotNull null
                val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                Triple(key.removePrefix("favorite_"), lat, lon)
            }.sortedBy { it.first.lowercase(Locale.ROOT) }

    private fun showFavoriteAddresses() {
        val favorites = readFavoriteAddresses()
        if (favorites.isEmpty()) {
            Toast.makeText(this, "Nessun indirizzo preferito salvato.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Indirizzi preferiti")
            .setItems(favorites.map { it.first }.toTypedArray()) { _, which ->
                val favorite = favorites[which]
                destination = PlaceResult(favorite.first, NavPoint(favorite.second, favorite.third), "Indirizzo preferito")
                destinationInput.setText(favorite.first)
                routeText.text = "DESTINAZIONE: " + favorite.first + "\nPremi AVVIA PERCORSO"
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun deleteFavoriteAddress() {
        val favorites = readFavoriteAddresses()
        if (favorites.isEmpty()) {
            Toast.makeText(this, "Nessun preferito da eliminare.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Elimina preferito")
            .setItems(favorites.map { it.first }.toTypedArray()) { _, which ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("favorite_" + favorites[which].first).apply()
                Toast.makeText(this, "Preferito eliminato.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Annulla", null).show()
    }

    private fun testNavigationVoice() {
        if (!voiceGuideReady) {
            Toast.makeText(this, "Voce non pronta o lingua TTS non disponibile.", Toast.LENGTH_LONG).show()
            return
        }
        voiceGuide?.speak("Avvio la navigazione. Tra trecento metri, prosegui.", TextToSpeech.QUEUE_FLUSH, null, "voice-test")
    }

    private fun applyTheme() {
        if (!::root.isInitialized) return
        root.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        val panelBackground = if (negativeTheme) negativeBackground else positiveBackground
        limitValue.setBackgroundColor(panelBackground)
        routeText.setBackgroundColor(panelBackground)
        speedText.setBackgroundColor(panelBackground)
        updateSpeedGaugeBorder(lastDisplayedSpeed)
        val textColor = if (negativeTheme) white else positiveText
        root.walkViews().forEach { view ->
            if (view is TextView && view !== destinationInput) {
                view.setTextColor(when {
                    view === speedText -> textColor
                    view === gpsText -> if (listOf("GPS INSUFFICIENTE", "GPS SALTO SCARTATO", "PERMESSO GPS NEGATO", "GPS NON DISPONIBILE", "LOCALIZZAZIONE DISATTIVATA").any { gpsText.text.toString().contains(it) }) Color.rgb(235, 45, 45) else Color.rgb(0, 190, 70)
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
        destinationInput.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(35, 35, 35))
        renderSpeedDigits(lastDisplayedSpeed)
        updateSpeedGaugeBorder(lastDisplayedSpeed)
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
        refreshDestinationMarker()
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
                    origin, target.point, motorcycle = false
                )
                runOnUiThread {
                    isCalculatingRoute = false
                    routeButton.isEnabled = true
                    routeResult = result
                    if (voiceGuideReady) voiceGuide?.speak("Avvio la navigazione", TextToSpeech.QUEUE_FLUSH, null, "navigation-start")
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
        refreshDestinationMarker()
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
                refreshDestinationMarker()
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
                    refreshDestinationMarker()
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
                                refreshSavedPoiMarkers()
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
        // elapsedRealtimeNanos is monotonic and avoids wall-clock changes making a fresh fix look stale.
        val ageMs = if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
        }
        if (ageMs > 10000 || !location.hasAccuracy() || location.accuracy > 50f) {
            gpsText.text = "GPS INSUFFICIENTE"
            return
        }
        val previousRaw = lastRawGpsLocation
        if (previousRaw != null && isImplausibleGpsJump(previousRaw, location)) {
            // Do not promote a rejected fix to the reference point; the next fix is
            // compared with the last accepted raw fix instead of the bad jump.
            gpsText.text = "GPS SALTO SCARTATO"
            return
        }
        lastRawGpsLocation = Location(location)
        val smoothed = smoothLocation(smoothedGpsLocation, location)
        currentLocation = Location(smoothed)
        val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
        updateSpeedDisplay(if (location.hasSpeed()) speed.toInt() else null)
        updateGpsStatus(location)
        animateGpsMarkerTo(smoothed)
        updateMapForLocation(smoothed, lastCameraLocation == null, updateMarker = false)
        updateTurnGuidance(smoothed)
        refreshRadarsIfNeeded(smoothed)
        refreshSpeedLimitIfNeeded(smoothed)
        checkOffRouteAndReroute(smoothed)
        checkRadarProximityAlert(smoothed)
    }

    private fun isImplausibleGpsJump(previous: Location, incoming: Location): Boolean {
        val elapsedDeltaNs = incoming.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        val dtSeconds = if (elapsedDeltaNs > 0L) {
            elapsedDeltaNs / 1_000_000_000f
        } else {
            ((incoming.time - previous.time).coerceAtLeast(1L)) / 1000f
        }.coerceAtLeast(0.001f)
        val previousSpeed = if (previous.hasSpeed()) previous.speed else 0f
        val incomingSpeed = if (incoming.hasSpeed()) incoming.speed else 0f
        val plausibleSpeed = maxOf(previousSpeed, incomingSpeed, 8f)
        val uncertainty = (if (previous.hasAccuracy()) previous.accuracy else 25f) +
            (if (incoming.hasAccuracy()) incoming.accuracy else 25f)
        val maximumPlausibleDistance = (plausibleSpeed * dtSeconds * 2.5f + uncertainty).coerceAtLeast(35f)
        return previous.distanceTo(incoming) > maximumPlausibleDistance
    }

    private fun smoothLocation(previous: Location?, incoming: Location): Location {
        // Accuracy-adaptive low-pass filtering: follow good fixes quickly, damp noisy fixes
        // more strongly, and avoid smoothing across large movements or tight turns.
        val result = Location(incoming)
        if (previous != null && previous.hasAccuracy() && incoming.hasAccuracy()) {
            val jump = previous.distanceTo(incoming)
            if (jump < 35f) {
                val accuracyAlpha = when {
                    incoming.accuracy <= 10f -> 0.72f
                    incoming.accuracy <= 20f -> 0.62f
                    incoming.accuracy <= 35f -> 0.48f
                    else -> 0.35f
                }
                val speed = if (incoming.hasSpeed()) incoming.speed else 0f
                val alpha = when {
                    speed >= 15f -> (accuracyAlpha + 0.15f).coerceAtMost(0.88f)
                    speed < 2.5f -> (accuracyAlpha - 0.15f).coerceAtLeast(0.20f)
                    else -> accuracyAlpha
                }
                result.latitude = previous.latitude + (incoming.latitude - previous.latitude) * alpha
                result.longitude = previous.longitude + (incoming.longitude - previous.longitude) * alpha
            }
        }
        smoothedGpsLocation = Location(result)
        return result
    }

    private fun refreshSpeedLimitIfNeeded(location: Location) {
        val previous = lastSpeedLimitQueryLocation
        val now = System.currentTimeMillis()
        // Public Overpass is rate-limited: refresh at least every 6 s, or sooner
        // if the vehicle has moved over 250 m. Avoid showing a previous road's
        // name/limit while the new road match is being resolved.
        if (previous != null && previous.distanceTo(location) < 250f &&
            now - lastSpeedLimitQueryMs < 6_000L) {
            updateSpeedLimitDisplay()
            return
        }
        if (previous != null && previous.distanceTo(location) > 180f) {
            currentRoadMatch = null
            currentSpeedLimit = null
            updateGpsStatus(location)
            updateSpeedLimitDisplay()
        }
        lastSpeedLimitQueryLocation = Location(location)
        lastSpeedLimitQueryMs = now
        val queryLocation = Location(location)
        val point = NavPoint(queryLocation.latitude, queryLocation.longitude)
        // Ignore bearing while almost stationary: Android may retain a stale course.
        val bearing = queryLocation.bearing.takeIf {
            queryLocation.hasBearing() && queryLocation.hasSpeed() && queryLocation.speed >= 2.5f
        }
        thread(name = "osm-road-match") {
            val match = try { SpeedLimitService.findRoadNear(point, bearing) } catch (_: Exception) { null }
            runOnUiThread {
                // A slow Overpass response must not overwrite a newer road match.
                if (lastSpeedLimitQueryMs != now) return@runOnUiThread
                val latest = currentLocation
                if (latest != null && latest.distanceTo(queryLocation) > 180f) {
                    currentRoadMatch = null
                    currentSpeedLimit = null
                    updateGpsStatus(latest)
                    updateSpeedLimitDisplay()
                    return@runOnUiThread
                }
                currentRoadMatch = match
                currentSpeedLimit = match?.speedLimitKmh
                latest?.let { updateGpsStatus(it) }
                updateSpeedLimitDisplay()
            }
        }
    }

    private fun updateGpsStatus(location: Location) {
        if (!::gpsText.isInitialized) return
        val roadName = currentRoadMatch?.displayName
        gpsText.text = if (!roadName.isNullOrBlank()) {
            roadName
        } else {
            "GPS OK  •  ±" + location.accuracy.toInt() + " m"
        }
        gpsText.setTextColor(Color.rgb(0, 190, 70))
    }

    private fun updateSpeedLimitDisplay() {
        if (!::limitValue.isInitialized) return
        limitValue.text = currentSpeedLimit?.toString() ?: "—"
        if (currentSpeedLimit != null && currentLocation?.hasSpeed() == true &&
            currentLocation!!.speed * 3.6f > currentSpeedLimit!! + 3f) {
            limitValue.setTextColor(Color.rgb(255, 55, 45))
        } else {
            limitValue.setTextColor(if (negativeTheme) white else positiveText)
        }
    }

    private fun checkRadarProximityAlert(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > 25f || !location.hasSpeed() || location.speed < 4f) return
        val camera = radarCameras.mapNotNull { item ->
            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, item.point.latitude, item.point.longitude, distance)
            if (distance[0] <= 650f) item to distance[0] else null
        }.minByOrNull { it.second } ?: return
        val now = System.currentTimeMillis()
        if (camera.first.osmId == lastRadarAlertId && now - lastRadarAlertMs < 90_000L) return
        lastRadarAlertId = camera.first.osmId
        lastRadarAlertMs = now
        if (voiceGuideReady) voiceGuide?.speak("Attenzione, possibile autovelox tra circa " + camera.second.toInt() + " metri", TextToSpeech.QUEUE_ADD, null, "radar-" + camera.first.osmId)
        radarText.text = "⚠ AUTOVELOX • " + camera.second.toInt() + " m"
    }

    private fun checkOffRouteAndReroute(location: Location) {
        val route = routeResult ?: return
        val target = destination ?: return
        if (!location.hasAccuracy() || location.accuracy > 25f) return
        val nearest = route.points.minOfOrNull { point ->
            val d = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, d)
            d[0]
        } ?: return
        if (nearest > maxOf(65f, location.accuracy * 2.5f)) offRouteFixes++ else offRouteFixes = 0
        if (offRouteFixes < 3) return
        val now = System.currentTimeMillis()
        if (now - lastRerouteAttemptMs < 20_000L || isCalculatingRoute) return
        lastRerouteAttemptMs = now
        offRouteFixes = 0
        isCalculatingRoute = true
        if (::routeText.isInitialized) routeText.text = "DEVIAZIONE RILEVATA • RICALCOLO…"
        thread(name = "valhalla-reroute") {
            try {
                val recalculated = NavigationServices.calculateRoute(
                    NavPoint(location.latitude, location.longitude), target.point, motorcycle = false
                )
                runOnUiThread {
                    isCalculatingRoute = false
                    routeResult = recalculated
                    announcedManeuverIndex = -1
                    announcedStage = 0
                    drawRoute(recalculated, false)
                    updateTurnGuidance(currentLocation)
                    if (::routeText.isInitialized) routeText.text = "PERCORSO RICALCOLATO\n" + recalculated.firstInstruction
                    if (voiceGuideReady) voiceGuide?.speak("Percorso ricalcolato", TextToSpeech.QUEUE_ADD, null, "reroute")
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isCalculatingRoute = false
                    if (::routeText.isInitialized) routeText.text = "DEVIAZIONE • RICALCOLO NON DISPONIBILE"
                }
            }
        }
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
        // Trust the routing engine's typed maneuver. Instruction text alone can
        // mention a roundabout/exit while the actual maneuver is a fork or turn.
        val roundabout = ManeuverIconResolver.isRoundabout(maneuver.type)
        val icon = ManeuverIconResolver.icon(maneuver.type, instruction)
        if (::maneuverIcon.isInitialized) {
            maneuverIcon.text = icon
            maneuverIcon.setTextColor(if (negativeTheme) white else Color.BLACK)
        }
        val instructionLower = instruction.lowercase(Locale.ITALIAN)
        val shortInstruction = if (roundabout && !instructionLower.contains("rotonda") && !instructionLower.contains("rotatoria")) {
            "Rotonda: " + instruction
        } else instruction
        if (::routeText.isInitialized) {
            val distanceLabel = when {
                distanceToTurn < 10.0 -> "ORA"
                distanceToTurn < 100.0 -> distanceToTurn.toInt().toString() + " m"
                else -> ((distanceToTurn / 10.0).toInt() * 10).toString() + " m"
            }
            routeText.text = shortInstruction + "\n" + distanceLabel
            routeText.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            routeText.textSize = 18f
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
                voiceGuide?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "turn-" + maneuverIndex + "-stage-" + stage)
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

    private fun createGpsArrowBitmap(): Bitmap {
        // Native bitmap avoids missing glyphs/fonts in vector map styles.
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 12f; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 212, 0); style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(size * 0.5f, size * 0.06f)
            lineTo(size * 0.86f, size * 0.91f)
            lineTo(size * 0.5f, size * 0.70f)
            lineTo(size * 0.14f, size * 0.91f)
            close()
        }
        canvas.drawPath(path, shadow)
        canvas.drawPath(path, fill)
        return bitmap
    }

    private fun createPlaceFlagBitmap(): Bitmap {
        // Draw the flag natively so it renders regardless of the map style's fonts/glyphs.
        val width = 96
        val height = 112
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
        }
        val poleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        val flagOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeJoin = Paint.Join.ROUND
        }
        val flagFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 59, 48)
            style = Paint.Style.FILL
        }
        canvas.drawLine(24f, 12f, 24f, 100f, pole)
        canvas.drawLine(24f, 12f, 24f, 100f, poleFill)
        val flag = Path().apply {
            moveTo(28f, 16f)
            lineTo(82f, 28f)
            lineTo(60f, 46f)
            lineTo(28f, 42f)
            close()
        }
        canvas.drawPath(flag, flagOutline)
        canvas.drawPath(flag, flagFill)
        return bitmap
    }

    private fun refreshSavedPoiMarkers() {
        if (!mapStyleReady) return
        val source = map?.style?.getSource(SOURCE_SAVED_POIS) as? GeoJsonSource ?: return
        val features = getSharedPreferences(PREFS, MODE_PRIVATE).all
            .filterKeys { it.startsWith("poi_") }
            .mapNotNull { (_, value) ->
                val coords = (value as? String)?.split(",") ?: return@mapNotNull null
                val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                Feature.fromGeometry(Point.fromLngLat(lon, lat))
            }
        source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features.toTypedArray()))
    }

    private fun refreshDestinationMarker() {
        if (!mapStyleReady) return
        val source = map?.style?.getSource(SOURCE_DESTINATION) as? GeoJsonSource ?: return
        val point = destination?.point
        val features = if (point == null) emptyArray() else arrayOf(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
        )
        source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features))
    }

    private fun animateGpsMarkerTo(target: Location) {
        if (!mapStyleReady) {
            displayedMarkerLocation = Location(target)
            return
        }
        val start = displayedMarkerLocation?.let { Location(it) }
            ?: currentLocation?.let { Location(it) }
            ?: Location(target)
        val distance = start.distanceTo(target)
        gpsMarkerAnimator?.cancel()
        if (!activityResumed || distance < 1f || distance > 80f) {
            displayedMarkerLocation = Location(target)
            updateUserMarker(target)
            return
        }
        val speed = if (target.hasSpeed()) target.speed else 0f
        val durationMs = ((distance / maxOf(speed, 5f)) * 1000f).toLong().coerceIn(180L, 850L)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val interpolated = Location(target).apply {
                    latitude = start.latitude + (target.latitude - start.latitude) * fraction
                    longitude = start.longitude + (target.longitude - start.longitude) * fraction
                }
                displayedMarkerLocation = Location(interpolated)
                updateUserMarker(interpolated)
                if (animation.animatedFraction >= 1f && gpsMarkerAnimator === animation) {
                    gpsMarkerAnimator = null
                }
            }
        }
        gpsMarkerAnimator = animator
        animator.start()
    }

    private fun updateUserMarker(location: Location) {
        if (!mapStyleReady) return
        val point = Point.fromLngLat(location.longitude, location.latitude)
        val positionFeature = Feature.fromGeometry(point).apply {
            addNumberProperty("bearing", if (location.hasBearing()) location.bearing.toDouble() else 0.0)
        }
        (map?.style?.getSource(SOURCE_USER) as? GeoJsonSource)?.setGeoJson(positionFeature)
    }

    private fun updateMapForLocation(location: Location, forceCenter: Boolean, updateMarker: Boolean = true) {
        if (!mapStyleReady) return
        if (updateMarker) {
            displayedMarkerLocation = Location(location)
            updateUserMarker(location)
        }
        val previous = lastCameraLocation
        val movedEnough = previous == null || previous.distanceTo(location) >= 2f
        if ((forceCenter || movedEnough) && (followGps || forceCenter)) {
            val oldBearing = map?.cameraPosition?.bearing ?: 0.0
            val bearing = if (location.hasBearing() && location.speed >= 3f) location.bearing.toDouble() else oldBearing
            val camera = CameraPosition.Builder()
                .target(LatLng(location.latitude, location.longitude))
                .zoom(if (routeResult == null) 16.0 else 16.5)
                .bearing(bearing)
                .build()
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 350)
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
        if (themeMode == "auto") lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (dashboardStarted) requestGps()
    }

    override fun onPause() {
        activityResumed = false
        if (gpsUpdatesRequested) {
            locationManager.removeUpdates(this)
            gpsUpdatesRequested = false
        }
        mapView?.onPause()
        sensorManager?.unregisterListener(this)
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
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        speedAlertHandler.removeCallbacksAndMessages(null)
        speedAlertActive = false
        speedAlertFlashRed = false
        gpsMarkerAnimator?.cancel()
        gpsMarkerAnimator = null
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
