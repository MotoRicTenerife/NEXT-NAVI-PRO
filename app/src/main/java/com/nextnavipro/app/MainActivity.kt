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
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
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
        private const val PERMISSION_AUDIO = 101
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
    private var motorcycleProfile = false
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
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var jarvisButton: Button
    private lateinit var jarvisStatusText: TextView
    private var jarvisListening = false
    private var pendingJarvisSpeech: String? = null
    private var pendingJarvisPlaces: List<PlaceResult> = emptyList()
    private var pendingJarvisLocality: String? = null
    private var pendingJarvisAddressMode = false
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
        // Any checkpoint present at process start belongs to a previous interrupted session.
        recoverInterruptedPairedRecording()
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
            if (status == TextToSpeech.SUCCESS) {
                val languageStatus = voiceGuide?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                voiceGuideReady = languageStatus >= TextToSpeech.LANG_AVAILABLE
                if (voiceGuideReady) {
                    applyJarvisVoicePreference()
                    // A calm, slightly slower delivery suits spoken directions better.
                    voiceGuide?.setSpeechRate(0.96f)
                    pendingJarvisSpeech?.let { pending ->
                        pendingJarvisSpeech = null
                        speakJarvis(pending)
                    }
                }
            } else {
                voiceGuideReady = false
            }
        }
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        motorcycleProfile = preferences.getBoolean("motorcycle_profile", false)
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
            text = appText("Avvia Navigazione")
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
        initializeJarvis()
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
            hint = appText("Cerca indirizzo o località")
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
        searchButton = makeButton(appText("CERCA")) { searchDestination() }
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(76), dp(46)).apply {
            setMargins(dp(4), 0, 0, 0)
        })
        val homeButton = makeButton(appText("CASA")) { showHomeOptions() }
        searchRow.addView(homeButton, LinearLayout.LayoutParams(dp(70), dp(46)).apply {
            setMargins(dp(4), 0, 0, 0)
        })
        menuButton = makeButton(appText("MENU")) { showMainMenu() }
        searchRow.addView(menuButton, LinearLayout.LayoutParams(dp(76), dp(46)).apply {
            setMargins(dp(4), 0, 0, 0)
        })
        root.addView(searchRow, params(-1, dp(50)))

        // Explicit push-to-talk only: no wake word and no background microphone.
        val jarvisRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        jarvisButton = makeButton(appText("🎙 JARVIS")) { onJarvisButtonPressed() }
        jarvisRow.addView(jarvisButton, LinearLayout.LayoutParams(dp(116), dp(38)).apply {
            setMargins(dp(2), 0, dp(8), 0)
        })
        jarvisStatusText = TextView(this).apply {
            text = appText("Premi il microfono e parla")
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (negativeTheme) cyan else Color.rgb(0, 85, 105))
        }
        jarvisRow.addView(jarvisStatusText, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(jarvisRow, params(-1, dp(40)))

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
            text = appText("🌤️ METEO in attesa GPS")
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
            text = appText("RICERCA GPS…")
            textSize = 10f
            gravity = Gravity.CENTER
        }
        infoColumn.addView(gpsText, params(-1, dp(38)))
        routeText = TextView(this).apply {
            text = appText("Nessun percorso attivo")
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(2), dp(3), dp(3))
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 33, 38) else Color.rgb(205, 216, 222))
        }
        infoColumn.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
        radarText = TextView(this).apply {
            text = appText("RADAR OSM")
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
        // Keep the tachometer at the top of the portrait stack. The live clock
        // follows it; km/h and route ETA/distance sit below, away from the route button.
        speedColumn.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1.2f))
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(184)))
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.03f
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        clockText = TextView(this).apply {
            text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
        // In portrait, show km/h directly below the tachometer, then the clock.
        // ETA and remaining distance stay below both; landscape order remains unchanged.
        if (isPortrait) {
            speedColumn.addView(unit, params(-1, dp(30)))
            speedColumn.addView(clockText, params(-1, dp(32)))
            speedColumn.addView(routeMetricsText, params(-1, dp(34)))
        } else {
            speedColumn.addView(unit, params(-1, dp(34)))
            speedColumn.addView(clockText, LinearLayout.LayoutParams(-1, dp(34)).apply {
                topMargin = dp(2)
            })
            speedColumn.addView(routeMetricsText, params(-1, dp(34)))
        }
        val centerSpacer = View(this)
        speedColumn.addView(centerSpacer, LinearLayout.LayoutParams(-1, 0, 0.8f))
        routeButton = makeButton(appText("AVVIA PERCORSO")) {
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
        val recenterButton = makeButton(appText("⌖ POSIZIONE")) { recenterMapToGps() }
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
            // Preserve the original portrait positioning; only prevent the instruction text from clipping.
            maneuverIcon.translationY = -dp(24).toFloat()
            navHalf.addView(maneuverIcon, params(-1, dp(98)))
            routeText.textSize = 15f
            routeText.translationY = -dp(8).toFloat()
            routeText.includeFontPadding = false
            routeText.maxLines = 3
            routeText.setPadding(dp(8), dp(2), dp(8), dp(2))
            navHalf.addView(routeText, LinearLayout.LayoutParams(-1, 0, 1f))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(dp(58), dp(58)).apply { gravity = Gravity.CENTER })
            upper.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            upper.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(weatherText, params(-1, dp(52)))
            content.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.65f))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(-1, 0, 1.6f))
            content.addView(routeButton, params(-1, dp(44)))
            content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 0.65f))
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
            // Keep the landscape dashboard inside its measured area. Translating the
            // whole content row upward caused it to overlap the search/weather/status row.
            content.translationY = 0f
            maneuverIcon.textSize = 116f
            maneuverIcon.translationY = 0f
            navHalf.addView(maneuverIcon, params(-1, dp(116)))
            routeText.textSize = 15f
            routeText.translationY = 0f
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
            .setPositiveButton(appText("Salva")) { _, _ ->
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
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun toggleTheme() {
        val modes = arrayOf("Automatico (sensore lux)", "☀  Positivo", "☾  Negativo")
        val values = arrayOf("auto", "positive", "negative")
        AlertDialog.Builder(this).setTitle("Modalità schermo")
            .setSingleChoiceItems(modes, values.indexOf(themeMode).coerceAtLeast(0)) { dialog, which ->
                setThemeMode(values[which])
                dialog.dismiss()
            }.setNegativeButton(appText("Annulla"), null).show()
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
        val choices = arrayOf(appText("Indirizzi preferiti"), appText("Salva destinazione come preferita"), appText("Elimina preferiti"), appText("Prova voce navigatore"), appText("Modalità schermo"), appText("Ricerche recenti"), appText("Soglie tachimetro e avvisi"), appText("Previsioni meteo"), appText("Percorsi GPX"), appText("Debug • CSV GPS: esporta / elimina"), appText("Profilo veicolo: ") + if (motorcycleProfile) appText("MOTO") else appText("AUTO"), appText("Nome assistente vocale"), appText("Voce Jarvis"))
        AlertDialog.Builder(this).setTitle(appText("NEXT NAVI PRO • Menu"))
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
                    10 -> showVehicleProfileMenu()
                    11 -> showJarvisNameSettings()
                    12 -> showJarvisVoiceSettings()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun showVehicleProfileMenu() {
        val current = if (motorcycleProfile) "MOTO" else "AUTO"
        AlertDialog.Builder(this)
            .setTitle("Profilo di navigazione")
            .setMessage("Profilo attuale: $current\nIl profilo moto usa il costing motorcycle di Valhalla e penalizza i sentieri non asfaltati. I dati di accesso stradale possono comunque essere incompleti.")
            .setSingleChoiceItems(arrayOf("Auto", "Moto / scooter stradale"), if (motorcycleProfile) 1 else 0) { dialog, which ->
                val newValue = which == 1
                val changed = motorcycleProfile != newValue
                motorcycleProfile = newValue
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("motorcycle_profile", newValue).apply()
                dialog.dismiss()
                Toast.makeText(this, "Profilo salvato: " + if (newValue) "MOTO" else "AUTO", Toast.LENGTH_SHORT).show()
                if (changed && routeResult != null) Toast.makeText(this, "Il profilo scelto verrà usato al prossimo calcolo del percorso; il percorso attuale non è stato modificato.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun gpsDiagnosticsDirectory(): File = File(filesDir, "gps_diagnostics").apply { if (!exists()) mkdirs() }

    private fun savedGpsDiagnosticFiles(): List<File> =
        gpsDiagnosticsDirectory().listFiles()?.filter { it.isFile && it.extension.equals("csv", true) && it.name != "gps_trace_in_progress.csv" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    private fun stopAndSaveGpsDiagnosticRecording(reason: String) {
        if (!gpsDiagnosticRecording) return
        gpsDiagnosticRecording = false
        val count = gpsDiagnosticSamples.size
        val file = saveGpsDiagnosticSession()
        File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv").delete()
        if (file != null) {
            Toast.makeText(this, "CSV GPS salvato ($count campioni) • $reason", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "CSV GPS non salvato: nessun campione o errore di scrittura.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showGpsDiagnosticsMenu() {
        val saved = savedGpsDiagnosticFiles()
        val checkpoint = File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv")
        val orphaned = saved.filter { csv ->
            val matchingGpx = File(gpxDirectory(), csv.nameWithoutExtension + ".gpx")
            !matchingGpx.isFile && !csv.name.startsWith("gps_trace_recovered_")
        }
        val allBytes = saved.sumOf { it.length() } + if (checkpoint.exists()) checkpoint.length() else 0L
        val pairedBytes = saved.filter { File(gpxDirectory(), it.nameWithoutExtension + ".gpx").isFile }.sumOf { it.length() }
        val active = if (recordingGpx && gpsDiagnosticRecording) "Registrazione GPX + CSV ATTIVA • ${gpsDiagnosticSamples.size} campioni"
            else "Registrazione GPX + CSV ferma"
        val status = "$active\nCSV associati: ${saved.count { File(gpxDirectory(), it.nameWithoutExtension + ".gpx").isFile }} • ${String.format(Locale.getDefault(), "%.2f", pairedBytes / (1024.0 * 1024.0))} MB\nTutti i CSV: ${saved.size} • ${String.format(Locale.getDefault(), "%.2f", allBytes / (1024.0 * 1024.0))} MB" +
            if (orphaned.isNotEmpty()) "\nCSV senza GPX associato: ${orphaned.size} (eliminabili)" else ""
        val dialog = AlertDialog.Builder(this).setTitle("DEBUG • GPX / CSV GPS")
            .setMessage(status)
            .setNegativeButton("Chiudi", null)
            .create()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        fun action(label: String, onClick: () -> Unit) {
            panel.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 6
            })
        }
        action("Apri archivio GPX + CSV (esporta / elimina)") { showSavedGpxRoutes() }
        action("Esporta ultimo CSV GPS") {
            val latest = savedGpsDiagnosticFiles().firstOrNull()
            if (latest == null) Toast.makeText(this, "Nessun CSV GPS salvato.", Toast.LENGTH_LONG).show()
            else exportSavedGpsDiagnostic(latest)
        }
        action("Elimina ultimo CSV GPS") {
            val latest = savedGpsDiagnosticFiles().firstOrNull()
            if (latest == null) Toast.makeText(this, "Nessun CSV GPS salvato.", Toast.LENGTH_LONG).show()
            else AlertDialog.Builder(this).setTitle("Eliminare il CSV GPS?")
                .setMessage("File: ${latest.name}\nSe è associato a un GPX, verrà eliminato solo il CSV; per eliminare entrambi usa l'archivio GPX.")
                .setNegativeButton(appText("Annulla"), null)
                .setPositiveButton("Elimina") { _, _ ->
                    Toast.makeText(this, if (latest.delete()) "CSV eliminato." else "Eliminazione non riuscita.", Toast.LENGTH_LONG).show()
                    showGpsDiagnosticsMenu()
                }.show()
        }
        action("Elimina CSV senza GPX associato (${orphaned.size})") {
            if (orphaned.isEmpty()) {
                Toast.makeText(this, "Non ci sono CSV orfani da eliminare.", Toast.LENGTH_LONG).show()
            } else AlertDialog.Builder(this).setTitle("Pulire i CSV senza GPX?")
                .setMessage("Saranno eliminati ${orphaned.size} file CSV non associati a un GPX. I GPX e i relativi CSV non verranno toccati.")
                .setNegativeButton(appText("Annulla"), null)
                .setPositiveButton("Elimina") { _, _ ->
                    val deleted = orphaned.count { it.delete() }
                    Toast.makeText(this, "Eliminati $deleted CSV orfani.", Toast.LENGTH_LONG).show()
                    showGpsDiagnosticsMenu()
                }.show()
        }
        if (checkpoint.isFile) action("Recupera CSV interrotto") { recoverGpsDiagnosticCheckpoint() }
        dialog.setView(panel)
        dialog.show()
    }

    private fun recoverGpsDiagnosticCheckpoint() {
        if (gpsDiagnosticRecording) {
            Toast.makeText(this, "Ferma prima la registrazione attiva.", Toast.LENGTH_LONG).show()
            return
        }
        val checkpoint = File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv")
        if (!checkpoint.isFile || checkpoint.length() <= 0L) {
            Toast.makeText(this, "Nessuna registrazione parziale da recuperare.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val recovered = File(gpsDiagnosticsDirectory(), "gps_trace_recovered_${System.currentTimeMillis()}.csv")
            checkpoint.copyTo(recovered, overwrite = false)
            checkpoint.delete()
            Toast.makeText(this, "Registrazione parziale recuperata. Puoi esportarla da «Gestisci registrazioni salvate».", Toast.LENGTH_LONG).show()
            showSavedGpsDiagnostics()
        } catch (error: Exception) {
            Toast.makeText(this, "Recupero non riuscito: ${error.message ?: "errore di file"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveGpsDiagnosticSession(): File? {
        if (gpsDiagnosticSamples.isEmpty()) return null
        return try {
            val file = File(gpsDiagnosticsDirectory(), "gps_trace_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).use { it.write(GpsTraceDiagnostics.toCsv(gpsDiagnosticSamples).toByteArray(Charsets.UTF_8)) }
            file
        } catch (_: Exception) { null }
    }

    private fun saveGpsDiagnosticCheckpoint() {
        if (gpsDiagnosticSamples.isEmpty()) return
        try {
            val temp = File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.tmp")
            FileOutputStream(temp).use { it.write(GpsTraceDiagnostics.toCsv(gpsDiagnosticSamples).toByteArray(Charsets.UTF_8)) }
            val checkpoint = File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv")
            if (!temp.renameTo(checkpoint)) {
                temp.copyTo(checkpoint, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) { /* Preserve the last valid checkpoint; finalization retries. */ }
    }

    private fun showSavedGpsDiagnostics() {
        val files = savedGpsDiagnosticFiles()
        if (files.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Registrazioni GPS")
                .setMessage("Nessuna registrazione salvata. Avvia una registrazione e poi scegli «Ferma e salva registrazione».")
                .setPositiveButton("OK", null).show()
            return
        }
        val total = files.sumOf { it.length() }
        val labels = files.map { file ->
            val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date(file.lastModified()))
            val mb = String.format(Locale.getDefault(), "%.2f", file.length() / (1024.0 * 1024.0))
            "$date • $mb MB"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Registrazioni GPS • ${files.size}")
            .setMessage("Memoria occupata: ${String.format(Locale.getDefault(), "%.2f", total / (1024.0 * 1024.0))} MB\nSeleziona una sessione da esportare o eliminare.")
            .setItems(labels) { _, which -> showSavedGpsDiagnosticActions(files[which]) }
            .setNegativeButton("Chiudi", null).show()
    }

    private fun showSavedGpsDiagnosticActions(file: File) {
        AlertDialog.Builder(this).setTitle(file.name)
            .setMessage("Dimensione file: ${String.format(Locale.getDefault(), "%.2f", file.length() / (1024.0 * 1024.0))} MB")
            .setItems(arrayOf("Esporta CSV…", "Elimina registrazione")) { _, which ->
                when (which) {
                    0 -> exportSavedGpsDiagnostic(file)
                    1 -> AlertDialog.Builder(this).setTitle("Eliminare la registrazione?")
                        .setMessage("Il file verrà rimosso definitivamente dal dispositivo.")
                        .setNegativeButton(appText("Annulla"), null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val deleted = file.delete()
                            Toast.makeText(this, if (deleted) "Registrazione eliminata." else "Eliminazione non riuscita.", Toast.LENGTH_LONG).show()
                            showSavedGpsDiagnostics()
                        }.show()
                }
            }.setNegativeButton("Indietro") { _, _ -> showSavedGpsDiagnostics() }.show()
    }

    private fun exportSavedGpsDiagnostic(file: File) {
        pendingGpsDiagnosticCsv = try { file.readText(Charsets.UTF_8) } catch (e: Exception) {
            Toast.makeText(this, "Impossibile leggere la registrazione: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, REQUEST_EXPORT_GPS_TRACE)
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
        if (gpsDiagnosticSamples.size % 10 == 0) saveGpsDiagnosticCheckpoint()
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
                    weatherText.text = "METEO IP\nnon disponibile"
                }
            }
        }
    }

    private fun showGpxMenu() {
        val recordingLabel = if (recordingGpx || gpsDiagnosticRecording)
            "FERMA E SALVA registrazione GPX + CSV"
        else "AVVIA registrazione GPX + CSV"
        val choices = arrayOf(
            recordingLabel,
            "Importa GPX dal telefono",
            "Salva percorso attivo",
            "GESTISCI VIAGGI: GPX + CSV • esporta / elimina",
            "Esporta GPX attivo…",
            "DEBUG: CSV GPS e memoria"
        )
        AlertDialog.Builder(this).setTitle("Percorsi e registrazioni").setItems(choices) { _, which ->
            when (which) {
                0 -> if (recordingGpx || gpsDiagnosticRecording) finishGpxRecording(save = true) else askToRecordGpx()
                1 -> importGpx()
                2 -> saveActiveGpx()
                3 -> showSavedGpxRoutes()
                4 -> exportActiveGpx()
                5 -> showGpsDiagnosticsMenu()
            }
        }.setNegativeButton("Chiudi", null).show()
    }

    private fun askToRecordGpx() {
        if (recordingGpx) return
        AlertDialog.Builder(this)
            .setTitle("Memorizzare il viaggio?")
            .setMessage("Vuoi registrare il viaggio in GPX? Se confermi, verrà registrato anche il CSV GPS associato. Eliminando il GPX, verrà eliminato anche il suo CSV.")
            .setPositiveButton("Sì, registra") { _, _ -> startGpxRecording() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun startGpxRecording() {
        gpxRecordingPoints.clear()
        lastRecordedGpxPoint = null
        lastGpxPointTimeMs = 0L
        gpsDiagnosticSamples.clear()
        gpsDiagnosticRecording = true
        recordingGpx = true
        try {
            startForegroundService(
                Intent(this, GpsRecordingForegroundService::class.java)
                    .setAction(GpsRecordingForegroundService.ACTION_START)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Servizio in background non disponibile: tieni NEXT NAVI PRO in primo piano durante la registrazione.", Toast.LENGTH_LONG).show()
        }
        lastRawGpsLocation?.let { appendGpsDiagnosticSample(it) }
        currentLocation?.let { appendGpxRecordingPoint(it, force = true) }
        saveGpsDiagnosticCheckpoint()
        saveGpxRecordingCheckpoint()
        Toast.makeText(this, "Registrazione GPX + CSV GPS avviata insieme.", Toast.LENGTH_LONG).show()
    }

    private fun appendGpxRecordingPoint(location: Location, force: Boolean = false) {
        if (!recordingGpx || !location.hasAccuracy() || location.accuracy > 25f) return
        val now = System.currentTimeMillis()
        val point = NavPoint(
            location.latitude,
            location.longitude,
            location.time.takeIf { it > 0L } ?: now
        )
        val previous = lastRecordedGpxPoint
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
        // Durable paired checkpoint: if Android kills the process, the last saved track remains recoverable.
        if (gpxRecordingPoints.size % 10 == 0) saveGpxRecordingCheckpoint()
    }

    private fun gpxRecordingCheckpointFile(): File =
        File(gpxDirectory(), "gpx_recording_in_progress.gpx")

    private fun saveGpxRecordingCheckpoint() {
        if (!recordingGpx || gpxRecordingPoints.isEmpty()) return
        try {
            val temp = File(gpxDirectory(), "gpx_recording_in_progress.tmp")
            FileOutputStream(temp).use { GpxRouteService.write(it, destination?.name ?: "Viaggio in corso", gpxRecordingPoints.toList()) }
            val checkpoint = gpxRecordingCheckpointFile()
            if (!temp.renameTo(checkpoint)) {
                temp.copyTo(checkpoint, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
            // Keep the in-memory recording active; finalization retries and reports storage errors.
        }
    }

    private fun finishGpxRecording(save: Boolean, showResult: Boolean = true) {
        if (!recordingGpx) return
        // Write both checkpoints before clearing memory or changing the active state.
        saveGpxRecordingCheckpoint()
        saveGpsDiagnosticCheckpoint()
        recordingGpx = false
        val points = gpxRecordingPoints.toList()
        val samples = gpsDiagnosticSamples.toList()
        gpsDiagnosticRecording = false
        stopService(Intent(this, GpsRecordingForegroundService::class.java))
        if (!save) {
            // Never silently discard an interrupted/short session: leave checkpoints for recovery.
            if (showResult) Toast.makeText(this, "Registrazione fermata. I dati parziali sono conservati per il recupero.", Toast.LENGTH_LONG).show()
            return
        }
        if (points.isEmpty() && samples.isEmpty()) {
            if (showResult) Toast.makeText(this, "Registrazione vuota: nessun dato da salvare.", Toast.LENGTH_LONG).show()
            return
        }
        thread(name = "save-recorded-gpx-csv") {
            var finalGpx: File? = null
            var finalCsv: File? = null
            try {
                val label = (destination?.name ?: "Viaggio registrato")
                    .replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifBlank { "Viaggio registrato" }.take(60)
                val baseName = "${System.currentTimeMillis()}_$label"
                val gpxDir = gpxDirectory()
                val csvDir = gpsDiagnosticsDirectory()
                val gpxTemp = File(gpxDir, "$baseName.gpx.tmp")
                val csvTemp = File(csvDir, "$baseName.csv.tmp")
                if (points.isNotEmpty()) {
                    FileOutputStream(gpxTemp).use { GpxRouteService.write(it, label, points) }
                    finalGpx = File(gpxDir, "$baseName.gpx")
                }
                // Always persist the CSV companion, including its header if Android delivered no GPS fixes.
                FileOutputStream(csvTemp).use { it.write(GpsTraceDiagnostics.toCsv(samples).toByteArray(Charsets.UTF_8)) }
                finalCsv = File(csvDir, "$baseName.csv")
                // Publish final names only after each temporary file has been completely written.
                if (gpxTemp.exists() && !gpxTemp.renameTo(finalGpx!!)) gpxTemp.copyTo(finalGpx!!, overwrite = true)
                if (csvTemp.exists() && !csvTemp.renameTo(finalCsv!!)) csvTemp.copyTo(finalCsv!!, overwrite = true)
                val gpxReady = finalGpx?.isFile == true && finalGpx!!.length() > 0L
                val csvReady = finalCsv?.isFile == true && finalCsv!!.length() > 0L
                if (!gpxReady || !csvReady) {
                    throw IllegalStateException("Una o entrambe le registrazioni non sono state scritte completamente")
                }
                // Only delete checkpoints after both paired files are safely present.
                gpxRecordingCheckpointFile().delete()
                File(gpxDirectory(), "gpx_recording_in_progress.tmp").delete()
                File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv").delete()
                File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.tmp").delete()
                runOnUiThread {
                    gpxRecordingPoints.clear()
                    gpsDiagnosticSamples.clear()
                    lastRecordedGpxPoint = null
                    if (showResult) Toast.makeText(this, "Viaggio salvato insieme: GPX + CSV GPS • ${points.size} punti • ${samples.size} campioni.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                // Do not remove checkpoints on failure; the user can recover them from the library.
                runOnUiThread {
                    if (showResult) Toast.makeText(this, "Salvataggio non completato: i dati parziali sono conservati. ${error.message ?: "Errore di scrittura"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun recoverInterruptedPairedRecording() {
        val gpxCheckpoint = gpxRecordingCheckpointFile()
        val csvCheckpoint = File(gpsDiagnosticsDirectory(), "gps_trace_in_progress.csv")
        if (!gpxCheckpoint.isFile && !csvCheckpoint.isFile) return
        val base = "Viaggio_interrotto_${System.currentTimeMillis()}"
        var recoveredAny = false
        try {
            if (gpxCheckpoint.isFile && gpxCheckpoint.length() > 0L) {
                val target = File(gpxDirectory(), "$base.gpx")
                if (gpxCheckpoint.renameTo(target)) recoveredAny = true
                else { gpxCheckpoint.copyTo(target, overwrite = false); if (target.length() > 0L) { gpxCheckpoint.delete(); recoveredAny = true } }
            }
        } catch (_: Exception) { /* Keep original checkpoint for another recovery attempt. */ }
        try {
            if (csvCheckpoint.isFile && csvCheckpoint.length() > 0L) {
                val target = File(gpsDiagnosticsDirectory(), "$base.csv")
                if (csvCheckpoint.renameTo(target)) recoveredAny = true
                else { csvCheckpoint.copyTo(target, overwrite = false); if (target.length() > 0L) { csvCheckpoint.delete(); recoveredAny = true } }
            }
        } catch (_: Exception) { /* Keep original checkpoint for another recovery attempt. */ }
        if (recoveredAny) {
            android.util.Log.i("NEXT-NAVI-PRO", "Recovered interrupted GPX/CSV recording as $base")
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
        val files = gpxDirectory().listFiles()?.filter { it.isFile && it.extension.equals("gpx", true) && it.name != "gpx_recording_in_progress.gpx" }
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
                val pairedCsvFiles = tracks.map { (gpxFile, _) ->
                    File(gpsDiagnosticsDirectory(), gpxFile.nameWithoutExtension + ".csv")
                }.filter { it.isFile }
                val csvBytes = pairedCsvFiles.sumOf { it.length() }
                val totalMb = totalBytes / (1024.0 * 1024.0)
                val csvMb = csvBytes / (1024.0 * 1024.0)
                // Android AlertDialog cannot reliably show both setMessage() and setItems()
                // in the same dialog: on some devices the message replaces the selectable list.
                // Put the storage summary in the title so every saved GPX remains selectable.
                val libraryTitle = String.format(
                    Locale.getDefault(),
                    "Libreria GPX • %d • %.2f MB | CSV: %d • %.2f MB",
                    tracks.size, totalMb, pairedCsvFiles.size, csvMb
                )
                AlertDialog.Builder(this).setTitle(libraryTitle)
                    .setItems(tracks.map { (_, track) ->
                        track.name + " • " + String.format(Locale.getDefault(), "%.1f km", track.distanceKm)
                    }.toTypedArray()) { _, which -> showSavedGpxActions(tracks[which].first, tracks[which].second) }
                    .setNegativeButton("Chiudi", null).show()
            }
        }
    }

    private fun showSavedGpxActions(file: File, track: GpxTrack) {
        val pairedCsv = File(gpsDiagnosticsDirectory(), file.nameWithoutExtension + ".csv")
        val csvInfo = if (pairedCsv.isFile) {
            "CSV GPS: ${String.format(Locale.getDefault(), "%.2f", pairedCsv.length() / (1024.0 * 1024.0))} MB"
        } else {
            "CSV GPS: non presente"
        }
        // Non combinare setMessage() e setItems(): su alcuni dispositivi Android
        // il messaggio può sostituire l'elenco delle azioni. Tutte le azioni restano selezionabili.
        val title = "${track.name}\n${String.format(Locale.getDefault(), "%.1f", track.distanceKm)} km • ${track.points.size} punti\n$csvInfo"
        val actions = arrayOf(
            "Segui questa traccia GPX",
            "Visualizza sulla mappa",
            "Esporta / condividi GPX…",
            if (pairedCsv.isFile) "Esporta CSV GPS associato…" else "CSV GPS associato non disponibile",
            "Elimina solo GPX",
            if (pairedCsv.isFile) "Elimina GPX + CSV associato" else "Elimina GPX"
        )
        AlertDialog.Builder(this).setTitle(title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> loadSavedGpx(file)
                    1 -> {
                        activateGpxTrack(track)
                        enterMapFullScreen()
                    }
                    2 -> exportGpxTrack(track)
                    3 -> if (pairedCsv.isFile) exportSavedGpsDiagnostic(pairedCsv) else
                        Toast.makeText(this, "Questo GPX non ha un CSV GPS associato.", Toast.LENGTH_LONG).show()
                    4 -> AlertDialog.Builder(this).setTitle("Eliminare solo il GPX?")
                        .setMessage("Verrà eliminato ${file.name}. Il CSV associato, se presente, resterà nella memoria.")
                        .setNegativeButton(appText("Annulla"), null)
                        .setPositiveButton("Elimina GPX") { _, _ ->
                            val deleted = file.delete()
                            Toast.makeText(this, if (deleted) "GPX eliminato." else "Impossibile eliminare il GPX.", Toast.LENGTH_LONG).show()
                            showSavedGpxRoutes()
                        }.show()
                    5 -> AlertDialog.Builder(this).setTitle("Eliminare GPX e CSV associato?")
                        .setMessage("Verranno eliminati definitivamente ${file.name} e il CSV GPS con lo stesso nome, se presente.")
                        .setNegativeButton(appText("Annulla"), null)
                        .setPositiveButton("Elimina entrambi") { _, _ ->
                            val deleted = file.delete()
                            val csvDeleted = !pairedCsv.exists() || pairedCsv.delete()
                            val message = when {
                                !deleted -> "Impossibile eliminare il GPX."
                                !csvDeleted -> "GPX eliminato, ma non è stato possibile eliminare il CSV associato."
                                else -> "GPX e CSV associato eliminati; memoria liberata."
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            showSavedGpxRoutes()
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
            routeText.text = "TRACCIA GPX\nSegui la linea sulla mappa • svolte vocali non disponibili per questa traccia"
            routeText.textSize = 14f
        }
        if (::routeButton.isInitialized) routeButton.text = appText("ANNULLA TRACCIA GPX")
        announcedManeuverIndex = -1
        announcedStage = 0
        drawRoute(routeResult!!, true)
        updateRouteMetrics(currentLocation)
        Toast.makeText(this, "Traccia GPX caricata: ${track.points.size} punti.", Toast.LENGTH_LONG).show()
        AlertDialog.Builder(this)
            .setTitle("Navigazione GPX")
            .setMessage("Vuoi provare ad agganciare la traccia alla rete stradale e ottenere le svolte dal motore di routing online? Se il matching fallisce, resterà disponibile la traccia originale senza istruzioni inventate.")
            .setPositiveButton("Calcola svolte guidate") { _, _ -> calculateGpxGuidedRoute(track) }
            .setNegativeButton("Segui solo la traccia", null)
            .show()
    }

    private fun calculateGpxGuidedRoute(track: GpxTrack) {
        if (track.hasDiscontinuities) {
            val message = "La traccia contiene segmenti separati o un salto GPS importante. Non creo un percorso unico per evitare collegamenti e svolte inventati. Puoi comunque esportare il GPX originale."
            if (::routeText.isInitialized) routeText.text = "GPX DISCONTINUO • guida non avviata"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        if (isCalculatingRoute) return
        isCalculatingRoute = true
        if (::routeButton.isInitialized) routeButton.isEnabled = false
        if (::routeText.isInitialized) routeText.text = "AGGANCIO GPX ALLA RETE STRADALE…"
        thread(name = "gpx-trace-matching") {
            try {
                val result = NavigationServices.calculateRouteFromTrace(track.points, motorcycleProfile)
                runOnUiThread {
                    isCalculatingRoute = false
                    if (::routeButton.isInitialized) {
                        routeButton.isEnabled = true
                        routeButton.text = appText("ANNULLA PERCORSO GPX")
                    }
                    routeResult = result
                    resetRouteProgress()
                    announcedManeuverIndex = -1
                    announcedStage = 0
                    drawRoute(result, true)
                    val durationMinutes = (result.durationSeconds / 60.0).toInt()
                    val first = result.maneuvers.firstOrNull()
                    val instruction = first?.let { ManeuverIconResolver.correctedInstruction(it.type, it.instruction) } ?: result.firstInstruction
                    routeText.text = String.format(Locale.ITALY, "GPX • %.1f km • circa %d min\n%s", result.distanceKm, durationMinutes, instruction)
                    updateTurnGuidance(currentLocation)
                    Toast.makeText(this, "Traccia agganciata: ${result.maneuvers.size} manovre restituite dal motore stradale.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    isCalculatingRoute = false
                    if (::routeButton.isInitialized) routeButton.isEnabled = true
                    routeText.text = "GPX SENZA SVOLTE GUIDATE • traccia originale mantenuta"
                    Toast.makeText(this, "Matching GPX non riuscito: ${error.message ?: "servizio non disponibile"}. Puoi seguire la traccia originale.", Toast.LENGTH_LONG).show()
                }
            }
        }
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
            .setPositiveButton(appText("Salva")) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("favorite_" + name, selected.point.latitude.toString() + "," + selected.point.longitude)
                        .apply()
                    Toast.makeText(this, "Preferito salvato.", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(appText("Annulla"), null).show()
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
            }.setNegativeButton(appText("Annulla"), null).show()
    }

    private fun initializeJarvis() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedName = prefs.getString("jarvis_user_name", "").orEmpty().trim()
        val assistantName = prefs.getString("jarvis_assistant_name", "Jarvis").orEmpty().trim()
            .ifBlank { "Jarvis" }
        jarvisStatusText.text = "$assistantName • " + appText("pronto • premi per parlare")
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val language = Locale.getDefault().language.lowercase(Locale.ROOT)
        val personalGreeting = if (savedName.isNotBlank()) savedName else ""
        val greeting = when (language) {
            "it" -> (if (hour in 5..11) "Buongiorno" else if (hour in 12..17) "Buon pomeriggio" else "Buonasera") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " sono $assistantName, il tuo assistente di navigazione NEXT NAVI PRO. Sono pronto. Dimmi dove vuoi andare e ti guiderò."
            "es" -> (if (hour in 5..12) "Buenos días" else if (hour in 13..20) "Buenas tardes" else "Buenas noches") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " soy $assistantName, tu asistente de navegación de NEXT NAVI PRO. Estoy listo. Dime adónde quieres ir y te guiaré."
            "fr" -> (if (hour in 5..17) "Bonjour" else "Bonsoir") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " je suis $assistantName, votre assistant de navigation NEXT NAVI PRO. Je suis prêt. Dites-moi où vous voulez aller et je vous guiderai."
            "de" -> (if (hour in 5..10) "Guten Morgen" else if (hour in 11..17) "Guten Tag" else "Guten Abend") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " ich bin $assistantName, dein NEXT NAVI PRO Navigationsassistent. Ich bin bereit. Sag mir, wohin du möchtest, und ich führe dich."
            "pt" -> (if (hour in 5..12) "Bom dia" else if (hour in 13..19) "Boa tarde" else "Boa noite") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " sou $assistantName, o teu assistente de navegação NEXT NAVI PRO. Estou pronto. Diz-me para onde queres ir e vou orientar-te."
            "nl" -> (if (hour in 5..11) "Goedemorgen" else if (hour in 12..17) "Goedemiddag" else "Goedenavond") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " ik ben $assistantName, je NEXT NAVI PRO-navigatieassistent. Ik ben klaar. Vertel me waar je heen wilt, dan begeleid ik je."
            else -> (if (hour in 5..11) "Good morning" else if (hour in 12..17) "Good afternoon" else "Good evening") +
                (if (personalGreeting.isNotBlank()) " $personalGreeting," else "") +
                " I'm $assistantName, your NEXT NAVI PRO navigation assistant. I'm ready. Tell me where you'd like to go and I'll guide you."
        }
        speakJarvis(greeting)
        // Preserve first-run name setup, but do not make the startup greeting depend on it.
        if (savedName.isBlank() && !prefs.getBoolean("jarvis_user_name_prompted", false)) {
            prefs.edit().putBoolean("jarvis_user_name_prompted", true).apply()
            val nameInput = EditText(this).apply {
                hint = appText("Il tuo nome (facoltativo)")
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            AlertDialog.Builder(this)
                .setTitle(appText("Personalizza Jarvis"))
                .setMessage(appText("Come vuoi che ti chiami? È facoltativo e puoi saltare."))
                .setView(nameInput)
                .setPositiveButton(appText("Salva")) { _, _ ->
                    val name = nameInput.text.toString().trim().take(40)
                    if (name.isNotBlank()) prefs.edit().putString("jarvis_user_name", name).apply()
                }
                .setNegativeButton(appText("Salta"), null)
                .show()
        }
    }

    private fun applyJarvisVoicePreference() {
        val tts = voiceGuide ?: return
        val language = Locale.getDefault().language
        val available = tts.voices?.filter { it.locale.language.equals(language, ignoreCase = true) }.orEmpty()
        if (available.isEmpty()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedVoiceName = prefs.getString("jarvis_voice_name", "").orEmpty()
        val mode = prefs.getString("jarvis_voice_style", "female").orEmpty()
        if (mode == "auto") {
            // Leave the TTS engine's own default voice selected, while keeping the system language.
            tts.setLanguage(Locale.getDefault())
            tts.setPitch(prefs.getFloat("jarvis_voice_pitch", 1.0f).coerceIn(0.7f, 1.4f))
            return
        }
        val exactVoice = if (mode == "custom") available.firstOrNull { it.name == savedVoiceName } else null
        val voice = exactVoice ?: when (mode) {
            "male" -> available.firstOrNull { voice ->
                val n = voice.name.lowercase(Locale.ROOT)
                listOf("male", "mascul", "man", "david", "daniel", "jorge", "thomas", "alex").any { n.contains(it) }
            }
            "female" -> available.firstOrNull { voice ->
                val n = voice.name.lowercase(Locale.ROOT)
                listOf("female", "fem", "woman", "alice", "lucia", "elsa", "paola", "sara", "chiara", "kda", "standard-a").any { n.contains(it) }
            }
            else -> null
        } ?: available.firstOrNull { !it.isNetworkConnectionRequired } ?: available.first()
        try {
            tts.voice = voice
            prefs.edit().putString("jarvis_voice_name", voice.name).apply()
        } catch (_: Exception) {
            // Keep the device's default voice if a vendor TTS engine rejects a voice.
        }
        val pitch = prefs.getFloat("jarvis_voice_pitch", 1.0f)
        tts.setPitch(pitch.coerceIn(0.7f, 1.4f))
    }

    private fun showJarvisVoiceSettings() {
        val tts = voiceGuide
        val language = Locale.getDefault().language
        val available = tts?.voices?.filter { it.locale.language.equals(language, ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
        val labels = mutableListOf(
            appText("Automatica (voce predefinita del dispositivo)"),
            appText("Femminile"),
            appText("Maschile")
        )
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedStyle = prefs.getString("jarvis_voice_style", "female").orEmpty()
        val selected = when (savedStyle) {
            "auto" -> 0
            "female" -> 1
            "male" -> 2
            "custom" -> (available.indexOfFirst { it.name == prefs.getString("jarvis_voice_name", "") } + 3).coerceAtLeast(0)
            else -> 0
        }
        available.forEach { labels.add(it.name + if (it.isNetworkConnectionRequired) " • online" else " • offline") }
        AlertDialog.Builder(this)
            .setTitle(appText("Voce di Jarvis") + " • " + Locale.getDefault().displayLanguage)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                when (which) {
                    0 -> prefs.edit().putString("jarvis_voice_style", "auto").remove("jarvis_voice_name").apply()
                    1 -> prefs.edit().putString("jarvis_voice_style", "female").remove("jarvis_voice_name").apply()
                    2 -> prefs.edit().putString("jarvis_voice_style", "male").remove("jarvis_voice_name").apply()
                    else -> {
                        val voice = available.getOrNull(which - 3)
                        if (voice != null) prefs.edit().putString("jarvis_voice_style", "custom")
                            .putString("jarvis_voice_name", voice.name).apply()
                    }
                }
                applyJarvisVoicePreference()
                dialog.dismiss()
                speakJarvis(appText("Ciao! Questa è la voce selezionata per Jarvis."))
            }
            .setNegativeButton(appText("Chiudi"), null)
            .show()
    }

    private fun appText(italian: String): String =
        NextNaviStrings.translate(italian, Locale.getDefault())

    private fun showJarvisNameSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val nameInput = EditText(this).apply {
            setText(prefs.getString("jarvis_assistant_name", "Jarvis").orEmpty())
            hint = appText("Nome dell'assistente")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle(appText("Nome dell'assistente vocale"))
            .setMessage(appText("Scegli come vuoi chiamare l'assistente di navigazione."))
            .setView(nameInput)
            .setPositiveButton(appText("Salva")) { _, _ ->
                val name = nameInput.text.toString().trim().take(30).ifBlank { "Jarvis" }
                prefs.edit().putString("jarvis_assistant_name", name).apply()
                if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "$name • " + appText("pronto • premi per parlare")
                Toast.makeText(this, "Assistente rinominato in $name", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(appText("Ripristina Jarvis")) { _, _ ->
                prefs.edit().putString("jarvis_assistant_name", "Jarvis").apply()
                if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Jarvis • " + appText("pronto • premi per parlare")
                Toast.makeText(this, "Nome ripristinato: Jarvis", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun localizedJarvisPrompt(): String = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "it" -> "Parla con Jarvis"
        "es" -> "Habla con Jarvis"
        "fr" -> "Parlez avec Jarvis"
        "de" -> "Sprich mit Jarvis"
        "pt" -> "Fala com Jarvis"
        "nl" -> "Praat met Jarvis"
        else -> "Talk to Jarvis"
    }

    private fun speakJarvis(message: String) {
        if (!voiceGuideReady) {
            pendingJarvisSpeech = message
            return
        }
        voiceGuide?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.currentTimeMillis())
    }

    private fun onJarvisButtonPressed() {
        if (jarvisListening) {
            speechRecognizer?.cancel()
            jarvisListening = false
            jarvisButton.text = "🎙 JARVIS"
            jarvisStatusText.text = "Ascolto annullato"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_AUDIO)
            return
        }
        startJarvisListening()
    }

    private fun startJarvisListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            jarvisStatusText.text = "Riconoscimento vocale non disponibile"
            speakJarvis("Il riconoscimento vocale non è disponibile su questo dispositivo.")
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Ti ascolto…" }
                    }
                    override fun onBeginningOfSpeech() {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Sto ascoltando…" }
                    }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Elaboro…" }
                    }
                    override fun onError(error: Int) {
                        runOnUiThread {
                            jarvisListening = false
                            if (::jarvisButton.isInitialized) jarvisButton.text = "🎙 JARVIS"
                            if (::jarvisStatusText.isInitialized) {
                                jarvisStatusText.text = when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Non ho capito • riprova"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permesso microfono mancante"
                                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Rete non disponibile • riprova"
                                    else -> "Ascolto terminato • riprova"
                                }
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        runOnUiThread {
                            jarvisListening = false
                            if (::jarvisButton.isInitialized) jarvisButton.text = "🎙 JARVIS"
                            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty().trim()
                            if (transcript.isBlank()) {
                                jarvisStatusText.text = "Non ho ricevuto parole • riprova"
                            } else {
                                jarvisStatusText.text = "Hai detto: $transcript"
                                handleJarvisCommand(transcript)
                            }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, localizedJarvisPrompt())
        }
        try {
            jarvisListening = true
            jarvisButton.text = "■ STOP"
            jarvisStatusText.text = "Avvio microfono…"
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            jarvisListening = false
            jarvisButton.text = "🎙 JARVIS"
            jarvisStatusText.text = "Impossibile avviare il microfono"
            speakJarvis("Non riesco ad avviare il microfono. Puoi riprovare.")
        }
    }

    private fun handleJarvisCommand(transcript: String) {
        // A broad locality is a conversation, not yet a precise navigation target.
        // Interpret the rider's next spoken answer locally; never ask the online AI to
        // pretend it can change navigation state.
        val pendingLocality = pendingJarvisLocality
        if (!pendingLocality.isNullOrBlank()) {
            val phrase = normalizeJarvisSpeech(transcript)
            val addressPrompt = setOf("indirizzo", "un indirizzo", "indirizzo specifico", "civico", "una via")
            val centrePhrases = setOf("centro", "in centro", "centro citta", "centro della citta", "centro cittadino")
            val cancelPhrases = setOf("annulla", "lascia stare", "niente", "cancella")
            when {
                phrase in cancelPhrases -> {
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    speakJarvis("Va bene, annullo la scelta di destinazione.")
                    return
                }
                phrase in centrePhrases -> {
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    destinationInput.setText("centro di " + pendingLocality)
                    searchDestination(voiceCommand = true)
                    return
                }
                phrase in addressPrompt || phrase.contains("indirizzo specifico") || phrase == "una strada" -> {
                    pendingJarvisAddressMode = true
                    speakJarvis("Dimmi la via e, se lo conosci, il numero civico a " + pendingLocality + ".")
                    return
                }
                pendingJarvisAddressMode -> {
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    destinationInput.setText(transcript.trim() + ", " + pendingLocality)
                    searchDestination(voiceCommand = true)
                    return
                }
                listOf("negozio", "attivita", "punto di interesse", "ristorante", "farmacia", "supermercato",
                    "mercadona", "bar", "hotel", "benzinaio", "officina", "parcheggio", "spiaggia",
                    "museo", "bancomat", "ospedale", "stazione", "centro commerciale", "negozi", "ristoranti")
                    .any { phrase.contains(it) } -> {
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    destinationInput.setText(transcript.trim() + " a " + pendingLocality)
                    searchDestination(voiceCommand = true)
                    return
                }
                JarvisCommandParser.parse(transcript) is JarvisCommand.Navigate -> {
                    // A new explicit destination replaces the previous clarification.
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                }
                else -> {
                    speakJarvis("Per " + pendingLocality + ", preferisci il centro, un indirizzo preciso, oppure un'attività o un punto di interesse? Puoi dirmi, per esempio, farmacia o Mercadona.")
                    return
                }
            }
        }
        when (val command = JarvisCommandParser.parse(transcript)) {
            is JarvisCommand.Navigate -> {
                if (command.destination.equals("casa", ignoreCase = true)) {
                    if (getSharedPreferences(PREFS, MODE_PRIVATE).getString("home_name", null).isNullOrBlank()) {
                        speakJarvis("Non hai ancora salvato un indirizzo di casa. Impostalo dal pulsante Casa quando sei fermo.")
                    } else {
                        speakJarvis("Calcolo il percorso verso casa. Controlla le indicazioni prima di partire.")
                        navigateHome()
                    }
                } else {
                    destinationInput.setText(command.destination)
                    searchDestination(voiceCommand = true)
                }
            }
            JarvisCommand.WhereAmI -> {
                val location = currentLocation
                if (location == null || !location.hasAccuracy()) {
                    speakJarvis("Sto ancora cercando una posizione GPS affidabile. Non voglio inventare dove ti trovi.")
                } else {
                    val accuracy = location.accuracy.roundToInt().coerceAtLeast(0)
                    speakJarvis("Il GPS è agganciato. Precisione dichiarata circa $accuracy metri.")
                }
            }
            JarvisCommand.Remaining -> {
                if (routeResult == null) {
                    speakJarvis("Non c'è un percorso attivo. Dimmi una destinazione, oppure cercala sullo schermo.")
                } else {
                    speakJarvis("Dati del percorso: " + routeMetricsText.text.toString().replace("•", ",") +
                        ". Prossima indicazione: " + routeText.text.toString().replace("\n", ", "))
                }
            }
            JarvisCommand.Repeat -> {
                if (routeResult == null) speakJarvis("Non c'è un'indicazione attiva da ripetere.")
                else speakJarvis(routeText.text.toString().replace("\n", ", "))
            }
            JarvisCommand.Silence -> {
                pendingJarvisSpeech = null
                voiceGuide?.stop()
                jarvisStatusText.text = "Va bene. Voce in pausa."
            }
            JarvisCommand.Help -> speakJarvis(
                "Posso cercare una destinazione, andare a casa se l'hai salvata, dirti la precisione GPS, " +
                    "ripetere la prossima manovra o leggere la distanza residua. Puoi chiedermi una destinazione o un punto di interesse. " +
                    "Non serve trasformare ogni rotonda in una scena d'azione."
            )
            JarvisCommand.ShowResults -> showJarvisPendingResults()
            JarvisCommand.Unknown -> askJarvisOnline(transcript)
        }
    }

    private fun askJarvisOnline(transcript: String) {
        // The online assistant answers conversational requests only. Navigation commands
        // are handled locally; no GPS-based stationary gate blocks ordinary use.
        if (!JarvisOnlineAiClient.isConfigured()) {
            speakJarvis("L'assistente AI online non è ancora configurato. I comandi offline restano disponibili; non invio dati a servizi non impostati.")
            return
        }
        jarvisStatusText.text = "Jarvis AI • elaborazione online…"
        JarvisOnlineAiClient.ask(transcript) { result ->
            runOnUiThread {
                result.onSuccess { reply ->
                    jarvisStatusText.text = "Jarvis AI • risposta ricevuta"
                    // Online text is conversational only: it cannot change route, speed limit or GPS state.
                    speakJarvis(reply)
                }.onFailure { error ->
                    val detail = error.message.orEmpty()
                    jarvisStatusText.text = "Jarvis AI • errore: " + detail.take(90)
                    val spoken = when {
                        detail.contains("HTTP_503") ->
                            "Il server Jarvis risponde, ma non è configurato correttamente. Controllare il secret Gemini nel Worker Cloudflare."
                        detail.contains("provider HTTP 401") || detail.contains("provider HTTP 403") ->
                            "Il server Jarvis raggiunge Google, ma la chiave Gemini o i suoi permessi non sono validi."
                        detail.contains("provider HTTP 400") ->
                            "Google Gemini ha rifiutato la richiesta. Potrebbe essere necessario correggere il modello configurato nel Worker."
                        detail.contains("HTTP_429") || detail.contains("provider HTTP 429") ->
                            "È stato raggiunto un limite di richieste o la quota gratuita di Gemini. Riprova più tardi."
                        detail.contains("HTTP_404") ->
                            "Il server Jarvis risponde con indirizzo non trovato. L'endpoint configurato nell'app potrebbe essere errato."
                        detail.contains("HTTP_502") ->
                            "Il proxy Jarvis è raggiungibile, ma il servizio Gemini ha restituito un errore. Controllare modello, chiave e quota."
                        detail.contains("SocketTimeoutException") || detail.contains("timed out", ignoreCase = true) ->
                            "Il server Jarvis non risponde in tempo. Controlla la connessione e riprova."
                        detail.contains("UnknownHostException") || detail.contains("ConnectException") ->
                            "Non riesco a collegarmi al server Jarvis. Controlla internet e indirizzo del servizio."
                        else ->
                            "Jarvis online ha un problema tecnico. Dettaglio: " + detail.take(100) +
                                ". I comandi offline restano disponibili."
                    }
                    speakJarvis(spoken)
                }
            }
        }
    }

    private fun showJarvisPendingResults() {
        val results = pendingJarvisPlaces
        if (results.isEmpty()) {
            speakJarvis("Non ci sono risultati in attesa. Dimmi, per esempio, portami a Santa Cruz.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Scegli destinazione • " + results.size + " risultati")
            .setItems(results.map { it.name + if (it.description.isBlank()) "" else " • " + it.description }.toTypedArray()) { _, which ->
                results.getOrNull(which)?.let { selectJarvisOrSearchDestination(it) }
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
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
            .setNegativeButton(appText("Annulla"), null)
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

    private fun searchDestination(voiceCommand: Boolean = false) {
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
                        if (voiceCommand) speakJarvis("Non ho trovato risultati per $query. Prova con nome della località o della strada.")
                        return@runOnUiThread
                    }
                    if (voiceCommand) {
                        val locality = sortedResults.firstOrNull { isBroadLocalityMatch(it, query) }
                        if (locality != null) {
                            pendingJarvisPlaces = emptyList()
                            pendingJarvisLocality = locality.name
                            pendingJarvisAddressMode = false
                            routeText.text = "LOCALITÀ: ${locality.name}\nScegli una destinazione più precisa con Jarvis"
                            jarvisStatusText.text = "Jarvis • attende il tipo di destinazione"
                            speakJarvis("Ho trovato la località " + locality.name +
                                ". Vuoi arrivare in centro, a un indirizzo preciso, oppure cerchi un'attività, un negozio o un punto di interesse?")
                            return@runOnUiThread
                        }
                    }
                    if (voiceCommand) {
                        pendingJarvisPlaces = sortedResults
                        // A single real result can be routed immediately. For multiple matches,
                        // present the actual geocoder results instead of a flaky GPS-stop gate.
                        if (sortedResults.size == 1) {
                            rememberSearch(query)
                            selectJarvisOrSearchDestination(sortedResults.first(), autoStart = true)
                            return@runOnUiThread
                        }
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
                            selectJarvisOrSearchDestination(sortedResults[which])
                        }
                        .setNegativeButton(appText("Annulla"), null)
                        .show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    isSearching = false
                    searchButton.isEnabled = true
                    routeText.text = "RICERCA NON DISPONIBILE • " + (error.message ?: "Errore di rete")
                    if (voiceCommand) speakJarvis("La ricerca online non è disponibile in questo momento.")
                }
            }
        }
    }

    private fun normalizeJarvisSpeech(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(Locale.ITALIAN), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun isBroadLocalityMatch(place: PlaceResult, query: String): Boolean {
        val kind = place.kind.lowercase(Locale.ROOT)
        val localityKinds = setOf("city", "town", "village", "hamlet", "municipality", "suburb",
            "quarter", "neighbourhood", "neighborhood", "district", "locality", "borough")
        val placeName = normalizeJarvisSpeech(place.name)
        val spokenQuery = normalizeJarvisSpeech(query)
        // Only ask a follow-up for an actual locality-type feature whose name matches
        // the request. Do not misclassify a business or an unrelated nearby result.
        return kind in localityKinds && (placeName == spokenQuery ||
            spokenQuery.startsWith(placeName + " ") || placeName.startsWith(spokenQuery + " "))
    }

    private fun selectJarvisOrSearchDestination(place: PlaceResult, autoStart: Boolean = false) {
        destination = place
        pendingJarvisPlaces = emptyList()
        routeResult = null
        resetRouteProgress()
        routeMetricsText.text = "— km  •  ARRIVO —"
        val routeSource = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
        routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
        destinationInput.setText(place.name)
        routeText.text = appText("DESTINAZIONE: ") + place.name + "\n" + appText("Premi AVVIA PERCORSO")
        routeButton.text = appText("AVVIA PERCORSO")
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.point.latitude, place.point.longitude), 14.0), 700)
        if (autoStart) {
            speakJarvis("Ho trovato " + place.name + ". Calcolo il percorso; verifica le indicazioni e la strada in sicurezza.")
            calculateSelectedRoute()
        } else {
            speakJarvis("Destinazione selezionata: " + place.name + ". Controlla il percorso prima di partire.")
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
                    origin, target.point, motorcycle = motorcycleProfile
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
                    routeButton.text = appText("ANNULLA PERCORSO")
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
        stopAndSaveGpsDiagnosticRecording("percorso terminato")
        routeResult = null
        resetRouteProgress()
        if (::routeMetricsText.isInitialized) routeMetricsText.text = "— km  •  ARRIVO —"
        announcedManeuverIndex = -1
        announcedStage = 0
        if (::maneuverIcon.isInitialized) maneuverIcon.text = "↑"
        destination = null
        refreshDestinationMarker()
        if (::routeText.isInitialized) routeText.text = appText("Nessun percorso attivo")
        if (::routeMetricsText.isInitialized) routeMetricsText.text = "— km  •  ARRIVO —"
        if (::routeButton.isInitialized) routeButton.text = appText("AVVIA PERCORSO")
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

    private fun readSavedPois(): List<Triple<String, Double, Double>> =
        getSharedPreferences(PREFS, MODE_PRIVATE).all
            .filterKeys { it.startsWith("poi_") }
            .mapNotNull { (key, value) ->
                val coords = (value as? String)?.split(",") ?: return@mapNotNull null
                val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                Triple(key.removePrefix("poi_"), lat, lon)
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }

    private fun savedPoisStorageBytes(): Long =
        getSharedPreferences(PREFS, MODE_PRIVATE).all
            .filterKeys { it.startsWith("poi_") }
            .entries.sumOf { (key, value) -> key.toByteArray(Charsets.UTF_8).size.toLong() + (value?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0) }

    private fun showSavedPois() {
        val saved = readSavedPois()
        val bytes = savedPoisStorageBytes()
        if (saved.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Luoghi salvati")
                .setMessage("Non hai ancora salvato luoghi di interesse.\nMemoria usata dai luoghi: 0 KB")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = saved.map { it.first }.toTypedArray()
        val kb = String.format(Locale.getDefault(), "%.2f", bytes / 1024.0)
        AlertDialog.Builder(this)
            .setTitle("Luoghi salvati • ${saved.size}")
            .setMessage("Memoria stimata dei dati dei luoghi: $kb KB.\nSeleziona un luogo per usarlo o eliminarlo.")
            .setItems(labels) { _, which -> showSavedPoiActions(saved[which]) }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun showSavedPoiActions(poi: Triple<String, Double, Double>) {
        AlertDialog.Builder(this).setTitle(poi.first)
            .setMessage("Coordinate: ${String.format(Locale.US, "%.6f", poi.second)}, ${String.format(Locale.US, "%.6f", poi.third)}")
            .setItems(arrayOf("Usa come destinazione", "Elimina luogo")) { _, which ->
                if (which == 0) {
                    destination = PlaceResult(poi.first, NavPoint(poi.second, poi.third), "Luogo salvato")
                    refreshDestinationMarker()
                    destinationInput.setText(poi.first)
                    routeText.text = "DESTINAZIONE: " + poi.first + "\nPremi AVVIA PERCORSO"
                    exitMapFullScreen()
                    calculateSelectedRoute()
                } else {
                    AlertDialog.Builder(this).setTitle("Eliminare il luogo?")
                        .setMessage("«${poi.first}» verrà rimosso dai luoghi salvati.")
                        .setNegativeButton(appText("Annulla"), null)
                        .setPositiveButton("Elimina") { _, _ ->
                            val removed = getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("poi_" + poi.first).commit()
                            refreshSavedPoiMarkers()
                            Toast.makeText(this, if (removed) "Luogo eliminato." else "Impossibile eliminare il luogo.", Toast.LENGTH_LONG).show()
                            showSavedPois()
                        }.show()
                }
            }.setNegativeButton("Indietro") { _, _ -> showSavedPois() }.show()
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
                        .setPositiveButton(appText("Salva")) { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotBlank()) {
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString("poi_" + name, point.latitude.toString() + "," + point.longitude).apply()
                                refreshSavedPoiMarkers()
                                Toast.makeText(this, "Luogo salvato: " + name, Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton(appText("Annulla"), null).show()
                }
            }.setNegativeButton(appText("Annulla"), null).show()
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
                // Keep enough of the raw fix to avoid visible lag, but do not let
                // one-second GNSS updates move the marker almost entirely in one jump.
                var alpha = when {
                    speed >= 8f -> (accuracyAlpha - 0.08f).coerceIn(0.48f, 0.68f)
                    speed < 2.5f -> 0.14f
                    else -> (accuracyAlpha - 0.06f).coerceIn(0.42f, 0.66f)
                }
                // A larger displacement can be real motion; respond faster without
                // reverting to the previous near-raw filter.
                if (jump > 12f) alpha = maxOf(alpha, 0.76f)
                if (previous.hasBearing() && incoming.hasBearing() && speed >= 2.5f) {
                    val courseDelta = abs(((incoming.bearing - previous.bearing + 540f) % 360f) - 180f)
                    if (courseDelta >= 30f) alpha = maxOf(alpha, 0.76f)
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
                    NavPoint(location.latitude, location.longitude), target.point, motorcycle = motorcycleProfile
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
        val confidenceLimit = minOf(30.0, maxOf(12.0, accuracy * 1.5))
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
        val confidenceLimit = minOf(30f, maxOf(12f, reportedAccuracy * 1.5f))
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
        val confidenceLimit = minOf(30f, maxOf(12f, reportedAccuracy * 1.5f))
        updateRouteMetrics(route, nearestIndex)
        if (nearestMeters > confidenceLimit) {
            if (::routeText.isInitialized && route.maneuvers.isNotEmpty()) {
                routeText.text = "POSIZIONE FUORI TRACCIA\nAttendo un aggancio affidabile alla strada"
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
        } else if (requestCode == PERMISSION_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startJarvisListening()
            else {
                if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Microfono non autorizzato"
                Toast.makeText(this, "Consenti il microfono per usare Jarvis. Nessun ascolto in background.", Toast.LENGTH_LONG).show()
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
        // Stop user-started speech recognition when the app loses foreground focus.
        if (jarvisListening) {
            try { speechRecognizer?.cancel() } catch (_: Exception) { }
            jarvisListening = false
            if (::jarvisButton.isInitialized) jarvisButton.text = "🎙 JARVIS"
            if (::jarvisStatusText.isInitialized) jarvisStatusText.text = appText("Premi il microfono e parla")
        }
        // Restore immediately when another app/system surface takes over.
        setNavigationBrightnessEnabled(false)
        activityResumed = false
        // While the explicit GPX+CSV recording service is active, keep location callbacks
        // registered even if the dashboard loses focus. Otherwise onPause creates long holes
        // in the trip trace whenever another app or system surface briefly takes over.
        if (!recordingGpx) {
            unregisterGnssDiagnostics()
            fusedLocationCallback?.let { callback ->
                try { fusedLocationClient?.removeLocationUpdates(callback) } catch (_: Exception) { }
            }
            if (usingLocationManagerFallback) {
                try { locationManager.removeUpdates(this) } catch (_: Exception) { }
                usingLocationManagerFallback = false
            }
            gpsUpdatesRequested = false
        }
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
        // A normal Activity shutdown finalizes the paired session. If Android kills the process
        // without this callback, the on-disk checkpoints are recovered on the next launch.
        if (recordingGpx || gpsDiagnosticRecording) finishGpxRecording(save = true, showResult = false)
        clockHandler.removeCallbacksAndMessages(null)
        ipFallbackHandler.removeCallbacksAndMessages(null)
        setNavigationBrightnessEnabled(false)
        splashHandler.removeCallbacksAndMessages(null)
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        speedAlertHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        jarvisListening = false
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
