package com.nextnavipro.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Keeps the process in foreground-service priority while the user explicitly records a trip.
 * Location collection and paired GPX/CSV persistence remain owned by MainActivity.
 */
class GpsRecordingForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.nextnavipro.app.action.START_GPS_RECORDING"
        const val ACTION_START_NAVIGATION = "com.nextnavipro.app.action.START_GPS_NAVIGATION"
        const val ACTION_SYNC = "com.nextnavipro.app.action.SYNC_GPS_FOREGROUND"
        const val ACTION_STOP = "com.nextnavipro.app.action.STOP_GPS_RECORDING"
        const val EXTRA_NAVIGATION_ACTIVE = "navigation_active"
        const val EXTRA_RECORDING_ACTIVE = "recording_active"
        private const val CHANNEL_ID = "next_navi_trip_recording"
        private const val NOTIFICATION_ID = 4107
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registrazione viaggio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene attiva la registrazione GPX e CSV durante il viaggio."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val navigationActive = intent?.getBooleanExtra(
            EXTRA_NAVIGATION_ACTIVE, intent.action == ACTION_START_NAVIGATION
        ) ?: false
        val recordingActive = intent?.getBooleanExtra(
            EXTRA_RECORDING_ACTIVE, intent.action == ACTION_START
        ) ?: false
        val title = when {
            navigationActive && recordingActive -> "NEXT NAVI PRO • navigazione e registrazione"
            navigationActive -> "NEXT NAVI PRO • navigazione GPS attiva"
            recordingActive -> "NEXT NAVI PRO • registrazione attiva"
            else -> "NEXT NAVI PRO • GPS attivo"
        }
        val content = when {
            navigationActive && recordingActive -> "Guida GPS e registrazione GPX + CSV in corso"
            navigationActive -> "La guida GPS continua anche con lo schermo spento"
            recordingActive -> "Registrazione GPX + CSV GPS in corso"
            else -> "Servizio GPS attivo"
        }
        val openApp = PendingIntent.getActivity(
            this,
            4107,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
