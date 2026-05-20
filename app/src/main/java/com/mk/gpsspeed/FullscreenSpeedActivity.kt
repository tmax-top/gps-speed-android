package com.mk.gpsspeed

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mk.gpsspeed.data.GpsStatus
import com.mk.gpsspeed.ui.GpsSpeedViewModel
import com.mk.gpsspeed.ui.theme.AppBackground
import com.mk.gpsspeed.ui.theme.GpsGood
import com.mk.gpsspeed.ui.theme.GpsSpeedTheme
import com.mk.gpsspeed.ui.theme.TextSecondary
import kotlin.math.roundToInt

class FullscreenSpeedActivity : ComponentActivity() {
    private val viewModel: GpsSpeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GpsSpeedTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                        .clickable(onClick = {})
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "返回",
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.clickable { finish() }
                            )
                            Text(
                                text = if (state.isPaused) "已暂停" else "测速中",
                                color = TextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.currentSpeedKmh.roundToInt().toString(),
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 132.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "km/h",
                                color = TextSecondary,
                                fontSize = 22.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when (state.gpsStatus) {
                                    GpsStatus.Disabled -> "GPS未开启"
                                    GpsStatus.Searching -> "GPS搜索中"
                                    GpsStatus.Weak -> "GPS信号弱"
                                    GpsStatus.Good -> "GPS正常"
                                },
                                color = if (state.gpsStatus == GpsStatus.Good) GpsGood else TextSecondary
                            )
                            Text(
                                text = "最高 ${state.maxSpeedKmh.roundToInt()} km/h",
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
