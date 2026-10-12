package com.nextnavipro.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var root: LinearLayout
    private lateinit var speedText: TextView
    private lateinit var gpsText: TextView
    private lateinit var modeButton: Button
    private lateinit var themeButton: Button
    private var carMode = false
    private var nightMode = true
    private val splashHandler = Handler(Looper.getMainLooper())
    private var dashboardStarted = false
    private val yellow = Color.rgb(255, 212, 0)
    private val cyan = Color.rgb(0, 229, 255)
    private val white = Color.WHITE
    private val black = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        showSplash()
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
        val buttonParams = LinearLayout.LayoutParams(-1, dp(58)).apply {
            setMargins(dp(12), dp(28), dp(12), dp(12))
        }
        splash.addView(start, buttonParams)
        setContentView(splash)
        brand.startAnimation(android.view.animation.AlphaAnimation(0f, 1f).apply {
            duration = 1800
            fillAfter = true
        })
        splashHandler.postDelayed({ launchDashboard() }, 2000)
    }

    private fun launchDashboard() {
        if (dashboardStarted) return
        dashboardStarted = true
        splashHandler.removeCallbacksAndMessages(null)
        buildDashboard()
        requestGps()
    }

    private fun buildDashboard() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }
        paintBackground()
        val header = TextView(this).apply {
            text = "NEXT NAVI PRO"
            textSize = 22f
            letterSpacing = 0.16f
            setTextColor(yellow)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(header, params(-1, dp(46)))
        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val speedLabel = TextView(this).apply {
            text = "VELOCITÀ GPS"
            textSize = 13f
            letterSpacing = 0.18f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }
        dashboard.addView(speedLabel, params(-1, dp(28)))
        speedText = TextView(this).apply {
            text = "—"
            textSize = if (landscape) 88f else 104f
            setTextColor(white)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        dashboard.addView(speedText, params(-1, if (landscape) dp(116) else dp(142)))
        val unit = TextView(this).apply {
            text = "km/h"
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.CENTER
        }
        dashboard.addView(unit, params(-1, dp(30)))
        gpsText = TextView(this).apply {
            text = "IN ATTESA DEL GPS"
            textSize = 12f
            setTextColor(yellow)
            gravity = Gravity.CENTER
        }
        dashboard.addView(gpsText, params(-1, dp(34)))
        val roadCard = TextView(this).apply {
            text = "LIMITE STRADALE   —\n\nNavigazione non ancora attiva"
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(26, 26, 26))
        }
        dashboard.addView(roadCard, params(-1, dp(92)))
        root.addView(dashboard, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = makeButton(if (carMode) "MODALITÀ AUTO" else "MODALITÀ MOTO") {
            carMode = !carMode
            modeButton.text = if (carMode) "MODALITÀ AUTO" else "MODALITÀ MOTO"
            speedText.textSize = if (carMode) 82f else 104f
        }
        themeButton = makeButton(if (nightMode) "TEMA NOTTE" else "TEMA GIORNO") {
            nightMode = !nightMode
            themeButton.text = if (nightMode) "TEMA NOTTE" else "TEMA GIORNO"
            paintBackground()
            gpsText.setTextColor(if (nightMode) yellow else Color.rgb(150, 100, 0))
        }
        controls.addView(modeButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            setMargins(dp(4), dp(5), dp(4), dp(5))
        })
        controls.addView(themeButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            setMargins(dp(4), dp(5), dp(4), dp(5))
        })
        root.addView(controls, params(-1, dp(66)))
        setContentView(root)
    }

    private fun paintBackground() {
        if (::root.isInitialized) root.setBackgroundColor(
            if (nightMode) black else Color.rgb(224, 232, 238)
        )
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(if (nightMode) white else Color.BLACK)
            setBackgroundColor(if (nightMode) Color.rgb(26, 26, 26) else Color.rgb(205, 215, 222))
            setOnClickListener { action() }
        }

    private fun params(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        try {
            val providers = locationManager.getProviders(true)
            if (providers.isEmpty()) {
                gpsText.text = "GPS NON DISPONIBILE"
                return
            }
            val provider = if (providers.contains(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
                else if (providers.contains(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
                else providers.first()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.requestLocationUpdates(provider, 500L, 0f, this, Looper.getMainLooper())
            } else {
                @Suppress("DEPRECATION")
                locationManager.requestLocationUpdates(provider, 500L, 0f, this)
            }
            val last = locationManager.getLastKnownLocation(provider)
            if (last != null) onLocationChanged(last)
            gpsText.text = "RICERCA POSIZIONE GPS…"
        } catch (_: SecurityException) {
            gpsText.text = "PERMESSO GPS NEGATO"
        } catch (_: Exception) {
            gpsText.text = "GPS NON DISPONIBILE"
        }
    }

    override fun onLocationChanged(location: Location) {
        val ageMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0)
        if (ageMs > 10_000 || !location.hasAccuracy() || location.accuracy > 50f) {
            gpsText.text = "GPS INSUFFICIENTE"
            return
        }
        speedText.text = (if (location.hasSpeed()) location.speed * 3.6f else 0f).toInt().toString()
        val speed = if (location.hasSpeed()) location.speed * 3.6f else 0f
        speedText.setTextColor(when {
            speed >= 120f -> Color.RED
            speed >= 50f -> yellow
            else -> white
        })
        gpsText.text = "GPS OK  •  ±" + location.accuracy.toInt() + " m"
    }

    override fun onProviderDisabled(provider: String) { gpsText.text = "GPS DISATTIVATO" }
    override fun onProviderEnabled(provider: String) { gpsText.text = "RICERCA POSIZIONE GPS…" }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestGps()
            else {
                gpsText.text = "PERMESSO GPS NEGATO"
                Toast.makeText(this, "Concedi il permesso di localizzazione per leggere la velocità GPS.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationManager.isInitialized &&
            (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) requestGps()
    }

    override fun onPause() {
        super.onPause()
        if (::locationManager.isInitialized) locationManager.removeUpdates(this)
    }
}
