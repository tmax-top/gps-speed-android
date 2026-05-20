package com.mk.gpsspeed.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mk.gpsspeed.data.RideSessionRecord
import com.mk.gpsspeed.data.SpeedSample
import com.mk.gpsspeed.data.SpeedMode
import com.mk.gpsspeed.data.TrackingUiState
import com.mk.gpsspeed.data.toClockText
import com.mk.gpsspeed.ui.theme.AppBackground
import com.mk.gpsspeed.ui.theme.CardBackground
import com.mk.gpsspeed.ui.theme.GpsGood
import com.mk.gpsspeed.ui.theme.PrimaryAccent
import com.mk.gpsspeed.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    state: TrackingUiState,
    onRequestPermissions: () -> Unit,
    onRefreshSystemState: () -> Unit,
    onToggleTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onToggleOverlay: () -> Unit,
    onSetMode: (SpeedMode) -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardBackground,
        ) {
            HistorySheet(items = state.rideHistory)
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardBackground,
        ) {
            SettingsSheet(
                selectedMode = state.speedMode,
                hasOverlayPermission = state.hasOverlayPermission,
                onSetMode = onSetMode,
                onToggleOverlay = onToggleOverlay,
                onRefreshSystemState = onRefreshSystemState,
            )
        }
    }

    Scaffold(containerColor = AppBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                TopBar(
                    onOpenHistory = { showHistory = true },
                    onOpenSettings = { showSettings = true },
                    onOpenFullscreen = onOpenFullscreen,
                    onToggleOverlay = onToggleOverlay,
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (!state.hasLocationPermission) {
                    PermissionBanner(onRequestPermissions = onRequestPermissions)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                GpsStatusPill(label = gpsStatusText(state))
                Spacer(modifier = Modifier.height(28.dp))
                SpeedHero(currentSpeed = state.currentSpeedKmh.roundToInt())
                Spacer(modifier = Modifier.height(28.dp))
                StatsRow(
                    maxSpeed = state.maxSpeedKmh.roundToInt(),
                    duration = state.elapsedMillis.toClockText(),
                    distance = String.format(Locale.US, "%.2f", state.distanceKm),
                )
                if (state.isTracking) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CurrentRideTrendSection(
                        samples = state.currentRideChartSamples,
                        isPaused = state.isPaused,
                    )
                }
            }
            BottomActions(
                state = state,
                onToggleTracking = onToggleTracking,
                onStopTracking = onStopTracking,
            )
        }
    }
}

@Composable
private fun CurrentRideTrendSection(samples: List<SpeedSample>, isPaused: Boolean) {
    Column {
        Text(
            text = if (isPaused) "本次记录趋势（已暂停）" else "本次记录趋势",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SpeedTrendChart(
            samples = samples,
            emptyText = "开始骑行后，每 10 秒刷新一次",
        )
    }
}

@Composable
private fun TopBar(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "GPS测速",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Rounded.History, contentDescription = "历史记录")
            }
            IconButton(onClick = onToggleOverlay) {
                Icon(Icons.Rounded.Layers, contentDescription = "悬浮窗")
            }
            IconButton(onClick = onOpenFullscreen) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "全屏")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置")
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequestPermissions: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("需要定位权限", color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "请授予精确定位权限，用于获取实时速度。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onRequestPermissions) {
                Text("去授权")
            }
        }
    }
}

@Composable
private fun GpsStatusPill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(CardBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = GpsGood, fontSize = 13.sp)
    }
}

