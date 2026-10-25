package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DatePickerDialog
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
import android.widget.ListView
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
import org.maplibre.android.style.layers.BackgroundLayer
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
        private const val PREF_MAP_LAYER = "map_layer"
        private const val PREF_NAVIGATION_MAP_MODE = "navigation_map_panel_mode"
        private const val PREF_ALERT_SPEED_SOURCE = "alert_speed_source"
        private const val PREF_MOTORCYCLE_SPEED_OFFSET = "motorcycle_speed_offset_kmh"
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
        private const val SOURCE_MANUAL_SEGMENTS = "next-manual-speed-segments"
        private const val SOURCE_MANUAL_ARROWS = "next-manual-speed-arrows"
        private const val SOURCE_MANUAL_FLAGS = "next-manual-speed-flags"
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
    private val jarvisMemoryDelegate = lazy { JarvisMemoryStore(applicationContext) }
    private val jarvisMemory by jarvisMemoryDelegate
    private lateinit var destinationInput: EditText
    private lateinit var searchButton: Button
    private lateinit var routeButton: Button
    private lateinit var themeButton: Button
    private lateinit var exitButton: Button
    private lateinit var gpxButton: Button
    private lateinit var menuButton: Button
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var themeMode: String = "negative"
    private var navigationMapMode = false
    private var alertSpeedSource = "gps"
    private var motorcycleSpeedOffsetKmh = 0
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
    private var mapLayer = "standard"
    private var destinationPickerOpen = false
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
    private val speedLimitTrace = mutableListOf<Location>()
    private var approximateLocation: ApproximateLocation? = null
    private var ipFallbackRequested = false
    private var weatherUsesApproximateLocation = false
    private var pendingJarvisWeather = false
    private val ipFallbackHandler = Handler(Looper.getMainLooper())
    private var pendingGpxExportName = "Percorso NEXT NAVI PRO"
    private var gpsDiagnosticRecording = false
    private val gpsDiagnosticSamples = ArrayList<GpsTraceSample>()
    private var pendingGpsDiagnosticCsv: String? = null
    private var pendingGpxExportPoints: List<NavPoint>? = null
    private var recordingGpx = false
    // True when recording was explicitly started from the dashboard, independently of navigation.
    private var recordingIndependentOfNavigation = false
    private val gpxRecordingPoints = ArrayList<NavPoint>()
    private var lastRecordedGpxPoint: NavPoint? = null
    private var lastGpxPointTimeMs = 0L
    private var destination: PlaceResult? = null
    private var routeResult: RouteResult? = null
    @Volatile private var prefetchedRouteLimits: List<TomTomRouteLimitSample> = emptyList()
    private var routeProgressInitialized = false
    private var lastRouteProgressIndex = 0
    private var voiceGuide: TextToSpeech? = null
    private var voiceGuideReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var jarvisButton: Button
    private lateinit var jarvisStatusText: TextView
    private var jarvisListening = false
    private var jarvisAwaitingDestination = false
    private var jarvisResponseGeneration = 0
    private var pendingJarvisSpeech: String? = null
    private var pendingJarvisPlaces: List<PlaceResult> = emptyList()
    private var pendingJarvisLocality: String? = null
    private var pendingJarvisAddressMode = false
    private var pendingJarvisPreview = false
    private var routePreviewResult: RouteResult? = null
    private var routePreviewDestination: PlaceResult? = null
    private var routePreviewFetchedAt = 0L
    private val routePreviewRefreshHandler = Handler(Looper.getMainLooper())
    private val routePreviewRefreshRunnable = object : Runnable {
        override fun run() {
            if (!dashboardStarted || routeResult != null) return
            val place = routePreviewDestination ?: return
            val location = currentLocation
            if (isCalculatingRoute || location == null || !location.hasAccuracy() || location.accuracy > 50f) {
                routePreviewRefreshHandler.postDelayed(this, 15L * 60L * 1000L)
                return
            }
            previewRouteForJarvis(place, announce = false)
        }
    }
    private var announcedManeuverIndex = -1
    private var announcedStage = 0
    private var radarCameras: List<RadarCamera> = emptyList()
    private var radarFetchFailed = false
    private var radarCacheCoversCurrentArea = false
    private var lastRadarAttemptMs = 0L
    private var lastCameraLocation: Location? = null
    private var lastSpeedLimitQueryLocation: Location? = null
    private var lastSpeedLimitQueryMs = 0L
    private var speedLimitLookupInFlight = false
    // Where/when the currently displayed provider limit was verified, and the paid-provider backoff.
    private var speedLimitVerifiedFix: SpeedLimitFix? = null
    private val tomTomBackoff = ProviderBackoff()
    private var currentSpeedLimit: Int? = null
    // User-confirmed limit override. Persisted independently from automatic road lookup.
    private var manualSpeedLimit: Int? = null
    private val manualSpeedLimitSegments = mutableListOf<ManualSpeedLimitSegment>()
    private val manualSegmentDraft = mutableListOf<NavPoint>()
    private var manualSegmentEditing = false
    private var roadEditorGeometry: List<NavPoint> = emptyList()
    private var roadEditorRoad: RoadSearchResult? = null
    private var roadEditorGeometryTags: org.json.JSONObject? = null
    private var roadEditorStartIndex: Int? = null
    private var roadEditorProposedLimit: Int? = null
    private var roadEditorProposedSource: String? = null
    private var finishSegmentButton: Button? = null
    private var currentRoadMatch: RoadMatch? = null
    private var lastDisplayedSpeed: Int? = null
    private var speedAlertActive = false
    private var speedAlertFlashRed = false
    private var speedingVoiceActive = false
    private var speedAlertFlashesRemaining = 0
    private var lastSpeedingVoiceMs = 0L
    private var lastSpeedingValidLimitMs = 0L
    private var speedingEpisodeStartMs = 0L
    private val speedAlertHandler = Handler(Looper.getMainLooper())
    private val speedAlertRunnable = object : Runnable {
        override fun run() {
            if (!isSpeedingWithKnownLimit()) {
                speedAlertActive = false
                speedAlertFlashRed = false
                speedAlertFlashesRemaining = 0
                renderSpeedDigits(lastDisplayedSpeed)
                return
            }
            // Three gentle red flashes: six on/off phases, one second apart.
            if (speedAlertFlashesRemaining <= 0) {
                speedAlertFlashesRemaining = 6
                speedAlertFlashRed = true
            }
            renderSpeedDigits(lastDisplayedSpeed)
            speedAlertFlashesRemaining--
            if (speedAlertFlashesRemaining > 0) {
                speedAlertFlashRed = !speedAlertFlashRed
                speedAlertHandler.postDelayed(this, 1_000L)
            } else {
                speedAlertFlashRed = false
                renderSpeedDigits(lastDisplayedSpeed)
                // Visual reminder no more than every 30 seconds while still speeding.
                speedAlertHandler.postDelayed(this, 30_000L)
            }
        }
    }
    private var lastRadarAlertId: Long = -1L
    private var lastRadarAlertMs = 0L
    private var lastRerouteAttemptMs = 0L
    private var offRouteSinceMs = 0L
    private var arrivalClosestMeters = Float.MAX_VALUE
    private var arrivalStopSinceMs = 0L
    private var navigationArrived = false
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
    private val positiveBackground = Color.WHITE
    private val positiveText = Color.rgb(25, 30, 34)
    private val negativeBackground = Color.BLACK
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
        if (routeResult == null) routePreviewResult?.let { drawRoute(it, false) }
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
                val languageStatus = voiceGuide?.setLanguage(preferredJarvisTtsLocale()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                voiceGuideReady = languageStatus >= TextToSpeech.LANG_AVAILABLE
                if (voiceGuideReady) {
                    applyJarvisVoicePreference()
                    // Navigation announcements keep a steady, safety-oriented pace.
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
        navigationMapMode = preferences.getBoolean(PREF_NAVIGATION_MAP_MODE, false)
        alertSpeedSource = preferences.getString(PREF_ALERT_SPEED_SOURCE, "gps") ?: "gps"
        motorcycleSpeedOffsetKmh = preferences.getInt(PREF_MOTORCYCLE_SPEED_OFFSET, 0).coerceIn(0, 30)
        // This release is the motorcycle/scooter edition. Keep routing in motorcycle
        // mode until the dedicated car layout and behavior are implemented.
        motorcycleProfile = true
        preferences.edit().putBoolean("motorcycle_profile", true).apply()
        manualSpeedLimit = preferences.getInt("manual_speed_limit_kmh", -1).takeIf { it in 5..200 }
        currentSpeedLimit = manualSpeedLimit
        runCatching { val a = org.json.JSONArray(preferences.getString("manual_speed_limit_segments_json", "[]") ?: "[]"); for (i in 0 until a.length()) manualSpeedLimitSegments.add(ManualSpeedLimitSegment.fromJson(a.getJSONObject(i))) }.onFailure { android.util.Log.w("NEXT_NAVI_SPEED_LIMIT", "Manual segments unreadable", it) }
        syncManualSegmentsWithCloud()
        // Always start in automatic light-sensor mode; keep the last palette until the sensor reports lux.
        themeMode = "auto"
        mapLayer = preferences.getString(PREF_MAP_LAYER, "standard") ?: "standard"
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
        splash.addView(start, LinearLayout.LayoutParams(-1, dp(64)).apply {
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
        jarvisButton = makeButton(jarvisButtonIdleLabel()) { onJarvisButtonPressed() }
        jarvisRow.addView(jarvisButton, LinearLayout.LayoutParams(dp((72 + jarvisAssistantName().length * 8).coerceIn(116, 220)), dp(38)).apply {
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
            // Keep ETA/distance strip strictly monochrome in both themes and orientations.
            setTextColor(if (negativeTheme) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (negativeTheme) Color.BLACK else Color.WHITE)
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
            // 50% larger than the previous 28sp text; keep the sign comfortably tappable.
            textSize = 42f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.BLACK)
            applySpeedLimitSignStyle(this)
            isClickable = true
            isFocusable = true
            contentDescription = "Limite di velocità. Tocca per impostare o aggiornare manualmente"
            setOnClickListener { showSpeedLimitOptions() }
        }
        infoColumn.addView(limitValue, params(dp(96), dp(96)))
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
        // The digits are drawn directly on the dashboard surface; no opaque panel
        // may cover the navigation instructions above them.
        speedText.setBackgroundColor(Color.TRANSPARENT)
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        // In portrait, reduce the top spacer to lift the tachometer slightly.
        speedColumn.addView(View(this), LinearLayout.LayoutParams(-1, 0, if (isPortrait) 0.65f else 0.75f))
        speedColumn.addView(speedText, LinearLayout.LayoutParams(-1, dp(184)))
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
            setTextColor(if (negativeTheme) Color.WHITE else Color.BLACK)
        }
        // In portrait, show km/h directly below the tachometer, then the clock.
        // ETA and remaining distance stay below both; landscape order remains unchanged.
        if (isPortrait) {
            // Keep unit and clock visually close to the tachometer; avoid extra gaps.
            // Pull the unit, clock and GPS warning closer to the tachometer to
            // reclaim a little vertical space for the map.
            speedColumn.addView(unit, LinearLayout.LayoutParams(-1, dp(30)).apply {
                topMargin = -dp(7)
            })
            speedColumn.addView(clockText, LinearLayout.LayoutParams(-1, dp(32)).apply {
                topMargin = -dp(5)
            })
            speedColumn.addView(routeMetricsText, LinearLayout.LayoutParams(-1, dp(34)).apply {
                topMargin = -dp(4)
            })
        } else {
            // Landscape: lift the speed unit, clock and ETA/distance row together by
            // roughly half a centimetre so the bottom row stays clear of the controls.
            unit.translationY = -dp(20).toFloat()
            clockText.translationY = -dp(20).toFloat()
            routeMetricsText.translationY = -dp(20).toFloat()
            speedColumn.addView(unit, params(-1, dp(30)))
            speedColumn.addView(clockText, LinearLayout.LayoutParams(-1, dp(30)).apply {
                topMargin = -dp(4)
            })
            speedColumn.addView(routeMetricsText, LinearLayout.LayoutParams(-1, dp(30)).apply {
                topMargin = -dp(5)
            })
        }
        val centerSpacer = View(this)
        speedColumn.addView(centerSpacer, LinearLayout.LayoutParams(-1, 0, if (isPortrait) 0.8f else 0.25f))
        routeButton = makeButton(appText(if (routeResult != null) "TERMINA NAVIGAZIONE" else "AVVIA PERCORSO")) {
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
        mapColumn.addView(mapStatus, params(-1, dp(if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 18 else 22)))
        mapColumn.addView(newMapView, LinearLayout.LayoutParams(-1, 0, 1f))
        val recenterButton = makeButton(appText("⌖ POSIZIONE")) { recenterMapToGps() }
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !navigationMapMode) {
            val portraitControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            portraitControls.addView(routeButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
            portraitControls.addView(recenterButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
            mapColumn.addView(portraitControls, params(-1, dp(44)))
        }

        // Keep the existing dashboard untouched by default. The optional map-panel
        // view reuses the same live map, speed-limit sign, guidance, weather and status views.
        listOf(brand, weatherText, limitValue, gpsText, routeText, radarText, routeMetricsText).forEach { infoColumn.removeView(it) }
        val isMapPanelPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (navigationMapMode) {
            speedColumn.removeView(routeMetricsText)
            mapColumn.removeView(mapStatus)
            mapStatus.removeView(gpsText)
            mapStatus.removeView(radarText)
            val infoPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(2), dp(3), dp(2))
            }
            weatherText.maxLines = 2
            infoPanel.addView(weatherText, LinearLayout.LayoutParams(-1, dp(if (isMapPanelPortrait) 38 else 42)))
            val navigationInfo = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val maneuverAndText = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            maneuverIcon.textSize = if (isMapPanelPortrait) 48f else 54f
            maneuverIcon.translationY = 0f
            maneuverIcon.includeFontPadding = false
            routeText.textSize = if (isMapPanelPortrait) 13f else 14f
            routeText.translationY = 0f
            routeText.includeFontPadding = false
            routeText.maxLines = 3
            routeText.setPadding(dp(4), dp(2), dp(4), dp(2))
            maneuverAndText.addView(maneuverIcon, LinearLayout.LayoutParams(dp(if (isMapPanelPortrait) 52 else 58), dp(if (isMapPanelPortrait) 58 else 64)))
            maneuverAndText.addView(routeText, LinearLayout.LayoutParams(0, -2, 1f))
            navigationInfo.addView(maneuverAndText, LinearLayout.LayoutParams(0, -1, 1f))
            limitValue.translationY = 0f
            navigationInfo.addView(limitValue, LinearLayout.LayoutParams(dp(if (isMapPanelPortrait) 76 else 84), dp(if (isMapPanelPortrait) 76 else 84)))
            infoPanel.addView(navigationInfo, LinearLayout.LayoutParams(-1, 0, 1f))
            routeMetricsText.translationY = 0f
            infoPanel.addView(routeMetricsText, params(-1, dp(if (isMapPanelPortrait) 28 else 30)))
            val statusRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            statusRow.addView(gpsText, LinearLayout.LayoutParams(0, dp(20), 1f))
            statusRow.addView(radarText, LinearLayout.LayoutParams(0, dp(20), 1f))
            infoPanel.addView(statusRow, params(-1, dp(20)))
            val panelControls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            // Do not reparent routeButton here: it already belongs to the dashboard
            // controls row (landscape) or the map controls (portrait). Reparenting a View
            // that still has a parent throws IllegalStateException and closes the Activity.
            panelControls.addView(makeButton(appText("⌖ GPS")) { recenterMapToGps() }, LinearLayout.LayoutParams(-1, dp(38)).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
            infoPanel.addView(panelControls, params(-1, dp(40)))
            if (isMapPanelPortrait) {
                content.orientation = LinearLayout.VERTICAL
                content.addView(infoPanel, LinearLayout.LayoutParams(-1, 0, 0.88f))
                content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 1.12f))
            } else {
                content.orientation = LinearLayout.HORIZONTAL
                content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 2f))
                content.addView(infoPanel, LinearLayout.LayoutParams(0, -1, 1f))
            }
        } else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            content.orientation = LinearLayout.VERTICAL
            val upper = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val navHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            // Portrait: center the complete navigation group (arrow + instruction)
            // vertically against the speed-limit sign; avoid translating only the arrow.
            // Portrait physical targets: maneuver glyph about 1 cm; instruction text about 0.65 cm.
            maneuverIcon.textSize = 88f
            maneuverIcon.translationY = -dp(38).toFloat()
            navHalf.addView(maneuverIcon, params(-1, dp(88)))
            routeText.textSize = 15f
            routeText.translationY = -dp(16).toFloat()
            routeText.includeFontPadding = false
            routeText.maxLines = 3
            routeText.setPadding(dp(8), dp(2), dp(8), dp(2))
            navHalf.addView(routeText, params(-1, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitValue.translationY = 0f
            // Portrait: weather occupies one full-width strip above navigation and speed limit.
            // Keep the maneuver/instruction and speed-limit sign in their own measured row.
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER })
            upper.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            upper.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            content.addView(weatherText, params(-1, dp(52)))
            content.addView(upper, LinearLayout.LayoutParams(-1, 0, 0.65f).apply {
                // Lower the navigation instruction and speed-limit area by about 0.5 cm.
                topMargin = dp(31)
            })
            speedText.textSize = 158f
            // Keep the tachometer transparent in portrait so it cannot visually
            // obscure the navigation row. The colored outline remains on each digit.
            speedText.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            // Rebalance portrait height: move GPS/radar status upward and give
            // the map a little more vertical space without squeezing the controls.
            content.addView(speedColumn, LinearLayout.LayoutParams(-1, 0, 1.34f).apply {
                topMargin = 0
            })
            content.addView(mapColumn, LinearLayout.LayoutParams(-1, 0, 0.96f))
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
            // Keep arrow and instruction as one centered group, sharing the sign's vertical center.
            // Landscape physical targets: maneuver glyph and instruction text about 0.75 cm.
            maneuverIcon.textSize = 116f
            maneuverIcon.translationY = -dp(73).toFloat()
            navHalf.addView(maneuverIcon, params(-1, dp(116)))
            routeText.textSize = 15f
            routeText.translationY = -dp(47).toFloat()
            routeText.includeFontPadding = false
            routeText.maxLines = 3
            navHalf.addView(routeText, params(-1, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            val limitHalf = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            limitValue.translationY = -dp(50).toFloat()
            limitHalf.addView(limitValue, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER })
            val navAndLimit = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            navAndLimit.addView(navHalf, LinearLayout.LayoutParams(0, -1, 1f))
            navAndLimit.addView(limitHalf, LinearLayout.LayoutParams(0, -1, 1f))
            left.addView(navAndLimit, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            // Landscape: group weather immediately above GPS and OSM radar status.
            mapColumn.addView(weatherText, 0, params(-1, dp(52)))
            speedText.textSize = 158f
            speedText.setShadowLayer(dp(1).toFloat(), 0f, 0f, if (negativeTheme) Color.BLACK else Color.WHITE)
            content.addView(speedColumn, LinearLayout.LayoutParams(0, -1, 1.2f))
            content.addView(mapColumn, LinearLayout.LayoutParams(0, -1, 1f))
        }
        if (!(navigationMapMode && isMapPanelPortrait)) {
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        clockHandler.removeCallbacks(clockRunnable)
        clockRunnable.run()

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        // Exit and GPX share the space previously occupied by the single EXIT button.
        exitButton = makeButton("ESCI") { showExitConfirmation() }
        gpxButton = makeButton("GPX") { toggleDashboardGpxRecording() }
        val navigatorButton = makeButton("NAVIGATORE") { enterMapFullScreen() }
        val controlHeight = dp(40)
        fun addBottomControl(button: Button) {
            controls.addView(button, LinearLayout.LayoutParams(0, controlHeight, 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        val exitAndGpx = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun addHalfExitControl(button: Button) {
            exitAndGpx.addView(button, LinearLayout.LayoutParams(0, controlHeight, 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
        addHalfExitControl(exitButton)
        addHalfExitControl(gpxButton)
        controls.addView(exitAndGpx, LinearLayout.LayoutParams(0, controlHeight, 1f))
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            addBottomControl(navigatorButton)
            addBottomControl(routeButton)
        } else {
            addBottomControl(navigatorButton)
        }
        if (navigationMapMode && isMapPanelPortrait) {
            // Portrait map-panel mode keeps every control above the map.
            root.addView(controls, params(-1, dp(44)))
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(controls, params(-1, dp(44)))
        }
        updateGpxButtonState()

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
                if (destinationPickerOpen) {
                    // Taps in the chooser inspect the map; they must not reparent it
                    // into full-screen mode or replace the selected geocoder result.
                    true
                } else if (fullScreenMap && manualSegmentEditing && roadEditorGeometry.isNotEmpty()) {
                    val picked = NavPoint(point.latitude, point.longitude)
                    val nearest = nearestRoadGeometryIndex(picked, roadEditorGeometry)
                    val nearestPoint = roadEditorGeometry[nearest]
                    val snapDistance = FloatArray(1)
                    Location.distanceBetween(picked.latitude, picked.longitude, nearestPoint.latitude, nearestPoint.longitude, snapDistance)
                    if (snapDistance[0] > 40f) {
                        Toast.makeText(this@MainActivity, "Tocca direttamente la strada evidenziata (entro 40 m). Nessun punto è stato selezionato.", Toast.LENGTH_LONG).show()
                    } else if (roadEditorStartIndex == null) {
                        roadEditorStartIndex = nearest
                        Toast.makeText(this@MainActivity, "Inizio selezionato sulla geometria OSM. Tocca ora la fine del tratto.", Toast.LENGTH_LONG).show()
                    } else {
                        val start = roadEditorStartIndex!!
                        if (kotlin.math.abs(nearest - start) < 1) {
                            Toast.makeText(this@MainActivity, "Scegli una fine diversa dall'inizio.", Toast.LENGTH_SHORT).show()
                        } else {
                            val low = minOf(start, nearest); val high = maxOf(start, nearest)
                            val selectedReverse = start > nearest
                            val oneWay = roadEditorGeometryTags?.optString("oneway")?.trim()?.lowercase()
                            val illegalDirection = (oneWay in setOf("yes", "1", "true") && selectedReverse) ||
                                (oneWay == "-1" && !selectedReverse)
                            if (illegalDirection) {
                                Toast.makeText(this@MainActivity, "Il senso scelto contrasta con il tag oneway di OpenStreetMap. Seleziona il verso consentito; se la mappa è errata, non salvare il tratto.", Toast.LENGTH_LONG).show()
                            } else {
                                var selected = roadEditorGeometry.subList(low, high + 1).toList()
                                if (selectedReverse) selected = selected.reversed()
                                manualSegmentDraft.clear(); manualSegmentDraft.addAll(selected)
                                // Keep the complete OSM way visible while choosing/editing the selected sub-segment.
                                // The selected draft is a slice of the actual road geometry, not a straight-line sketch.
                                finishSegmentButton?.isEnabled = false
                                Toast.makeText(this@MainActivity, "Tratto agganciato alla geometria OSM: " + selected.size + " punti. Verifico ora il limite per questo tratto…", Toast.LENGTH_LONG).show()
                                Thread {
                                    val provider = RenderRoadEditorService.proposeSpeedLimit(selected)
                                    runOnUiThread {
                                        // OSM geometry/tags may guide road direction, but never supply a legal speed limit.
                                        // Automatic proposals in the editor must come from TomTom only.
                                        roadEditorProposedLimit = provider.speedLimitKmh
                                        roadEditorProposedSource = provider.source
                                        finishSegmentButton?.isEnabled = true
                                        val proposal = roadEditorProposedLimit?.let { it.toString() + " km/h (" + (roadEditorProposedSource ?: "fonte non specificata") + ")" }
                                            ?: when (provider.reason) {
                                                "multiple_speed_limits_in_selected_segment" -> "limiti diversi nel tratto; suddividilo in segmenti più corti"
                                                "backend_not_configured" -> "backend Render non configurato"
                                                "provider_http_503" -> "TomTom non configurato sul server"
                                                "provider_http_401", "provider_http_403" -> "TomTom ha rifiutato la chiave o i permessi"
                                                "provider_http_429" -> "limite richieste/quota TomTom raggiunto"
                                                "tomtom_auth_or_permission_error" -> "TomTom: chiave o permessi non validi"
                                                "tomtom_quota_or_rate_limit" -> "TomTom: quota o frequenza richieste superata"
                                                "tomtom_timeout" -> "TomTom non ha risposto in tempo"
                                                "tomtom_network_error" -> "errore di rete verso TomTom"
                                                "no_explicit_speed_limit" -> "provider interrogato, nessun limite esplicito restituito"
                                                else -> "limite non disponibile (" + (provider.reason ?: "motivo sconosciuto") + ")"
                                            }
                                        Toast.makeText(this@MainActivity, "Limite proposto: " + proposal + ". Controlla prima di salvare.", Toast.LENGTH_LONG).show()
                                    }
                                }.start()
                            }
                        }
                    }
                    refreshManualSegmentMarkers(); true
                } else if (fullScreenMap && manualSegmentEditing) {
                    manualSegmentDraft.add(NavPoint(point.latitude, point.longitude)); refreshManualSegmentMarkers()
                    Toast.makeText(this@MainActivity, "Punto " + manualSegmentDraft.size + " aggiunto • premi FINE al termine", Toast.LENGTH_SHORT).show(); true
                } else if (fullScreenMap && selectionMode) {
                    val picked = NavPoint(point.latitude, point.longitude)
                    showPickedPointDialog(picked)
                    true
                } else {
                    enterMapFullScreen()
                    true
                }
            }
            readyMap.addOnCameraMoveStartedListener { reason ->
                if (destinationPickerOpen) {
                    followGps = false
                    mapFollowHandler.removeCallbacks(mapFollowRunnable)
                    return@addOnCameraMoveStartedListener
                }
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
        val satelliteStyle = """{"version":8,"name":"NEXT NAVI PRO Satellite","sources":{"satellite":{"type":"raster","tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],"tileSize":256,"maxzoom":19,"attribution":"Imagery © Esri, Maxar, Earthstar Geographics and the GIS community"}},"layers":[{"id":"satellite-imagery","type":"raster","source":"satellite","minzoom":0,"maxzoom":22}]}"""
        val builder = when (mapLayer) {
            "satellite" -> Style.Builder().fromJson(satelliteStyle)
            "light" -> Style.Builder().fromUri("https://tiles.openfreemap.org/styles/positron")
            else -> Style.Builder().fromUri(if (negativeTheme) STYLE_URL else "https://tiles.openfreemap.org/styles/positron")
        }
        readyMap.setStyle(builder) { style ->
            if (mapLayer != "satellite") {
                style.layers.forEach { layer ->
                    when (layer) {
                        is SymbolLayer -> layer.setProperties(
                            PropertyFactory.textColor(if (negativeTheme) "#FFFFFF" else "#111111"),
                            PropertyFactory.textHaloColor(if (negativeTheme) "#000000" else "#FFFFFF"),
                            PropertyFactory.textHaloWidth(1.2f)
                        )
                        is LineLayer -> layer.setProperties(
                            PropertyFactory.lineColor(if (negativeTheme) "#FFFFFF" else "#111111")
                        )
                        // Invert the vector-map contrast with the dashboard theme:
                        // black canvas and white roads/labels in negative mode,
                        // white canvas and dark roads/labels in positive mode.
                        is BackgroundLayer -> layer.setProperties(
                            PropertyFactory.backgroundColor(if (negativeTheme) "#000000" else "#FFFFFF")
                        )
                    }
                }
            }
            mapStyleReady = true
            style.addImage("next-user-arrow-icon", createGpsArrowBitmap())
            style.addImage("next-place-flag-icon", createPlaceFlagBitmap())
            style.addImage("next-manual-direction-icon", createGpsArrowBitmap())
            style.addSource(GeoJsonSource(SOURCE_ROUTE, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(LineLayer("next-route-line", SOURCE_ROUTE).withProperties(
                PropertyFactory.lineColor(if (negativeTheme) "#FFD400" else "#00D6C9"),
                PropertyFactory.lineWidth(5f)
            ))
            style.addSource(GeoJsonSource(SOURCE_MANUAL_SEGMENTS, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(LineLayer("next-manual-speed-segment-lines", SOURCE_MANUAL_SEGMENTS).withProperties(PropertyFactory.lineColor("#D500F9"), PropertyFactory.lineWidth(5f)))
            style.addSource(GeoJsonSource(SOURCE_MANUAL_ARROWS, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(SymbolLayer("next-manual-speed-arrows", SOURCE_MANUAL_ARROWS).withProperties(PropertyFactory.iconImage("next-manual-direction-icon"), PropertyFactory.iconSize(0.65f), PropertyFactory.iconRotate(Expression.get("bearing")), PropertyFactory.iconRotationAlignment("map"), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)))
            style.addSource(GeoJsonSource(SOURCE_MANUAL_FLAGS, """{"type":"FeatureCollection","features":[]}"""))
            style.addLayer(SymbolLayer("next-manual-speed-flags", SOURCE_MANUAL_FLAGS).withProperties(PropertyFactory.iconImage("next-place-flag-icon"), PropertyFactory.iconSize(0.55f), PropertyFactory.iconAnchor("bottom"), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true)))
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
            refreshManualSegmentMarkers()
            currentLocation?.let { updateMapForLocation(it, true) }
            drawRadars()
            routeResult?.let { drawRoute(it, false) }
                ?: routePreviewResult?.let { drawRoute(it, false) }
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
        // Never paint a rectangular tachometer panel; only the digit outlines are colored.
        speedText.setBackgroundColor(Color.TRANSPARENT)
        renderSpeedDigits(speed)
    }

    private fun isSpeedingWithKnownLimit(): Boolean {
        val limit = currentSpeedLimit ?: return false
        val speedKmh = speedForLimitAlertsKmh() ?: return false
        return speedKmh > limit + 3f
    }

    private fun updateSpeedingAlertState() {
        if (isSpeedingWithKnownLimit()) {
            if (!speedAlertActive) {
                speedAlertActive = true
                speedAlertFlashesRemaining = 0
                speedAlertFlashRed = false
                speedAlertHandler.removeCallbacks(speedAlertRunnable)
                speedAlertHandler.post(speedAlertRunnable)
            }
        } else {
            // Keep the alert state through brief TomTom/GPS gaps; a confirmed return
            // below the limit is handled by updateSpeedLimitDisplay().
            if (speedAlertActive && SystemClock.elapsedRealtime() - lastSpeedingValidLimitMs > 10_000L) {
                speedAlertHandler.removeCallbacks(speedAlertRunnable)
                speedAlertActive = false
                speedAlertFlashRed = false
                speedAlertFlashesRemaining = 0
                renderSpeedDigits(lastDisplayedSpeed)
            }
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
        val choices = arrayOf(appText("Indirizzi preferiti"), appText("Salva destinazione come preferita"), appText("Elimina preferiti"), appText("Prova voce navigatore"), appText("Modalità schermo"), appText("Ricerche recenti"), appText("Soglie tachimetro e avvisi"), appText("Previsioni meteo"), appText("Percorsi GPX"), appText("Debug • CSV GPS: esporta / elimina"), appText("Modalità veicolo: MOTO (attiva)"), appText("Jarvis • Assistente e impostazioni"), appText("Visualizzazione: " + if (navigationMapMode) "Mappa + pannello" else "Cruscotto"), appText("Velocità per avvisi: " + if (alertSpeedSource == "motorcycle") "Moto calibrata" else "GPS"))
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
                    11 -> showJarvisMenu()
                    12 -> showNavigationMapModeSettings()
                    13 -> showAlertSpeedSourceSettings()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun showJarvisMenu() {
        val choices = arrayOf(
            appText("Apri Jarvis / parla con l'assistente"),
            appText("Nome dell'assistente"),
            appText("Voce di Jarvis"),
            appText("Personalità di Jarvis"),
            appText("Memoria Jarvis • spazio e pulizia")
        )
        AlertDialog.Builder(this)
            .setTitle(appText("JARVIS • Assistente"))
            .setMessage(appText("Voce unica per conversazione, voce, personalità e memoria."))
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> onJarvisButtonPressed()
                    1 -> showJarvisNameSettings()
                    2 -> showJarvisVoiceSettings()
                    3 -> showJarvisPersonalitySettings()
                    4 -> showJarvisMemorySettings()
                }
            }
            .setNegativeButton(appText("Chiudi"), null)
            .show()
    }

    private fun showNavigationMapModeSettings() {
        val labels = arrayOf(appText("Cruscotto tachimetro (attuale)"), appText("Mappa + pannello informazioni"))
        AlertDialog.Builder(this)
            .setTitle(appText("Visualizzazione navigazione"))
            .setSingleChoiceItems(labels, if (navigationMapMode) 1 else 0) { dialog, which ->
                val enabled = which == 1
                navigationMapMode = enabled
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_NAVIGATION_MAP_MODE, enabled).apply()
                dialog.dismiss()
                rebuildDashboardForDisplayMode()
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun showAlertSpeedSourceSettings() {
        val labels = arrayOf(
            appText("GPS — velocità GPS senza correzioni"),
            appText("Moto — velocità stimata GPS + scarto del tachimetro")
        )
        AlertDialog.Builder(this)
            .setTitle(appText("Velocità usata per gli avvisi"))
            .setMessage(appText("Il telefono non può leggere direttamente il tachimetro della moto. In modalità Moto stima la velocità indicata sommando al GPS lo scarto calibrato. Esempio: GPS 74 + scarto 6 = stima tachimetro 80 km/h."))
            .setSingleChoiceItems(labels, if (alertSpeedSource == "motorcycle") 1 else 0) { dialog, which ->
                if (which == 0) {
                    alertSpeedSource = "gps"
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_ALERT_SPEED_SOURCE, alertSpeedSource).apply()
                    dialog.dismiss()
                    updateSpeedingAlertState()
                    updateSpeedLimitDisplay()
                } else {
                    dialog.dismiss()
                    showMotorcycleSpeedOffsetDialog()
                }
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun showMotorcycleSpeedOffsetDialog() {
        val tachometerInput = EditText(this).apply {
            hint = appText("Tachimetro moto (km/h)")
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val gpsInput = EditText(this).apply {
            hint = appText("Velocità GPS telefono (km/h)")
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val resultText = TextView(this).apply {
            text = appText("Inserisci entrambe le velocità per calcolare lo scarto.")
            textSize = 16f
            setPadding(0, dp(12), 0, dp(4))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(TextView(this@MainActivity).apply {
                text = appText("Inserisci le due velocità rilevate nello stesso momento, mantenendo un'andatura costante. NEXT NAVI PRO calcolerà automaticamente la differenza.")
                textSize = 14f
            })
            addView(tachometerInput, params(-1, dp(52)))
            addView(gpsInput, params(-1, dp(52)))
            addView(resultText, params(-1, -2))
        }

        fun updateCalculatedOffset() {
            val tachometer = tachometerInput.text.toString().toIntOrNull()
            val gps = gpsInput.text.toString().toIntOrNull()
            if (tachometer == null || gps == null || tachometer <= 0 || gps <= 0) {
                resultText.text = appText("Inserisci entrambe le velocità per calcolare lo scarto.")
                return
            }
            val offset = tachometer - gps
            resultText.text = if (offset >= 0) {
                appText("Scarto calcolato: +$offset km/h")
            } else {
                appText("Scarto calcolato: $offset km/h (il tachimetro indica meno del GPS)")
            }
        }
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCalculatedOffset()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        tachometerInput.addTextChangedListener(watcher)
        gpsInput.addTextChangedListener(watcher)

        val dialog = AlertDialog.Builder(this)
            .setTitle(appText("Calibrazione tachimetro moto"))
            .setView(panel)
            .setPositiveButton(appText("Calcola e salva"), null)
            .setNegativeButton(appText("Annulla"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tachometer = tachometerInput.text.toString().toIntOrNull()
                val gps = gpsInput.text.toString().toIntOrNull()
                if (tachometer == null || gps == null || tachometer <= 0 || gps <= 0) {
                    Toast.makeText(this, appText("Inserisci entrambe le velocità in km/h."), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val offset = tachometer - gps
                if (offset !in 0..30) {
                    Toast.makeText(this, appText("Lo scarto deve essere compreso tra 0 e 30 km/h: controlla i due valori."), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                motorcycleSpeedOffsetKmh = offset
                alertSpeedSource = "motorcycle"
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(PREF_MOTORCYCLE_SPEED_OFFSET, offset)
                    .putString(PREF_ALERT_SPEED_SOURCE, alertSpeedSource)
                    .apply()
                updateSpeedingAlertState()
                updateSpeedLimitDisplay()
                Toast.makeText(this, appText("Scarto salvato: +$offset km/h. Avvisi Moto attivi."), Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun rebuildDashboardForDisplayMode() {
        if (!dashboardStarted) return
        gpsMarkerAnimator?.cancel()
        gpsMarkerAnimator = null
        val oldMapView = mapView
        try {
            oldMapView?.onPause()
            oldMapView?.onStop()
            oldMapView?.onDestroy()
        } catch (_: Exception) { }
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
        latestWeather?.let { updateWeatherDisplay(it) }
        updateSpeedLimitDisplay()
        currentLocation?.let { updateGpsStatus(it); updateMapForLocation(it, true) }
        routeResult?.let { drawRoute(it, false); currentLocation?.let { location -> updateTurnGuidance(location) } }
        if (routeResult == null) routePreviewResult?.let { drawRoute(it, false) }
        if (activityResumed) requestGps()
    }

    private fun speedForLimitAlertsKmh(): Int? {
        val location = currentLocation ?: return null
        if (!location.hasSpeed() || !location.hasAccuracy() || location.accuracy > 35f) return null
        val gpsKmh = (location.speed * 3.6f).roundToInt()
        return if (alertSpeedSource == "motorcycle") {
            (gpsKmh + motorcycleSpeedOffsetKmh).coerceAtLeast(0)
        } else gpsKmh
    }

    private fun showVehicleProfileMenu() {
        // The current app is deliberately motorcycle-only. A car profile will be
        // introduced later together with its own dashboard layout and behavior.
        motorcycleProfile = true
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("motorcycle_profile", true).apply()
        AlertDialog.Builder(this)
            .setTitle(appText("Modalità veicolo"))
            .setMessage(appText("Modalità attiva: MOTO / SCOOTER.\nLa navigazione usa il profilo stradale per moto. La modalità AUTO verrà aggiunta successivamente con un layout e caratteristiche dedicate; per ora non è selezionabile."))
            .setPositiveButton(appText("OK"), null)
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
        val now = System.currentTimeMillis()
        val reliableGps = currentLocation?.takeIf {
            it.hasAccuracy() && it.accuracy <= 100f && now - it.time in 0..120_000L
        }
        // A stale last-known GPS fix must not block approximate IP location forever.
        if (ipFallbackRequested || reliableGps != null) return
        ipFallbackRequested = true
        thread(name = "ip-geolocation") {
            val result = try { IpLocationService.fetch() } catch (_: Exception) { null }
            runOnUiThread {
                ipFallbackRequested = false
                val latestNow = System.currentTimeMillis()
                val latestReliableGps = currentLocation?.takeIf {
                    it.hasAccuracy() && it.accuracy <= 100f && latestNow - it.time in 0..120_000L
                }
                // Prefer a fresh GPS fix if it arrived while IP geolocation was in flight.
                if (latestReliableGps != null) return@runOnUiThread
                if (result == null) {
                    if (::gpsText.isInitialized) gpsText.text = "GPS ASSENTE • POSIZIONE IP NON DISPONIBILE"
                    return@runOnUiThread
                }
                approximateLocation = result
                if (::gpsText.isInitialized) {
                    gpsText.text = "POSIZIONE IP ≈ " + result.areaLabel
                    gpsText.setTextColor(Color.rgb(235, 165, 0))
                }
                if (pendingJarvisWeather) {
                    pendingJarvisWeather = false
                    answerJarvisWeather()
                } else {
                    refreshWeatherForApproximateLocation(result, true)
                }
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
            val result = try { WeatherService.fetch(location.latitude, location.longitude) } catch (error: Exception) { android.util.Log.w("NEXT_NAVI_WEATHER", "Approximate-location weather lookup failed", error); null }
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
            .setPositiveButton("Sì, registra") { _, _ ->
                recordingIndependentOfNavigation = false
                startGpxRecording()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun toggleDashboardGpxRecording() {
        if (recordingGpx) {
            // Tapping the green GPX control stops and saves the paired GPX + CSV session.
            finishGpxRecording(save = true)
            return
        }
        if (gpsDiagnosticRecording) {
            Toast.makeText(this, "Termina prima la registrazione diagnostica CSV dal menu Debug.", Toast.LENGTH_LONG).show()
            return
        }
        recordingIndependentOfNavigation = true
        startGpxRecording()
        updateGpxButtonState()
    }

    private fun updateGpxButtonState() {
        if (!::gpxButton.isInitialized) return
        val active = recordingGpx
        gpxButton.text = if (active) "GPX • REC" else "GPX"
        gpxButton.setTextColor(Color.WHITE)
        gpxButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            if (active) Color.rgb(20, 145, 65) else Color.rgb(190, 35, 35)
        ))
        gpxButton.contentDescription = if (active) "Registrazione GPX attiva. Tocca per fermare e salvare." else "Registrazione GPX ferma. Tocca per iniziare."
    }

    private fun startGpxRecording() {
        gpxRecordingPoints.clear()
        lastRecordedGpxPoint = null
        lastGpxPointTimeMs = 0L
        gpsDiagnosticSamples.clear()
        gpsDiagnosticRecording = true
        recordingGpx = true
        updateGpxButtonState()
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
        recordingIndependentOfNavigation = false
        updateGpxButtonState()
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
                    prefetchedRouteLimits = emptyList()
                    prefetchRouteSpeedLimits(result)
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
        speakJarvis(greeting, remember = false)
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

    private fun preferredJarvisTtsLocale(): Locale {
        // The app UI and greeting cover these locales. For other system languages, use English
        // consistently instead of silently disabling speech when a TTS voice/data pack is absent.
        val language = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (language in setOf("it", "es", "fr", "de", "pt", "nl")) Locale.getDefault() else Locale.ENGLISH
    }

    private fun applyJarvisVoicePreference() {
        val tts = voiceGuide ?: return
        val language = preferredJarvisTtsLocale().language
        val available = tts.voices?.filter { it.locale.language.equals(language, ignoreCase = true) }.orEmpty()
        if (available.isEmpty()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedVoiceName = prefs.getString("jarvis_voice_name", "").orEmpty()
        val mode = prefs.getString("jarvis_voice_style", "female").orEmpty()
        if (mode == "auto") {
            // Leave the TTS engine's own default voice selected, while keeping the system language.
            tts.setLanguage(preferredJarvisTtsLocale())
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
        val language = preferredJarvisTtsLocale().language
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
            "custom" -> available.indexOfFirst { it.name == prefs.getString("jarvis_voice_name", "") }
                .let { if (it >= 0) it + 3 else 0 }
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

    private fun showJarvisPersonalitySettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        }
        val presetLabels = arrayOf(
            "Jarvis — intelligente, ironico e discreto",
            "Amichevole — caloroso e spontaneo",
            "Ironico — umorismo più frequente",
            "Professionale — preciso e formale",
            "Discreto — poche parole"
        )
        val presetValues = arrayOf("jarvis", "friendly", "ironic", "professional", "discreet")
        val currentPreset = prefs.getString("jarvis_personality_preset", "jarvis") ?: "jarvis"
        val presetGroup = android.widget.RadioGroup(this).apply { orientation = android.widget.RadioGroup.VERTICAL }
        presetLabels.forEachIndexed { index, label ->
            val radio = android.widget.RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                tag = presetValues[index]
                isChecked = presetValues[index] == currentPreset
                textSize = 15f
                setPadding(0, dp(5), 0, dp(5))
            }
            presetGroup.addView(radio)
        }
        layout.addView(TextView(this).apply {
            text = "Stile di partenza"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })
        layout.addView(presetGroup)

        data class SliderSetting(val key: String, val label: String, val min: Int, val max: Int, val initial: Int, val suffix: String = "%")
        val settings = listOf(
            SliderSetting("jarvis_personality_irony", "Ironia", 0, 100, prefs.getInt("jarvis_personality_irony", 45)),
            SliderSetting("jarvis_personality_warmth", "Calore e simpatia", 0, 100, prefs.getInt("jarvis_personality_warmth", 80)),
            SliderSetting("jarvis_personality_energy", "Energia ed espressività", 0, 100, prefs.getInt("jarvis_personality_energy", 55)),
            SliderSetting("jarvis_personality_concision", "Sintesi delle risposte", 0, 100, prefs.getInt("jarvis_personality_concision", 75)),
            SliderSetting("jarvis_voice_rate", "Velocità della voce Jarvis", 70, 120, prefs.getInt("jarvis_voice_rate", 96), "%"),
            SliderSetting("jarvis_voice_pitch", "Tono della voce", 70, 140, (prefs.getFloat("jarvis_voice_pitch", 1.0f) * 100).toInt().coerceIn(70, 140), "%")
        )
        val seekBars = mutableMapOf<String, android.widget.SeekBar>()
        val valueLabels = mutableMapOf<String, TextView>()
        fun addSetting(setting: SliderSetting) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(7), 0, dp(2))
            }
            val label = TextView(this).apply {
                textSize = 14f
                text = setting.label
            }
            val value = TextView(this).apply {
                textSize = 12f
                alpha = 0.8f
                text = "${setting.initial}${setting.suffix}"
            }
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                addView(value)
            }
            val seek = android.widget.SeekBar(this).apply {
                max = setting.max - setting.min
                progress = (setting.initial.coerceIn(setting.min, setting.max) - setting.min)
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        value.text = "${progress + setting.min}${setting.suffix}"
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                })
            }
            row.addView(heading)
            row.addView(seek)
            layout.addView(row)
            seekBars[setting.key] = seek
            valueLabels[setting.key] = value
        }
        settings.forEach { addSetting(it) }

        presetGroup.setOnCheckedChangeListener { group, checkedId ->
            val selected = group.findViewById<android.widget.RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            val values = when (selected) {
                "friendly" -> listOf(25, 95, 70, 65)
                "ironic" -> listOf(85, 75, 70, 70)
                "professional" -> listOf(10, 45, 35, 35)
                "discreet" -> listOf(10, 55, 25, 95)
                else -> listOf(45, 80, 55, 75)
            }
            listOf("jarvis_personality_irony", "jarvis_personality_warmth", "jarvis_personality_energy", "jarvis_personality_concision").forEachIndexed { index, key ->
                seekBars[key]?.progress = values[index]
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Personalità di Jarvis")
            .setMessage("Personalizza il modo in cui Jarvis conversa. Queste preferenze guidano le risposte AI online; gli avvisi stradali e le istruzioni di navigazione restano neutri e prioritari.")
            .setView(scroll)
            .setPositiveButton("Salva", null)
            .setNeutralButton("Ascolta anteprima", null)
            .setNegativeButton(appText("Annulla"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = presetGroup.findViewById<android.widget.RadioButton>(presetGroup.checkedRadioButtonId)?.tag as? String ?: "jarvis"
                val edit = prefs.edit().putString("jarvis_personality_preset", selected)
                settings.forEach { setting ->
                    val value = (seekBars[setting.key]?.progress ?: (setting.initial - setting.min)) + setting.min
                    edit.putInt(setting.key, value)
                }
                edit.putFloat("jarvis_voice_pitch", ((seekBars["jarvis_voice_pitch"]?.progress ?: 30) + 70) / 100f)
                edit.apply()
                applyJarvisVoicePreference()
                voiceGuide?.setSpeechRate((prefs.getInt("jarvis_voice_rate", 96) / 100f).coerceIn(0.7f, 1.2f))
                Toast.makeText(this, "Personalità di Jarvis salvata", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val selected = presetGroup.findViewById<android.widget.RadioButton>(presetGroup.checkedRadioButtonId)?.tag as? String ?: "jarvis"
                val irony = seekBars["jarvis_personality_irony"]?.progress ?: 45
                val warmth = seekBars["jarvis_personality_warmth"]?.progress ?: 80
                val line = when {
                    irony >= 70 && warmth >= 60 -> "Ecco, ci sono. Prometto di essere brillante, ma non al punto da distrarti dalla strada."
                    warmth >= 75 -> "Sono qui con te. Dimmi pure, ti ascolto."
                    selected == "professional" -> "Assistente pronto. Dimmi pure di cosa hai bisogno."
                    selected == "discreet" -> "Sono qui. Dimmi."
                    else -> "Ciao! Sono qui, pronto ad aiutarti."
                }
                voiceGuide?.setSpeechRate(((seekBars["jarvis_voice_rate"]?.progress ?: 26) + 70) / 100f)
                voiceGuide?.setPitch(((seekBars["jarvis_voice_pitch"]?.progress ?: 30) + 70) / 100f)
                if (voiceGuideReady) voiceGuide?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "jarvis-personality-preview")
            }
        }
        dialog.show()
    }

    private fun showJarvisMemorySettings() {
        val stats = jarvisMemory.stats()
        val summary = "Spazio occupato: ${formatMemoryBytes(stats.bytes)}\n" +
            "Messaggi conservati: ${stats.messages}\n" +
            "Giornate riassunte: ${stats.summaryDays}\n\n" +
            "Jarvis conserva testo, non registrazioni audio. La memoria resta sul telefono."
        val choices = arrayOf(
            "Compatta la cronologia",
            "Nuova chat (mantieni i riepiloghi)",
            "Elimina per intervallo di date",
            "Elimina tutta la memoria",
            "Aggiorna spazio occupato"
        )
        AlertDialog.Builder(this).setTitle("Memoria di Jarvis").setMessage(summary)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        val progress = AlertDialog.Builder(this).setTitle("Compattazione memoria")
                            .setMessage("Sto riassumendo le conversazioni più vecchie e recuperando lo spazio…")
                            .create()
                        progress.setCancelable(false)
                        progress.show()
                        thread(name = "jarvis-memory-compaction") {
                            val before = jarvisMemory.stats()
                            val after = jarvisMemory.compact()
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    progress.dismiss()
                                    AlertDialog.Builder(this).setTitle("Compattazione completata")
                                        .setMessage("Messaggi prima: ${before.messages}\nMessaggi rimasti: ${after.messages}\nGiornate riassunte: ${after.summaryDays}\nSpazio attuale: ${formatMemoryBytes(after.bytes)}\n\nLe conversazioni meno recenti sono state sintetizzate localmente; gli ultimi 30 messaggi restano disponibili integralmente.")
                                        .setPositiveButton("OK", null).show()
                                }
                            }
                        }
                    }
                    1 -> AlertDialog.Builder(this).setTitle("Nuova chat")
                        .setMessage("Vuoi iniziare una nuova conversazione? I messaggi recenti verranno eliminati, ma i riepiloghi compattati resteranno disponibili.")
                        .setPositiveButton("Nuova chat") { _, _ ->
                            thread(name = "jarvis-memory-new-chat") {
                                jarvisMemory.clearCurrentConversation()
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) Toast.makeText(this, "Nuova chat pronta. I riepiloghi sono conservati.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.setNegativeButton("Annulla", null).show()
                    2 -> showJarvisMemoryDateRange()
                    3 -> AlertDialog.Builder(this).setTitle("Eliminare tutta la memoria?")
                        .setMessage("Verranno eliminati tutti i messaggi e tutti i riepiloghi locali. L'operazione non si può annullare.")
                        .setPositiveButton("ELIMINA TUTTO") { _, _ ->
                            thread(name = "jarvis-memory-clear-all") {
                                jarvisMemory.clearAll()
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) Toast.makeText(this, "Memoria Jarvis eliminata.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.setNegativeButton("Annulla", null).show()
                    4 -> showJarvisMemorySettings()
                }
            }.setNegativeButton("Chiudi", null).show()
    }

    private fun showJarvisMemoryDateRange() {
        val today = java.util.Calendar.getInstance()
        val initialStart = (today.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, -30)
        }
        DatePickerDialog(this, { _, sy, sm, sd ->
            val start = java.util.Calendar.getInstance().apply {
                set(sy, sm, sd, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            DatePickerDialog(this, { _, ey, em, ed ->
                val endDay = java.util.Calendar.getInstance().apply {
                    set(ey, em, ed, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                if (endDay.timeInMillis < start.timeInMillis) {
                    Toast.makeText(this, "La data finale precede quella iniziale.", Toast.LENGTH_LONG).show()
                    return@DatePickerDialog
                }
                val endExclusive = (endDay.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                val count = jarvisMemory.countRange(start.timeInMillis, endExclusive.timeInMillis)
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val rangeText = dateFormat.format(start.time) + " – " + dateFormat.format(endDay.time)
                AlertDialog.Builder(this).setTitle("Conferma eliminazione")
                    .setMessage("Intervallo: $rangeText\nMessaggi da eliminare: $count\nSaranno rimossi anche i riepiloghi delle stesse giornate, per evitare che le informazioni cancellate restino nella memoria sintetica.")
                    .setPositiveButton("ELIMINA INTERVALLO") { _, _ ->
                        thread(name = "jarvis-memory-delete-range") {
                            val deleted = jarvisMemory.deleteRange(start.timeInMillis, endExclusive.timeInMillis)
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Eliminati $deleted messaggi e i riepiloghi dell'intervallo.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.setNegativeButton("Annulla", null).show()
            }, today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }, initialStart.get(java.util.Calendar.YEAR), initialStart.get(java.util.Calendar.MONTH), initialStart.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatMemoryBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
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
                updateJarvisButtonLabel(false)
                if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "$name • " + appText("pronto • premi per parlare")
                Toast.makeText(this, appText("Assistente rinominato in ") + name, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(appText("Ripristina Jarvis")) { _, _ ->
                prefs.edit().putString("jarvis_assistant_name", "Jarvis").apply()
                updateJarvisButtonLabel(false)
                if (::jarvisStatusText.isInitialized) jarvisStatusText.text = "Jarvis • " + appText("pronto • premi per parlare")
                Toast.makeText(this, appText("Nome ripristinato: Jarvis"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(appText("Annulla"), null)
            .show()
    }

    private fun jarvisAssistantName(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString("jarvis_assistant_name", "Jarvis")
            .orEmpty().trim().take(30).ifBlank { "Jarvis" }

    private fun jarvisIdentityReply(): String {
        val name = jarvisAssistantName()
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "it" -> "Mi chiamo $name. Sono il tuo assistente di navigazione NEXT NAVI PRO."
            "es" -> "Me llamo $name. Soy tu asistente de navegación de NEXT NAVI PRO."
            "fr" -> "Je m'appelle $name. Je suis votre assistant de navigation NEXT NAVI PRO."
            "de" -> "Ich heiße $name. Ich bin dein Navigationsassistent von NEXT NAVI PRO."
            "pt" -> "Chamo-me $name. Sou o teu assistente de navegação NEXT NAVI PRO."
            "nl" -> "Ik heet $name. Ik ben je navigatieassistent van NEXT NAVI PRO."
            else -> "My name is $name. I'm your NEXT NAVI PRO navigation assistant."
        }
    }

    private fun jarvisButtonIdleLabel(): String = "🎙 " + jarvisAssistantName()

    private fun updateJarvisButtonLabel(listening: Boolean = jarvisListening) {
        if (!::jarvisButton.isInitialized) return
        jarvisButton.text = if (listening) appText("■ STOP") else jarvisButtonIdleLabel()
        if (!listening) {
            jarvisButton.layoutParams = jarvisButton.layoutParams.apply {
                width = dp((72 + jarvisAssistantName().length * 8).coerceIn(116, 220))
            }
            jarvisButton.requestLayout()
        }
    }

    private fun localizedJarvisPrompt(): String {
        val name = jarvisAssistantName()
        return when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "it" -> "Parla con $name"
            "es" -> "Habla con $name"
            "fr" -> "Parlez avec $name"
            "de" -> "Sprich mit $name"
            "pt" -> "Fala con $name"
            "nl" -> "Praat met $name"
            else -> "Talk to $name"
        }
    }

    private fun speakJarvis(message: String, remember: Boolean = true) {
        if (remember) jarvisMemory.addMessage("assistant", message)
        if (!voiceGuideReady) {
            pendingJarvisSpeech = message
            return
        }
        val voiceRate = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("jarvis_voice_rate", 96).coerceIn(70, 120) / 100f
        voiceGuide?.setSpeechRate(voiceRate)
        voiceGuide?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.currentTimeMillis())
    }

    private fun onJarvisButtonPressed() {
        // A press is an interruption command: stop any current TTS immediately,
        // discard queued speech and invalidate an in-flight online answer before listening.
        jarvisResponseGeneration++
        pendingJarvisSpeech = null
        try { voiceGuide?.stop() } catch (_: Exception) { }
        if (jarvisListening) {
            speechRecognizer?.cancel()
            jarvisListening = false
            jarvisButton.text = jarvisButtonIdleLabel()
            jarvisStatusText.text = appText("Ascolto annullato")
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
            jarvisStatusText.text = appText("Riconoscimento vocale non disponibile")
            speakJarvis(appText("Il riconoscimento vocale non è disponibile su questo dispositivo."))
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = appText("Ti ascolto…") }
                    }
                    override fun onBeginningOfSpeech() {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = appText("Sto ascoltando…") }
                    }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        runOnUiThread { if (::jarvisStatusText.isInitialized) jarvisStatusText.text = appText("Elaboro…") }
                    }
                    override fun onError(error: Int) {
                        runOnUiThread {
                            jarvisListening = false
                            if (::jarvisButton.isInitialized) jarvisButton.text = jarvisButtonIdleLabel()
                            if (::jarvisStatusText.isInitialized) {
                                jarvisStatusText.text = when (error) {
                                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> appText("Non ho capito • riprova")
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> appText("Permesso microfono mancante")
                                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> appText("Rete non disponibile • riprova")
                                    else -> appText("Ascolto terminato • riprova")
                                }
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        runOnUiThread {
                            jarvisListening = false
                            if (::jarvisButton.isInitialized) jarvisButton.text = jarvisButtonIdleLabel()
                            val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty().trim()
                            if (transcript.isBlank()) {
                                jarvisStatusText.text = appText("Non ho ricevuto parole • riprova")
                            } else {
                                jarvisStatusText.text = appText("Hai detto: ") + transcript
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
            jarvisButton.text = appText("■ STOP")
            jarvisStatusText.text = appText("Avvio microfono…")
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            jarvisListening = false
            jarvisButton.text = jarvisButtonIdleLabel()
            jarvisStatusText.text = appText("Impossibile avviare il microfono")
            speakJarvis(appText("Non riesco ad avviare il microfono. Puoi riprovare."))
        }
    }

    private fun handleJarvisCommand(transcript: String) {
        // A newer utterance cancels a pending weather request; otherwise the request
        // is resumed automatically when a reliable GPS or approximate network location arrives.
        pendingJarvisWeather = false
        val spokenTranscript = JarvisCommandParser.stripAssistantName(transcript, jarvisAssistantName())
        if (spokenTranscript.isNotBlank()) jarvisMemory.addMessage("user", spokenTranscript)
        // Live weather requests take priority over any pending destination clarification.
        if (JarvisIntentClassifier.isWeatherQuestion(spokenTranscript)) {
            answerJarvisWeather()
            return
        }
        // When the rider explicitly asked to change destination, the next utterance
        // is treated as the requested destination unless it is another known command.
        if (jarvisAwaitingDestination) {
            val parsed = JarvisCommandParser.parse(spokenTranscript)
            if (parsed !is JarvisCommand.Unknown && parsed !is JarvisCommand.Navigate) {
                jarvisAwaitingDestination = false
            } else {
                val rawDestination = if (parsed is JarvisCommand.Navigate) parsed.destination else spokenTranscript.trim()
                jarvisAwaitingDestination = false
                pendingJarvisLocality = null
                pendingJarvisAddressMode = false
                pendingJarvisPreview = false
                destinationInput.setText(rawDestination)
                speakJarvis("Cerco la nuova destinazione: $rawDestination.")
                searchDestination(voiceCommand = true)
                return
            }
        }
        // A broad locality is a conversation, not yet a precise navigation target.
        // Interpret the rider's next spoken answer locally; never ask the online AI to
        // pretend it can change navigation state.
        val pendingLocality = pendingJarvisLocality
        if (!pendingLocality.isNullOrBlank()) {
            val phrase = normalizeJarvisSpeech(spokenTranscript)
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
                    destinationInput.setText(spokenTranscript.trim() + ", " + pendingLocality)
                    searchDestination(voiceCommand = true)
                    return
                }
                listOf("negozio", "attivita", "punto di interesse", "ristorante", "farmacia", "supermercato",
                    "mercadona", "bar", "hotel", "benzinaio", "officina", "parcheggio", "spiaggia",
                    "museo", "bancomat", "ospedale", "stazione", "centro commerciale", "negozi", "ristoranti")
                    .any { phrase.contains(it) } -> {
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    destinationInput.setText(spokenTranscript.trim() + " a " + pendingLocality)
                    searchDestination(voiceCommand = true)
                    return
                }
                JarvisCommandParser.parse(spokenTranscript) is JarvisCommand.Navigate -> {
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
        when (val command = JarvisCommandParser.parse(spokenTranscript)) {
            JarvisCommand.CancelNavigation -> {
                if (routeResult == null) {
                    speakJarvis("Non c'è una navigazione attiva da annullare.")
                } else {
                    clearRoute()
                    speakJarvis("Navigazione annullata. La registrazione del viaggio è stata salvata.")
                }
            }
            is JarvisCommand.ChangeDestination -> {
                val newDestination = command.destination
                if (!newDestination.isNullOrBlank()) {
                    jarvisAwaitingDestination = false
                    pendingJarvisLocality = null
                    pendingJarvisAddressMode = false
                    pendingJarvisPreview = false
                    destinationInput.setText(newDestination)
                    speakJarvis("Cambio destinazione in $newDestination. Cerco il luogo.")
                    searchDestination(voiceCommand = true)
                } else {
                    jarvisAwaitingDestination = true
                    speakJarvis("Va bene. Dimmi la nuova destinazione.")
                }
            }
            JarvisCommand.AssistantName -> speakJarvis(jarvisIdentityReply())
            is JarvisCommand.PreviewRoute -> {
                pendingJarvisPreview = true
                destinationInput.setText(command.destination)
                searchDestination(voiceCommand = true)
            }
            JarvisCommand.BriefRoute -> {
                val route = routeResult ?: routePreviewResult
                if (route == null) {
                    val selected = destination
                    if (selected != null) {
                        speakJarvis("Preparo un'anteprima per " + selected.name + ", senza avviare la navigazione.")
                        previewRouteForJarvis(selected)
                    } else {
                        speakJarvis("Non ho ancora una destinazione o un percorso da analizzare. Puoi chiedermi: voglio andare a Guaza, cosa mi aspetta per la strada?")
                    }
                } else {
                    val name = if (routeResult != null) destination?.name ?: "destinazione attuale"
                        else routePreviewDestination?.name ?: "destinazione in anteprima"
                    speakRouteBriefing(name, route, routePreviewFetchedAt.takeIf { routeResult == null })
                }
            }
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
            JarvisCommand.Unknown -> askJarvisOnline(spokenTranscript)
        }
    }


    /**
     * Weather is a deterministic live-data query, not a free-form AI guess.
     * Fetch it directly from Open-Meteo so a missing Gemini backend cannot
     * prevent Jarvis from answering a weather question.
     */
    private fun answerJarvisWeather() {
        val now = System.currentTimeMillis()
        val gps = currentLocation?.takeIf {
            it.hasAccuracy() && it.accuracy <= 100f && now - it.time in 0..120_000L
        }
        val approximate = if (gps == null) approximateLocation else null
        if (gps == null && approximate == null) {
            pendingJarvisWeather = true
            requestIpLocationFallback()
            speakJarvis("Non ho ancora una posizione attendibile. Cerco una zona approssimativa e riprovo automaticamente appena arriva il GPS o la posizione di rete.")
            return
        }

        val latitude = gps?.latitude ?: approximate!!.latitude
        val longitude = gps?.longitude ?: approximate!!.longitude
        val locationDescription = if (gps != null) {
            "nella posizione GPS corrente"
        } else {
            "nella zona approssimativa " + approximate!!.areaLabel
        }
        val requestGeneration = jarvisResponseGeneration
        jarvisStatusText.text = jarvisAssistantName() + " • verifica meteo Open-Meteo…"
        thread(name = "jarvis-live-weather") {
            val result = try {
                WeatherService.fetch(latitude, longitude)
            } catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_WEATHER", "Jarvis live weather lookup failed", error)
                null
            }
            runOnUiThread {
                if (requestGeneration != jarvisResponseGeneration || jarvisListening || isFinishing) return@runOnUiThread
                if (result == null) {
                    jarvisStatusText.text = jarvisAssistantName() + " • meteo non disponibile"
                    speakJarvis("Non riesco a verificare il meteo attuale da Open-Meteo. Non voglio inventare temperatura o vento; riprova quando la connessione è disponibile.")
                    return@runOnUiThread
                }
                latestWeather = result
                weatherUsesApproximateLocation = gps == null
                updateWeatherDisplay(result)
                val time = result.currentTime.substringAfter("T", "").take(5).ifBlank { "ora locale del servizio" }
                jarvisStatusText.text = jarvisAssistantName() + " • meteo verificato"
                speakJarvis(
                    "Meteo attuale verificato da Open-Meteo $locationDescription: " +
                        WeatherService.description(result.weatherCode).lowercase(Locale.ITALIAN) + ", " +
                        WeatherService.format(result.temperatureC) + " gradi, vento " +
                        WeatherService.format(result.windKmh) + " chilometri orari da " +
                        WeatherService.windDirection(result.windDirectionDegrees) +
                        ", raffiche fino a " + WeatherService.format(result.gustKmh) +
                        " chilometri orari. Dato valido alle $time."
                )
            }
        }
    }


    private fun askJarvisOnline(transcript: String) {
        // The online assistant answers conversational requests only. Navigation commands
        // are handled locally; no GPS-based stationary gate blocks ordinary use.
        if (!JarvisOnlineAiClient.isConfigured()) {
            speakJarvis("L'assistente AI online non è ancora configurato. I comandi offline restano disponibili; non invio dati a servizi non impostati.")
            return
        }
        jarvisStatusText.text = jarvisAssistantName() + " AI • elaborazione online…"
        val requestGeneration = jarvisResponseGeneration
        val liveQuestion = Regex("\\b(meteo|che tempo fa|previsioni(?: del)? tempo|previsioni meteo|pioggia|vento|weather|forecast|lluvia|clima|el tiempo|tiempo actual|trafico|traffico|traffic|atasco|incidente|retencion|congestion)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(transcript)
        val liveLocation = if (liveQuestion) currentLocation?.let {
            JarvisOnlineAiClient.LiveLocation(it.latitude, it.longitude)
        } else null
        // Read the prior turns before adding the current utterance, avoiding duplication
        // in Gemini's contents while preserving context across app restarts.
        val recentTurns = jarvisMemory.recent(11)
        // The current utterance was recorded by handleJarvisCommand; send it only once.
        val history = if (recentTurns.lastOrNull()?.let {
                it.role == "user" && it.content == transcript
            } == true) recentTurns.dropLast(1).takeLast(10) else recentTurns.takeLast(10)
        val summary = jarvisMemory.summary()
        val personalityPrefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val personality = JarvisOnlineAiClient.Personality(
            preset = personalityPrefs.getString("jarvis_personality_preset", "jarvis") ?: "jarvis",
            irony = personalityPrefs.getInt("jarvis_personality_irony", 45).coerceIn(0, 100),
            warmth = personalityPrefs.getInt("jarvis_personality_warmth", 80).coerceIn(0, 100),
            energy = personalityPrefs.getInt("jarvis_personality_energy", 55).coerceIn(0, 100),
            concision = personalityPrefs.getInt("jarvis_personality_concision", 75).coerceIn(0, 100)
        )
        JarvisOnlineAiClient.ask(
            message = transcript,
            assistantName = jarvisAssistantName(),
            location = liveLocation,
            history = history,
            summary = summary,
            personality = personality
        ) { result ->
            runOnUiThread {
                // Never let a delayed online response restart speech after the rider
                // has pressed Jarvis to interrupt or ask a newer question.
                if (requestGeneration != jarvisResponseGeneration || jarvisListening) return@runOnUiThread
                result.onSuccess { reply ->
                    jarvisStatusText.text = jarvisAssistantName() + " AI • risposta ricevuta"
                    // Online text is conversational only: it cannot change route, speed limit or GPS state.
                    speakJarvis(reply)
                }.onFailure { error ->
                    val detail = error.message.orEmpty()
                    jarvisStatusText.text = jarvisAssistantName() + " AI • errore: " + detail.take(90)
                    val spoken = when {
                        detail.contains("HTTP_503") ->
                            "Il server Jarvis su Render risponde, ma non è configurato correttamente. Controllare la variabile GEMINI_API_KEY nelle impostazioni del servizio Render."
                        detail.contains("provider HTTP 401") || detail.contains("provider HTTP 403") ->
                            "Il server Jarvis raggiunge Google, ma la chiave Gemini o i suoi permessi non sono validi."
                        detail.contains("provider HTTP 400") ->
                            "Google Gemini ha rifiutato la richiesta. Controllare la variabile GEMINI_MODEL nel servizio Render di Jarvis."
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
        showDestinationPicker(results, currentLocation, pendingJarvisPreview)
    }

    private fun localizedVoiceTestPhrase(): String = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "it" -> "Avvio la navigazione. Tra trecento metri, prosegui."
        "es" -> "Iniciando la navegación. En trescientos metros, continúa."
        "fr" -> "Navigation en cours. Dans trois cents mètres, continuez."
        "de" -> "Navigation wird gestartet. In dreihundert Metern weiterfahren."
        "pt" -> "A iniciar a navegação. Daqui a trezentos metros, continue."
        "nl" -> "Navigatie gestart. Ga over driehonderd meter rechtdoor."
        else -> "Starting navigation. Continue in three hundred meters."
    }

    private fun testNavigationVoice() {
        if (!voiceGuideReady) {
            Toast.makeText(this, appText("Voce non pronta o lingua TTS non disponibile."), Toast.LENGTH_LONG).show()
            return
        }
        voiceGuide?.setSpeechRate(getSharedPreferences(PREFS, MODE_PRIVATE).getInt("jarvis_voice_rate", 96).coerceIn(70, 120) / 100f)
        voiceGuide?.speak(localizedVoiceTestPhrase(), TextToSpeech.QUEUE_FLUSH, null, "voice-test")
    }

    private fun applyTheme() {
        if (!::root.isInitialized) return
        root.setBackgroundColor(if (negativeTheme) negativeBackground else positiveBackground)
        val panelBackground = if (negativeTheme) negativeBackground else positiveBackground
        applySpeedLimitSignStyle(limitValue)
        routeText.setBackgroundColor(panelBackground)
        speedText.setBackgroundColor(panelBackground)
        if (::routeMetricsText.isInitialized) {
            routeMetricsText.setTextColor(if (negativeTheme) Color.WHITE else Color.BLACK)
            routeMetricsText.setBackgroundColor(if (negativeTheme) Color.BLACK else Color.WHITE)
        }
        if (::weatherText.isInitialized) weatherText.setBackgroundColor(panelBackground)
        updateSpeedGaugeBorder(lastDisplayedSpeed)
        val textColor = if (negativeTheme) white else positiveText
        root.walkViews().forEach { view ->
            if (view is TextView && view !== destinationInput) {
                view.setTextColor(when {
                    view === speedText -> textColor
                    view === limitValue -> Color.BLACK
                    view === clockText -> if (negativeTheme) Color.WHITE else Color.BLACK
                    view === routeMetricsText -> if (negativeTheme) Color.WHITE else Color.BLACK
                    view === gpsText -> if (listOf("GPS INSUFFICIENTE", "GPS SALTO SCARTATO", "PERMESSO GPS NEGATO", "GPS NON DISPONIBILE", "LOCALIZZAZIONE DISATTIVATA").any { gpsText.text.toString().contains(it) }) Color.rgb(235, 45, 45) else Color.rgb(0, 190, 70)
                    view === routeText -> if (negativeTheme) cyan else Color.rgb(0, 85, 105)
                    view === radarText -> Color.rgb(255, 70, 55)
                    view.text.toString().contains("NEXT NAVI PRO") -> yellow
                    else -> textColor
                })
            }
            if (view is Button) {
                view.setTextColor(if (negativeTheme) Color.WHITE else Color.rgb(0, 48, 54))
                view.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(0, 174, 190))
            }
        }
        destinationInput.setTextColor(textColor)
        destinationInput.setHintTextColor(if (negativeTheme) Color.LTGRAY else Color.DKGRAY)
        destinationInput.setBackgroundColor(if (negativeTheme) Color.rgb(0, 115, 135) else Color.rgb(229, 249, 250))
        renderSpeedDigits(lastDisplayedSpeed)
        updateSpeedGaugeBorder(lastDisplayedSpeed)
        currentLocation?.let { updateGpsStatus(it) }
        updateRadarStatusColor()
        // Reapply after generic theme styling so GPX keeps its unmistakable red/green state.
        updateGpxButtonState()
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
                // Approximate IP location may rank search results, but only a reliable GPS fix
                // can authorize automatic selection of a locality for turn-by-turn navigation.
                val sortReference = origin ?: approximateLocation?.let {
                    Location("approximate-search").apply {
                        latitude = it.latitude
                        longitude = it.longitude
                    }
                }
                val sortedResults = if (sortReference != null) results.sortedBy { result ->
                    val distance = FloatArray(1)
                    Location.distanceBetween(sortReference.latitude, sortReference.longitude, result.point.latitude, result.point.longitude, distance)
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
                    if (voiceCommand && !pendingJarvisPreview) {
                        val reliableOrigin = origin?.takeIf {
                            it.hasAccuracy() && it.accuracy <= 100f &&
                                System.currentTimeMillis() - it.time in 0..120_000L
                        }?.let { NavPoint(it.latitude, it.longitude) }
                        val exactLocality = JarvisDestinationResolver.uniqueNearbyExactLocality(
                            sortedResults, query, reliableOrigin
                        )
                        if (exactLocality != null) {
                            pendingJarvisPlaces = emptyList()
                            pendingJarvisLocality = null
                            pendingJarvisAddressMode = false
                            rememberSearch(query)
                            selectJarvisOrSearchDestination(exactLocality, autoStart = true)
                            return@runOnUiThread
                        }

                        // Do not ask a redundant "center/address/POI?" follow-up when an exact
                        // locality was found but remains ambiguous. Show the real candidates
                        // with distances instead, so the rider can choose the intended place.
                        if (!JarvisDestinationResolver.hasExactLocalityMatch(sortedResults, query)) {
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
                    showDestinationPicker(sortedResults, origin, pendingJarvisPreview)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    isSearching = false
                    searchButton.isEnabled = true
                    val cached = if (voiceCommand && pendingJarvisPreview) {
                        val here = currentLocation?.let { NavPoint(it.latitude, it.longitude) }
                        RouteInsightCache.loadForDestination(this, query, here)
                    } else null
                    if (cached != null) {
                        pendingJarvisPreview = false
                        val place = PlaceResult(cached.destinationName, cached.destination,
                            "Destinazione e percorso dalla cache locale")
                        destination = place
                        destinationInput.setText(place.name)
                        showCachedRoutePreview(place, cached)
                    } else {
                        routeText.text = "RICERCA NON DISPONIBILE • " + (error.message ?: "Errore di rete")
                        if (voiceCommand) speakJarvis("La ricerca online non è disponibile e non ho una copia locale utilizzabile per questa destinazione.")
                        pendingJarvisPreview = false
                    }
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

    /**
     * Full-screen destination chooser. The live dashboard MapView is temporarily moved into
     * this dialog so the rider can compare every candidate against the real map. Its parent,
     * position and layout parameters are restored when the chooser closes.
     */
    private fun showDestinationPicker(
        places: List<PlaceResult>,
        origin: Location?,
        previewOnly: Boolean
    ) {
        if (places.isEmpty()) return
        val activityMapView = mapView ?: run {
            // Defensive fallback if the dashboard map is not available.
            val labels = places.map { it.name }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Scegli destinazione")
                .setItems(labels) { _, which ->
                    val chosen = places[which]
                    selectJarvisOrSearchDestination(chosen)
                }.setNegativeButton("Annulla", null).show()
            return
        }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val oldDestination = destination
        val oldParent = activityMapView.parent as? android.view.ViewGroup
        val oldIndex = oldParent?.indexOfChild(activityMapView) ?: -1
        val oldParams = activityMapView.layoutParams
        var accepted = false
        var selectedIndex = 0

        val dialog = Dialog(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setBackgroundColor(if (negativeTheme) Color.rgb(15, 18, 21) else Color.rgb(235, 239, 241))
        }
        val heading = TextView(this).apply {
            text = "SCEGLI LA LOCALITÀ • ${places.size} risultati"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (negativeTheme) Color.WHITE else Color.BLACK)
            setPadding(dp(8), 0, dp(8), 0)
        }
        page.addView(heading, params(-1, dp(44)))

        val chooserContent = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val labels = places.map { place ->
            val distanceLabel = origin?.let { here ->
                val distance = FloatArray(1)
                Location.distanceBetween(here.latitude, here.longitude, place.point.latitude, place.point.longitude, distance)
                if (distance[0] < 1000f) " • ${distance[0].roundToInt()} m"
                else " • " + String.format(Locale.ITALY, "%.1f km", distance[0] / 1000f)
            }.orEmpty()
            place.name + distanceLabel +
                (if (place.description.isBlank()) "" else "\n" + place.description) +
                "\n" + String.format(Locale.ROOT, "%.5f, %.5f", place.point.latitude, place.point.longitude)
        }
        val resultList = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            adapter = android.widget.ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_list_item_activated_1,
                labels
            )
            setBackgroundColor(if (negativeTheme) Color.rgb(28, 32, 36) else Color.WHITE)
            setOnItemClickListener { _, _, which, _ ->
                selectedIndex = which
                setItemChecked(which, true)
                val place = places[which]
                destination = place
                refreshDestinationMarker()
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(place.point.latitude, place.point.longitude), 15.0),
                    500
                )
            }
        }

        // Keep the existing MapLibre instance and style; no second map or duplicate map state.
        oldParent?.removeView(activityMapView)
        chooserContent.addView(resultList, if (landscape)
            LinearLayout.LayoutParams(0, -1, 0.43f)
        else LinearLayout.LayoutParams(-1, 0, 0.9f))
        chooserContent.addView(activityMapView, if (landscape)
            LinearLayout.LayoutParams(0, -1, 0.57f).apply { setMargins(dp(5), 0, 0, 0) }
        else LinearLayout.LayoutParams(-1, 0, 1.1f).apply { setMargins(0, dp(5), 0, 0) })
        page.addView(chooserContent, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val changeButton = makeButton("MODIFICA RICERCA") {
            dialog.dismiss()
            destinationInput.requestFocus()
            destinationInput.setSelection(destinationInput.text.length)
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(destinationInput, InputMethodManager.SHOW_IMPLICIT)
        }
        val goButton = makeButton(if (previewOnly) "ANTEPRIMA" else "NAVIGA QUI") {
            val chosen = places.getOrNull(selectedIndex) ?: return@makeButton
            accepted = true
            dialog.dismiss()
            selectJarvisOrSearchDestination(chosen, autoStart = previewOnly)
            if (!previewOnly) calculateSelectedRoute()
        }
        val cancelButton = makeButton("ANNULLA") { dialog.dismiss() }
        actions.addView(changeButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        actions.addView(goButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        actions.addView(cancelButton, LinearLayout.LayoutParams(0, dp(48), 0.72f).apply { setMargins(dp(2), 0, dp(2), 0) })
        page.addView(actions, params(-1, dp(54)))

        dialog.setContentView(page)
        dialog.setOnDismissListener {
            destinationPickerOpen = false
            // Return the map to the dashboard without recreating it or losing its current style.
            (activityMapView.parent as? android.view.ViewGroup)?.removeView(activityMapView)
            if (oldParent != null && activityMapView.parent == null) {
                val restoreIndex = if (oldIndex < 0) oldParent.childCount else oldIndex.coerceAtMost(oldParent.childCount)
                oldParent.addView(activityMapView, restoreIndex, oldParams)
            }
            if (!accepted) destination = oldDestination
            refreshDestinationMarker()

            // The chooser intentionally pans the shared MapView to candidate destinations.
            // Always restore dashboard follow mode and recenter on the latest accepted GPS fix
            // after the MapView has been reattached and laid out; never leave the camera on a POI.
            followGps = true
            lastCameraLocation = null
            mapFollowHandler.removeCallbacks(mapFollowRunnable)
            activityMapView.post {
                if (dashboardStarted && !destinationPickerOpen && !fullScreenMap) {
                    followGps = true
                    lastCameraLocation = null
                    val latestFix = currentLocation
                    if (latestFix != null) {
                        updateMapForLocation(latestFix, forceCenter = true)
                    } else {
                        requestGps()
                    }
                    mapFollowHandler.removeCallbacks(mapFollowRunnable)
                    mapFollowHandler.postDelayed(mapFollowRunnable, 10_000L)
                }
            }
        }
        destinationPickerOpen = true
        followGps = false
        mapFollowHandler.removeCallbacks(mapFollowRunnable)
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        resultList.setItemChecked(0, true)
        destination = places.first()
        refreshDestinationMarker()
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(places.first().point.latitude, places.first().point.longitude), 15.0
            ), 500
        )
    }

    private fun selectJarvisOrSearchDestination(place: PlaceResult, autoStart: Boolean = false) {
        destination = place
        pendingJarvisPlaces = emptyList()
        routeResult = null
        navigationArrived = false
        arrivalClosestMeters = Float.MAX_VALUE
        arrivalStopSinceMs = 0L
        offRouteSinceMs = 0L
        routePreviewResult = null
        routePreviewDestination = null
        routePreviewFetchedAt = 0L
        routePreviewRefreshHandler.removeCallbacks(routePreviewRefreshRunnable)
        resetRouteProgress()
        routeMetricsText.text = "— km  •  ARRIVO —"
        val routeSource = map?.style?.getSource(SOURCE_ROUTE) as? GeoJsonSource
        routeSource?.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
        destinationInput.setText(place.name)
        routeText.text = appText("DESTINAZIONE: ") + place.name + "\n" + appText("Premi AVVIA PERCORSO")
        routeButton.text = appText("AVVIA PERCORSO")
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.point.latitude, place.point.longitude), 14.0), 700)
        if (autoStart) {
            if (pendingJarvisPreview) {
                pendingJarvisPreview = false
                speakJarvis("Ho trovato " + place.name + ". Preparo un'anteprima del percorso, senza avviare la navigazione.")
                previewRouteForJarvis(place)
            } else {
                speakJarvis("Ho trovato " + place.name + ". Calcolo il percorso; verifica le indicazioni e la strada in sicurezza.")
                calculateSelectedRoute()
            }
        } else {
            speakJarvis("Destinazione selezionata: " + place.name + ". Controlla il percorso prima di partire.")
        }
    }

    /**
     * Route preview is deliberately separate from active navigation. It does not
     * start turn guidance, radar proximity alerts, or paired GPX/CSV recording.
     */
    private fun previewRouteForJarvis(place: PlaceResult, announce: Boolean = true) {
        val location = currentLocation
        val anyOrigin = location?.let { NavPoint(it.latitude, it.longitude) }
        val origin = location?.takeIf { it.hasAccuracy() && it.accuracy <= 50f }
            ?.let { NavPoint(it.latitude, it.longitude) }
        if (origin == null) {
            val cached = RouteInsightCache.loadForDestination(this, place.name, anyOrigin)
            if (cached != null) showCachedRoutePreview(place, cached, announce)
            else if (announce) speakJarvis("Per analizzare il percorso mi serve una posizione GPS affidabile. Non invento il punto di partenza.")
            return
        }
        if (isCalculatingRoute) return
        isCalculatingRoute = true
        routeButton.isEnabled = false
        routeText.text = "CALCOLO ANTEPRIMA PERCORSO…"
        Thread({
            try {
                val result = NavigationServices.calculateRoute(origin, place.point, motorcycle = motorcycleProfile)
                runOnUiThread {
                    isCalculatingRoute = false
                    routeButton.isEnabled = true
                    routePreviewResult = result
                    routePreviewDestination = place
                    routePreviewFetchedAt = System.currentTimeMillis()
                    RouteInsightCache.save(this, place.name, place.point, origin, result, routePreviewFetchedAt)
                    scheduleRoutePreviewRefresh()
                    drawRoute(result, true)
                    val minutes = (result.durationSeconds / 60.0).roundToInt()
                    routeText.text = String.format(Locale.ITALY,
                        "ANTEPRIMA • %.1f km • circa %d min\n%s",
                        result.distanceKm, minutes, result.firstInstruction)
                    routeButton.text = appText("AVVIA PERCORSO")
                    if (announce) speakRouteBriefing(place.name, result, routePreviewFetchedAt)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isCalculatingRoute = false
                    routeButton.isEnabled = true
                    val cached = RouteInsightCache.loadForDestination(this, place.name, origin)
                    if (cached != null) showCachedRoutePreview(place, cached, announce)
                    else {
                        routeText.text = "ANTEPRIMA NON DISPONIBILE • connessione necessaria"
                        if (announce) speakJarvis("Non riesco a calcolare il percorso online e non ho una copia locale utilizzabile per questa destinazione.")
                        scheduleRoutePreviewRefresh()
                    }
                }
            }
        }, "jarvis-route-preview").start()
    }

    private fun showCachedRoutePreview(place: PlaceResult, cached: CachedRouteInsight, announce: Boolean = true) {
        routePreviewResult = cached.route
        routePreviewDestination = place
        routePreviewFetchedAt = cached.fetchedAtEpochMs
        drawRoute(cached.route, true)
        val minutes = (cached.route.durationSeconds / 60.0).roundToInt()
        val ageMinutes = cached.ageMs() / 60_000L
        val freshness = if (cached.isFresh()) "cache " + ageMinutes + " min"
            else "DATI VECCHI: " + ageMinutes + " min"
        routeText.text = String.format(Locale.ITALY,
            "ANTEPRIMA OFFLINE • %.1f km • circa %d min\n%s",
            cached.route.distanceKm, minutes, freshness)
        routeButton.text = appText("AVVIA PERCORSO")
        scheduleRoutePreviewRefresh()
        if (announce) speakRouteBriefing(place.name, cached.route, cached.fetchedAtEpochMs)
    }

    private fun scheduleRoutePreviewRefresh() {
        routePreviewRefreshHandler.removeCallbacks(routePreviewRefreshRunnable)
        if (routePreviewDestination != null && routeResult == null && dashboardStarted) {
            routePreviewRefreshHandler.postDelayed(routePreviewRefreshRunnable, 15L * 60L * 1000L)
        }
    }

    private fun speakRouteBriefing(destinationName: String, route: RouteResult, fetchedAt: Long?) {
        val minutes = (route.durationSeconds / 60.0).roundToInt()
        val cumulativeDistanceKm = DoubleArray(route.points.size)
        for (index in 1 until route.points.size) {
            val previous = route.points[index - 1]
            val point = route.points[index]
            val segmentMeters = FloatArray(1)
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                point.latitude, point.longitude, segmentMeters
            )
            cumulativeDistanceKm[index] = cumulativeDistanceKm[index - 1] +
                segmentMeters[0].toDouble() / 1000.0
        }
        val mainManeuvers = route.maneuvers
            .sortedBy { it.pointIndex }
            .mapNotNull { maneuver ->
                val instruction = ManeuverIconResolver
                    .correctedInstruction(maneuver.type, maneuver.instruction).trim()
                if (instruction.isBlank()) null else {
                    val distanceKm = cumulativeDistanceKm.getOrElse(maneuver.pointIndex) { 0.0 }
                    String.format(Locale.ITALY, "tra %.1f km: %s", distanceKm, instruction)
                }
            }
            .distinct()
            .take(3)
        val currentLimit = currentSpeedLimit
        val roadName = currentRoadMatch?.displayName
        val ageText = fetchedAt?.let {
            val stamp = java.text.SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                .format(java.util.Date(it))
            val ageMinutes = (System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L
            if (ageMinutes <= 30L) "calcolato alle " + stamp
            else "copia del percorso aggiornata alle " + stamp + ", non necessariamente attuale"
        }.orEmpty()
        val briefing = buildString {
            append("Anteprima per ").append(destinationName).append(". ")
            append(String.format(Locale.ITALY, "%.1f chilometri, circa %d minuti. ", route.distanceKm, minutes))
            if (mainManeuvers.isNotEmpty()) {
                append("Tra le indicazioni principali: ")
                append(mainManeuvers.joinToString("; ")).append(". ")
            } else {
                append("Il motore non ha restituito dettagli di svolta sufficienti per un riepilogo più preciso. ")
            }
            if (!roadName.isNullOrBlank()) append("Sulla strada attuale risulta ").append(roadName).append(". ")
            if (currentLimit != null) {
                append("Il limite mappato sulla strada attuale è ").append(currentLimit)
                    .append(" chilometri orari; non significa che valga per tutto il percorso. ")
            } else {
                append("Non ho un limite di velocità verificato per la strada attuale. ")
            }
            if (ageText.isNotBlank()) append(ageText).append(". ")
            append("Attenzione: questa anteprima non contiene un controllo verificato di limiti, autovelox, traffico o meteo lungo tutto il percorso; l'assenza di queste informazioni non significa che non ci siano. ")
            append("Questa è un'anteprima: la navigazione non è stata avviata.")
        }
        speakJarvis(briefing)
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
                    prefetchedRouteLimits = emptyList()
                    prefetchRouteSpeedLimits(result)
                    navigationArrived = false
                    arrivalClosestMeters = Float.MAX_VALUE
                    arrivalStopSinceMs = 0L
                    offRouteSinceMs = 0L
                    routePreviewResult = null
                    routePreviewDestination = null
                    routePreviewFetchedAt = 0L
                    routePreviewRefreshHandler.removeCallbacks(routePreviewRefreshRunnable)
                    RouteInsightCache.save(this, target.name, target.point, origin, result)
                    resetRouteProgress()
                    updateRouteMetrics(currentLocation)
                    if (voiceGuideReady) { voiceGuide?.setSpeechRate(0.96f); voiceGuide?.speak("Avvio la navigazione", TextToSpeech.QUEUE_FLUSH, null, "navigation-start") }
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
                    routeButton.text = appText("TERMINA NAVIGAZIONE")
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
        // A dashboard-started recording is a standalone trip log and must survive
        // arrival, route cancellation, or clearing a destination.
        if (!recordingIndependentOfNavigation) {
            finishGpxRecording(save = true)
            stopAndSaveGpsDiagnosticRecording("percorso terminato")
        }
        routeResult = null
        prefetchedRouteLimits = emptyList()
        routePreviewResult = null
        routePreviewDestination = null
        routePreviewFetchedAt = 0L
        routePreviewRefreshHandler.removeCallbacks(routePreviewRefreshRunnable)
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

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
        }
        fullScreenRoot = page

        // Two compact rows prevent labels from being clipped in portrait orientation.
        fun controlRow(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(1), dp(2), dp(1))
        }
        fun addControl(row: LinearLayout, button: Button, weight: Float = 1f) {
            row.addView(button, LinearLayout.LayoutParams(0, dp(44), weight).apply {
                setMargins(dp(2), dp(1), dp(2), dp(1))
            })
        }

        val firstRow = controlRow()
        addControl(firstRow, makeButton("CRUSCOTTO") { exitMapFullScreen() }, 1.25f)
        addControl(firstRow, makeButton("LUOGHI") { showSavedPois() })
        addControl(firstRow, makeButton("⌖ GPS") { recenterMapToGps() })
        addControl(firstRow, makeButton("LAYER") { showMapLayerPicker() })

        val secondRow = controlRow()
        val segmentsButton = makeButton("TRATTI") { }
        segmentsButton.setOnClickListener { showManualSpeedLimitSegmentsMenu() }
        addControl(secondRow, segmentsButton)
        val flagButton = makeButton(if (selectionMode) "SELEZIONA ✓" else "SELEZIONA") { }
        flagButton.setOnClickListener {
            selectionMode = !selectionMode
            flagButton.text = if (selectionMode) "SELEZIONA ✓" else "SELEZIONA"
        }
        addControl(secondRow, flagButton, 1.2f)
        finishSegmentButton = makeButton("FINE") { finishManualSegmentDraft() }
        finishSegmentButton?.visibility = if (manualSegmentEditing) View.VISIBLE else View.GONE
        addControl(secondRow, finishSegmentButton!!)

        page.addView(firstRow, params(-1, dp(48)))
        page.addView(secondRow, params(-1, dp(48)))
        page.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun showMapLayerPicker() {
        val options = arrayOf("Stradale (segue il tema automatico)", "Chiara", "Satellite")
        val selected = when (mapLayer) { "light" -> 1; "satellite" -> 2; else -> 0 }
        AlertDialog.Builder(this)
            .setTitle("Livello mappa")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                mapLayer = when (which) { 1 -> "light"; 2 -> "satellite"; else -> "standard" }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_MAP_LAYER, mapLayer).apply()
                map?.let { applyMapStyle(it) }
                dialog.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
    private fun exitMapFullScreen() {
        val view = mapView ?: return
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        val parent = mapOriginalParent
        if (parent != null) {
            parent.addView(view, mapOriginalIndex.coerceIn(0, parent.childCount), mapOriginalParams)
        }
        fullScreenMap = false
        finishSegmentButton = null
        if (manualSegmentEditing) { manualSegmentEditing = false; manualSegmentDraft.clear(); refreshManualSegmentMarkers() }
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
        appendSpeedLimitTracePoint(smoothed)
        if (pendingJarvisWeather && location.hasAccuracy() && location.accuracy <= 100f) {
            pendingJarvisWeather = false
            answerJarvisWeather()
        }
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
        if (checkArrivalAndFinish(smoothed)) return
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

    private fun appendSpeedLimitTracePoint(location: Location) {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        synchronized(speedLimitTrace) {
            val last = speedLimitTrace.lastOrNull()
            val elapsed = if (last != null && location.elapsedRealtimeNanos > 0L && last.elapsedRealtimeNanos > 0L)
                (location.elapsedRealtimeNanos - last.elapsedRealtimeNanos) / 1_000_000L else Long.MAX_VALUE
            if (last == null || last.distanceTo(location) >= 12f || elapsed >= 2500L) {
                speedLimitTrace.add(Location(location))
            }
            val cutoffNs = location.elapsedRealtimeNanos - 60_000_000_000L
            speedLimitTrace.removeAll { it.elapsedRealtimeNanos > 0L && it.elapsedRealtimeNanos < cutoffNs }
            while (speedLimitTrace.size > 8) speedLimitTrace.removeAt(0)
        }
    }

    private fun speedLimitFix(location: Location, timeMs: Long): SpeedLimitFix {
        // Ignore the course while almost stationary: Android may retain a stale bearing.
        val reliableBearing = if (location.hasBearing() && location.hasSpeed() && location.speed >= 2.5f &&
            location.bearing.isFinite()) location.bearing else null
        return SpeedLimitFix(location.latitude, location.longitude, reliableBearing, timeMs)
    }

    private fun recordTomTomOutcome(result: TomTomSpeedLimitResult?) {
        if (result == null) return
        val nowMs = System.currentTimeMillis()
        when (result.status) {
            TomTomLookupStatus.RATE_LIMITED -> tomTomBackoff.onRateLimited(result.retryAfterSec, nowMs)
            TomTomLookupStatus.TIMEOUT,
            TomTomLookupStatus.NETWORK_ERROR,
            TomTomLookupStatus.HTTP_ERROR -> tomTomBackoff.onTransientFailure(nowMs)
            TomTomLookupStatus.LIMIT,
            TomTomLookupStatus.NO_LIMIT -> tomTomBackoff.onSuccess()
        }
    }

    private fun prefetchRouteSpeedLimits(route: RouteResult) {
        prefetchedRouteLimits = emptyList()
        if (route.points.size < 2) return
        thread(name = "tomtom-route-prefetch") {
            val samples = RenderTomTomSpeedLimitService.prefetchRouteLimits(route.points)
                .filter { it.routeIndex in route.points.indices && it.speedLimitKmh != null }
            runOnUiThread {
                // A route may have been replaced while the network request was running.
                if (routeResult === route || routeResult == route) {
                    prefetchedRouteLimits = samples
                    android.util.Log.i("NEXT_NAVI_SPEED_LIMIT", "TomTom route prefetch cached ${samples.size} explicit limits")
                    currentLocation?.let { refreshSpeedLimitIfNeeded(it) }
                }
            }
        }
    }

    private fun refreshSpeedLimitIfNeeded(location: Location) {
        // A manual value takes precedence over all providers until the rider explicitly
        // switches back to automatic mode. Never let a late network response replace it.
        if (manualSpeedLimitSegments.isNotEmpty()) {
            val b = location.bearing.takeIf { location.hasBearing() && location.hasSpeed() && location.speed >= 2.5f }
            val match = ManualSpeedLimitMatcher.find(manualSpeedLimitSegments, location.latitude, location.longitude, b, if (location.hasAccuracy()) location.accuracy else null)
            if (match != null) { currentSpeedLimit = match.limitKmh; updateSpeedLimitDisplay(); return }
            // Do not carry a previous segment limit onto a nearby parallel road or beyond its endpoint.
            currentSpeedLimit = null
            updateSpeedLimitDisplay()
        } else manualSpeedLimit?.let { currentSpeedLimit = it; updateSpeedLimitDisplay(); return }
        val now = System.currentTimeMillis()
        val nowFix = speedLimitFix(location, now)
        // Prefer a prefetched TomTom result only after the rider has reached its route sample.
        // This avoids showing a future limit early and keeps live GPS lookups as the safe fallback.
        val activeRoute = routeResult
        if (activeRoute != null && prefetchedRouteLimits.isNotEmpty()) {
            val progress = findRouteProgressIndex(activeRoute, location)
            if (progress.second.isFinite() && progress.second < Float.MAX_VALUE) {
                val eligible = prefetchedRouteLimits.asSequence()
                    .filter { it.routeIndex <= progress.first && it.routeIndex in activeRoute.points.indices }
                    .mapNotNull { sample ->
                        val point = activeRoute.points[sample.routeIndex]
                        val distance = FloatArray(1)
                        Location.distanceBetween(location.latitude, location.longitude, point.latitude, point.longitude, distance)
                        if (distance[0] <= 180f) sample to distance[0] else null
                    }
                    .minByOrNull { it.second }
                val cachedLimit = eligible?.first
                if (cachedLimit?.speedLimitKmh != null) {
                    currentRoadMatch = RoadMatch(cachedLimit.road, cachedLimit.ref, cachedLimit.speedLimitKmh,
                        null, (eligible?.second ?: 0f), location.bearing.takeIf { location.hasBearing() } ?: 0f)
                    currentSpeedLimit = cachedLimit.speedLimitKmh
                    speedLimitVerifiedFix = nowFix
                    updateSpeedLimitDisplay()
                    return
                }
            }
        }
        // A verified limit stays on screen only while the vehicle is still compatible with the place
        // where it was verified (bounded distance and age, same line of travel, no turn). Otherwise
        // it becomes unknown. This replaces the old blanket "moved more than 180 m" rule, which
        // blanked a perfectly valid limit on a straight road yet had no notion of parallel roads.
        val verifiedFix = speedLimitVerifiedFix
        if (currentSpeedLimit != null && verifiedFix != null &&
            !SpeedLimitPolicy.isLimitApplicable(verifiedFix, nowFix)) {
            currentRoadMatch = null
            currentSpeedLimit = null
            speedLimitVerifiedFix = null
            updateGpsStatus(location)
            updateSpeedLimitDisplay()
        }
        // Ask the providers again by distance travelled, not every few seconds: the paid TomTom
        // quota is limited and the previous 8 s / 150 m cadence also tripped the backend limiter.
        val previousFix = lastSpeedLimitQueryLocation?.let { speedLimitFix(it, lastSpeedLimitQueryMs) }
        if (!SpeedLimitPolicy.shouldQuery(previousFix, nowFix)) {
            updateSpeedLimitDisplay()
            return
        }
        // Never launch a second slow network lookup while the previous one is running.
        // Otherwise each new GPS fix supersedes the older response and the sign can
        // remain blank forever on a moving vehicle.
        if (speedLimitLookupInFlight) {
            updateSpeedLimitDisplay()
            return
        }
        lastSpeedLimitQueryLocation = Location(location)
        lastSpeedLimitQueryMs = now
        val queryLocation = Location(location)
        val queryFix = speedLimitFix(queryLocation, now)
        // Ignore bearing while almost stationary: Android may retain a stale course.
        val bearing = queryLocation.bearing.takeIf {
            queryLocation.hasBearing() && queryLocation.hasSpeed() && queryLocation.speed >= 2.5f
        }
        speedLimitLookupInFlight = true
        val tracePoints = synchronized(speedLimitTrace) {
            val recent = speedLimitTrace.map { Location(it) }.takeLast(8)
            if (recent.isEmpty() || recent.last().distanceTo(queryLocation) > 3f) recent + Location(queryLocation) else recent
        }.takeLast(8)
        thread(name = "speed-limit-provider-match") {
            val tomTomResult = if (tomTomBackoff.isBlocked(System.currentTimeMillis())) {
                android.util.Log.i("NEXT_NAVI_SPEED_LIMIT", "TomTom lookup skipped: backoff for " +
                    tomTomBackoff.remainingMs(System.currentTimeMillis()) / 1000L + " s")
                null
            } else try {
                RenderTomTomSpeedLimitService.findLimit(tracePoints)
            } catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_SPEED_LIMIT", "TomTom trace lookup failed", error)
                null
            }
            recordTomTomOutcome(tomTomResult)
            val tomTomMatch = tomTomResult?.speedLimitKmh?.let { limit ->
                RoadMatch(tomTomResult.road, tomTomResult.ref, limit, tomTomResult.highwayType, 0f, bearing ?: 0f)
            }
            // TomTom is the sole automatic speed-limit provider. Do not silently mix
            // OSM values with TomTom: conflicting datasets can display a plausible but wrong limit.
            val match = tomTomMatch
            runOnUiThread {
                speedLimitLookupInFlight = false
                if (lastSpeedLimitQueryMs != now) return@runOnUiThread
                val latest = currentLocation
                // A late answer is still useful if the vehicle is still on the line of travel it was
                // asked about; it is dropped only when position/course are no longer compatible.
                val latestFix = latest?.let { speedLimitFix(it, System.currentTimeMillis()) }
                if (latest != null && latestFix != null && !SpeedLimitPolicy.isLimitApplicable(queryFix, latestFix)) {
                    currentRoadMatch = null
                    currentSpeedLimit = null
                    speedLimitVerifiedFix = null
                    lastSpeedLimitQueryLocation = null
                    lastSpeedLimitQueryMs = 0L
                    updateGpsStatus(latest)
                    updateSpeedLimitDisplay()
                    refreshSpeedLimitIfNeeded(latest)
                    return@runOnUiThread
                }
                currentRoadMatch = match
                currentSpeedLimit = manualSpeedLimit ?: match?.speedLimitKmh
                speedLimitVerifiedFix = if (manualSpeedLimit == null && match?.speedLimitKmh != null) queryFix else null
                if (match == null) {
                    android.util.Log.i("NEXT_NAVI_SPEED_LIMIT", "No plausible road match for current GPS fix")
                } else if (match.speedLimitKmh == null) {
                    android.util.Log.i("NEXT_NAVI_SPEED_LIMIT", "Road matched but no explicit usable speed limit; road=" + (match.displayName ?: "unnamed"))
                } else {
                    android.util.Log.d("NEXT_NAVI_SPEED_LIMIT", "Matched road=" + (match.displayName ?: "unnamed") +
                        ", limit=" + match.speedLimitKmh + " km/h, source=TomTom")
                }
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
            when {
                !location.hasAccuracy() -> Color.rgb(235, 165, 0)
                location.accuracy <= 15f -> Color.rgb(0, 190, 70)
                location.accuracy <= 35f -> Color.rgb(235, 165, 0)
                else -> Color.rgb(235, 70, 55)
            }
        )
    }

    /**
     * Shared status palette for GPS and Radar OSM:
     * green = usable/current data, amber = pending or incomplete, red = unavailable/error.
     * Radar data availability is independent from GPS fix quality.
     */
    private fun updateRadarStatusColor() {
        if (!::radarText.isInitialized) return
        val label = radarText.text?.toString().orEmpty()
        val color = when {
            label.contains("AUTOVELOX", ignoreCase = true) ||
                radarFetchFailed ||
                label.contains("server non disponibile", ignoreCase = true) -> Color.rgb(235, 70, 55)
            label.contains("aggiornamento", ignoreCase = true) ||
                label.contains("ricerca dati", ignoreCase = true) ||
                !radarCacheCoversCurrentArea -> Color.rgb(235, 165, 0)
            else -> Color.rgb(0, 190, 70)
        }
        radarText.setTextColor(color)
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
            val result = try { WeatherService.fetch(latitude, longitude) } catch (error: Exception) { android.util.Log.w("NEXT_NAVI_WEATHER", "GPS weather refresh failed", error); null }
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


    private fun persistManualSpeedLimitSegments() {
        val a = org.json.JSONArray(); manualSpeedLimitSegments.forEach { a.put(it.toJson()) }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("manual_speed_limit_segments_json", a.toString()).apply()
        refreshManualSegmentMarkers()
        // Local save is immediate; cloud synchronization runs off the UI thread.
        manualSpeedLimitSegments.toList().forEach { syncManualSegmentToCloud(it) }
    }

    private fun syncManualSegmentToCloud(segment: ManualSpeedLimitSegment) {
        Thread {
            try {
                CloudSpeedLimitService.upsert(segment)
                android.util.Log.i("NEXT_NAVI_CLOUD", "Segment synced: " + segment.id)
            } catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_CLOUD", "Segment remains local; cloud sync failed", error)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) Toast.makeText(this, "Tratto salvato sul telefono; sincronizzazione cloud non riuscita. Riproverò al prossimo avvio.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun deleteManualSegmentFromCloud(id: String) {
        Thread {
            try { CloudSpeedLimitService.delete(id) }
            catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_CLOUD", "Cloud delete failed for " + id, error)
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val pending = prefs.getStringSet("manual_segments_pending_cloud_delete", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                pending.add(id)
                prefs.edit().putStringSet("manual_segments_pending_cloud_delete", pending).apply()
            }
        }.start()
    }

    private fun syncManualSegmentsWithCloud() {
        Thread {
            try {
                val cloudSegments = CloudSpeedLimitService.list()
                val cloudById = cloudSegments.associateBy { it.id }
                val localSnapshot = manualSpeedLimitSegments.toList()
                val merged = LinkedHashMap<String, ManualSpeedLimitSegment>()
                cloudSegments.forEach { merged[it.id] = it }
                localSnapshot.forEach { local ->
                    val cloud = cloudById[local.id]
                    if (cloud == null || local.updatedAtEpochMs > cloud.updatedAtEpochMs) {
                        merged[local.id] = local
                        CloudSpeedLimitService.upsert(local)
                    }
                }
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val pendingDeletes = prefs.getStringSet("manual_segments_pending_cloud_delete", mutableSetOf())?.toSet().orEmpty()
                pendingDeletes.forEach { id ->
                    try {
                        CloudSpeedLimitService.delete(id)
                        val remaining = prefs.getStringSet("manual_segments_pending_cloud_delete", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        remaining.remove(id)
                        prefs.edit().putStringSet("manual_segments_pending_cloud_delete", remaining).apply()
                    } catch (_: Exception) { }
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    manualSpeedLimitSegments.clear()
                    manualSpeedLimitSegments.addAll(merged.values.sortedBy { it.roadName.orEmpty().lowercase(Locale.ROOT) })
                    val a = org.json.JSONArray(); manualSpeedLimitSegments.forEach { a.put(it.toJson()) }
                    prefs.edit().putString("manual_speed_limit_segments_json", a.toString()).apply()
                    refreshManualSegmentMarkers()
                    currentLocation?.let { refreshSpeedLimitIfNeeded(it) }
                    Toast.makeText(this, "Archivio cloud sincronizzato: " + manualSpeedLimitSegments.size + " tratti.", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_CLOUD", "Cloud archive unavailable; keeping local archive", error)
            }
        }.start()
    }

    private fun showManualSpeedLimitSegmentsMenu() {
        val labels = mutableListOf("⌕ Cerca strada online (geometria OSM + limite TomTom)", "＋ Crea tratto manualmente sulla mappa")
        labels += manualSpeedLimitSegments.mapIndexed { i, s -> (s.roadName ?: "Tratto " + (i + 1)) + " • → " + (s.forwardLimitKmh ?: "—") + " / ← " + (s.reverseLimitKmh ?: "—") + " km/h" }
        // Android AlertDialog cannot reliably display setMessage() and setItems() together.
        // Keeping both caused the explanatory message to replace the actionable editor choices.
        AlertDialog.Builder(this).setTitle("Limiti stradali per tratto")
            .setItems(labels.toTypedArray()) { _, i ->
                when { i == 0 -> searchRoadForEditor(); i == 1 -> startManualSegmentDraft(); else -> editManualSegment(i - 2) }
            }
            .setNegativeButton("Chiudi", null).show()
    }

    private fun startManualSegmentDraft() {
        roadEditorGeometry = emptyList(); roadEditorRoad = null; roadEditorGeometryTags = null; roadEditorStartIndex = null; roadEditorProposedLimit = null; roadEditorProposedSource = null
        manualSegmentDraft.clear(); manualSegmentEditing = true; selectionMode = false
        if (!fullScreenMap) enterMapFullScreen()
        finishSegmentButton?.visibility = View.VISIBLE
        Toast.makeText(this, "Tocca almeno 3 punti sulla strada, dal primo all'ultimo nel verso desiderato. Poi premi FINE.", Toast.LENGTH_LONG).show()
        refreshManualSegmentMarkers()
    }

    private fun finishManualSegmentDraft() {
        if (manualSegmentDraft.size < (if (roadEditorRoad != null) 2 else 3)) { Toast.makeText(this, if (roadEditorRoad != null) "Seleziona un tratto valido sulla geometria OSM." else "Servono almeno 3 punti lungo la strada.", Toast.LENGTH_LONG).show(); return }
        val name = EditText(this).apply { hint = "Nome strada (facoltativo)"; setText(roadEditorRoad?.name.orEmpty()) }
        val f = EditText(this).apply { hint = "Limite nel verso selezionato"; inputType = InputType.TYPE_CLASS_NUMBER; roadEditorProposedLimit?.let { setText(it.toString()) } }
        val r = EditText(this).apply { hint = "Limite nel verso opposto (facoltativo)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)); addView(name); addView(f); addView(r) }
        val d = AlertDialog.Builder(this).setTitle("Limiti verificati").setMessage("Inserisci i due sensi separatamente; lascia vuoto quello non verificato.").setView(box).setPositiveButton("Salva", null).setNegativeButton("Annulla", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fv=f.text.toString().trim().toIntOrNull(); val rv=r.text.toString().trim().toIntOrNull()
                if ((fv!=null && fv !in 5..200)||(rv!=null && rv !in 5..200)||(fv==null && rv==null)) { Toast.makeText(this,"Inserisci almeno un limite valido da 5 a 200.",Toast.LENGTH_LONG).show(); return@setOnClickListener }
                manualSpeedLimitSegments.add(ManualSpeedLimitSegment("manual-"+System.currentTimeMillis(), roadEditorRoad?.let { "osm:way:" + it.osmId }, name.text.toString().trim().takeIf { it.isNotBlank() }, manualSegmentDraft.toList(), fv, rv, System.currentTimeMillis(), providerSpeedLimitKmh = roadEditorProposedLimit, providerSource = roadEditorProposedSource))
                manualSpeedLimit=null; getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("manual_speed_limit_kmh").apply()
                manualSegmentDraft.clear(); manualSegmentEditing=false; roadEditorGeometry=emptyList(); roadEditorRoad=null; roadEditorStartIndex=null; roadEditorProposedLimit=null; roadEditorProposedSource=null; finishSegmentButton?.visibility=View.GONE
                persistManualSpeedLimitSegments(); d.dismiss()
                Toast.makeText(this,"Tratto salvato: limite applicato solo vicino alla linea e con direzione coerente.",Toast.LENGTH_LONG).show()
                currentLocation?.let { refreshSpeedLimitIfNeeded(it) }
            }
        }
        d.show()
    }

    private fun searchRoadForEditor() {
        val query = EditText(this).apply { hint = "Es. TF-82, TF-1, Calle Morro Afonso Alto"; setSingleLine(true) }
        val dialog = AlertDialog.Builder(this).setTitle("Cerca una strada").setMessage("Ricerca online OpenStreetMap. Seleziona il risultato corretto, controllando località e posizione.")
            .setView(query)
            .setPositiveButton("Cerca", null)
            .setNeutralButton("Seleziona sulla mappa") { _, _ -> startManualSegmentDraft() }
            .setNegativeButton("Annulla", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = query.text.toString().trim()
                if (text.length < 2) { query.error = "Inserisci almeno 2 caratteri"; return@setOnClickListener }
                dialog.dismiss()
                Toast.makeText(this, "Cerco la strada su OpenStreetMap…", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val roads = RenderRoadEditorService.searchRoads(text)
                        runOnUiThread {
                            if (isFinishing) return@runOnUiThread
                            if (roads.isEmpty()) {
                                AlertDialog.Builder(this).setTitle("Nessuna strada trovata").setMessage("OpenStreetMap non ha restituito corrispondenze stradali utilizzabili per “" + text + "”. Nessun dato è stato inventato.")
                                    .setPositiveButton("OK", null).show()
                            } else {
                                val labels = roads.map { it.name + "\n" + it.displayName + "\nOSM way " + it.osmId }
                                AlertDialog.Builder(this).setTitle("Scegli la strada corretta")
                                    .setItems(labels.toTypedArray()) { _, index -> loadRoadForEditor(roads[index]) }
                                    .setNegativeButton("Annulla", null).show()
                            }
                        }
                    } catch (error: Exception) {
                        runOnUiThread { if (!isFinishing) AlertDialog.Builder(this).setTitle("Ricerca non disponibile")
                            .setMessage(error.message ?: "Il backend Render o OpenStreetMap non ha risposto.")
                            .setPositiveButton("OK", null).show() }
                    }
                }.start()
            }
        }
        dialog.show()
    }

    private fun loadRoadForEditor(road: RoadSearchResult) {
        Toast.makeText(this, "Carico geometria OSM e verifico il limite disponibile…", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val geometry = RenderRoadEditorService.fetchGeometry(road)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    roadEditorRoad = road
                    roadEditorGeometryTags = geometry.tags
                    roadEditorGeometry = geometry.geometry
                    roadEditorStartIndex = null
                    roadEditorProposedLimit = null
                    roadEditorProposedSource = null
                    manualSegmentDraft.clear()
                    manualSegmentEditing = true
                    selectionMode = false
                    if (!fullScreenMap) enterMapFullScreen()
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(road.latitude, road.longitude), 15.5))
                    finishSegmentButton?.visibility = View.VISIBLE
                    val direction = geometry.tags.optString("oneway").takeIf { it.isNotBlank() && it != "null" } ?: "non specificato"
                    val limitText = "Limite automatico: solo TomTom; verrà verificato dopo aver selezionato il tratto"
                    AlertDialog.Builder(this).setTitle(geometry.name ?: road.name)
                        .setMessage("Geometria reale OSM caricata (" + geometry.geometry.size + " punti). Limite proposto: " + limitText + ". OSM oneway: " + direction + ". Tocca INIZIO e poi FINE del tratto sulla strada. La geometria viene mostrata sulla mappa.")
                        .setPositiveButton("Seleziona tratto") { _, _ ->
                            manualSegmentDraft.clear()
                            refreshManualSegmentMarkers()
                            Toast.makeText(this, "Tocca l’inizio del tratto, poi la fine. I punti verranno agganciati alla strada OSM.", Toast.LENGTH_LONG).show()
                        }.setNegativeButton("Annulla") { _, _ ->
                            manualSegmentEditing = false; roadEditorGeometry = emptyList(); roadEditorRoad = null; roadEditorGeometryTags = null; roadEditorStartIndex = null; roadEditorProposedLimit = null; roadEditorProposedSource = null
                            refreshManualSegmentMarkers()
                        }.show()
                    refreshManualSegmentMarkers()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing) AlertDialog.Builder(this)
                        .setTitle("Geometria stradale non disponibile")
                        .setMessage((error.message ?: "Non è stato possibile recuperare la geometria OSM.") +
                            "\n\nPuoi continuare senza la geometria online selezionando manualmente il tratto sulla mappa. " +
                            "In questo caso dovrai indicare almeno 3 punti reali lungo la strada e verificare tu il limite.")
                        .setPositiveButton("Seleziona manualmente") { _, _ -> startManualSegmentDraft() }
                        .setNegativeButton("Chiudi", null)
                        .show()
                }
            }
        }.start()
    }

    private fun parseExplicitSpeedLimit(raw: String?): Int? {
        val value = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
        val match = Regex("^(\\d{1,3})(?:\\s*(km/h|kmh|kph|mph|mi/h))?$").matchEntire(value) ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        val kmh = if (unit == "mph" || unit == "mi/h") kotlin.math.round(number * 1.609344).toInt() else number
        return kmh.takeIf { it in 5..200 }
    }

    private fun nearestRoadGeometryIndex(point: NavPoint, geometry: List<NavPoint>): Int {
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        geometry.forEachIndexed { index, candidate ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, candidate.latitude, candidate.longitude, results)
            if (results[0].toDouble() < bestDistance) { bestDistance = results[0].toDouble(); bestIndex = index }
        }
        return bestIndex
    }
    private fun editManualSegment(index: Int) {
        val s=manualSpeedLimitSegments.getOrNull(index) ?: return
        val f=EditText(this).apply { hint="Limite nel verso delle frecce"; inputType=InputType.TYPE_CLASS_NUMBER; setText(s.forwardLimitKmh?.toString().orEmpty()) }
        val r=EditText(this).apply { hint="Limite nel verso opposto"; inputType=InputType.TYPE_CLASS_NUMBER; setText(s.reverseLimitKmh?.toString().orEmpty()) }
        AlertDialog.Builder(this).setTitle(s.roadName ?: "Tratto manuale")
            .setItems(arrayOf("Modifica limiti", "Elimina tratto")) { _, which ->
                if (which==1) AlertDialog.Builder(this).setTitle("Eliminare il tratto?").setPositiveButton("Elimina") { _, _ -> deleteManualSegmentFromCloud(s.id); manualSpeedLimitSegments.removeAt(index); persistManualSpeedLimitSegments(); currentLocation?.let { refreshSpeedLimitIfNeeded(it) } }.setNegativeButton("Annulla",null).show()
                else {
                    val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(4)); addView(f); addView(r) }
                    val d=AlertDialog.Builder(this).setTitle("Modifica limiti").setView(box).setPositiveButton("Salva",null).setNegativeButton("Annulla",null).create()
                    d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val fv=f.text.toString().trim().toIntOrNull(); val rv=r.text.toString().trim().toIntOrNull()
                        if ((fv!=null&&fv !in 5..200)||(rv!=null&&rv !in 5..200)||(fv==null&&rv==null)) { Toast.makeText(this,"Limiti non validi.",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                        manualSpeedLimitSegments[index]=s.copy(forwardLimitKmh=fv,reverseLimitKmh=rv,updatedAtEpochMs=System.currentTimeMillis(),revision=s.revision+1)
                        persistManualSpeedLimitSegments(); d.dismiss(); currentLocation?.let { refreshSpeedLimitIfNeeded(it) }
                    } }; d.show()
                }
            }.setNeutralButton("Chiudi",null).show()
    }

    private fun refreshManualSegmentMarkers() {
        if (!mapStyleReady) return
        val style=map?.style ?: return
        val ls=style.getSource(SOURCE_MANUAL_SEGMENTS) as? GeoJsonSource ?: return
        val ars=style.getSource(SOURCE_MANUAL_ARROWS) as? GeoJsonSource ?: return
        val fs=style.getSource(SOURCE_MANUAL_FLAGS) as? GeoJsonSource ?: return
        val lines=mutableListOf<Feature>(); val arrows=mutableListOf<Feature>(); val flags=mutableListOf<Feature>()
        fun add(points:List<NavPoint>) {
            if(points.size<2)return
            lines.add(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude,it.latitude) })))
            points.forEachIndexed { i,p ->
                if (i == 0 || i == points.lastIndex) flags.add(Feature.fromGeometry(Point.fromLngLat(p.longitude,p.latitude)))
                if(i<points.lastIndex) { val n=points[i+1]; val a=Feature.fromGeometry(Point.fromLngLat((p.longitude+n.longitude)/2,(p.latitude+n.latitude)/2)); a.addNumberProperty("bearing",bearingBetween(p,n)); arrows.add(a) }
            }
        }
        // Keep the map clean during normal navigation: saved speed-limit segments are matched
        // against GPS in the background and should not remain painted over the live map.
        // Show the full OSM way and selected segment only while the editor is active.
        if (manualSegmentEditing && roadEditorGeometry.size >= 2) add(roadEditorGeometry)
        if (manualSegmentEditing && manualSegmentDraft.size >= 2) add(manualSegmentDraft)
        ls.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(lines.toTypedArray()))
        ars.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(arrows.toTypedArray()))
        fs.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(flags.toTypedArray()))
    }

    private fun bearingBetween(a:NavPoint,b:NavPoint):Float {
        val p1=Math.toRadians(a.latitude); val p2=Math.toRadians(b.latitude); val dl=Math.toRadians(b.longitude-a.longitude)
        val y=kotlin.math.sin(dl)*kotlin.math.cos(p2); val x=kotlin.math.cos(p1)*kotlin.math.sin(p2)-kotlin.math.sin(p1)*kotlin.math.cos(p2)*kotlin.math.cos(dl)
        return ((Math.toDegrees(kotlin.math.atan2(y,x))+360.0)%360.0).toFloat()
    }

    private fun showSpeedLimitOptions() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Limite in km/h (5–200)"
            textSize = 22f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setText(manualSpeedLimit?.toString() ?: currentSpeedLimit?.toString().orEmpty())
            selectAll()
            contentDescription = "Inserisci il limite di velocità in chilometri orari"
        }
        val manageSegmentsButton = Button(this).apply {
            text = "Gestisci limiti manuali per tratto"
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
            addView(TextView(this@MainActivity).apply {
                text = if (manualSpeedLimit != null) {
                    "Limite manuale attivo. Il valore resta salvato anche dopo la chiusura dell'app."
                } else {
                    "Imposta un limite noto per la strada attuale. Non viene verificato automaticamente."
                }
                textSize = 14f
                setPadding(0, 0, 0, dp(10))
            })
            addView(manageSegmentsButton, LinearLayout.LayoutParams(-1, dp(48)))
            addView(input, LinearLayout.LayoutParams(-1, dp(56)))
            addView(TextView(this@MainActivity).apply {
                text = "Usa solo un limite verificato sulla segnaletica stradale."
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Limite di velocità")
            .setView(content)
            .setPositiveButton("Salva limite", null)
            .setNeutralButton(if (manualSpeedLimit != null) "Modalità automatica" else "Riprova ricerca", null)
            .setNegativeButton("Annulla", null)
            .create()
        // Close the speed-limit dialog before opening the segment editor, avoiding a hidden dialog.
        manageSegmentsButton.setOnClickListener {
            dialog.dismiss()
            showManualSpeedLimitSegmentsMenu()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null || value !in 5..200) {
                    input.error = "Inserisci un valore da 5 a 200 km/h"
                    return@setOnClickListener
                }
                manualSpeedLimit = value
                currentSpeedLimit = value
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt("manual_speed_limit_kmh", value)
                    .apply()
                lastSpeedLimitQueryLocation = null
                lastSpeedLimitQueryMs = 0L
                speedingVoiceActive = false
                updateSpeedLimitDisplay()
                Toast.makeText(this, "Limite manuale impostato: $value km/h", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (manualSpeedLimit != null) {
                    manualSpeedLimit = null
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .remove("manual_speed_limit_kmh")
                        .apply()
                    currentSpeedLimit = null
                    currentRoadMatch = null
                    lastSpeedLimitQueryLocation = null
                    lastSpeedLimitQueryMs = 0L
                    speedingVoiceActive = false
                    updateSpeedLimitDisplay()
                    val location = currentLocation
                    if (location != null) refreshSpeedLimitIfNeeded(location)
                    Toast.makeText(this, "Modalità automatica riattivata", Toast.LENGTH_SHORT).show()
                } else {
                    val location = currentLocation
                    if (location == null) {
                        Toast.makeText(this, "In attesa della posizione GPS", Toast.LENGTH_SHORT).show()
                    } else {
                        lastSpeedLimitQueryLocation = null
                        lastSpeedLimitQueryMs = 0L
                        refreshSpeedLimitIfNeeded(location)
                        Toast.makeText(this, "Nuova ricerca del limite avviata", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updateSpeedLimitDisplay() {
        val limit = currentSpeedLimit
        val speedKmh = speedForLimitAlertsKmh()

        // Speak on first confirmed overspeed, then at most once every 120 seconds.
        // A short missing-limit interval must not reset the episode and cause repeated speech.
        val nowElapsed = SystemClock.elapsedRealtime()
        if (limit != null && speedKmh != null) {
            if (speedKmh > limit + 3) {
                lastSpeedingValidLimitMs = nowElapsed
                val firstWarning = !speedingVoiceActive
                val repeatDue = speedingVoiceActive && (lastSpeedingVoiceMs == 0L || nowElapsed - lastSpeedingVoiceMs >= 120_000L)
                if (firstWarning || repeatDue) {
                    speedingVoiceActive = true
                    lastSpeedingVoiceMs = nowElapsed
                    if (voiceGuideReady) {
                        voiceGuide?.setSpeechRate(0.96f)
                        voiceGuide?.speak(
                            if (firstWarning) "Attenzione. Limite di velocità $limit chilometri orari. Stai viaggiando a $speedKmh chilometri orari."
                            else "Attenzione, sei ancora sopra il limite di $limit chilometri orari.",
                            TextToSpeech.QUEUE_FLUSH, null, "speed-limit-overspeed"
                        )
                    }
                }
            } else if (speedKmh <= limit + 1) {
                // Clear only on a confirmed return to the limit, not when the provider
                // temporarily has no answer.
                speedingVoiceActive = false
                lastSpeedingVoiceMs = 0L
                lastSpeedingValidLimitMs = 0L
                speedingEpisodeStartMs = 0L
                speedAlertHandler.removeCallbacks(speedAlertRunnable)
                speedAlertActive = false
                speedAlertFlashRed = false
                speedAlertFlashesRemaining = 0
                renderSpeedDigits(lastDisplayedSpeed)
            }
        } else if (speedingVoiceActive && nowElapsed - lastSpeedingValidLimitMs > 10_000L) {
            // The limit provider may drop out briefly; do not announce from stale/unknown data.
            lastSpeedingValidLimitMs = 0L
        }

        if (!::limitValue.isInitialized) return
        limitValue.text = limit?.toString() ?: "—"
        limitValue.contentDescription = when {
            manualSpeedLimit != null -> "Limite manuale $limit chilometri orari. Tocca per modificarlo o riattivare l'aggiornamento automatico"
            limit != null -> "Limite automatico $limit chilometri orari. Tocca per impostarlo manualmente o aggiornare"
            else -> "Limite di velocità non disponibile. Tocca per impostarlo manualmente o riprovare la ricerca"
        }
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
        if (voiceGuideReady) { voiceGuide?.setSpeechRate(0.96f); voiceGuide?.speak(
            "Attenzione, possibile autovelox tra circa " + candidates.second.toInt() + " metri",
            TextToSpeech.QUEUE_ADD, null, "radar-" + candidates.first.osmId
        ) }
        radarText.text = "⚠ AUTOVELOX • " + candidates.second.toInt() + " m"
        updateRadarStatusColor()
    }

    /**
     * Finish navigation once the rider reaches, passes, or parks just beyond the destination.
     * Passing is accepted only near the end of the active route, preventing nearby parallel
     * roads from prematurely completing navigation.
     */
    private fun checkArrivalAndFinish(location: Location): Boolean {
        val route = routeResult ?: run {
            arrivalClosestMeters = Float.MAX_VALUE
            arrivalStopSinceMs = 0L
            navigationArrived = false
            return false
        }
        val target = destination ?: return false
        if (navigationArrived) return false
        val targetLocation = Location("destination").apply {
            latitude = target.point.latitude
            longitude = target.point.longitude
        }
        val distanceToDestination = location.distanceTo(targetLocation)
        arrivalClosestMeters = minOf(arrivalClosestMeters, distanceToDestination)
        val speed = if (location.hasSpeed()) location.speed else Float.MAX_VALUE
        val now = SystemClock.elapsedRealtime()
        if (speed < 1.0f) {
            if (arrivalStopSinceMs == 0L) arrivalStopSinceMs = now
        } else {
            arrivalStopSinceMs = 0L
        }
        val accuracy = if (location.hasAccuracy()) location.accuracy else 20f
        val arrivalRadius = maxOf(30f, (accuracy * 1.5f).coerceAtMost(60f))
        val routeMatch = findRouteProgressIndex(route, location)
        // A failed/low-confidence match returns Float.MAX_VALUE while preserving the last index.
        // Never interpret that fallback index as proof that the rider reached the route end.
        val routeMatchConfident = routeMatch.second.isFinite() && routeMatch.second < Float.MAX_VALUE
        val nearRouteEnd = routeMatchConfident && route.points.size >= 2 &&
            routeMatch.first >= (route.points.lastIndex * 0.85f).toInt()
        val movingAwayAfterPassing = nearRouteEnd &&
            arrivalClosestMeters <= maxOf(70f, arrivalRadius) &&
            distanceToDestination - arrivalClosestMeters >= 35f &&
            speed > 2.5f
        val parkedJustBeyond = nearRouteEnd &&
            arrivalClosestMeters <= 100f &&
            arrivalStopSinceMs != 0L && now - arrivalStopSinceMs >= 8_000L &&
            distanceToDestination <= 120f
        if (distanceToDestination > arrivalRadius && !movingAwayAfterPassing && !parkedJustBeyond) return false

        navigationArrived = true
        // Stop the route/recording exactly once, then leave a durable arrival message on screen.
        clearRoute()
        routeText.text = appText("SEI ARRIVATO A DESTINAZIONE")
        if (voiceGuideReady) {
            voiceGuide?.speak("Sei arrivato a destinazione.", TextToSpeech.QUEUE_FLUSH, null, "navigation-arrived")
        }
        Toast.makeText(this, "Sei arrivato a destinazione", Toast.LENGTH_LONG).show()
        return true
    }

    /**
     * Compute distance to route LINE SEGMENTS, not only route vertices. Vertex-only distance
     * overestimates cross-track error on long geometry segments and can trigger false reroutes.
     */
    private fun distanceToActiveRouteMeters(location: Location, route: RouteResult): Float {
        if (route.points.size < 2) return Float.MAX_VALUE
        val lat0 = Math.toRadians(location.latitude)
        val metersPerLon = 111_320.0 * kotlin.math.cos(lat0)
        val metersPerLat = 111_320.0
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until route.points.lastIndex) {
            val a = route.points[i]
            val b = route.points[i + 1]
            val ax = (a.longitude - location.longitude) * metersPerLon
            val ay = (a.latitude - location.latitude) * metersPerLat
            val bx = (b.longitude - location.longitude) * metersPerLon
            val by = (b.latitude - location.latitude) * metersPerLat
            val dx = bx - ax
            val dy = by - ay
            val denominator = dx * dx + dy * dy
            val t = if (denominator <= 1e-6) 0.0 else ((-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0))
            val distance = kotlin.math.hypot(ax + t * dx, ay + t * dy)
            if (distance < best) best = distance
        }
        return best.toFloat()
    }

    private fun checkOffRouteAndReroute(location: Location) {
        val route = routeResult ?: run { offRouteSinceMs = 0L; return }
        val target = destination ?: run { offRouteSinceMs = 0L; return }
        if (!location.hasAccuracy() || location.accuracy > 25f) {
            offRouteSinceMs = 0L
            return
        }
        // Do not initiate a new route while already approaching/serving the destination.
        val targetLocation = Location("destination").apply {
            latitude = target.point.latitude
            longitude = target.point.longitude
        }
        if (location.distanceTo(targetLocation) <= 120f) {
            offRouteSinceMs = 0L
            return
        }
        if (!location.hasSpeed() || location.speed < 2.0f) {
            offRouteSinceMs = 0L
            return
        }
        val lateralMeters = distanceToActiveRouteMeters(location, route)
        val threshold = maxOf(25f, location.accuracy * 2.5f)
        val now = SystemClock.elapsedRealtime()
        if (lateralMeters <= threshold) {
            offRouteSinceMs = 0L
            return
        }
        if (offRouteSinceMs == 0L) offRouteSinceMs = now
        // A single noisy fix or failed route match is not enough. Require sustained departure;
        // very large departures still need a short confirmation to avoid one-fix GPS jumps.
        val hardDeparture = lateralMeters >= 60f && now - offRouteSinceMs >= 2_000L
        val sustainedDeparture = now - offRouteSinceMs >= 6_000L
        if (!hardDeparture && !sustainedDeparture) return
        if (now - lastRerouteAttemptMs < 30_000L || isCalculatingRoute) return
        lastRerouteAttemptMs = now
        offRouteSinceMs = 0L
        isCalculatingRoute = true
        if (::routeText.isInitialized) routeText.text = "DEVIAZIONE CONFERMATA • RICALCOLO…"
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
                    if (voiceGuideReady) { voiceGuide?.setSpeechRate(0.96f); voiceGuide?.speak("Percorso ricalcolato", TextToSpeech.QUEUE_FLUSH, null, "reroute") }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isCalculatingRoute = false
                    if (::routeText.isInitialized) routeText.text = "FUORI PERCORSO • RICALCOLO NON DISPONIBILE"
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
                voiceGuide?.setSpeechRate(0.96f)
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
        radarCacheCoversCurrentArea = RadarCache.isCacheNear(this, center)
        drawRadars()
        val now = System.currentTimeMillis()
        if (!RadarCache.shouldRefresh(this, center, now)) {
            updateRadarLabel()
            return
        }
        // A short request cooldown prevents duplicate calls while allowing a new
        // area to be queried quickly during a ride.
        if (now - lastRadarAttemptMs < 2L * 60L * 1000L) {
            updateRadarLabel()
            return
        }
        lastRadarAttemptMs = now
        radarText.text = if (radarCameras.isEmpty()) "RADAR OSM • aggiornamento dati…" else "RADAR OSM: " + radarCameras.size + " • aggiornamento…"
        updateRadarStatusColor()
        Thread({
            try {
                val fresh = RadarService.fetchNear(center)
                runOnUiThread {
                    radarFetchFailed = false
                    radarCameras = fresh
                    RadarCache.save(this, center, fresh)
                    radarCacheCoversCurrentArea = true
                    drawRadars()
                    updateRadarLabel()
                }
            } catch (error: Exception) {
                android.util.Log.w("NEXT_NAVI_RADAR", "OSM/Overpass radar refresh failed", error)
                runOnUiThread {
                    // Do not cache failures as empty data. Retry after a short cooldown.
                    lastRadarAttemptMs = System.currentTimeMillis()
                    radarFetchFailed = true
                    updateRadarLabel()
                }
            }
        }, "osm-radar-refresh").start()
    }

    private fun updateRadarLabel() {
        if (!::radarText.isInitialized) return
        radarText.text = if (radarCameras.isEmpty()) {
            when {
                radarFetchFailed -> "RADAR OSM • server non disponibile, riprovo"
                radarCacheCoversCurrentArea && RadarCache.lastUpdatedAt(this) > 0L -> "RADAR OSM • 0 radar OSM nell'area"
                else -> "RADAR OSM • ricerca dati…"
            }
        } else {
            "RADAR OSM: " + radarCameras.size + " • dati comunitari"
        }
        updateRadarStatusColor()
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
        // Animate across nearly the whole interval between accepted fixes. The previous
        // distance/speed duration often ended early, leaving the marker visibly paused before
        // the next fix arrived (especially at low speeds or after smoothing).
        val durationMs = if (movingTimeMs in 180L..2_000L) {
            movingTimeMs.coerceIn(220L, 1_100L)
        } else {
            ((distance / maxOf(speed, 5f)) * 1000f).toLong().coerceIn(220L, 900L)
        }
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

    private fun showExitConfirmation() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(appText("Uscire da NEXT NAVI PRO?"))
            .setMessage(appText("La navigazione verrà chiusa. Gli eventuali dati GPX/CSV in registrazione saranno salvati prima dell'uscita."))
            .setNegativeButton(appText("Annulla"), null)
            .setPositiveButton(appText("Sì, esci")) { dialog, _ ->
                dialog.dismiss()
                // Finalize paired recordings before finishing so no last samples are lost.
                if (recordingGpx || gpsDiagnosticRecording) {
                    finishGpxRecording(save = true, showResult = false)
                }
                finish()
            }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Back exits the full-screen map first; a second Back asks before closing the app.
        if (fullScreenMap) {
            exitMapFullScreen()
        } else {
            showExitConfirmation()
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
            if (::jarvisButton.isInitialized) jarvisButton.text = jarvisButtonIdleLabel()
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
        // Do not let the delayed preview runnable retain or call a destroyed Activity.
        routePreviewRefreshHandler.removeCallbacksAndMessages(null)
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
