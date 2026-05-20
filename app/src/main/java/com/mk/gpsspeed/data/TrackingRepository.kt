package com.mk.gpsspeed.data

import android.content.Context
import android.location.Location
import com.mk.gpsspeed.data.local.RideSessionEntity
import com.mk.gpsspeed.data.local.SpeedSampleEntity
import com.mk.gpsspeed.data.local.TrackingDao
import com.mk.gpsspeed.data.local.TrackingDatabase
import com.mk.gpsspeed.data.local.toRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

object TrackingRepository {
    private const val LIVE_CHART_WINDOW_MILLIS = 10_000L

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var rideStartTimestamp: Long = 0L
    private var pausedAccumulatedMillis: Long = 0L
    private var pausedStartedAt: Long = 0L
    private var points: MutableList<Location> = mutableListOf()
    private var pendingSamples: MutableList<SpeedSampleEntity> = mutableListOf()
    private var lastPublishedChartBucket: Long = -1L
    private var trackingDao: TrackingDao? = null
    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        trackingDao = TrackingDatabase.getInstance(context).trackingDao()
        repositoryScope.launch {
            trackingDao?.observeRideSessions()?.collectLatest { items ->
                _uiState.update { state ->
                    state.copy(rideHistory = items.map { it.toRecord() })
                }
            }
        }
    }

    fun updatePermissions(hasLocationPermission: Boolean, hasNotificationPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasLocationPermission = hasLocationPermission,
                hasNotificationPermission = hasNotificationPermission,
            )
        }
    }

    fun updateOverlayPermission(granted: Boolean) {
        _uiState.update { it.copy(hasOverlayPermission = granted) }
    }

    fun updateOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(isOverlayVisible = visible) }
    }

    fun setSpeedMode(mode: SpeedMode) {
        val intervalMillis = when (mode) {
            SpeedMode.STABLE -> 500L
            SpeedMode.REALTIME -> 100L
        }
        _uiState.update { it.copy(speedMode = mode, locationIntervalMillis = intervalMillis) }
    }

    fun startRide() {
        val now = System.currentTimeMillis()
        rideStartTimestamp = now
        pausedAccumulatedMillis = 0L
        pausedStartedAt = 0L
        points = mutableListOf()
        pendingSamples = mutableListOf()
        lastPublishedChartBucket = -1L
        _uiState.update {
            it.copy(
                isTracking = true,
                isPaused = false,
                currentSpeedKmh = 0.0,
                maxSpeedKmh = 0.0,
                distanceKm = 0.0,
                elapsedMillis = 0L,
                gpsStatus = GpsStatus.Searching,
                currentRideChartSamples = emptyList(),
                lastLocation = null,
            )
        }
    }

    fun pauseRide() {
        if (!_uiState.value.isTracking || _uiState.value.isPaused) return
        pausedStartedAt = System.currentTimeMillis()
        _uiState.update { it.copy(isPaused = true) }
    }

    fun resumeRide() {
        if (!_uiState.value.isTracking || !_uiState.value.isPaused) return
        if (pausedStartedAt != 0L) {
            pausedAccumulatedMillis += System.currentTimeMillis() - pausedStartedAt
            pausedStartedAt = 0L
        }
        _uiState.update { it.copy(isPaused = false) }
    }

    fun stopRide() {
        val state = _uiState.value
        if (!state.isTracking) return
        val summary = RideSessionRecord(
            id = rideStartTimestamp,
            startedAt = rideStartTimestamp,
            durationMillis = state.elapsedMillis,
            distanceKm = state.distanceKm,
            maxSpeedKmh = state.maxSpeedKmh,
            averageSpeedKmh = if (state.elapsedMillis > 0) state.distanceKm / (state.elapsedMillis / 3600000.0) else 0.0,
            speedSamples = pendingSamples
                .sortedBy { it.elapsedMillis }
                .map { SpeedSample(elapsedMillis = it.elapsedMillis, speedKmh = it.speedKmh) },
        )
        persistRide(summary, pendingSamples.toList())
        _uiState.update {
            it.copy(
                isTracking = false,
                isPaused = false,
                currentSpeedKmh = 0.0,
                elapsedMillis = 0L,
                gpsStatus = GpsStatus.Searching,
                currentRideChartSamples = emptyList(),
                rideHistory = if (isInitialized) it.rideHistory else listOf(summary) + it.rideHistory,
                lastLocation = null,
            )
        }
        pausedAccumulatedMillis = 0L
        pausedStartedAt = 0L
        points.clear()
        pendingSamples.clear()
        rideStartTimestamp = 0L
    }

    fun tickElapsed() {
        val state = _uiState.value
        if (!state.isTracking || state.isPaused || rideStartTimestamp == 0L) return
        val elapsed = System.currentTimeMillis() - rideStartTimestamp - pausedAccumulatedMillis
        _uiState.update { it.copy(elapsedMillis = elapsed) }
    }

    fun onLocation(location: Location) {
        val state = _uiState.value
        if (!state.isTracking || state.isPaused) return

        val previous = state.lastLocation
        val distanceMeters = if (previous != null) previous.distanceTo(location).toDouble() else 0.0
        val rawSpeedKmh = when {
            location.hasSpeed() -> location.speed * 3.6
            previous != null -> {
                val deltaMillis = (location.time - previous.time).coerceAtLeast(1L)
                (distanceMeters / deltaMillis) * 3600
            }
            else -> 0.0
        }
        val smoothedSpeed = when (state.speedMode) {
            SpeedMode.STABLE -> state.currentSpeedKmh * 0.7 + rawSpeedKmh * 0.3
            SpeedMode.REALTIME -> state.currentSpeedKmh * 0.35 + rawSpeedKmh * 0.65
        }
        val normalizedSpeed = smoothedSpeed.coerceAtLeast(0.0)
        val elapsedMillis = currentElapsedMillis()
        points.add(location)
        pendingSamples.add(
            SpeedSampleEntity(
                sessionId = 0L,
                elapsedMillis = elapsedMillis,
                recordedAt = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                speedKmh = normalizedSpeed,
                accuracyMeters = location.accuracy,
            )
        )
        val liveChartSamples = updatedLiveChartSamples(elapsedMillis)
        _uiState.update {
            it.copy(
                currentSpeedKmh = normalizedSpeed,
                maxSpeedKmh = max(it.maxSpeedKmh, normalizedSpeed),
                distanceKm = it.distanceKm + (distanceMeters / 1000.0),
                gpsStatus = when {
                    location.accuracy <= 12f -> GpsStatus.Good
                    location.accuracy <= 30f -> GpsStatus.Weak
                    else -> GpsStatus.Searching
                },
                currentRideChartSamples = liveChartSamples ?: it.currentRideChartSamples,
                lastLocation = location,
            )
        }
    }

    fun onGpsUnavailable() {
        _uiState.update { it.copy(gpsStatus = GpsStatus.Disabled) }
    }

    private fun currentElapsedMillis(now: Long = System.currentTimeMillis()): Long {
        if (rideStartTimestamp == 0L) return 0L
        return (now - rideStartTimestamp - pausedAccumulatedMillis).coerceAtLeast(0L)
    }

    private fun persistRide(summary: RideSessionRecord, samples: List<SpeedSampleEntity>) {
        if (!isInitialized) return
        repositoryScope.launch {
            trackingDao?.insertRideWithSamples(
                session = RideSessionEntity(
                    startedAt = summary.startedAt,
                    durationMillis = summary.durationMillis,
                    distanceKm = summary.distanceKm,
                    maxSpeedKmh = summary.maxSpeedKmh,
                    averageSpeedKmh = summary.averageSpeedKmh,
                ),
                samples = samples,
            )
        }
    }

    private fun updatedLiveChartSamples(elapsedMillis: Long): List<SpeedSample>? {
        val completedBucket = (elapsedMillis / LIVE_CHART_WINDOW_MILLIS) - 1L
        if (completedBucket < 0 || completedBucket <= lastPublishedChartBucket) return null
        lastPublishedChartBucket = completedBucket
        return pendingSamples
            .groupBy { it.elapsedMillis / LIVE_CHART_WINDOW_MILLIS }
            .toSortedMap()
            .filterKeys { it <= completedBucket }
            .map { (bucket, samples) ->
                SpeedSample(
                    elapsedMillis = (bucket + 1L) * LIVE_CHART_WINDOW_MILLIS,
                    speedKmh = samples.map { it.speedKmh }.average(),
                )
            }
    }
}
