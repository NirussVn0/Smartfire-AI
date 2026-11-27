package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AiInsight
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.EspDeviceConfig
import com.example.domain.model.FirmwareState
import com.example.domain.model.HomeProfile
import com.example.domain.model.SensorSnapshotPoint
import com.example.domain.model.SmartFireNode
import com.example.domain.model.SmartFireTelemetry
import com.example.ui.components.ChartPoint
import com.example.ui.components.CriticalAlarmOverlay
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.FirmwareStateBadge
import com.example.ui.components.MultiChartMode
import com.example.ui.components.MultiMetricSensorChart
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
import com.example.ui.theme.OfflineBackground
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBackground
import com.example.ui.theme.WarningBorder
import java.util.Locale
import kotlin.math.roundToInt

enum class ChartTimeRange {
    HOURLY, // Theo Giờ
    DAILY   // Theo Ngày
}

enum class ChartMetricType {
    GAS_ADC,  // Khói ADC
    TEMP_C,   // Nhiệt °C
    FLAME_IR, // Lửa (IR %)
    PROB_PCT  // Xác suất %
}

enum class SensorListFilterTab {
    ALL,       // Nút 1: Tất cả cảm biến
    REAL_ONLY, // Nút 2: Máy ESP32 Thực Tế
    DEMO_ONLY  // Nút 3: Máy Báo Cháy Demo
}

