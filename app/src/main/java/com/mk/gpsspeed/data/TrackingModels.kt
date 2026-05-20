package com.mk.gpsspeed.data

import android.location.Location
import kotlin.math.roundToLong

enum class SpeedMode {
    STABLE,
    REALTIME,
}

enum class GpsStatus {
    Disabled,
    Searching,
    Weak,
    Good,
}

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)

data class SpeedSample(
    val elapsedMillis: Long,
    val speedKmh: Double,
)

data class RideSessionRecord(
    val id: Long,
    val startedAt: Long,
    val durationMillis: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val averageSpeedKmh: Double,
    val speedSamples: List<SpeedSample> = emptyList(),
)

data class TrackingUiState(
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val hasOverlayPermission: Boolean = false,
    val isOverlayVisible: Boolean = false,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val elapsedMillis: Long = 0L,
    val gpsStatus: GpsStatus = GpsStatus.Searching,
    val speedMode: SpeedMode = SpeedMode.REALTIME,
    val locationIntervalMillis: Long = 100L,
    val currentRideChartSamples: List<SpeedSample> = emptyList(),
    val rideHistory: List<RideSessionRecord> = emptyList(),
    val lastLocation: Location? = null,
)

fun Long.toClockText(): String {
    val totalSeconds = (this / 1000.0).roundToLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
