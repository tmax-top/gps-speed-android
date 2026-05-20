package com.mk.gpsspeed

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.gpsspeed.data.TrackingRepository
import com.mk.gpsspeed.ui.GpsSpeedViewModel
import com.mk.gpsspeed.ui.screen.HomeRoute
import com.mk.gpsspeed.ui.theme.GpsSpeedTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GpsSpeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TrackingRepository.initialize(applicationContext)

        setContent {
            GpsSpeedTheme {
                val context = LocalContext.current
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val permissions = remember {
                    buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { result ->
                        viewModel.onPermissionResult(result)
                    }
                )

                LaunchedEffect(Unit) {
                    viewModel.refreshSystemState(context)
                }

                LaunchedEffect(state.isTracking, state.isOverlayVisible) {
                    if (state.isTracking || state.isOverlayVisible) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                HomeRoute(
                    state = state,
                    onRequestPermissions = { permissionLauncher.launch(permissions) },
                    onRefreshSystemState = { viewModel.refreshSystemState(context) },
                    onToggleTracking = { viewModel.toggleTracking(context) },
                    onStopTracking = { viewModel.stopTracking(context) },
                    onOpenFullscreen = {
                        context.startActivity(Intent(context, FullscreenSpeedActivity::class.java))
                    },
                    onToggleOverlay = { viewModel.toggleOverlay(context) },
                    onSetMode = viewModel::setSpeedMode,
                )
            }
        }
    }
}
