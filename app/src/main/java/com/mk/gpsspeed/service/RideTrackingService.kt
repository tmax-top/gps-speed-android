package com.mk.gpsspeed.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mk.gpsspeed.R
import com.mk.gpsspeed.data.TrackingRepository
import com.mk.gpsspeed.location.FusedLocationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RideTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationClient by lazy { FusedLocationClient(applicationContext) }

    private var trackingJob: Job? = null
    private var tickerJob: Job? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(initialStart = true)
            ACTION_RESUME -> startTracking(initialStart = false)
            ACTION_PAUSE -> pauseTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startTracking(initialStart: Boolean) {
        if (initialStart) {
            TrackingRepository.startRide()
        } else {
            TrackingRepository.resumeRide()
        }
        startForeground(NOTIFICATION_ID, buildNotification("0 km/h"))
        startLocationCollection()
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                TrackingRepository.tickElapsed()
                delay(1000L)
            }
        }
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            TrackingRepository.uiState.collectLatest { state ->
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NOTIFICATION_ID,
                    buildNotification("${state.currentSpeedKmh.roundToInt()} km/h")
                )
            }
        }
    }

    private fun startLocationCollection() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            TrackingRepository.uiState.collectLatest { state ->
                if (!state.isTracking || state.isPaused) return@collectLatest
                locationClient.locationUpdates(state.locationIntervalMillis).collectLatest { location ->
                    TrackingRepository.onLocation(location)
                }
            }
        }
    }

    private fun pauseTracking() {
        TrackingRepository.pauseRide()
        trackingJob?.cancel()
        tickerJob?.cancel()
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        tickerJob?.cancel()
        notificationJob?.cancel()
        TrackingRepository.stopRide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(speedText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(speedText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ride_tracking"
        private const val NOTIFICATION_ID = 1101

        private const val ACTION_START = "com.mk.gpsspeed.action.START"
        private const val ACTION_PAUSE = "com.mk.gpsspeed.action.PAUSE"
        private const val ACTION_RESUME = "com.mk.gpsspeed.action.RESUME"
        private const val ACTION_STOP = "com.mk.gpsspeed.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_RESUME }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
