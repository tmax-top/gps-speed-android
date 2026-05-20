package com.mk.gpsspeed.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mk.gpsspeed.data.SpeedMode
import com.mk.gpsspeed.data.TrackingRepository
import com.mk.gpsspeed.service.RideTrackingService
import com.mk.gpsspeed.service.SpeedOverlayService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class GpsSpeedViewModel : ViewModel() {
    val uiState: StateFlow<com.mk.gpsspeed.data.TrackingUiState> = TrackingRepository.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TrackingRepository.uiState.value,
    )

    fun onPermissionResult(result: Map<String, Boolean>) {
        val hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result[Manifest.permission.POST_NOTIFICATIONS] != false
        } else {
            true
        }
        TrackingRepository.updatePermissions(hasLocation, hasNotifications)
    }

    fun refreshSystemState(context: Context) {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlayGranted = Settings.canDrawOverlays(context)
        TrackingRepository.updatePermissions(hasLocation, hasNotifications)
        TrackingRepository.updateOverlayPermission(overlayGranted)
    }

    fun toggleTracking(context: Context) {
        val state = uiState.value
        if (!state.hasLocationPermission) return
        when {
            !state.isTracking -> RideTrackingService.start(context)
            state.isPaused -> RideTrackingService.resume(context)
            else -> RideTrackingService.pause(context)
        }
    }

    fun stopTracking(context: Context) {
        RideTrackingService.stop(context)
    }

    fun toggleOverlay(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        val serviceIntent = Intent(context, SpeedOverlayService::class.java)
        TrackingRepository.updateOverlayPermission(true)
        if (uiState.value.isOverlayVisible) {
            context.stopService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun setSpeedMode(mode: SpeedMode) {
        TrackingRepository.setSpeedMode(mode)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
