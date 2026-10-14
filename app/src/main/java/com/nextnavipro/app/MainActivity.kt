package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import android.text.TextPaint
import android.location.Location
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback as FusedLocationCallback
import com.google.android.gms.location.LocationRequest as FusedLocationRequest
import com.google.android.gms.location.LocationResult as FusedLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

class MainActivity : Activity(), LocationListener, SensorEventListener {
    companion object {
        private const val PREFS = "next_navi_settings"
        private const val PREF_NEGATIVE = "negative_theme"
        private const val PREF_THEME_MODE = "theme_mode"
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val PERMISSION_LOCATION = 100
        private const val REQUEST_IMPORT_GPX = 2001
        private const val REQUEST_EXPORT_GPX = 2002
        private const val REQUEST_EXPORT_GPS_TRACE = 2003
        private const val SOURCE_USER = "next-user-position"
        private const val SOURCE_SAVED_POIS = "next-saved-pois"
        private const val SOURCE_DESTINATION = "next-destination"
        private const val SOURCE_ROUTE = "next-active-route"
        private const val SOURCE_RADARS = "next-speed-cameras"
    }

    private lateinit var locationManager: LocationManager
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var fusedLocationCallback: FusedLocationCallback? = null
    private var usingLocationManagerFallback = false
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var gnssStatusRegistered = false
    private var satellitesInView = 0
    private var satellitesUsedInFix = 0
    private var usedConstellations: Set<Int> = emptySet()
    private lateinit var root: LinearLayout
    private lateinit var speedText: SpeedometerTextView
    private lateinit var clockText: TextView
    private lateinit var routeMetricsText: TextView
    private lateinit var weatherText: TextView
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
    private var originalWindowBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var originalWindowBrightnessCaptured = false
    private var gpsUpdatesRequested = false
    private var negativeTheme = true
    private var currentLocation: Location? = null
    private var approximateLocation: ApproximateLocation? = null
    private var ipFallbackRequested = false
    private var weatherUsesApproximateLocation = false
    private val ipFallbackHandler = Handler(Looper.getMainLooper())
    private var pendingGpxExportName = "Percorso NEXT NAVI PRO"
    private var gpsDiagnosticRecording = false
    private val gpsDiagnosticSamples = ArrayList<GpsTraceSample>()
    private var pendingGpsDiagnosticCsv: String? = null
    private var pendingGpxExportPoints: List<NavPoint>? = null
    private var recordingGpx = false
    private val gpxRecordingPoints = ArrayList<NavPoint>()
    private var lastRecordedGpxPoint: NavPoint? = null
    private var lastGpxPointTimeMs = 0L
    private var destination: PlaceResult? = null
    private var routeResult: RouteResult? = null
    private var routeProgressInitialized = false
    private var lastRouteProgressIndex = 0
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
    private var speedingVoiceActive = false
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
    private var lastAcceptedFixElapsedMs = 0L
    private var smoothedGpsLocation: Location? = null
    private var lastWeatherUpdateMs = 0L
    private var lastWeatherLocation: Location? = null
    private var latestWeather: WeatherSnapshot? = null
    private var forecastRequested = false
    private val mapFollowHandler = Handler(Looper.getMainLooper())
    private val mapFollowRunnable = object : Runnable {
        override fun run() {
            // Timeout only for the embedded dashboard map; never interrupt full-screen exploration.
            if (dashboardStarted && activityResumed && !fullScreenMap) {
                followGps = true
                currentLocation?.let { updateMapForLocation(it, true) }
            }
        }
    }
    private var followGps = true
    private var isSearching = false
    private var isCalculatingRoute = false
    private val splashHandler = Handler(Looper.getMainLooper())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::clockText.isInitialized) {
                clockText.text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                clockHandler.postDelayed(this, 1000L)
            }
        }
    }
    private val yellow = Color.rgb(255, 212, 0)
    private val cyan = Color.rgb(0, 229, 255)
    private val white = Color.WHITE
    private val black = Color.BLACK
    private val positiveBackground = Color.rgb(220, 224, 227)
    private val positiveText = Color.rgb(25, 30, 34)
    private val negativeBackground = Color.rgb(28, 33, 38)
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
        // Recreated TextViews start empty/default: restore the last real weather snapshot immediately
        // instead of waiting for the next 15-minute/location refresh interval.
        latestWeather?.let { updateWeatherDisplay(it) }
            ?: currentLocation?.let { refreshWeatherIfNeeded(it, true) }
        // The speed-limit view is also recreated by buildDashboard(); restore the last
        // resolved value immediately instead of leaving the new sign at its placeholder.
        updateSpeedLimitDisplay()
        currentLocation?.let { updateGpsStatus(it) }
        currentLocation?.let { updateMapForLocation(it, true) }
        routeResult?.let { drawRoute(it, false); updateTurnGuidance(currentLocation ?: return) }
        if (activityResumed) requestGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Preserve the window-level setting. The system brightness slider and adaptive-brightness
        // preference are never changed; -1 delegates brightness back to Android/Samsung.
        if (!originalWindowBrightnessCaptured) {
            originalWindowBrightness = window.attributes.screenBrightness
            originalWindowBrightnessCaptured = true
        }
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
        // Always start in automatic light-sensor mode; keep the last palette until the sensor reports lux.
        themeMode = "auto"
        preferences.edit().putString(PREF_THEME_MODE, "auto").apply()
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
        if (activityResumed) setNavigationBrightnessEnabled(true)
        splashHandler.removeCallbacksAndMessages(null)
        buildDashboard(null)
        requestGps()
        // IP geolocation is deliberately delayed: GNSS/network location always wins.
        ipFallbackHandler.postDelayed({
            if (dashboardStarted && currentLocation == null) requestIpLocationFallback()
        }, 8000L)
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
        routeMetricsText = TextView(this).apply {
            text = "— km  •  ARRIVO —"
            textSize = 12f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(if (negativeTheme) yellow else Color.rgb(0, 105, 110))
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
        }
        weatherText = TextView(this).apply {
            text = "🌤️ METEO in attesa GPS"
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
            includeFontPadding = false
            setLineSpacing(dp(2).toFloat(), 1.0f)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            // Match the dashboard surface exactly; the weather strip is not a separate card.
            setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        }
        infoColumn.addView(weatherText, params(-1, dp(52)))
        limitValue = TextView(this).apply {
            text = "—"
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.BLACK)
            applySpeedLimitSignStyle(this)
        }
        infoColumn.addView(limitValue, params(dp(58), dp(58)))
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
            setTextColor(if (negativeTheme) white else positiveText)
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
        speedText = SpeedometerTextView(this).apply {
            text = "000"
            textSize = 132f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        speedText.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        // Keep the arrival distance/time immediately below the speedometer in portrait,
        // where vertical space is limited and the metrics must remain visible.
        speedColumn.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1.2f))
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(184)))
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) {
            speedColumn.addView(routeMetricsText, params(-1, dp(32)))
        }
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.03f
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        speedColumn.addView(unit, params(-1, dp(34)))
        clockText = TextView(this).apply {
            text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
        // Keep the clock directly below the km/h label, in the free strip above trip metrics.
        speedColumn.addView(clockText, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 4 else 2)
        })
        // In landscape keep trip metrics below the clock; in portrait they are
        // deliberately placed directly under the speedometer above.
        if (!isPortrait) speedColumn.addView(routeMetricsText, params(-1, dp(34)))
        val centerSpacer = View(this)
        speedColumn.addView(centerSpacer, LinearLayout.LayoutParams(-1, 0, 0.8f))
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
        val newMapView = MapView(this)
        mapView = newMapView
        newMapView.onCreate(savedState)
        mapColumn.addView(mapStatus, params(-1, dp(22)))
        mapColumn.addView(newMapView, LinearLayout.LayoutParams(-1, 0, 1f))
        val recenterButton = makeButton("⌖ POSIZIONE") { recenterMapToGps() }
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mapColumn.addView(recenterButton, params(-1, dp(36)))
        }

        // Keep the map in the dashboard in both orientations. The speedometer stays
        // in the central column/band; the map is the right-hand (landscape) or lower
        // (portrait) pane rather than being used to position the speedometer.
        listOf(brand, weatherText, limitValue, gpsText, routeText, radarText, routeMetricsText).forEach { infoColumn.removeView(it) }
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
            // Portrait: compact and lift turn guidance so it stays visible on shorter screens.
            maneuverIcon.textSize = 92f
            maneuverIcon.translationY = -dp(24).toFloat()
            navHalf.addView(maneuverIcon, params(-1, dp(98)))
            routeText.textSize = 15f
            routeText.translationY = -dp(8).toFloat()
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(dp(58), dp(58)).apply { gravity = Gravity.CENTER })
            upper.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            upper.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(weatherText, params(-1, dp(52)))
            content.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.85f))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(-1, 0, 1.15f))
            content.addView(routeButton, params(-1, dp(44)))
            content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val navHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            // Landscape: lift the dashboard by about 1 cm (38 dp) and compact turn guidance.
            content.translationY = -dp(38).toFloat()
            maneuverIcon.textSize = 116f
            maneuverIcon.translationY = -dp(6).toFloat()
            navHalf.addView(maneuverIcon, params(-1, dp(116)))
            routeText.textSize = 15f
            routeText.translationY = -dp(6).toFloat()
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(dp(58), dp(58)).apply { gravity = Gravity.CENTER })
            val navAndLimit = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            navAndLimit.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            navAndLimit.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            left.addView(weatherText, params(-1, dp(52)))
            left.addView(navAndLimit, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(0, -1, 1.2f))
            content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        clockHandler.removeCallbacks(clockRunnable)
        clockRunnable.run()

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        themeButton = makeButton(themeButtonLabel()) { toggleTheme() }
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
                    // Full-screen: manual control until GPS is tapped. Dashboard: resume after 10 s idle.
                    followGps = false
                    mapFollowHandler.removeCallbacks(mapFollowRunnable)
                    if (!fullScreenMap) mapFollowHandler.postDelayed(mapFollowRunnable, 10_000L)
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
            else if (negativeTheme) white else positiveText
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
        // The outline is drawn by SpeedometerTextView; keep the digits in the normal
        // TextView rendering pipeline so foreground colors and glyphs cannot disappear.
        speedText.outlineColor = speedGaugeColor(speed)
        speedText.text = styled
        speedText.invalidate()
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

    private inner class SpeedometerTextView(context: android.content.Context) : TextView(context) {
        var outlineColor: Int = Color.GRAY
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val textLayout = layout
            val label = text?.toString().orEmpty()
            if (textLayout != null && label.isNotEmpty() && textLayout.lineCount > 0) {
                val p = TextPaint(paint)
                p.style = Paint.Style.STROKE
                p.strokeJoin = Paint.Join.ROUND
                p.strokeWidth = dp(2).toFloat()
                p.color = outlineColor
                p.alpha = 255
                val x = compoundPaddingLeft + textLayout.getLineLeft(0)
                val baseline = extendedPaddingTop + textLayout.getLineBaseline(0).toFloat()
                canvas.drawText(label, x, baseline, p)
            }
            // Android renders the real SpannableString on top, preserving per-digit colors.
            super.onDraw(canvas)
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
        val modes = arrayOf("Automatico (sensore lux)", "☀  Positivo", "☾  Negativo")
        val values = arrayOf("auto", "positive", "negative")
        AlertDialog.Builder(this).setTitle("Modalità schermo")
            .setSingleChoiceItems(modes, values.indexOf(themeMode).coerceAtLeast(0)) { dialog, which ->
                setThemeMode(values[which])
                dialog.dismiss()
            }.setNegativeButton("Annulla", null).show()
    }

    private fun themeButtonLabel(): String = when {
        themeMode == "auto" && !negativeTheme -> "☀ AUTO"
        themeMode == "auto" && negativeTheme -> "☾ AUTO"
        !negativeTheme -> "☀ POSITIVO"
        else -> "☾ NEGATIVO"
    }

    private fun refreshThemeButton() {
        if (::themeButton.isInitialized) themeButton.text = themeButtonLabel()
    }

    private fun recenterMapToGps() {
        followGps = true
        lastCameraLocation = null
        currentLocation?.let { updateMapForLocation(it, true) }
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        mapFollowHandler.postDelayed(mapFollowRunnable, 10_000L)
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
        refreshThemeButton()
    }

    private fun applyThemeMode(isNegative: Boolean) {
        if (negativeTheme == isNegative) return
        negativeTheme = isNegative
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_NEGATIVE, negativeTheme).apply()
        applyTheme()
        map?.let { applyMapStyle(it) }
        refreshThemeButton()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (themeMode != "auto" || event.sensor.type != Sensor.TYPE_LIGHT || event.values.isEmpty()) return
        val lux = event.values[0]
        // Bright sunlight: light panel with dark text; ordinary/dim light: dark panel.
        if (lux >= 6000f && negativeTheme) applyThemeMode(false)
        else if (lux < 2500f && !negativeTheme) applyThemeMode(true)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun showMainMenu() {
        val choices = arrayOf("Indirizzi preferiti", "Salva destinazione come preferita", "Elimina preferiti", "Prova voce navigatore", "Modalità schermo", "Ricerche recenti", "Soglie tachimetro e avvisi", "Previsioni meteo", "Percorsi GPX", "Diagnostica GPS / esporta traccia")
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
                    7 -> showWeatherForecast()
                    8 -> showGpxMenu()
                    9 -> showGpsDiagnosticsMenu()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun showGpsDiagnosticsMenu() {
        val status = if (gpsDiagnosticRecording) "Registrazione attiva • ${gpsDiagnosticSamples.size} campioni" else "Registrazione ferma • ${gpsDiagnosticSamples.size} campioni"
        val choices = arrayOf(
            if (gpsDiagnosticRecording) "Ferma registrazione GPS" else "Avvia registrazione diagnostica GPS",
            "Esporta CSV per analisi",
            "Azzera campioni"
        )
        AlertDialog.Builder(this).setTitle("Diagnostica GPS").setMessage(status)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        if (!gpsDiagnosticRecording) {
                            gpsDiagnosticSamples.clear()
                            gpsDiagnosticRecording = true
                            lastRawGpsLocation?.let { appendGpsDiagnosticSample(it) }
                            Toast.makeText(this, "Diagnostica GPS avviata. Registra il tragitto e fermati in sicurezza prima di esportare.", Toast.LENGTH_LONG).show()
                        } else {
                            gpsDiagnosticRecording = false
                            Toast.makeText(this, "Registrazione GPS terminata: ${gpsDiagnosticSamples.size} campioni.", Toast.LENGTH_LONG).show()
                        }
                    }
                    1 -> exportGpsDiagnostics()
                    2 -> {
                        if (gpsDiagnosticRecording) {
                            Toast.makeText(this, "Ferma prima la registrazione.", Toast.LENGTH_LONG).show()
                        } else {
                            gpsDiagnosticSamples.clear()
                            Toast.makeText(this, "Campioni diagnostici cancellati.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun appendGpsDiagnosticSample(location: Location, rejectionStatus: String? = null) {
        if (!gpsDiagnosticRecording || gpsDiagnosticSamples.size >= 50_000) {
            if (gpsDiagnosticRecording && gpsDiagnosticSamples.size >= 50_000) {
                gpsDiagnosticRecording = false
                Toast.makeText(this, "Diagnostica fermata: raggiunto il limite di 50.000 campioni.", Toast.LENGTH_LONG).show()
            }
            return
        }
        val route = routeResult
        val match = if (rejectionStatus == null && route != null && route.points.size >= 2)
            findRouteProgressIndex(route, location) else null
        val validMatch = match?.takeIf { it.second.isFinite() && it.second < Float.MAX_VALUE }
        gpsDiagnosticSamples.add(
            GpsTraceSample(
                timestampEpochMs = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
                bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                satellitesUsed = satellitesUsedInFix,
                satellitesVisible = satellitesInView,
                constellations = usedConstellations.mapNotNull {
                    when (it) {
                        GnssStatus.CONSTELLATION_GPS -> "GPS"
                        GnssStatus.CONSTELLATION_GALILEO -> "GAL"
                        GnssStatus.CONSTELLATION_GLONASS -> "GLO"
                        GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
                        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
                        else -> null
                    }
                }.distinct().joinToString("/"),
                routeSegmentIndex = validMatch?.first,
                routeDistanceMeters = validMatch?.second,
                matchStatus = rejectionStatus ?: when {
                    route == null -> "NO_ACTIVE_ROUTE"
                    validMatch == null -> "NO_CONFIDENT_MATCH"
                    else -> "MATCHED"
                }
            )
        )
    }

    private fun exportGpsDiagnostics() {
        if (gpsDiagnosticRecording) {
            Toast.makeText(this, "Ferma la registrazione prima di esportare.", Toast.LENGTH_LONG).show()
            return
        }
        if (gpsDiagnosticSamples.isEmpty()) {
            Toast.makeText(this, "Nessun campione GPS. Avvia prima la registrazione diagnostica.", Toast.LENGTH_LONG).show()
            return
        }
        pendingGpsDiagnosticCsv = GpsTraceDiagnostics.toCsv(gpsDiagnosticSamples)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "next_navi_gps_trace_${System.currentTimeMillis()}.csv")
        }
        startActivityForResult(intent, REQUEST_EXPORT_GPS_TRACE)
    }

    private fun requestIpLocationFallback() {
        if (ipFallbackRequested || currentLocation != null) return
        ipFallbackRequested = true
        thread(name = "ip-geolocation") {
            val result = try { IpLocationService.fetch() } catch (_: Exception) { null }
            runOnUiThread {
                if (currentLocation != null) return@runOnUiThread
                if (result == null) {
                    if (::gpsText.isInitialized) gpsText.text = "GPS ASSENTE • POSIZIONE IP NON DISPONIBILE"
                    return@runOnUiThread
                }
                approximateLocation = result
                if (::gpsText.isInitialized) {
                    gpsText.text = "POSIZIONE IP ≈ " + result.areaLabel
                    gpsText.setTextColor(Color.rgb(235, 165, 0))
                }
                refreshWeatherForApproximateLocation(result, true)
            }
        }
    }

    private fun refreshWeatherForApproximateLocation(location: ApproximateLocation, force: Boolean = false) {
        if (currentLocation != null) return
        val now = System.currentTimeMillis()
        if (!force && weatherUsesApproximateLocation && now - lastWeatherUpdateMs < 15L * 60L * 1000L) return
        lastWeatherUpdateMs = now
        weatherUsesApproximateLocation = true
        thread(name = "open-meteo-ip-fallback") {
            val result = try { WeatherService.fetch(location.latitude, location.longitude) } catch (_: Exception) { null }
            runOnUiThread {
                // Do not let a slow IP/weather response overwrite a newer real GPS fix.
                if (currentLocation != null || approximateLocation != location) return@runOnUiThread
                if (result != null) {
                    latestWeather = result
                    updateWeatherDisplay(result)
                    if (forecastRequested) {
                        forecastRequested = false
                        showWeatherForecast()
                    }
                } else if (latestWeather == null && ::weatherText.isInitialized) {
                    weatherText.text = "METEO IP\\nnon disponibile"
                }
            }
        }
    }

    private fun showGpxMenu() {
        val choices = arrayOf("Importa GPX dal telefono", "Salva percorso attivo", "Gestisci percorsi e memoria", "Esporta GPX attivo…")
        AlertDialog.Builder(this).setTitle("Percorsi GPX")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> importGpx()
                    1 -> saveActiveGpx()
                    2 -> showSavedGpxRoutes()
                    3 -> exportActiveGpx()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun askToRecordGpx() {
        if (recordingGpx) return
        AlertDialog.Builder(this)
            .setTitle("Memorizzare il viaggio?")
            .setMessage("Vuoi registrare il percorso realmente seguito in un file GPX? La registrazione usa solo posizioni GPS valide.")
            .setPositiveButton("Sì, registra") { _, _ -> startGpxRecording() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun startGpxRecording() {
        gpxRecordingPoints.clear()
        lastRecordedGpxPoint = null
        lastGpxPointTimeMs = 0L
        recordingGpx = true
        currentLocation?.let { appendGpxRecordingPoint(it, force = true) }
        Toast.makeText(this, "Registrazione GPX avviata.", Toast.LENGTH_LONG).show()
    }

    private fun appendGpxRecordingPoint(location: Location, force: Boolean = false) {
        if (!recordingGpx || !location.hasAccuracy() || location.accuracy > 25f) return
        val point = NavPoint(location.latitude, location.longitude)
        val previous = lastRecordedGpxPoint
        val now = System.currentTimeMillis()
        if (!force && previous != null) {
            val distance = GpxRouteService.distanceMeters(previous, point)
            if (distance < 5.0 && now - lastGpxPointTimeMs < 5000L) return
        }
        if (gpxRecordingPoints.size >= 100_000) {
            finishGpxRecording(save = true)
            Toast.makeText(this, "Registrazione fermata: raggiunto il limite di sicurezza dei punti GPX.", Toast.LENGTH_LONG).show()
            return
        }
        gpxRecordingPoints.add(point)
        lastRecordedGpxPoint = point
        lastGpxPointTimeMs = now
    }

    private fun finishGpxRecording(save: Boolean) {
        if (!recordingGpx) return
        recordingGpx = false
        val points = gpxRecordingPoints.toList()
        gpxRecordingPoints.clear()
        lastRecordedGpxPoint = null
        if (!save || points.size < 2) {
            Toast.makeText(this, "Registrazione GPX terminata: non ci sono abbastanza punti per salvare una traccia.", Toast.LENGTH_LONG).show()
            return
        }
        thread(name = "save-recorded-gpx") {
            try {
                val label = (destination?.name ?: "Viaggio registrato")
                    .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifBlank { "Viaggio registrato" }.take(60)
                val file = File(gpxDirectory(), "${System.currentTimeMillis()}_$label.gpx")
                FileOutputStream(file).use { GpxRouteService.write(it, label, points) }
                runOnUiThread {
                    Toast.makeText(this, "Viaggio GPX salvato: $label (${points.size} punti).", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "Salvataggio registrazione fallito: ${error.message ?: "errore"}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun gpxDirectory(): File = File(filesDir, "gpx_routes").apply { if (!exists()) mkdirs() }

    private fun importGpx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "application/gpx", "application/xml", "text/xml", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_IMPORT_GPX)
    }

    private fun saveActiveGpx() {
        val route = routeResult
        if (route == null || route.points.size < 2) {
            Toast.makeText(this, "Non c'è un percorso da salvare.", Toast.LENGTH_LONG).show()
            return
        }
        thread(name = "save-gpx") {
            try {
                val label = (destination?.name ?: "Percorso").replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim()
                    .ifBlank { "Percorso" }.take(60)
                val file = File(gpxDirectory(), "${System.currentTimeMillis()}_$label.gpx")
                FileOutputStream(file).use { GpxRouteService.write(it, label, route.points) }
                runOnUiThread { Toast.makeText(this, "Percorso salvato: $label", Toast.LENGTH_LONG).show() }
            } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "Salvataggio GPX fallito: ${error.message ?: "errore"}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showSavedGpxRoutes() {
        val files = gpxDirectory().listFiles()?.filter { it.isFile && it.extension.equals("gpx", true) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            Toast.makeText(this, "Nessun GPX salvato. Importa o salva prima un percorso.", Toast.LENGTH_LONG).show()
            return
        }
        thread(name = "list-gpx-routes") {
            val tracks = files.mapNotNull { file ->
                try { file to FileInputStream(file).use { GpxRouteService.read(it) } } catch (_: Exception) { null }
            }
            runOnUiThread {
                if (tracks.isEmpty()) {
                    Toast.makeText(this, "Nessun file GPX valido nella libreria.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val totalBytes = tracks.sumOf { it.first.length() }
                val totalMb = totalBytes / (1024.0 * 1024.0)
                AlertDialog.Builder(this).setTitle("Libreria GPX • " + tracks.size + " percorsi")
                    .setMessage(String.format(Locale.getDefault(), "Memoria occupata: %.2f MB\\nScegli un percorso per visualizzarlo, seguirlo, esportarlo o eliminarlo.", totalMb))
                    .setItems(tracks.map { (_, track) ->
                        track.name + " • " + String.format(Locale.getDefault(), "%.1f km", track.distanceKm)
                    }.toTypedArray()) { _, which -> showSavedGpxActions(tracks[which].first, tracks[which].second) }
                    .setNegativeButton("Chiudi", null).show()
            }
        }
    }

    private fun showSavedGpxActions(file: File, track: GpxTrack) {
        val actions = arrayOf("Segui questa traccia GPX", "Visualizza sulla mappa", "Esporta / condividi file…", "Elimina percorso")
        AlertDialog.Builder(this).setTitle(track.name)
            .setMessage(String.format(Locale.getDefault(), "%.1f km • %d punti\\nFile: %s", track.distanceKm, track.points.size, file.name))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> loadSavedGpx(file)
                    1 -> {
                        activateGpxTrack(track)
                        enterMapFullScreen()
                    }
                    2 -> exportGpxTrack(track)
                    3 -> AlertDialog.Builder(this).setTitle("Eliminare il GPX?")
                        .setMessage("Il file " + file.name + " verrà eliminato dal dispositivo.")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val deleted = file.delete()
                            Toast.makeText(this, if (deleted) "Percorso GPX eliminato." else "Impossibile eliminare il file.", Toast.LENGTH_LONG).show()
                        }.show()
                }
            }.setNegativeButton("Indietro") { _, _ -> showSavedGpxRoutes() }.show()
    }

    private fun loadSavedGpx(file: File) {
        thread(name = "load-gpx-route") {
            val result = try { FileInputStream(file).use { GpxRouteService.read(it) } } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "GPX non valido: ${error.message ?: "errore"}", Toast.LENGTH_LONG).show() }
                return@thread
            }
            runOnUiThread { activateGpxTrack(result) }
        }
    }

    private fun activateGpxTrack(track: GpxTrack) {
        routeResult = RouteResult(track.points, track.distanceKm, 0, "Traccia GPX: segui la linea sulla mappa.", emptyList())
        resetRouteProgress()
        destination = PlaceResult(track.name, track.points.last().let { NavPoint(it.latitude, it.longitude) }, "Destinazione da traccia GPX")
        if (::destinationInput.isInitialized) destinationInput.setText(track.name)
        if (::routeText.isInitialized) {
            routeText.text = "TRACCIA GPX\\nSegui la linea sulla mappa • svolte vocali non disponibili per questa traccia"
            routeText.textSize = 14f
        }
        if (::routeButton.isInitialized) routeButton.text = "ANNULLA TRACCIA GPX"
        announcedManeuverIndex = -1
        announcedStage = 0
        drawRoute(routeResult!!, true)
        updateRouteMetrics(currentLocation)
        Toast.makeText(this, "Traccia GPX caricata: ${track.points.size} punti. Per le svolte guidate serve un ricalcolo stradale.", Toast.LENGTH_LONG).show()
    }

    private fun exportActiveGpx() {
        val route = routeResult
        if (route == null || route.points.size < 2) {
            Toast.makeText(this, "Non c'è un percorso da esportare.", Toast.LENGTH_LONG).show()
            return
        }
        pendingGpxExportName = (destination?.name ?: "Percorso NEXT NAVI PRO")
            .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifBlank { "Percorso NEXT NAVI PRO" }.take(60)
        pendingGpxExportPoints = route.points.toList()
        launchGpxExportPicker()
    }

    private fun exportGpxTrack(track: GpxTrack) {
        pendingGpxExportName = track.name.replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "")
            .trim().ifBlank { "Percorso NEXT NAVI PRO" }.take(60)
        pendingGpxExportPoints = track.points.toList()
        launchGpxExportPicker()
    }

    private fun launchGpxExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, pendingGpxExportName.replace(' ', '_') + ".gpx")
        }
        startActivityForResult(intent, REQUEST_EXPORT_GPX)
    }

    @Deprecated("Uses the document picker contract supported on all Android versions targeted by this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQUEST_IMPORT_GPX -> thread(name = "import-gpx") {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("Impossibile leggere il file selezionato.")
                    if (bytes.size > 30 * 1024 * 1024) throw IllegalArgumentException("Il GPX supera il limite di 30 MB.")
                    val track = ByteArrayInputStream(bytes).use { GpxRouteService.read(it) }
                    val safeName = track.name.replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifBlank { "Percorso" }.take(60)
                    val saved = File(gpxDirectory(), "${System.currentTimeMillis()}_$safeName.gpx")
                    FileOutputStream(saved).use { it.write(bytes) }
                    runOnUiThread { activateGpxTrack(track) }
                } catch (error: Exception) {
                    runOnUiThread { Toast.makeText(this, "Importazione GPX fallita: ${error.message ?: "file non valido"}", Toast.LENGTH_LONG).show() }
                }
            }
            REQUEST_EXPORT_GPX -> thread(name = "export-gpx") {
                try {
                    val points = pendingGpxExportPoints ?: throw IllegalStateException("Percorso non più disponibile.")
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        GpxRouteService.write(it, pendingGpxExportName, points)
                    } ?: throw IllegalStateException("Impossibile scrivere il file selezionato.")
                    runOnUiThread { Toast.makeText(this, "GPX esportato. Ora puoi condividerlo dal file manager.", Toast.LENGTH_LONG).show() }
                } catch (error: Exception) {
                    runOnUiThread { Toast.makeText(this, "Esportazione GPX fallita: ${error.message ?: "errore"}", Toast.LENGTH_LONG).show() }
                }
            }
            REQUEST_EXPORT_GPS_TRACE -> thread(name = "export-gps-trace") {
                try {
                    val csv = pendingGpsDiagnosticCsv ?: throw IllegalStateException("Traccia diagnostica non più disponibile.")
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
                        ?: throw IllegalStateException("Impossibile scrivere il file selezionato.")
                    runOnUiThread { Toast.makeText(this, "CSV GPS esportato. Puoi allegarlo per l'analisi della traccia reale.", Toast.LENGTH_LONG).show() }
                } catch (error: Exception) {
                    runOnUiThread { Toast.makeText(this, "Esportazione diagnostica fallita: ${error.message ?: "errore"}", Toast.LENGTH_LONG).show() }
                }
            }
        }
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
        applySpeedLimitSignStyle(limitValue)
        routeText.setBackgroundColor(panelBackground)
        speedText.setBackgroundColor(panelBackground)
        if (::weatherText.isInitialized) weatherText.setBackgroundColor(panelBackground)
        updateSpeedGaugeBorder(lastDisplayedSpeed)
        val textColor = if (negativeTheme) white else positiveText
        root.walkViews().forEach { view ->
            if (view is TextView && view !== destinationInput) {
                view.setTextColor(when {
                    view === speedText -> textColor
                    view === limitValue -> Color.BLACK
                    view === clockText -> Color.WHITE
                    view === routeMetricsText -> if (negativeTheme) yellow else Color.rgb(0, 105, 110)
                    view === gpsText -> if (listOf("GPS INSUFFICIENTE", "GPS SALTO SCARTATO", "PERMESSO GPS NEGATO", "GPS NON DISPONIBILE", "LOCALIZZAZIONE DISATTIVATA").any { gpsText.text.toString().contains(it) }) Color.rgb(235, 45, 45) else Color.rgb(0, 190, 70)
                    view === routeText -> if (negativeTheme) cyan else Color.rgb(0, 85, 105)
                    view === radarText -> Color.rgb(255, 70, 55)
                    view.text.toString().contains("NEXT NAVI PRO") -> yellow
                    else -> textColor
                })
            }
            if (view is Button) {
                view.setTextColor(textColor)
                view.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(205, 216, 222))
            }
        }
        destinationInput.setTextColor(textColor)
        destinationInput.setHintTextColor(if (negativeTheme) Color.LTGRAY else Color.DKGRAY)
        destinationInput.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(235, 239, 241))
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
                val origin = currentLocation
                val near = origin?.let { NavPoint(it.latitude, it.longitude) }
                    ?: approximateLocation?.let { NavPoint(it.latitude, it.longitude) }
                val results = NavigationServices.searchPlaces(query, near)
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
                    val labels = sortedResults.map { place ->
                        val distanceLabel = origin?.let { here ->
                            val distance = FloatArray(1)
                            Location.distanceBetween(here.latitude, here.longitude, place.point.latitude, place.point.longitude, distance)
                            if (distance[0] < 1000f) {
                                " • " + distance[0].roundToInt() + " m"
                            } else {
                                " • " + String.format(Locale.ITALY, "%.1f km", distance[0] / 1000f)
                            }
                        }.orEmpty()
                        place.name + distanceLabel + if (place.description.isBlank()) "" else "\n" + place.description
                    }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Scegli destinazione")
                        .setItems(labels) { _, which ->
                            destination = sortedResults[which]
                            routeResult = null
                            resetRouteProgress()
                            routeMetricsText.text = "— km  •  ARRIVO —"
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
                    resetRouteProgress()
                    updateRouteMetrics(currentLocation)
                    if (voiceGuideReady) voiceGuide?.speak("Avvio la navigazione", TextToSpeech.QUEUE_FLUSH, null, "navigation-start")
                    announcedManeuverIndex = -1
                    announcedStage = 0
                    drawRoute(result, true)
                    val durationMinutes = (result.durationSeconds / 60.0).toInt()
                    val firstManeuver = result.maneuvers.firstOrNull()
                    val firstInstruction = firstManeuver?.let {
                        ManeuverIconResolver.correctedInstruction(it.type, it.instruction)
                    } ?: result.firstInstruction
                    // Set the initial route summary first; GPS guidance must run last so
                    // it cannot be overwritten by the route's first (possibly passed) maneuver.
                    routeText.text = String.format(
                        Locale.ITALY, "%.1f km  •  circa %d min\n%s",
                        result.distanceKm, durationMinutes, firstInstruction
                    )
                    updateTurnGuidance(currentLocation)
                    routeButton.text = "ANNULLA PERCORSO"
                    askToRecordGpx()
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
        finishGpxRecording(save = true)
        routeResult = null
        resetRouteProgress()
        if (::routeMetricsText.isInitialized) routeMetricsText.text = "— km  •  ARRIVO —"
        announcedManeuverIndex = -1
        announcedStage = 0
        if (::maneuverIcon.isInitialized) maneuverIcon.text = "↑"
        destination = null
        refreshDestinationMarker()
        if (::routeText.isInitialized) routeText.text = "Nessun percorso attivo"
        if (::routeMetricsText.isInitialized) routeMetricsText.text = "— km  •  ARRIVO —"
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
        followGps = false
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(black) }
        fullScreenRoot = page
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(makeButton("CRUSCOTTO") { exitMapFullScreen() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(makeButton("LUOGHI") { showSavedPois() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bar.addView(makeButton("⌖ GPS") { recenterMapToGps() }, LinearLayout.LayoutParams(0, dp(48), 1f))
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
        followGps = true
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        currentLocation?.let { updateMapForLocation(it, true) }
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

    private fun registerGnssDiagnostics() {
        if (gnssStatusRegistered || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val callback = gnssStatusCallback ?: object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var visible = 0
                var used = 0
                val constellations = linkedSetOf<Int>()
                for (i in 0 until status.satelliteCount) {
                    visible++
                    if (status.usedInFix(i)) {
                        used++
                        constellations.add(status.getConstellationType(i))
                    }
                }
                satellitesInView = visible
                satellitesUsedInFix = used
                usedConstellations = constellations
                currentLocation?.let { updateGpsStatus(it) }
            }
        }.also { gnssStatusCallback = it }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(callback)
            }
            gnssStatusRegistered = true
        } catch (_: Exception) {
            // Some devices or system builds may not expose satellite diagnostics.
            // Fused location continues to work; absence of diagnostics is not a GPS failure.
            gnssStatusRegistered = false
        }
    }

    private fun unregisterGnssDiagnostics() {
        val callback = gnssStatusCallback ?: return
        if (!gnssStatusRegistered) return
        try { locationManager.unregisterGnssStatusCallback(callback) } catch (_: Exception) { }
        gnssStatusRegistered = false
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
            registerGnssDiagnostics()
            val client = fusedLocationClient ?: LocationServices.getFusedLocationProviderClient(this).also {
                fusedLocationClient = it
            }
            val callback = fusedLocationCallback ?: object : FusedLocationCallback() {
                override fun onLocationResult(result: FusedLocationResult) {
                    result.lastLocation?.let { onLocationChanged(it) }
                }
            }.also { fusedLocationCallback = it }
            // Fused GNSS/network/sensor positioning; OsmAnd uses the same provider family
            // with a very short high-accuracy interval. The marker is interpolated separately.
            val request = FusedLocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 250L)
                .setMinUpdateIntervalMillis(250L)
                .setWaitForAccurateLocation(true)
                .build()
            gpsUpdatesRequested = true
            usingLocationManagerFallback = false
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    runOnUiThread {
                        gpsUpdatesRequested = false
                        requestLocationManagerFallback()
                    }
                }
            client.lastLocation.addOnSuccessListener { last ->
                if (last != null && currentLocation == null) onLocationChanged(last)
            }
        } catch (_: SecurityException) {
            gpsUpdatesRequested = false
            gpsText.text = "PERMESSO GPS NEGATO"
        } catch (_: Exception) {
            gpsUpdatesRequested = false
            requestLocationManagerFallback()
        }
    }

    private fun requestLocationManagerFallback() {
        if (!dashboardStarted || gpsUpdatesRequested) return
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
                locationManager.requestLocationUpdates(provider, 500L, 0f, this, Looper.getMainLooper())
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestLocationUpdates(provider, 500L, 0f, this)
            }
            usingLocationManagerFallback = true
            gpsUpdatesRequested = true
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
                ?: run { gpsText.text = "RICERCA GPS REALE…" }
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
            appendGpsDiagnosticSample(location, "REJECTED_STALE_OR_ACCURACY")
            gpsText.text = "GPS INSUFFICIENTE"
            return
        }
        val previousRaw = lastRawGpsLocation
        if (previousRaw != null && isImplausibleGpsJump(previousRaw, location)) {
            // Keep rejected fixes in opt-in diagnostics so real traces reveal the failures too.
            appendGpsDiagnosticSample(location, "REJECTED_IMPLAUSIBLE_JUMP")
            // Do not promote a rejected fix to the reference point; the next fix is
            // compared with the last accepted raw fix instead of the bad jump.
            gpsText.text = "GPS SALTO SCARTATO"
            return
        }
        val previousSpeedForPrediction = previousRaw?.takeIf { it.hasSpeed() }?.speed ?: location.speed
        val fixElapsedMs = if (location.elapsedRealtimeNanos > 0L) location.elapsedRealtimeNanos / 1_000_000L else SystemClock.elapsedRealtime()
        val movingTimeMs = if (lastAcceptedFixElapsedMs > 0L) (fixElapsedMs - lastAcceptedFixElapsedMs).coerceIn(0L, 2000L) else 0L
        lastAcceptedFixElapsedMs = fixElapsedMs
        lastRawGpsLocation = Location(location)
        val smoothed = smoothLocation(smoothedGpsLocation, location)
        currentLocation = Location(smoothed)
        val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
        updateSpeedDisplay(if (location.hasSpeed()) speed.toInt() else null)
        updateGpsStatus(location)
        animateGpsMarkerTo(smoothed, movingTimeMs, previousSpeedForPrediction)
        updateMapForLocation(smoothed, lastCameraLocation == null, updateMarker = false)
        updateTurnGuidance(smoothed)
        updateRouteMetrics(smoothed)
        appendGpsDiagnosticSample(location)
        appendGpxRecordingPoint(smoothed)
        refreshRadarsIfNeeded(smoothed)
        refreshWeatherIfNeeded(smoothed)
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
        // Adaptive low-pass filter: suppress stationary GPS wander, but rapidly accept
        // genuine movement and course changes so the marker does not cut across a turn.
        val result = Location(incoming)
        if (previous != null && previous.hasAccuracy() && incoming.hasAccuracy()) {
            val jump = previous.distanceTo(incoming)
            if (jump < 35f) {
                val accuracyAlpha = when {
                    incoming.accuracy <= 5f -> 0.70f
                    incoming.accuracy <= 10f -> 0.64f
                    incoming.accuracy <= 20f -> 0.55f
                    incoming.accuracy <= 35f -> 0.42f
                    else -> 0.30f
                }
                val speed = if (incoming.hasSpeed()) incoming.speed else 0f
                var alpha = when {
                    speed >= 8f -> (accuracyAlpha + 0.18f).coerceAtMost(0.92f)
                    speed < 2.5f -> 0.14f
                    else -> accuracyAlpha
                }
                if (jump > 12f) alpha = maxOf(alpha, 0.82f)
                if (previous.hasBearing() && incoming.hasBearing() && speed >= 2.5f) {
                    val courseDelta = abs(((incoming.bearing - previous.bearing + 540f) % 360f) - 180f)
                    if (courseDelta >= 30f) alpha = maxOf(alpha, 0.82f)
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
        val accuracy = if (location.hasAccuracy()) "±" + location.accuracy.toInt() + " m" else "accuratezza n/d"
        val satelliteInfo = if (gnssStatusRegistered && satellitesInView > 0) {
            " • SAT $satellitesUsedInFix/$satellitesInView" + constellationSummary()
        } else ""
        gpsText.text = (roadName?.takeIf { it.isNotBlank() } ?: "GPS") +
            " • " + accuracy + satelliteInfo
        gpsText.setTextColor(
            if (location.hasAccuracy() && location.accuracy <= 15f) Color.rgb(0, 190, 70)
            else Color.rgb(235, 165, 0)
        )
    }

    private fun constellationSummary(): String {
        val names = usedConstellations.mapNotNull {
            when (it) {
                GnssStatus.CONSTELLATION_GPS -> "GPS"
                GnssStatus.CONSTELLATION_GALILEO -> "GAL"
                GnssStatus.CONSTELLATION_GLONASS -> "GLO"
                GnssStatus.CONSTELLATION_BEIDOU -> "BDS"
                GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
                else -> null
            }
        }.distinct()
        return if (names.isEmpty()) "" else " • " + names.joinToString("/")
    }

    private fun applySpeedLimitSignStyle(view: TextView) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(4), Color.rgb(210, 25, 35))
        }
        view.setTextColor(Color.BLACK)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(2), dp(1), dp(2), dp(1))
    }

    private fun updateWeatherDisplay(snapshot: WeatherSnapshot) {
        if (!::weatherText.isInitialized) return
        val currentRainChance = snapshot.hourly
            .minByOrNull { hour ->
                kotlin.math.abs(
                    (hour.time.take(13).substringAfter("T").take(2).toIntOrNull() ?: 0) -
                        (snapshot.currentTime.substringAfter("T").take(2).toIntOrNull() ?: 0)
                )
            }?.precipitationProbability
        val rainIcon = if ((currentRainChance ?: 0) >= 50) "🌧️" else "☔"
        val rainText = currentRainChance?.let { "$rainIcon $it%" } ?: "☔ —"
        weatherText.text = WeatherService.icon(snapshot.weatherCode) + " " +
            WeatherService.description(snapshot.weatherCode) + "  •  " +
            WeatherService.format(snapshot.temperatureC) + "°C\n" +
            "💨 " + WeatherService.format(snapshot.windKmh) + " km/h " +
            WeatherService.windDirection(snapshot.windDirectionDegrees) +
            "  •  raff. " + WeatherService.format(snapshot.gustKmh) + " km/h  •  " + rainText
        weatherText.contentDescription = WeatherService.description(snapshot.weatherCode) +
            ", " + WeatherService.format(snapshot.temperatureC) + " gradi, vento " +
            WeatherService.format(snapshot.windKmh) + " chilometri orari, direzione " +
            WeatherService.windDirection(snapshot.windDirectionDegrees) + ", raffiche " +
            WeatherService.format(snapshot.gustKmh) + " chilometri orari, probabilità di pioggia " +
            (currentRainChance?.let { "$it per cento" } ?: "non disponibile") +
            if (weatherUsesApproximateLocation) ", meteo basato su posizione IP approssimativa: " + (approximateLocation?.areaLabel ?: "zona incerta") else ""
    }

    private fun refreshWeatherIfNeeded(location: Location, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val previous = lastWeatherLocation
        if (!force && !weatherUsesApproximateLocation && now - lastWeatherUpdateMs < 15L * 60L * 1000L &&
            previous != null && previous.distanceTo(location) < 15_000f) return
        lastWeatherUpdateMs = now
        lastWeatherLocation = Location(location)
        val previousWeatherWasApproximate = weatherUsesApproximateLocation
        // Mark the new request as GPS-based immediately to prevent one HTTP request per fix.
        weatherUsesApproximateLocation = false
        val latitude = location.latitude
        val longitude = location.longitude
        thread(name = "open-meteo-refresh") {
            val result = try { WeatherService.fetch(latitude, longitude) } catch (_: Exception) { null }
            runOnUiThread {
                if (result != null) {
                    weatherUsesApproximateLocation = false
                    latestWeather = result
                    updateWeatherDisplay(result)
                    if (forecastRequested) {
                        forecastRequested = false
                        showWeatherForecast()
                    }
                } else if (latestWeather == null && ::weatherText.isInitialized) {
                    weatherText.text = "METEO\nnon disponibile"
                } else if (previousWeatherWasApproximate && ::weatherText.isInitialized) {
                    weatherText.contentDescription = weatherText.contentDescription.toString() +
                        ", aggiornamento GPS meteo fallito; ultima risposta basata su posizione IP approssimativa"
                }
            }
        }
    }

    private fun showWeatherForecast() {
        val snapshot = latestWeather
        if (snapshot == null) {
            val location = currentLocation
            if (location == null) {
                if (approximateLocation != null) {
                    forecastRequested = true
                    refreshWeatherForApproximateLocation(approximateLocation!!, true)
                    Toast.makeText(this, "Previsioni richieste per una zona approssimativa via IP.", Toast.LENGTH_LONG).show()
                } else {
                    forecastRequested = true
                    ipFallbackRequested = false
                    requestIpLocationFallback()
                    Toast.makeText(this, "Nessuna posizione GPS: provo a ricavare una zona approssimativa dalla rete.", Toast.LENGTH_LONG).show()
                }
                return
            }
            forecastRequested = true
            refreshWeatherIfNeeded(location, true)
            Toast.makeText(this, "Caricamento previsioni meteo…", Toast.LENGTH_SHORT).show()
            return
        }
        val today = snapshot.currentTime.substringBefore("T")
        val hours = snapshot.hourly.filter { it.time.substringBefore("T") == today }
        val periods = listOf("Mattina", "Pomeriggio", "Sera")
        val grouped = periods.map { name ->
            name to hours.filter { hour ->
                val h = hour.time.substringAfter("T").take(2).toIntOrNull() ?: -1
                when (name) {
                    "Mattina" -> h in 6..11
                    "Pomeriggio" -> h in 12..17
                    else -> h in 18..23
                }
            }
        }
        val labels = grouped.map { (name, list) ->
            if (list.isEmpty()) "$name • nessun dato orario"
            else {
                val t = list.map { it.temperatureC }.average()
                val w = list.map { it.windKmh }.average()
                val g = list.map { it.gustKmh }.average()
                val rain = list.map { it.precipitationProbability.toDouble() }.average()
                "$name • ${WeatherService.format(t)}°C • vento ${WeatherService.format(w)} km/h • raffiche ${WeatherService.format(g)} • pioggia ${WeatherService.format(rain)}%"
            }
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Previsioni meteo • oggi")
            .setItems(labels) { _, which ->
                val (name, list) = grouped[which]
                if (list.isEmpty()) {
                    Toast.makeText(this, "Nessun dato disponibile per questa fascia oraria.", Toast.LENGTH_SHORT).show()
                } else {
                    val detail = list.joinToString("\n") { hour ->
                        val h = hour.time.substringAfter("T").take(5)
                        "$h  ${WeatherService.icon(hour.weatherCode)} ${WeatherService.format(hour.temperatureC)}°C  •  vento ${WeatherService.format(hour.windKmh)} km/h  •  raffiche ${WeatherService.format(hour.gustKmh)}  •  pioggia ${hour.precipitationProbability}%"
                    }
                    AlertDialog.Builder(this).setTitle("$name • dettaglio orario")
                        .setMessage(detail).setPositiveButton("Chiudi", null).show()
                }
            }.setPositiveButton("Aggiorna") { _, _ ->
                forecastRequested = true
                currentLocation?.let { refreshWeatherIfNeeded(it, true) }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun updateSpeedLimitDisplay() {
        val location = currentLocation
        val limit = currentSpeedLimit
        val speedKmh = location?.takeIf { it.hasSpeed() }?.speed?.times(3.6f)?.roundToInt()

        // Announce once per overspeed episode and only when an explicit OSM limit is known.
        if (limit != null && speedKmh != null && speedKmh > limit + 3) {
            if (!speedingVoiceActive) {
                speedingVoiceActive = true
                if (voiceGuideReady) {
                    voiceGuide?.speak(
                        "Attenzione. Limite di velocità $limit chilometri orari. Stai viaggiando a $speedKmh chilometri orari.",
                        TextToSpeech.QUEUE_ADD, null, "speed-limit-overspeed"
                    )
                }
            }
        } else if (limit == null || speedKmh == null || speedKmh <= limit + 1) {
            speedingVoiceActive = false
        }

        if (!::limitValue.isInitialized) return
        limitValue.text = limit?.toString() ?: "—"
        // The speed-limit digits stay black inside the white/red regulatory sign.
        limitValue.setTextColor(Color.BLACK)
    }

    private fun checkRadarProximityAlert(location: Location) {
        // A wide straight-line radius creates false alarms from parallel roads,
        // opposite carriageways and cameras on nearby junctions. When a route is
        // active, accept only cameras close to the active route and ahead along it.
        if (!location.hasAccuracy() || location.accuracy > 18f ||
            !location.hasSpeed() || location.speed < 4f) return

        val route = routeResult
        val candidates = if (route != null && route.points.size >= 2) {
            var currentIndex = 0
            var currentDistance = Float.MAX_VALUE
            for (i in route.points.indices) {
                val p = route.points[i]
                val d = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, p.latitude, p.longitude, d)
                if (d[0] < currentDistance) {
                    currentDistance = d[0]
                    currentIndex = i
                }
            }
            val cumulative = DoubleArray(route.points.size)
            for (i in 1 until route.points.size) {
                val a = route.points[i - 1]
                val b = route.points[i]
                val d = FloatArray(1)
                Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, d)
                cumulative[i] = cumulative[i - 1] + d[0]
            }
            radarCameras.mapNotNull { camera ->
                var cameraIndex = -1
                var crossTrackMeters = Float.MAX_VALUE
                for (i in route.points.indices) {
                    val p = route.points[i]
                    val d = FloatArray(1)
                    Location.distanceBetween(camera.point.latitude, camera.point.longitude, p.latitude, p.longitude, d)
                    if (d[0] < crossTrackMeters) {
                        crossTrackMeters = d[0]
                        cameraIndex = i
                    }
                }
                if (cameraIndex < currentIndex || crossTrackMeters > 22f) return@mapNotNull null
                val alongRouteMeters = cumulative[cameraIndex] - cumulative[currentIndex]
                if (alongRouteMeters !in 0.0..650.0) return@mapNotNull null
                camera to alongRouteMeters.toFloat()
            }.minByOrNull { it.second }
        } else {
            // Without a route, require a close camera in the direction of travel
            // instead of announcing every camera inside a 650 m circle.
            if (!location.hasBearing() || location.speed < 6f) return
            radarCameras.mapNotNull { camera ->
                val target = Location("radar").apply {
                    latitude = camera.point.latitude
                    longitude = camera.point.longitude
                }
                val directMeters = location.distanceTo(target)
                if (directMeters > 250f) return@mapNotNull null
                val bearingToCamera = location.bearingTo(target)
                val delta = abs(((bearingToCamera - location.bearing + 540f) % 360f) - 180f)
                if (delta > 40f) return@mapNotNull null
                camera to directMeters
            }.minByOrNull { it.second }
        } ?: return

        val now = System.currentTimeMillis()
        if (candidates.first.osmId == lastRadarAlertId && now - lastRadarAlertMs < 90_000L) return
        lastRadarAlertId = candidates.first.osmId
        lastRadarAlertMs = now
        if (voiceGuideReady) voiceGuide?.speak(
            "Attenzione, possibile autovelox tra circa " + candidates.second.toInt() + " metri",
            TextToSpeech.QUEUE_ADD, null, "radar-" + candidates.first.osmId
        )
        radarText.text = "⚠ AUTOVELOX • " + candidates.second.toInt() + " m"
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
                    resetRouteProgress()
                    updateRouteMetrics(currentLocation)
                    announcedManeuverIndex = -1
                    announcedStage = 0
                    drawRoute(recalculated, false)
                    val firstManeuver = recalculated.maneuvers.firstOrNull()
                    val firstInstruction = firstManeuver?.let {
                        ManeuverIconResolver.correctedInstruction(it.type, it.instruction)
                    } ?: recalculated.firstInstruction
                    if (::routeText.isInitialized) routeText.text = "PERCORSO RICALCOLATO\n" + firstInstruction
                    updateTurnGuidance(currentLocation)
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

    /**
     * Updates remaining distance and ETA from the active route geometry and the latest
     * accepted GPS fix. No ETA/distance is shown as a real value unless a route and GPS fix exist.
     */
    private val routeProgressTracker = RouteProgressTracker()

    private fun resetRouteProgress() {
        routeProgressInitialized = false
        lastRouteProgressIndex = 0
        routeProgressTracker.reset()
    }

    /**
     * Match only nearby route points after the first accepted match. This monotonic local
     * window reduces jumps to a parallel carriageway or a distant section of a looping route.
     * Bearing is a soft tie-breaker only when the device reports meaningful forward motion.
     */
    private fun findRouteProgressIndex(route: RouteResult, location: Location): Pair<Int, Float> {
        if (route.points.size < 2) return 0 to Float.MAX_VALUE
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 20.0
        val heading = location.bearing.takeIf {
            location.hasBearing() && location.hasSpeed() && location.speed >= 3.0f &&
                (!location.hasBearingAccuracy() || location.bearingAccuracyDegrees <= 35f)
        }
        val elapsedMs = if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos / 1_000_000L
        } else {
            SystemClock.elapsedRealtime()
        }
        val match = routeProgressTracker.match(
            points = route.points,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracy,
            bearingDegrees = heading,
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() }?.toDouble(),
            elapsedRealtimeMs = elapsedMs
        ) ?: return (if (routeProgressInitialized) lastRouteProgressIndex else 0) to Float.MAX_VALUE
        val confidenceLimit = minOf(65.0, maxOf(25.0, accuracy * 1.8))
        if (match.distanceMeters <= confidenceLimit) {
            lastRouteProgressIndex = if (routeProgressInitialized) maxOf(lastRouteProgressIndex, match.segmentIndex) else match.segmentIndex
            routeProgressInitialized = true
            return lastRouteProgressIndex to match.distanceMeters.toFloat()
        }
        return (if (routeProgressInitialized) lastRouteProgressIndex else 0) to Float.MAX_VALUE
    }

    private fun updateRouteMetrics(location: Location?) {
        if (!::routeMetricsText.isInitialized) return
        val route = routeResult
        val here = location
        if (route == null || here == null || route.points.size < 2) {
            routeMetricsText.text = "— km  •  ARRIVO —"
            return
        }

        val match = findRouteProgressIndex(route, here)
        val nearestIndex = match.first
        val nearestMeters = match.second
        val reportedAccuracy = if (here.hasAccuracy()) here.accuracy else 20f
        val confidenceLimit = minOf(65f, maxOf(25f, reportedAccuracy * 1.8f))
        if (nearestMeters > confidenceLimit) {
            routeMetricsText.text = "GPS INSUFFICIENTE • percorso non agganciato"
            return
        }
        var remainingMeters = nearestMeters.toDouble()
        for (i in nearestIndex until route.points.lastIndex) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val segment = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, segment)
            remainingMeters += segment[0].toDouble()
        }

        var totalGeometryMeters = 0.0
        for (i in 0 until route.points.lastIndex) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val segment = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, segment)
            totalGeometryMeters += segment[0].toDouble()
        }
        val remainingKm = (remainingMeters / 1000.0).coerceAtLeast(0.0)
        val totalKm = totalGeometryMeters / 1000.0
        val remainingSeconds = if (totalKm > 0.01) {
            (route.durationSeconds * (remainingKm / totalKm)).toLong().coerceAtLeast(0L)
        } else 0L
        val arrivalTime = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis() + remainingSeconds * 1000L))
        val distanceText = String.format(Locale.getDefault(), "%.1f km", remainingKm)
        routeMetricsText.text = if (route.durationSeconds > 0) "$distanceText  •  ARRIVO $arrivalTime"
            else "$distanceText  •  ETA — (traccia GPX)"
    }

    private fun updateTurnGuidance(location: Location?) {
        val route = routeResult ?: return
        val here = location ?: return
        if (route.points.size < 2) return
        val match = findRouteProgressIndex(route, here)
        val nearestIndex = match.first
        val nearestMeters = match.second
        val reportedAccuracy = if (here.hasAccuracy()) here.accuracy else 20f
        val confidenceLimit = minOf(65f, maxOf(25f, reportedAccuracy * 1.8f))
        updateRouteMetrics(route, nearestIndex)
        if (nearestMeters > confidenceLimit) {
            if (::routeText.isInitialized && route.maneuvers.isNotEmpty()) {
                routeText.text = "POSIZIONE FUORI TRACCIA\\nAttendo un aggancio affidabile alla strada"
            }
            return
        }
        if (route.maneuvers.isEmpty()) return
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
        // Use the typed maneuver to catch a contradictory left/right word in the
        // server narration. The same corrected text drives both the display and voice.
        val instruction = ManeuverIconResolver.correctedInstruction(maneuver.type, maneuver.instruction)
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
                distanceToTurn < 100.0 -> "FRA " + distanceToTurn.toInt() + " m"
                else -> "FRA " + ((distanceToTurn / 10.0).toInt() * 10) + " m"
            }
            routeText.text = distanceLabel + "\n" + shortInstruction
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

    private fun updateRouteMetrics(route: RouteResult, nearestIndex: Int) {
        if (!::routeMetricsText.isInitialized) return
        if (route.distanceKm <= 0.0 || route.points.size < 2) {
            routeMetricsText.text = "— km  •  ARRIVO —"
            return
        }
        var remainingMeters = 0.0
        val start = nearestIndex.coerceIn(0, route.points.lastIndex)
        for (i in start until route.points.lastIndex) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val segment = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, segment)
            remainingMeters += segment[0]
        }
        val remainingKm = (remainingMeters / 1000.0).coerceAtMost(route.distanceKm)
        if (route.durationSeconds <= 0) {
            routeMetricsText.text = String.format(Locale.ITALY, "TRACCIA GPX  •  %.1f km", remainingKm)
            return
        }
        val remainingSeconds = (route.durationSeconds * (remainingKm / route.distanceKm)).toLong().coerceAtLeast(0L)
        val arrival = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis() + remainingSeconds * 1000L))
        routeMetricsText.text = String.format(Locale.ITALY, "ARRIVO %s  •  %.1f km", arrival, remainingKm)
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

    /**
     * OsmAnd's public guidance documents emphasize filtering poor-accuracy fixes and attaching
     * tracks to roads. For our live arrow, use the active route as a *visual* map-match only when
     * a nearby segment is plausible; keep currentLocation raw/smoothed for speed and guidance.
     * This avoids inventing a position or snapping across to a parallel road.
     */
    private fun snapMarkerToActiveRoute(location: Location): Location {
        val route = routeResult ?: return Location(location)
        if (route.points.size < 2 || !location.hasAccuracy() || location.accuracy > 25f) {
            return Location(location)
        }

        val metersPerLat = 111320.0
        val metersPerLon = metersPerLat * cos(Math.toRadians(location.latitude))
        var bestDistance = Double.MAX_VALUE
        var bestLat = location.latitude
        var bestLon = location.longitude
        var bestBearing = Float.NaN

        for (i in 0 until route.points.lastIndex) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val ax = (a.longitude - location.longitude) * metersPerLon
            val ay = (a.latitude - location.latitude) * metersPerLat
            val bx = (b.longitude - location.longitude) * metersPerLon
            val by = (b.latitude - location.latitude) * metersPerLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared < 0.25) continue
            val fraction = ((-ax * dx + -ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val px = ax + dx * fraction
            val py = ay + dy * fraction
            val distance = sqrt(px * px + py * py)
            if (distance >= bestDistance) continue

            val segmentBearing = ((Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0).toFloat()
            if (location.hasBearing() && location.hasSpeed() && location.speed >= 3f) {
                val delta = abs(((location.bearing - segmentBearing + 540f) % 360f) - 180f)
                if (delta > 55f) continue
            }
            bestDistance = distance
            bestLat = location.latitude + py / metersPerLat
            bestLon = location.longitude + px / metersPerLon
            bestBearing = segmentBearing
        }

        // Tighten the allowed snap radius as the reported GPS accuracy improves.
        val maxSnapMeters = minOf(25f, maxOf(10f, location.accuracy * 1.25f)).toDouble()
        if (bestDistance > maxSnapMeters) return Location(location)
        return Location(location).apply {
            latitude = bestLat
            longitude = bestLon
            if (!bestBearing.isNaN() && hasSpeed() && speed >= 3f) {
                bearing = bestBearing
                setBearing(bearing)
            }
        }
    }

    /**
     * OsmAnd predicts a short distance ahead along the active route using the time
     * between fixes and average speed, then animates the marker to that prediction.
     * We use a conservative 30% interpolation lead, only for the visual marker and
     * only when a route is active; raw/smoothed GPS still drives guidance and alerts.
     * Prediction is disabled close to a maneuver to avoid visually cutting a turn.
     */
    private fun predictMarkerAlongRoute(location: Location, movingTimeMs: Long, previousSpeedMps: Float): Location {
        val route = routeResult ?: return Location(location)
        if (route.points.size < 2 || movingTimeMs !in 101L..2000L ||
            !location.hasSpeed() || location.speed < 2.0f) return Location(location)

        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE
        for (i in route.points.indices) {
            val point = route.points[i]
            val d = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, d)
            if (d[0] < nearestDistance) {
                nearestDistance = d[0]
                nearestIndex = i
            }
        }
        // Do not predict across an imminent junction/turn.
        val nextManeuver = route.maneuvers.firstOrNull { it.pointIndex > nearestIndex }
        if (nextManeuver != null) {
            var metersToManeuver = 0.0
            for (i in nearestIndex until nextManeuver.pointIndex.coerceAtMost(route.points.lastIndex)) {
                val a = route.points[i]
                val b = route.points[i + 1]
                val d = FloatArray(1)
                Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, d)
                metersToManeuver += d[0]
            }
            if (metersToManeuver < 25.0) return Location(location)
        }

        val speedNow = if (location.hasSpeed()) location.speed else 0f
        val previousSpeed = previousSpeedMps.takeIf { it.isFinite() && it >= 0f } ?: speedNow
        var remainingMeters = ((previousSpeed + speedNow) / 2.0) *
            (movingTimeMs / 1000.0) * 0.30
        if (!remainingMeters.isFinite() || remainingMeters <= 0.0) return Location(location)
        // Bound the visual lead; this is marker interpolation, not a claimed GPS fix.
        remainingMeters = remainingMeters.coerceAtMost(8.0)

        var start = NavPoint(location.latitude, location.longitude)
        for (i in (nearestIndex + 1)..route.points.lastIndex) {
            val end = route.points[i]
            val endLocation = Location("route").apply {
                latitude = end.latitude
                longitude = end.longitude
            }
            val startLocation = Location("route").apply {
                latitude = start.latitude
                longitude = start.longitude
            }
            val segmentMeters = startLocation.distanceTo(endLocation).toDouble()
            if (segmentMeters < 0.1) {
                start = end
                continue
            }
            if (remainingMeters <= segmentMeters) {
                val fraction = (remainingMeters / segmentMeters).coerceIn(0.0, 1.0)
                val predicted = Location(location).apply {
                    latitude = start.latitude + (end.latitude - start.latitude) * fraction
                    longitude = start.longitude + (end.longitude - start.longitude) * fraction
                    bearing = startLocation.bearingTo(endLocation)
                    setBearing(bearing)
                }
                return predicted
            }
            remainingMeters -= segmentMeters
            start = end
        }
        return Location(location)
    }

    private fun animateGpsMarkerTo(target: Location, movingTimeMs: Long, previousSpeedMps: Float) {
        val snapped = snapMarkerToActiveRoute(target)
        val markerTarget = predictMarkerAlongRoute(snapped, movingTimeMs, previousSpeedMps)
        if (!mapStyleReady) {
            displayedMarkerLocation = Location(markerTarget)
            return
        }
        val start = displayedMarkerLocation?.let { Location(it) }
            ?: currentLocation?.let { snapMarkerToActiveRoute(it) }
            ?: Location(markerTarget)
        val distance = start.distanceTo(markerTarget)
        gpsMarkerAnimator?.cancel()
        if (!activityResumed || distance < 0.5f || distance > 80f) {
            displayedMarkerLocation = Location(markerTarget)
            updateUserMarker(markerTarget)
            return
        }
        val speed = if (markerTarget.hasSpeed()) markerTarget.speed else 0f
        val durationMs = ((distance / maxOf(speed, 5f)) * 1000f).toLong().coerceIn(220L, 900L)
        val startBearing = if (start.hasBearing()) start.bearing else markerTarget.bearing
        val targetBearing = if (markerTarget.hasBearing()) markerTarget.bearing else startBearing
        val bearingDelta = ((targetBearing - startBearing + 540f) % 360f) - 180f
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val interpolated = Location(markerTarget).apply {
                    latitude = start.latitude + (markerTarget.latitude - start.latitude) * fraction
                    longitude = start.longitude + (markerTarget.longitude - start.longitude) * fraction
                    if (hasBearing()) {
                        bearing = (startBearing + bearingDelta * fraction + 360f) % 360f
                        setBearing(bearing)
                    }
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
        if (dashboardStarted) setNavigationBrightnessEnabled(true)
        mapView?.onResume()
        if (themeMode == "auto") lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (dashboardStarted) requestGps()
    }

    override fun onPause() {
        // Restore immediately when another app/system surface takes over.
        setNavigationBrightnessEnabled(false)
        activityResumed = false
        unregisterGnssDiagnostics()
        fusedLocationCallback?.let { callback ->
            try { fusedLocationClient?.removeLocationUpdates(callback) } catch (_: Exception) { }
        }
        if (usingLocationManagerFallback) {
            try { locationManager.removeUpdates(this) } catch (_: Exception) { }
            usingLocationManagerFallback = false
        }
        gpsUpdatesRequested = false
        mapView?.onPause()
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onStop() {
        setNavigationBrightnessEnabled(false)
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
        clockHandler.removeCallbacksAndMessages(null)
        ipFallbackHandler.removeCallbacksAndMessages(null)
        setNavigationBrightnessEnabled(false)
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

    /** Forces maximum brightness only for this Activity window, then delegates back to Android. */
    private fun setNavigationBrightnessEnabled(enabled: Boolean) {
        if (!originalWindowBrightnessCaptured) return
        val attributes = window.attributes
        val desired = if (enabled) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL else originalWindowBrightness
        if (attributes.screenBrightness != desired) {
            attributes.screenBrightness = desired
            window.attributes = attributes
        }
    }

    private fun params(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
