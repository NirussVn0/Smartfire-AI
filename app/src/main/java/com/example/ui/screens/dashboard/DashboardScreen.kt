package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.FirmwareState
import com.example.domain.model.HomeProfile
import com.example.domain.model.SmartFireNode
import com.example.domain.model.SmartFireTelemetry
import com.example.ui.components.ChartPoint
import com.example.ui.components.CriticalAlarmOverlay
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.FirmwareStateBadge
import com.example.ui.components.SensorChart
import com.example.ui.theme.CriticalAlarmBackground
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.DemoGold
import com.example.ui.theme.DemoGoldBackground
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.NormalGreenBackground
import com.example.ui.theme.NormalGreenBorder
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    nodes: List<SmartFireNode>,
    currentHome: HomeProfile?,
    isDemoMode: Boolean,
    loggedInEmail: String = "admin@sovlabs.edu.vn",
    loggedInRole: String = "Kỹ sư STEM · SOV Labs",
    onNodeClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onAddQuickDemoDeviceClick: () -> Unit = {},
    onDeleteDeviceClick: (String) -> Unit = {},
    onRouteToNodeOnMapClick: (String) -> Unit = {},
    onOpenMapClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit = {},
    onSilenceClick: (String) -> Unit,
    onAckClick: (String) -> Unit,
    onTestClick: (String) -> Unit,
    onAdjustMultiSensorParams: (String, Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val alarmNodes = nodes.filter { it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM }
    val onlineCount = nodes.count { it.connectionState == DeviceConnectionState.ONLINE_FRESH }
    val totalCount = nodes.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // Top User Account Bar with Logout Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightCard)
                    .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = loggedInEmail,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = loggedInRole,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onLogoutClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("btn_dashboard_logout")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Đăng xuất",
                        tint = CriticalAlarmRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đăng xuất", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Demo Mode Banner if active
            if (isDemoMode) {
                DemoModeBanner(onDismissOrSwitch = onOpenSettingsClick)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Fresh & Friendly Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SmartFire AI",
                        color = PrimaryBlue,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "STEM của SOV labs.",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$totalCount Cảm biến · $onlineCount Hoạt động" + if (alarmNodes.isNotEmpty()) " · ${alarmNodes.size} ĐANG CHÁY" else "",
                        color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (alarmNodes.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Add Sensor & Add Demo Sensor Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onAddQuickDemoDeviceClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_dashboard_add_demo_device")
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = DemoGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Máy Demo", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAddDeviceClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("btn_dashboard_add_device")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thêm cảm biến", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Critical Alarm Overlay Banner if any alarm is active
            if (alarmNodes.isNotEmpty()) {
                CriticalAlarmOverlay(
                    alarmNodes = alarmNodes,
                    onSilenceClick = onSilenceClick,
                    onAckClick = onAckClick,
                    onOpenMapClick = onOpenMapClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Status Overview Banner
            val maxProb = nodes.maxOfOrNull { it.telemetry.calculateFireProbability().totalProbabilityPercent } ?: 3.5f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (alarmNodes.isNotEmpty()) CriticalAlarmBackground else NormalGreenBackground)
                    .border(
                        1.dp,
                        if (alarmNodes.isNotEmpty()) CriticalAlarmBorder else NormalGreenBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (alarmNodes.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (alarmNodes.isNotEmpty()) "HIỆN ĐANG CÓ BÁO ĐỘNG CHÁY!" else "HỆ THỐNG ĐA CẢM BIẾN AN TOÀN",
                            color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Xác suất cháy tổng hợp cao nhất P(Fire) = %.1f%% (Kết hợp Nhiệt + RoR + Khói + CO + Ẩm + IR)",
                                maxProb
                            ),
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (alarmNodes.isNotEmpty()) {
                    Button(
                        onClick = {
                            alarmNodes.forEach { onSilenceClick(it.deviceId) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("TẮT TẤT CẢ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ALWAYS-VISIBLE MASTER REAL-TIME TELEMETRY CHART ON OVERVIEW
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LightBorder, RoundedCornerShape(16.dp))
                    .testTag("overview_master_chart_card"),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val peakNode = nodes.maxByOrNull { it.telemetry.calculateFireProbability().totalProbabilityPercent }
                    val peakGas = peakNode?.telemetry?.gasRaw ?: 920
                    val peakTemp = peakNode?.telemetry?.temperatureCelsius ?: 28.6f
                    val peakProb = peakNode?.telemetry?.calculateFireProbability()?.totalProbabilityPercent ?: 3.5f
                    val now = System.currentTimeMillis()
                    val masterPoints = remember(peakGas, nodes.size) {
                        listOf(
                            ChartPoint(now - 75_000L, (peakGas * 0.86f).toInt().coerceAtLeast(400)),
                            ChartPoint(now - 60_000L, (peakGas * 0.90f).toInt().coerceAtLeast(420)),
                            ChartPoint(now - 45_000L, (peakGas * 0.88f).toInt().coerceAtLeast(410)),
                            ChartPoint(now - 30_000L, (peakGas * 0.94f).toInt().coerceAtLeast(430)),
                            ChartPoint(now - 15_000L, (peakGas * 0.97f).toInt().coerceAtLeast(440)),
                            ChartPoint(now, peakGas)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Biểu Đồ Đa Thông Số & Xác Suất Cháy Thời Gian Thực",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (peakNode != null) "Đang phân tích xác suất từ: ${peakNode.displayName}" else "Mô hình Bayesian kết hợp 5 thông số vật lý",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (peakProb >= 75f) CriticalAlarmBackground else PrimaryBlueLight)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "P=%.1f%%", peakProb),
                                    color = if (peakProb >= 75f) CriticalAlarmRed else PrimaryBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (peakTemp >= 50f) CriticalAlarmBackground else Color(0xFFF0FDF4))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f°C", peakTemp),
                                    color = if (peakTemp >= 50f) CriticalAlarmRed else NormalGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SensorChart(
                        points = masterPoints,
                        thresholdValue = 1800
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Danh Sách Báo Cháy Đa Thông Số (${nodes.size})",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tính xác suất P(Cháy) kết hợp 5 cảm biến",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Live Sensor Cards List
        if (nodes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = LightCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Chưa có cảm biến nào trong STEM của SOV labs.", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Bấm '+ Thêm cảm biến' hoặc '+ Máy Demo' ở góc trên để thêm ngay", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onAddQuickDemoDeviceClick) {
                                Text("+ Thêm Máy Báo Cháy DEMO")
                            }
                            Button(
                                onClick = onAddDeviceClick,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("Thêm Cảm Biến Mới")
                            }
                        }
                    }
                }
            }
        } else {
            items(nodes, key = { it.deviceId }) { node ->
                FriendlySensorCard(
                    node = node,
                    onClick = { onNodeClick(node.deviceId) },
                    onRouteToSensor = { onRouteToNodeOnMapClick(node.deviceId) },
                    onSilence = { onSilenceClick(node.deviceId) },
                    onAck = { onAckClick(node.deviceId) },
                    onTest = { onTestClick(node.deviceId) },
                    onDelete = { onDeleteDeviceClick(node.deviceId) },
                    onAdjustMultiSensorParams = { tempC, ror, gas, co, hum, ir ->
                        onAdjustMultiSensorParams(node.deviceId, tempC, ror, gas, co, hum, ir)
                    },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun FriendlySensorCard(
    node: SmartFireNode,
    onClick: () -> Unit,
    onRouteToSensor: () -> Unit,
    onSilence: () -> Unit,
    onAck: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onAdjustMultiSensorParams: (Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val isAlarm = node.lastKnownAlarm || node.firmwareState == FirmwareState.ALARM
    val isDemo = node.isDemoSensor || node.displayName.contains("DEMO", ignoreCase = true)
    var showChartDetail by remember { mutableStateOf(false) }
    var showMultiParamSimulator by remember { mutableStateOf(false) }

    val probBreakdown = node.telemetry.calculateFireProbability()
    val probPercent = probBreakdown.totalProbabilityPercent
    val rssiDistanceM = node.telemetry.estimateDistanceFromRssiMeters()

    // Local slider state for testing multi-parameter probabilistic calculation
    var simTemp by remember(node.telemetry.temperatureCelsius) { mutableFloatStateOf(node.telemetry.temperatureCelsius) }
    var simRor by remember(node.telemetry.tempRateOfRiseCPerMin) { mutableFloatStateOf(node.telemetry.tempRateOfRiseCPerMin) }
    var simGas by remember(node.telemetry.gasRaw) { mutableFloatStateOf(node.telemetry.gasRaw.toFloat()) }
    var simCo by remember(node.telemetry.coPpm) { mutableFloatStateOf(node.telemetry.coPpm.toFloat()) }
    var simHum by remember(node.telemetry.humidityPercent) { mutableFloatStateOf(node.telemetry.humidityPercent) }
    var simIr by remember(node.telemetry.irFlameIntensity) { mutableFloatStateOf(node.telemetry.irFlameIntensity) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                if (isAlarm) 2.dp else 1.dp,
                if (isAlarm) CriticalAlarmBorder else LightBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .testTag("sensor_card_${node.deviceId}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlarm) CriticalAlarmBackground else LightCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Name + [DEMO] Badge + Firmware Badge + Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDemo) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DemoGoldBackground)
                                    .border(1.dp, DemoGold, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "MÁY DEMO",
                                    color = DemoGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = node.displayName.replace("[DEMO]", "").trim(),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "ID: %s · Tín hiệu: %d dBm (~%.1fm)",
                            node.deviceId,
                            node.telemetry.rssiDbm,
                            rssiDistanceM
                        ),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FirmwareStateBadge(
                        firmwareState = node.firmwareState,
                        connectionState = node.connectionState
                    )

                    // Delete sensor button (Xóa báo cháy)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEE2E2))
                            .testTag("btn_delete_${node.deviceId}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Xóa cảm biến báo cháy",
                            tint = CriticalAlarmRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MULTI-PARAMETER PROBABILISTIC FIRE SCORE BAR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            probPercent >= 75f -> CriticalAlarmRed
                            probPercent >= 45f -> Color(0xFFFEF3C7)
                            else -> Color(0xFFF0FDF4)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "XÁC SUẤT CHÁY TỔNG HỢP: %.1f%%", probPercent),
                        color = when {
                            probPercent >= 75f -> Color.White
                            probPercent >= 45f -> Color(0xFFB45309)
                            else -> NormalGreen
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = probBreakdown.riskLevelLabel,
                        color = when {
                            probPercent >= 75f -> Color.White
                            probPercent >= 45f -> Color(0xFFB45309)
                            else -> NormalGreen
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { (probPercent / 100f).coerceIn(0.02f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        probPercent >= 75f -> Color.White
                        probPercent >= 45f -> WarningAmber
                        else -> NormalGreen
                    },
                    trackColor = Color.Black.copy(alpha = 0.12f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.US,
                        "Đóng góp xác suất: Nhiệt+RoR %.1f%% · Khói %.1f%% · CO %.1f%% · Ẩm %.1f%% · Lửa IR %.1f%%",
                        probBreakdown.tempContributionPercent,
                        probBreakdown.smokeGasContributionPercent,
                        probBreakdown.coContributionPercent,
                        probBreakdown.humidityContributionPercent,
                        probBreakdown.irFlameContributionPercent
                    ),
                    color = if (probPercent >= 75f) Color.White.copy(alpha = 0.92f) else TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5-Parameter Telemetry Grid (Instead of a single fixed value)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Nhiệt độ & Tốc độ tăng RoR
                Column {
                    Text("Nhiệt Độ & RoR", color = TextSecondary, fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = if (node.telemetry.temperatureCelsius >= 50f) CriticalAlarmRed else NormalGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f°C", node.telemetry.temperatureCelsius),
                            color = if (node.telemetry.temperatureCelsius >= 50f) CriticalAlarmRed else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "+%.1f°C/phút", node.telemetry.tempRateOfRiseCPerMin),
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                // 2. Khói MQ-2 ADC
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Khói MQ-2", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${node.telemetry.gasRaw} ADC",
                        color = if (node.telemetry.gasRaw > 1700) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Quang học & Gas",
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                // 3. Khí CO & Độ Ẩm
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Khí CO & Độ Ẩm", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${node.telemetry.coPpm} ppm",
                        color = if (node.telemetry.coPpm > 80) CriticalAlarmRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = String.format(Locale.US, "Ẩm: %.0f%%RH", node.telemetry.humidityPercent),
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                // 4. Bức xạ IR & Nút mở bộ chỉnh đa thông số
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showMultiParamSimulator = !showMultiParamSimulator },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showMultiParamSimulator) PrimaryBlue else LightSurfaceVariant)
                            .size(34.dp)
                            .testTag("btn_toggle_prob_sim_${node.deviceId}")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Chỉnh đa thông số tính xác suất",
                            tint = if (showMultiParamSimulator) Color.White else PrimaryBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { showChartDetail = !showChartDetail },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (showChartDetail) PrimaryBlueLight else LightSurfaceVariant)
                            .size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = "Ẩn/Hiện biểu đồ",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Expandable Multi-Parameter Probabilistic Simulator (Cho phép chỉnh kết hợp 5 thông số để tính xác suất cháy)
            AnimatedVisibility(visible = showMultiParamSimulator) {
                val previewTelem = SmartFireTelemetry(
                    temperatureCelsius = simTemp,
                    tempRateOfRiseCPerMin = simRor,
                    gasRaw = simGas.roundToInt(),
                    coPpm = simCo.roundToInt(),
                    humidityPercent = simHum,
                    irFlameIntensity = simIr
                )
                val previewProb = previewTelem.calculateFireProbability()

                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightSurfaceVariant)
                        .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "🧮 Mô Phỏng Tổ Hợp 5 Thông Số ➔ Xác Suất Cháy: %.1f%% (%s)",
                            previewProb.totalProbabilityPercent,
                            if (previewProb.totalProbabilityPercent >= 75f) "BÁO ĐỘNG CHÁY" else "CHƯA VƯỢT NGƯỠNG 75%"
                        ),
                        color = if (previewProb.totalProbabilityPercent >= 75f) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Slider 1: Nhiệt độ
                    Text(
                        text = String.format(Locale.US, "1. Nhiệt độ: %.1f°C · Tốc độ tăng RoR: +%.1f°C/p", simTemp, simRor),
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simTemp,
                        onValueChange = {
                            simTemp = it
                            simRor = ((it - 26f) * 0.22f).coerceIn(0.2f, 16f)
                        },
                        valueRange = 25f..95f,
                        colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                        modifier = Modifier.height(26.dp)
                    )

                    // Slider 2: Mật độ khói MQ-2
                    Text(
                        text = "2. Mật độ Khói & Gas MQ-2: ${simGas.roundToInt()} ADC",
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simGas,
                        onValueChange = { simGas = it },
                        valueRange = 500f..3200f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue),
                        modifier = Modifier.height(26.dp)
                    )

                    // Slider 3: Nồng độ CO & Độ ẩm
                    Text(
                        text = String.format(Locale.US, "3. Khí CO: %d ppm · Độ ẩm giảm còn: %.0f%%RH", simCo.roundToInt(), simHum),
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simCo,
                        onValueChange = {
                            simCo = it
                            simHum = (65f - (it / 220f) * 40f).coerceIn(18f, 68f)
                        },
                        valueRange = 5f..250f,
                        colors = SliderDefaults.colors(thumbColor = WarningAmber, activeTrackColor = WarningAmber),
                        modifier = Modifier.height(26.dp)
                    )

                    // Slider 4: Bức xạ hồng ngoại IR
                    Text(
                        text = String.format(Locale.US, "4. Cường độ bức xạ lửa IR: %.0f%%", simIr * 100f),
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simIr,
                        onValueChange = { simIr = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                        modifier = Modifier.height(26.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            onAdjustMultiSensorParams(
                                simTemp,
                                simRor,
                                simGas.roundToInt(),
                                simCo.roundToInt(),
                                simHum,
                                simIr
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("btn_apply_prob_sim_${node.deviceId}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (previewProb.totalProbabilityPercent >= 75f) CriticalAlarmRed else PrimaryBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Áp Dụng Đa Thông Số (P = %.1f%%)",
                                previewProb.totalProbabilityPercent
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expandable Professional Chart
            AnimatedVisibility(visible = showChartDetail) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val now = System.currentTimeMillis()
                    val base = node.telemetry.gasRaw.coerceAtLeast(300)
                    val chartData = remember(base) {
                        listOf(
                            ChartPoint(now - 60000, (base * 0.89).toInt()),
                            ChartPoint(now - 45000, (base * 0.93).toInt()),
                            ChartPoint(now - 30000, (base * 0.96).toInt()),
                            ChartPoint(now - 15000, (base * 0.98).toInt()),
                            ChartPoint(now, base)
                        )
                    }
                    SensorChart(points = chartData, thresholdValue = 1800)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ROW 1: DẪN TỚI VỊ TRÍ BÁO CHÁY NÀY TRÊN BẢN ĐỒ 2D
            Button(
                onClick = onRouteToSensor,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAlarm) Color(0xFFEA580C) else PrimaryBlue
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_route_to_node_${node.deviceId}")
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAlarm) "DẪN TỚI BÁO CHÁY ĐANG CHÁY NÀY" else "DẪN TỚI VỊ TRÍ BÁO CHÁY NÀY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ROW 2 (DƯỚI NÚT DẪN TỚI BÁO CHÁY): NÚT TẮT BÁO CHÁY & NÚT TEST BÁO CHÁY
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút Tắt Báo Cháy
                Button(
                    onClick = onSilence,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlarm) CriticalAlarmRed else Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_silence_${node.deviceId}")
                ) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAlarm) "TẮT BÁO CHÁY NGAY" else "Tắt Báo Cháy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Nút Test Báo Cháy (hoặc Xác nhận)
                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_test_${node.deviceId}")
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Báo Cháy",
                        color = CriticalAlarmRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