@Composable
fun DashboardScreen(
    nodes: List<SmartFireNode>,
    currentHome: HomeProfile?,
    isDemoMode: Boolean,
    gasThresholdAdc: Int = 1800,
    snapshots: List<SensorSnapshotPoint> = emptyList(),
    insights: List<AiInsight> = emptyList(),
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
    onDisarmClick: (String) -> Unit = {},
    onArmClick: (String) -> Unit = {},
    onAdjustMultiSensorParams: (String, Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _, _ -> },
    savedEspConfig: EspDeviceConfig = EspDeviceConfig(),
    onResetOfflineBurnedClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alarmNodes = nodes.filter { it.isEffectivelyAlarming(savedEspConfig) }
    val offlineCount = nodes.count { it.connectionState == DeviceConnectionState.OFFLINE }
    val safeCount = nodes.count {
        !it.isEffectivelyAlarming(savedEspConfig) && it.connectionState != DeviceConnectionState.OFFLINE
    }
    val totalCount = nodes.size
    val demoNodes = nodes.filter { it.isDemoSensor || it.displayName.contains("DEMO", ignoreCase = true) }
    val realNodes = nodes.filterNot { it.isDemoSensor || it.displayName.contains("DEMO", ignoreCase = true) }

    var selectedTimeRange by remember { mutableStateOf(ChartTimeRange.HOURLY) }
    var selectedChartMode by remember { mutableStateOf(MultiChartMode.ALL_THREE) }
    var selectedChartDeviceId by remember { mutableStateOf<String?>(null) }
    var activeSensorTab by remember { mutableStateOf(SensorListFilterTab.ALL) }
    var isSensorListExpanded by remember { mutableStateOf(true) }

    val defaultChartNode = alarmNodes.firstOrNull() ?: realNodes.firstOrNull() ?: nodes.firstOrNull()
    val selectedChartNode = nodes.find { it.deviceId == selectedChartDeviceId } ?: defaultChartNode

    val highestRiskNode = nodes.maxByOrNull {
        it.telemetry.calculateFireProbability(gasThresholdAdc, it.effectiveConfig(savedEspConfig)).totalProbabilityPercent
    }
    val summaryFocusNode = if (selectedChartDeviceId != null) {
        selectedChartNode
    } else {
        alarmNodes.firstOrNull() ?: highestRiskNode ?: selectedChartNode
    }
    val summaryCfg = summaryFocusNode?.effectiveConfig(savedEspConfig) ?: savedEspConfig
    val summaryProb = summaryFocusNode?.telemetry?.calculateFireProbability(gasThresholdAdc, summaryCfg)?.totalProbabilityPercent ?: 0f
    val summaryTemp = summaryFocusNode?.telemetry?.temperatureCelsius
    val summaryGas = summaryFocusNode?.telemetry?.gasRaw ?: 0

    val displayedNodes = when (activeSensorTab) {
        SensorListFilterTab.ALL -> nodes
        SensorListFilterTab.REAL_ONLY -> realNodes
        SensorListFilterTab.DEMO_ONLY -> demoNodes
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(horizontal = 14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. COMPACT SINGLE-CARD HEADER BAR (Không đẩy chữ, không tràn dòng)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    // Row 1: Brand Title + User Chip + Logout Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (alarmNodes.isNotEmpty()) CriticalAlarmRed else PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SmartFire AI",
                                        color = PrimaryBlue,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(PrimaryBlueLight)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "SOV Labs",
                                            color = PrimaryBlue,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Text(
                                    text = loggedInEmail.substringBefore("@") + " · " + loggedInRole,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Compact Action Group: + Demo, + Thêm, Logout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAddQuickDemoDeviceClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_dashboard_add_demo_device")
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = DemoGold, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("+Demo", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = onAddDeviceClick,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_dashboard_add_device")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Thêm", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            IconButton(
                                onClick = onLogoutClick,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .testTag("btn_dashboard_logout")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Đăng xuất",
                                    tint = CriticalAlarmRed,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Demo Mode Banner if active
            if (isDemoMode) {
                DemoModeBanner(onDismissOrSwitch = onOpenSettingsClick)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Critical Alarm Overlay Banner if any alarm is active
            if (alarmNodes.isNotEmpty()) {
                CriticalAlarmOverlay(
                    alarmNodes = alarmNodes,
                    onSilenceClick = onSilenceClick,
                    onAckClick = onAckClick,
                    onOpenMapClick = onOpenMapClick
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. BẢNG OVERALL (INDEPENDENT PER-DEVICE SUMMARY BOARD)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (alarmNodes.isNotEmpty()) CriticalAlarmBorder else LightBorder,
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Top status row inside Overall Board
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (alarmNodes.isNotEmpty()) CriticalAlarmBackground else NormalGreenBackground)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (alarmNodes.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (alarmNodes.isNotEmpty()) {
                                        "BÁO ĐỘNG (${alarmNodes.size} ĐANG CHÁY)"
                                    } else {
                                        "HỆ THỐNG AN TOÀN"
                                    },
                                    color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${alarmNodes.size} đang cháy · $safeCount an toàn · $offlineCount offline",
                                    color = TextPrimary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (alarmNodes.isNotEmpty()) {
                            Button(
                                onClick = { alarmNodes.forEach { onSilenceClick(it.deviceId) } },
                                colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Tắt còi", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$safeCount/$totalCount Sẵn sàng",
                                    color = NormalGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (nodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Per-device selector chips (tapping selects that device in the chart & focus strip)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(nodes, key = { it.deviceId }) { node ->
                                val nodeCfg = node.effectiveConfig(savedEspConfig)
                                val nodeProb = node.telemetry.calculateFireProbability(gasThresholdAdc, nodeCfg).totalProbabilityPercent
                                val isNodeAlarm = node.isEffectivelyAlarming(savedEspConfig)
                                val isNodeWarn = node.isEffectivelyWarningLevel1(savedEspConfig)
                                val isNodeOffline = node.connectionState == DeviceConnectionState.OFFLINE
                                val isSelected = selectedChartNode?.deviceId == node.deviceId
                                val dotColor = when {
                                    isNodeAlarm -> CriticalAlarmRed
                                    isNodeWarn -> WarningAmber
                                    isNodeOffline -> OfflineGray
                                    else -> NormalGreen
                                }
                                val shortName = node.displayName.replace("[DEMO]", "").trim()

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isSelected) PrimaryBlueLight else LightSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) PrimaryBlue else LightBorder,
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { selectedChartDeviceId = node.deviceId }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "$shortName · ${nodeProb.roundToInt()}%",
                                        color = if (isSelected) PrimaryBlue else TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (summaryFocusNode != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val focusCleanName = summaryFocusNode.displayName.replace("[DEMO]", "").trim()
                            val focusOfflineTag = if (summaryFocusNode.isCachedOfflineData) " · Offline" else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LightSurfaceVariant)
                                    .border(1.dp, LightBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$focusCleanName$focusOfflineTag",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Nhiệt: %s · Khói: %d ADC",
                                            summaryTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                                            summaryGas
                                        ),
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                summaryProb >= 75f -> CriticalAlarmBackground
                                                summaryProb >= 45f -> WarningBackground
                                                else -> NormalGreenBackground
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "P(Cháy) %.1f%%", summaryProb),
                                        color = when {
                                            summaryProb >= 75f -> CriticalAlarmRed
                                            summaryProb >= 45f -> Color(0xFFB45309)
                                            else -> NormalGreen
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. BIỂU ĐỒ CẢM BIẾN ĐỘC LẬP THEO THIẾT BỊ (FULL-WIDTH MULTIMETRIC CHART + 2x2 KPI TILES)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LightBorder, RoundedCornerShape(16.dp))
                    .testTag("overview_master_chart_card"),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Top Row: Title + Segmented Toggle [Theo Giờ] | [Theo Ngày]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Biểu Đồ Cảm Biến",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        // Segmented Buttons: Theo Giờ vs Theo Ngày
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(LightSurfaceVariant)
                                .border(1.dp, LightBorder, RoundedCornerShape(9.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (selectedTimeRange == ChartTimeRange.HOURLY) PrimaryBlue else Color.Transparent)
                                    .clickable { selectedTimeRange = ChartTimeRange.HOURLY }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("btn_chart_hourly")
                            ) {
                                Text(
                                    text = "Theo Giờ",
                                    color = if (selectedTimeRange == ChartTimeRange.HOURLY) Color.White else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (selectedTimeRange == ChartTimeRange.DAILY) PrimaryBlue else Color.Transparent)
                                    .clickable { selectedTimeRange = ChartTimeRange.DAILY }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("btn_chart_daily")
                            ) {
                                Text(
                                    text = "Theo Ngày",
                                    color = if (selectedTimeRange == ChartTimeRange.DAILY) Color.White else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Device Selector Row
                    if (nodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(nodes, key = { "chart_${it.deviceId}" }) { node ->
                                val isSelected = selectedChartNode?.deviceId == node.deviceId
                                val cleanLabel = node.displayName.replace("[DEMO]", "").trim()
                                val isAlarmNode = node.isEffectivelyAlarming(savedEspConfig)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            when {
                                                isSelected && isAlarmNode -> CriticalAlarmRed
                                                isSelected -> PrimaryBlue
                                                isAlarmNode -> CriticalAlarmBackground
                                                else -> LightSurfaceVariant
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isSelected && isAlarmNode -> CriticalAlarmRed
                                                isSelected -> PrimaryBlue
                                                isAlarmNode -> CriticalAlarmBorder
                                                else -> LightBorder
                                            },
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { selectedChartDeviceId = node.deviceId }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = cleanLabel,
                                        color = when {
                                            isSelected -> Color.White
                                            isAlarmNode -> CriticalAlarmRed
                                            else -> TextPrimary
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metric Selector Row: Cả 3 Thông Số (default) | Khói MQ-2 | Nhiệt Độ | Lửa (IR) | P(Cháy)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chartModes = listOf(
                            MultiChartMode.ALL_THREE to "Cả 3 Thông Số",
                            MultiChartMode.GAS_ADC to "Khói MQ-2",
                            MultiChartMode.TEMP_C to "Nhiệt Độ",
                            MultiChartMode.FLAME_IR to "Lửa (IR)",
                            MultiChartMode.PROB_PCT to "P(Cháy)"
                        )
                        items(chartModes) { (mode, label) ->
                            MetricFilterChip(
                                label = label,
                                selected = selectedChartMode == mode,
                                onClick = { selectedChartMode = mode }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Full-Width MultiMetricSensorChart for selectedChartNode ONLY
                    val chartCfg = selectedChartNode?.effectiveConfig(savedEspConfig) ?: savedEspConfig
                    val chartTemp = selectedChartNode?.telemetry?.temperatureCelsius
                    val chartGas = selectedChartNode?.telemetry?.gasRaw ?: 0
                    val chartCo = selectedChartNode?.telemetry?.coPpm
                    val chartHum = selectedChartNode?.telemetry?.humidityPercent
                    val chartRor = selectedChartNode?.telemetry?.tempRateOfRiseCPerMin
                    val chartFlamePct = if (selectedChartNode?.telemetry?.flame == true) {
                        100f
                    } else {
                        ((selectedChartNode?.telemetry?.irFlameIntensity ?: 0f) * 100f).coerceIn(0f, 100f)
                    }
                    val chartProb = selectedChartNode?.telemetry
                        ?.calculateFireProbability(gasThresholdAdc, chartCfg)
                        ?.totalProbabilityPercent ?: 0f

                    val deviceChartSnapshots = remember(snapshots, selectedChartNode?.deviceId, selectedTimeRange) {
                        val devId = selectedChartNode?.deviceId
                        if (devId == null) {
                            emptyList()
                        } else {
                            val filtered = snapshots
                                .filter { it.deviceId == devId }
                                .sortedBy { it.timestamp }
                            if (selectedTimeRange == ChartTimeRange.HOURLY) {
                                filtered.takeLast(40)
                            } else {
                                filtered.takeLast(120)
                            }
                        }
                    }

                    MultiMetricSensorChart(
                        snapshots = deviceChartSnapshots,
                        currentGas = chartGas,
                        currentTemp = chartTemp,
                        currentFlamePct = chartFlamePct,
                        currentProb = chartProb,
                        gasWarnThreshold = chartCfg.gasWarn,
                        gasAlarmThreshold = chartCfg.gasOn,
                        tempWarnC = chartCfg.tempWarnC,
                        tempAlarmC = chartCfg.tempAlarmC,
                        selectedMode = selectedChartMode,
                        timeRangeLabel = if (selectedTimeRange == ChartTimeRange.HOURLY) "24h" else "7 ngày",
                        isCachedOffline = selectedChartNode?.isCachedOfflineData == true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedChartNode != null) {
                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedChartNode.isCachedOfflineData) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WarningBackground)
                                    .border(1.dp, WarningBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Dữ liệu cũ (Offline) · Hiển thị thông số gần nhất của ${selectedChartNode.displayName.replace("[DEMO]", "").trim()}",
                                    color = Color(0xFFB45309),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Clean 2x2 KPI Tiles for selectedChartNode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SquareMetricBlock(
                                    title = "Nhiệt Độ",
                                    mainValue = chartTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                                    subLabel = if (selectedChartNode.isCachedOfflineData) {
                                        "Dữ liệu cũ (Offline)"
                                    } else {
                                        chartRor?.let { String.format(Locale.US, "+%.1f°/p · Ngưỡng %.0f°", it, chartCfg.tempAlarmC) }
                                            ?: String.format(Locale.US, "Ngưỡng %.0f°C", chartCfg.tempAlarmC)
                                    },
                                    progress = if (chartTemp != null) ((chartTemp - 20f) / 70f).coerceIn(0.08f, 1f) else 0.08f,
                                    color = if ((chartTemp ?: 0f) >= chartCfg.tempAlarmC) CriticalAlarmRed
                                    else if ((chartTemp ?: 0f) >= chartCfg.tempWarnC) WarningAmber
                                    else NormalGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                SquareMetricBlock(
                                    title = "Khói MQ-2",
                                    mainValue = "$chartGas ADC",
                                    subLabel = if (selectedChartNode.isCachedOfflineData) {
                                        "Dữ liệu cũ (Offline)"
                                    } else {
                                        "Mức 1: ${chartCfg.gasWarn} · Mức 2: ${chartCfg.gasOn}"
                                    },
                                    progress = (chartGas / maxOf(3000f, chartCfg.gasOn * 1.2f)).coerceIn(0.08f, 1f),
                                    color = when {
                                        !selectedChartNode.isDemoSensor && (selectedChartNode.telemetry.gasFault || chartGas >= 4090) -> WarningAmber
                                        chartGas >= chartCfg.gasOn -> CriticalAlarmRed
                                        chartGas >= chartCfg.gasWarn -> WarningAmber
                                        else -> PrimaryBlue
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SquareMetricBlock(
                                    title = "Lửa IR / CO",
                                    mainValue = if (selectedChartNode.telemetry.flame) {
                                        "CÓ LỬA"
                                    } else {
                                        "${chartFlamePct.roundToInt()}%"
                                    },
                                    subLabel = when {
                                        selectedChartNode.isCachedOfflineData -> "Dữ liệu cũ (Offline)"
                                        chartCo != null && chartHum != null -> "CO ${chartCo}ppm · Ẩm ${chartHum.roundToInt()}%"
                                        chartCo != null -> "CO ${chartCo} ppm"
                                        else -> "Cảm biến quang IR"
                                    },
                                    progress = (chartFlamePct / 100f).coerceIn(0.08f, 1f),
                                    color = if (selectedChartNode.telemetry.flame || chartFlamePct >= 70f) CriticalAlarmRed
                                    else if (chartFlamePct >= 35f) WarningAmber
                                    else PrimaryBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                SquareMetricBlock(
                                    title = "P(Cháy)",
                                    mainValue = String.format(Locale.US, "%.1f%%", chartProb),
                                    subLabel = if (selectedChartNode.isCachedOfflineData) {
                                        "Dữ liệu cũ (Offline)"
                                    } else if (chartProb >= 75f) {
                                        "Báo cháy khẩn cấp"
                                    } else if (chartProb >= 45f) {
                                        "Cảnh báo sớm"
                                    } else {
                                        "An toàn"
                                    },
                                    progress = (chartProb / 100f).coerceIn(0.08f, 1f),
                                    color = when {
                                        chartProb >= 75f -> CriticalAlarmRed
                                        chartProb >= 45f -> WarningAmber
                                        else -> NormalGreen
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3.5. THẺ PHÂN TÍCH THÔNG MINH & CẢNH BÁO SỚM (AI INSIGHTS EARLY WARNING)
            if (insights.isNotEmpty()) {
                AiInsightsEarlyWarningSection(
                    insights = insights.take(3),
                    nodes = nodes,
                    onInsightNodeClick = { devId ->
                        if (devId != null) onNodeClick(devId)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4. BÊN DƯỚI LÀ 3 NÚT LỌC RÕ RÀNG: TẤT CẢ | ESP32 THỰC TẾ | MÁY DEMO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Nút 1: Tất cả cảm biến
                val isTabAllActive = isSensorListExpanded && activeSensorTab == SensorListFilterTab.ALL
                Button(
                    onClick = {
                        if (activeSensorTab == SensorListFilterTab.ALL) {
                            isSensorListExpanded = !isSensorListExpanded
                        } else {
                            activeSensorTab = SensorListFilterTab.ALL
                            isSensorListExpanded = true
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTabAllActive) PrimaryBlue else LightCard,
                        contentColor = if (isTabAllActive) Color.White else TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(
                            1.dp,
                            if (isTabAllActive) PrimaryBlue else LightBorder,
                            RoundedCornerShape(11.dp)
                        )
                        .testTag("btn_toggle_all_sensors_list")
                ) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tất Cả (${nodes.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Nút 2: Máy Báo Cháy ESP32 Thực Tế
                val isTabRealActive = isSensorListExpanded && activeSensorTab == SensorListFilterTab.REAL_ONLY
                Button(
                    onClick = {
                        if (activeSensorTab == SensorListFilterTab.REAL_ONLY) {
                            isSensorListExpanded = !isSensorListExpanded
                        } else {
                            activeSensorTab = SensorListFilterTab.REAL_ONLY
                            isSensorListExpanded = true
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTabRealActive) NormalGreen else LightCard,
                        contentColor = if (isTabRealActive) Color.White else TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(
                            1.dp,
                            if (isTabRealActive) NormalGreen else LightBorder,
                            RoundedCornerShape(11.dp)
                        )
                        .testTag("btn_toggle_real_sensors_list")
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isTabRealActive) Color.White else NormalGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ESP32 (${realNodes.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Nút 3: Máy Báo Cháy Demo
                val isTabDemoActive = isSensorListExpanded && activeSensorTab == SensorListFilterTab.DEMO_ONLY
                Button(
                    onClick = {
                        if (activeSensorTab == SensorListFilterTab.DEMO_ONLY) {
                            isSensorListExpanded = !isSensorListExpanded
                        } else {
                            activeSensorTab = SensorListFilterTab.DEMO_ONLY
                            isSensorListExpanded = true
                        }
                    },
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTabDemoActive) Color(0xFF9333EA) else LightCard,
                        contentColor = if (isTabDemoActive) Color.White else TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(
                            1.dp,
                            if (isTabDemoActive) Color(0xFF9333EA) else LightBorder,
                            RoundedCornerShape(11.dp)
                        )
                        .testTag("btn_toggle_demo_sensors_list")
                ) {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = if (isTabDemoActive) Color.White else DemoGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Máy Demo (${demoNodes.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Live Sensor Cards List (Hiển thị khi ấn nút chọn bên trên)
        if (isSensorListExpanded) {
            if (displayedNodes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = LightCard),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (activeSensorTab) {
                                    SensorListFilterTab.DEMO_ONLY -> "Chưa có Máy Báo Cháy Demo nào"
                                    SensorListFilterTab.REAL_ONLY -> "Chưa có Cảm Biến ESP32 Thực Tế nào"
                                    SensorListFilterTab.ALL -> "Chưa có cảm biến nào"
                                },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onAddQuickDemoDeviceClick) {
                                    Text("+ Máy Demo", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = onAddDeviceClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("+ Thêm ESP32", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(displayedNodes, key = { it.deviceId }) { node ->
                    val nodeSnapshots = remember(snapshots, node.deviceId) {
                        snapshots.filter { it.deviceId == node.deviceId }
                    }
                    FriendlySensorCard(
                        node = node,
                        gasThresholdAdc = gasThresholdAdc,
                        nodeSnapshots = nodeSnapshots,
                        onClick = { onNodeClick(node.deviceId) },
                        onRouteToSensor = { onRouteToNodeOnMapClick(node.deviceId) },
                        onSilence = { onSilenceClick(node.deviceId) },
                        onAck = { onAckClick(node.deviceId) },
                        onTest = { onTestClick(node.deviceId) },
                        onDisarm = { onDisarmClick(node.deviceId) },
                        onArm = { onArmClick(node.deviceId) },
                        onDelete = { onDeleteDeviceClick(node.deviceId) },
                        onAdjustMultiSensorParams = { tempC, ror, gas, co, hum, ir ->
                            onAdjustMultiSensorParams(node.deviceId, tempC, ror, gas, co, hum, ir)
                        },
                        savedEspConfig = savedEspConfig,
                        onResetOfflineBurnedClick = { onResetOfflineBurnedClick(node.deviceId) },
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MetricFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PrimaryBlueLight else LightSurfaceVariant)
            .border(
                1.dp,
                if (selected) PrimaryBlue else LightBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (selected) PrimaryBlue else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SquareMetricBlock(
    title: String,
    mainValue: String,
    subLabel: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(LightSurfaceVariant)
            .border(1.dp, LightBorder, RoundedCornerShape(11.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }

        Text(
            text = mainValue,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Column {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = Color.Black.copy(alpha = 0.08f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                color = TextSecondary,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AiInsightsEarlyWarningSection(
    insights: List<AiInsight>,
    nodes: List<SmartFireNode>,
    onInsightNodeClick: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val criticalCount = insights.count { it.severity.equals("CRITICAL", ignoreCase = true) }
    val warningCount = insights.count { it.severity.equals("WARNING", ignoreCase = true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                when {
                    criticalCount > 0 -> CriticalAlarmBorder
                    warningCount > 0 -> DemoGold.copy(alpha = 0.6f)
                    else -> LightBorder
                },
                RoundedCornerShape(16.dp)
            )
            .testTag("ai_insights_section_card"),
        colors = CardDefaults.cardColors(containerColor = LightCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                when {
                                    criticalCount > 0 -> CriticalAlarmBackground
                                    warningCount > 0 -> DemoGoldBackground
                                    else -> PrimaryBlueLight
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = when {
                                criticalCount > 0 -> CriticalAlarmRed
                                warningCount > 0 -> WarningAmber
                                else -> PrimaryBlue
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Cảnh Báo Sớm & Phân Tích",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Theo dõi xu hướng & cảnh báo sớm theo từng khu vực",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Mở rộng / Thu gọn",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    insights.forEach { insight ->
                        val nodeName = nodes.find { it.deviceId == insight.deviceId }?.displayName
                            ?.replace("[DEMO]", "")?.trim()
                        val sevUpper = insight.severity.uppercase()
                        val sevColor = when (sevUpper) {
                            "CRITICAL" -> CriticalAlarmRed
                            "WARNING" -> Color(0xFFB45309)
                            else -> PrimaryBlue
                        }
                        val sevBg = when (sevUpper) {
                            "CRITICAL" -> CriticalAlarmBackground
                            "WARNING" -> WarningBackground
                            else -> PrimaryBlueLight
                        }
                        val sevLabel = when (sevUpper) {
                            "CRITICAL" -> "KHẨN CẤP"
                            "WARNING" -> "CẢNH BÁO"
                            else -> "THÔNG TIN"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightSurfaceVariant)
                                .border(1.dp, sevColor.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                .clickable { onInsightNodeClick(insight.deviceId) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(sevBg)
                                            .border(1.dp, sevColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = sevLabel,
                                            color = sevColor,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = nodeName ?: insight.deviceId,
                                        color = TextPrimary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }

                                Text(
                                    text = insight.title,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (insight.sourceMeasurements.isNotBlank()) {
                                    Text(
                                        text = insight.sourceMeasurements,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(sevColor)
                                    .clickable { onInsightNodeClick(insight.deviceId) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Kiểm tra",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendlySensorCard(
    node: SmartFireNode,
    gasThresholdAdc: Int = 1800,
    nodeSnapshots: List<SensorSnapshotPoint> = emptyList(),
    onClick: () -> Unit,
    onRouteToSensor: () -> Unit,
    onSilence: () -> Unit,
    onAck: () -> Unit,
    onTest: () -> Unit,
    onDisarm: () -> Unit = {},
    onArm: () -> Unit = {},
    onDelete: () -> Unit,
    onAdjustMultiSensorParams: (Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    savedEspConfig: EspDeviceConfig = EspDeviceConfig(),
    onResetOfflineBurnedClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effConfig = node.effectiveConfig(savedEspConfig)
    val probBreakdown = node.telemetry.calculateFireProbability(gasThresholdAdc, effConfig)
    val probPercent = probBreakdown.totalProbabilityPercent
    val isDisarmed = node.telemetry.disarmed || node.firmwareState == FirmwareState.DISARMED
    val isAlarm = node.isEffectivelyAlarming(savedEspConfig)
    val isDemo = node.isDemoSensor || node.displayName.contains("DEMO", ignoreCase = true)
    var showChartDetail by remember { mutableStateOf(false) }
    var showMultiParamSimulator by remember { mutableStateOf(false) }

    val rssiDistanceM = node.telemetry.estimateDistanceFromRssiMeters()

    // Local slider state for testing multi-parameter probabilistic calculation
    var simTemp by remember(node.telemetry.temperatureCelsius) { mutableFloatStateOf(node.telemetry.temperatureCelsius ?: 28.5f) }
    var simRor by remember(node.telemetry.tempRateOfRiseCPerMin) { mutableFloatStateOf(node.telemetry.tempRateOfRiseCPerMin ?: 0.4f) }
    var simGas by remember(node.telemetry.gasRaw) { mutableFloatStateOf(node.telemetry.gasRaw.toFloat()) }
    var simCo by remember(node.telemetry.coPpm) { mutableFloatStateOf((node.telemetry.coPpm ?: 12).toFloat()) }
    var simHum by remember(node.telemetry.humidityPercent) { mutableFloatStateOf(node.telemetry.humidityPercent ?: 60f) }
    var simIr by remember(node.telemetry.irFlameIntensity) { mutableFloatStateOf(node.telemetry.irFlameIntensity ?: 0.02f) }

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
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Name + [DEMO] Badge + Firmware Badge + Delete Button (Gọn gàng 1 dòng)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDemo) DemoGoldBackground else PrimaryBlueLight)
                                .border(1.dp, if (isDemo) DemoGold else PrimaryBlue, RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isDemo) "DEMO" else "ESP32",
                                color = if (isDemo) DemoGold else PrimaryBlue,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = node.displayName.replace("[DEMO]", "").trim(),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "%s · %d dBm (~%.1fm)",
                            node.ipAddress,
                            node.telemetry.rssiDbm,
                            rssiDistanceM
                        ),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FirmwareStateBadge(
                        firmwareState = node.firmwareState,
                        connectionState = node.connectionState,
                        suspectedBurnedOffline = node.suspectedBurnedOffline
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEE2E2))
                            .testTag("btn_delete_${node.deviceId}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Xóa cảm biến báo cháy",
                            tint = CriticalAlarmRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (node.suspectedBurnedOffline) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarningBackground)
                        .border(1.dp, WarningBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CriticalAlarmRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mất kết nối khi cháy (Nghi hư hại) · Đã ngừng hú lặp",
                            color = Color(0xFF92400E),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onResetOfflineBurnedClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("btn_reset_offline_burned_${node.deviceId}")
                    ) {
                        Text("Đặt lại", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (node.isCachedOfflineData) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OfflineBackground)
                        .border(1.dp, LightBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Dữ liệu cũ · Offline",
                        color = OfflineGray,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Multi-Parameter Probability Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            probPercent >= 75f -> CriticalAlarmRed
                            probPercent >= 45f -> Color(0xFFFEF3C7)
                            else -> Color(0xFFF0FDF4)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "P(Cháy): %.1f%% · %s", probPercent, probBreakdown.riskLevelLabel),
                    color = when {
                        probPercent >= 75f -> Color.White
                        probPercent >= 45f -> Color(0xFFB45309)
                        else -> NormalGreen
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = String.format(
                        Locale.US,
                        "T:%.0f%% K:%.0f%% CO:%.0f%%",
                        probBreakdown.tempContributionPercent,
                        probBreakdown.smokeGasContributionPercent,
                        probBreakdown.coContributionPercent
                    ),
                    color = if (probPercent >= 75f) Color.White.copy(alpha = 0.9f) else TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Square Mini Metric Tiles + Simulator/Chart Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tile 1: Nhiệt độ
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(LightSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nhiệt", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                    Text(
                        text = node.telemetry.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                        color = if ((node.telemetry.temperatureCelsius ?: 0f) >= 50f) CriticalAlarmRed else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Tile 2: Khói MQ-2
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(LightSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Khói ADC", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                    Text(
                        text = if (!node.isDemoSensor && (node.telemetry.gasFault || node.telemetry.gasRaw >= 4090)) "${node.telemetry.gasRaw} (LỖI)" else "${node.telemetry.gasRaw}",
                        color = if (!node.isDemoSensor && (node.telemetry.gasFault || node.telemetry.gasRaw >= 4090)) WarningAmber else if (node.telemetry.gasRaw >= gasThresholdAdc) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Tile 3: Khí CO & Ẩm
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(LightSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CO / Ẩm", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                    val coStr = if (node.telemetry.coPpm != null && node.telemetry.humidityPercent != null) {
                        String.format(Locale.US, "%d / %.0f%%", node.telemetry.coPpm, node.telemetry.humidityPercent)
                    } else {
                        "N/A"
                    }
                    Text(
                        text = coStr,
                        color = if ((node.telemetry.coPpm ?: 0) > 80) CriticalAlarmRed else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Tool Icons: Chỉnh 5 thông số & Biểu đồ
                IconButton(
                    onClick = { showMultiParamSimulator = !showMultiParamSimulator },
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (showMultiParamSimulator) PrimaryBlue else LightSurfaceVariant)
                        .size(34.dp)
                        .testTag("btn_toggle_prob_sim_${node.deviceId}")
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Chỉnh đa thông số",
                        tint = if (showMultiParamSimulator) Color.White else PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { showChartDetail = !showChartDetail },
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (showChartDetail) PrimaryBlueLight else LightSurfaceVariant)
                        .size(34.dp)
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = "Biểu đồ riêng",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable Multi-Parameter Simulator
            AnimatedVisibility(visible = showMultiParamSimulator) {
                val previewTelem = SmartFireTelemetry(
                    temperatureCelsius = simTemp,
                    tempRateOfRiseCPerMin = simRor,
                    gasRaw = simGas.roundToInt(),
                    coPpm = simCo.roundToInt(),
                    humidityPercent = simHum,
                    irFlameIntensity = simIr
                )
                val previewProb = previewTelem.calculateFireProbability(gasThresholdAdc, effConfig)

                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightSurfaceVariant)
                        .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Tổ hợp 5 thông số ➔ P(Cháy) = %.1f%%",
                            previewProb.totalProbabilityPercent
                        ),
                        color = if (previewProb.totalProbabilityPercent >= 75f) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = String.format(Locale.US, "Nhiệt: %.1f°C (+%.1f°/p)", simTemp, simRor),
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
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "Khói MQ-2: ${simGas.roundToInt()} ADC",
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simGas,
                        onValueChange = { simGas = it },
                        valueRange = 500f..3200f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue),
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = String.format(Locale.US, "CO: %d ppm · Ẩm: %.0f%%", simCo.roundToInt(), simHum),
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
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = String.format(Locale.US, "Hồng ngoại lửa IR: %.0f%%", simIr * 100f),
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = simIr,
                        onValueChange = { simIr = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                        modifier = Modifier.height(24.dp)
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
                            .height(34.dp)
                            .testTag("btn_apply_prob_sim_${node.deviceId}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (previewProb.totalProbabilityPercent >= 75f) CriticalAlarmRed else PrimaryBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "Áp Dụng (P = %.1f%%)", previewProb.totalProbabilityPercent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Expandable Individual Sensor Chart (Connected to real Room DB snapshots!)
            AnimatedVisibility(visible = showChartDetail) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    val now = System.currentTimeMillis()
                    val base = node.telemetry.gasRaw.coerceAtLeast(300)
                    val chartData = remember(nodeSnapshots, base) {
                        val sorted = nodeSnapshots.sortedBy { it.timestamp }.takeLast(12)
                        if (sorted.size >= 3) {
                            sorted.map { ChartPoint(it.timestamp, it.gasRaw) }
                        } else {
                            listOf(
                                ChartPoint(now - 60000, (base * 0.89).toInt()),
                                ChartPoint(now - 45000, (base * 0.93).toInt()),
                                ChartPoint(now - 30000, (base * 0.96).toInt()),
                                ChartPoint(now - 15000, (base * 0.98).toInt()),
                                ChartPoint(now, base)
                            )
                        }
                    }
                    SensorChart(points = chartData, thresholdValue = gasThresholdAdc)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compact Action Rows: (Dẫn Đường | Test Còi) + (Tắt 30s | Tắt Hẳn / Bật Lại)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onRouteToSensor,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAlarm) Color(0xFFEA580C) else PrimaryBlue
                        ),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(36.dp)
                            .testTag("btn_route_to_node_${node.deviceId}")
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAlarm) "Dẫn Tới Đám Cháy" else "Dẫn Đường",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isDisarmed,
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(36.dp)
                            .testTag("btn_test_${node.deviceId}")
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = if (isDisarmed) TextSecondary else CriticalAlarmRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Test Còi",
                            color = if (isDisarmed) TextSecondary else CriticalAlarmRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSilence,
                        enabled = !isDisarmed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAlarm) CriticalAlarmRed else Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("btn_silence_${node.deviceId}")
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tắt Tạm 30s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (isDisarmed) {
                        Button(
                            onClick = onArm,
                            colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_arm_${node.deviceId}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Bật Lại",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Button(
                            onClick = onDisarm,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_disarm_${node.deviceId}")
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tắt Hẳn",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