@Composable
private fun SpeedHero(currentSpeed: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = currentSpeed.toString(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 108.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "km/h", color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun StatsRow(maxSpeed: Int, duration: String, distance: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(title = "最高", value = "$maxSpeed")
        MetricCard(title = "时长", value = duration)
        MetricCard(title = "里程", value = distance)
    }
}

@Composable
private fun RowScope.MetricCard(title: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = TextSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BottomActions(
    state: TrackingUiState,
    onToggleTracking: () -> Unit,
    onStopTracking: () -> Unit,
) {
    Column {
        if (!state.isTracking) {
            Button(
                onClick = onToggleTracking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
            ) {
                Text("开始骑行")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onToggleTracking,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(if (state.isPaused) "继续" else "暂停")
                }
                Button(
                    onClick = onStopTracking,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text("结束")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                state.isOverlayVisible -> "悬浮窗显示中"
                state.hasOverlayPermission -> "已获得悬浮窗权限"
                else -> "需要悬浮窗权限"
            },
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SettingsSheet(
    selectedMode: SpeedMode,
    hasOverlayPermission: Boolean,
    onSetMode: (SpeedMode) -> Unit,
    onToggleOverlay: () -> Unit,
    onRefreshSystemState: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("测速模式", color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = selectedMode == SpeedMode.STABLE,
                onClick = { onSetMode(SpeedMode.STABLE) },
                label = { Text("稳定") },
            )
            FilterChip(
                selected = selectedMode == SpeedMode.REALTIME,
                onClick = { onSetMode(SpeedMode.REALTIME) },
                label = { Text("实时") },
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("悬浮窗", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasOverlayPermission) "权限已授予" else "尚未授权",
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onToggleOverlay) {
                Text(if (hasOverlayPermission) "开关悬浮窗" else "授权悬浮窗")
            }
            OutlinedButton(onClick = onRefreshSystemState) {
                Text("刷新状态")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HistorySheet(items: List<RideSessionRecord>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("骑行记录", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        if (items.isEmpty()) {
            Text("还没有骑行记录。", color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppBackground),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.startedAt)),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "时长 ${item.durationMillis.toClockText()}  里程 ${String.format(Locale.US, "%.2f", item.distanceKm)} 公里",
                            color = TextSecondary,
                        )
                        Text(
                            text = "最高 ${item.maxSpeedKmh.roundToInt()} km/h  平均 ${item.averageSpeedKmh.roundToInt()} km/h",
                            color = PrimaryAccent,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SpeedTrendChart(samples = item.speedSamples)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun gpsStatusText(state: TrackingUiState): String = when {
    !state.hasLocationPermission -> "等待定位权限"
    state.isPaused -> "已暂停测速"
    else -> when (state.gpsStatus) {
        com.mk.gpsspeed.data.GpsStatus.Disabled -> "GPS未开启"
        com.mk.gpsspeed.data.GpsStatus.Searching -> "正在搜索GPS信号"
        com.mk.gpsspeed.data.GpsStatus.Weak -> "GPS信号较弱"
        com.mk.gpsspeed.data.GpsStatus.Good -> "GPS已就绪"
    }
}

@Composable
private fun SpeedTrendChart(samples: List<SpeedSample>) {
    SpeedTrendChart(
        samples = samples,
        emptyText = "采样点不足，暂无折线",
    )
}

@Composable
private fun SpeedTrendChart(samples: List<SpeedSample>, emptyText: String) {
    val chartSamples = remember(samples) { samples.downSample(maxPoints = 40) }
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("速度趋势", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (chartSamples.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emptyText, color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    val maxSpeed = chartSamples.maxOf { it.speedKmh }.coerceAtLeast(1.0)
                    val widthStep = size.width / (chartSamples.size - 1)
                    val path = Path()

                    chartSamples.forEachIndexed { index, sample ->
                        val x = index * widthStep
                        val yRatio = (sample.speedKmh / maxSpeed).toFloat().coerceIn(0f, 1f)
                        val y = size.height - (size.height * yRatio)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawLine(
                        color = TextSecondary.copy(alpha = 0.2f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawPath(
                        path = path,
                        color = PrimaryAccent,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

private fun List<SpeedSample>.downSample(maxPoints: Int): List<SpeedSample> {
    if (size <= maxPoints) return this
    val bucketSize = ceil(size / maxPoints.toDouble()).toInt().coerceAtLeast(1)
    return chunked(bucketSize).map { bucket ->
        val avgElapsed = bucket.sumOf { it.elapsedMillis } / bucket.size
        val avgSpeed = bucket.sumOf { it.speedKmh } / bucket.size
        SpeedSample(
            elapsedMillis = avgElapsed,
            speedKmh = avgSpeed,
        )
    }.take(min(maxPoints, size))
}
