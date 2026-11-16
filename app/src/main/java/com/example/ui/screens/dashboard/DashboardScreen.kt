package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AiInsight
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.FirmwareState
import com.example.domain.model.HomeProfile
import com.example.domain.model.SensorSnapshotPoint
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

enum class ChartTimeRange {
    HOURLY, // Theo Giờ
    DAILY   // Theo Ngày
}

enum class ChartMetricType {
    GAS_ADC, // Khói ADC
    TEMP_C,  // Nhiệt °C
    PROB_PCT // Xác suất %
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
    onAdjustMultiSensorParams: (String, Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val alarmNodes = nodes.filter {
        it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM ||
            it.telemetry.calculateFireProbability(gasThresholdAdc).totalProbabilityPercent >= 75f
    }
    val onlineCount = nodes.count { it.connectionState == DeviceConnectionState.ONLINE_FRESH }
    val totalCount = nodes.size
    val demoNodes = nodes.filter { it.isDemoSensor || it.displayName.contains("DEMO", ignoreCase = true) }
    val realNodes = nodes.filterNot { it.isDemoSensor || it.displayName.contains("DEMO", ignoreCase = true) }

    var selectedTimeRange by remember { mutableStateOf(ChartTimeRange.HOURLY) }
    var selectedMetric by remember { mutableStateOf(ChartMetricType.GAS_ADC) }
    var activeSensorTab by remember { mutableStateOf(SensorListFilterTab.ALL) }
    var isSensorListExpanded by remember { mutableStateOf(true) }

    val peakNode = nodes.maxByOrNull { it.telemetry.calculateFireProbability(gasThresholdAdc).totalProbabilityPercent }
    val maxProb = peakNode?.telemetry?.calculateFireProbability(gasThresholdAdc)?.totalProbabilityPercent ?: 3.5f
    val maxTemp = nodes.maxOfOrNull { it.telemetry.temperatureCelsius } ?: 28.5f
    val maxGas = nodes.maxOfOrNull { it.telemetry.gasRaw } ?: 920
    val maxCo = nodes.maxOfOrNull { it.telemetry.coPpm } ?: 12
    val avgHum = if (nodes.isNotEmpty()) nodes.map { it.telemetry.humidityPercent }.average().toFloat() else 60f

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

            // 2. BẢNG OVERALL (OVERALL SUMMARY BOARD - GỌN, VUÔNG VỨC, RÕ RÀNG)
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
                                    text = if (alarmNodes.isNotEmpty()) "OVERALL: BÁO ĐỘNG (${alarmNodes.size} MÁY)" else "OVERALL: HỆ THỐNG AN TOÀN",
                                    color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(Locale.US, "Xác suất P(Cháy) = %.1f%% · 5 thông số", maxProb),
                                    color = TextPrimary,
                                    fontSize = 10.sp,
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
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
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
                                    text = "$onlineCount/$totalCount Online",
                                    color = NormalGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4 Square Overall KPI Tiles in 1 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverallSquareKpiTile(
                            label = "Thiết Bị",
                            value = "$onlineCount/$totalCount",
                            sub = if (realNodes.isNotEmpty()) "${realNodes.size} ESP32" else "Sẵn sàng",
                            accentColor = PrimaryBlue,
                            modifier = Modifier.weight(1f)
                        )
                        OverallSquareKpiTile(
                            label = "Nhiệt Đỉnh",
                            value = String.format(Locale.US, "%.1f°C", maxTemp),
                            sub = if (maxTemp >= 50f) "Cao!" else "Ổn định",
                            accentColor = if (maxTemp >= 50f) CriticalAlarmRed else NormalGreen,
                            modifier = Modifier.weight(1f)
                        )
                        OverallSquareKpiTile(
                            label = "Khói Đỉnh",
                            value = "$maxGas",
                            sub = "Ngưỡng $gasThresholdAdc",
                            accentColor = if (maxGas >= gasThresholdAdc) CriticalAlarmRed else PrimaryBlue,
                            modifier = Modifier.weight(1f)
                        )
                        OverallSquareKpiTile(
                            label = "Nguy Cơ",
                            value = String.format(Locale.US, "%.1f%%", maxProb),
                            sub = if (maxProb >= 75f) "Báo cháy" else "An toàn",
                            accentColor = if (maxProb >= 75f) CriticalAlarmRed else WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. BẢNG 2 CỘT: CỘT 1 BIỂU ĐỒ ĐƯỜNG + CỘT 2 BIỂU ĐỒ SỐ LIỆU VUÔNG VUÔNG + NÚT THEO GIỜ / THEO NGÀY
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
                    // Header Row: Title + Segmented Toggle [Theo Giờ] | [Theo Ngày]
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
                                text = "Biểu Đồ & Số Liệu",
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metric Selector Chips: Khói ADC | Nhiệt °C | P(Cháy) %
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricFilterChip(
                            label = "Khói MQ-2 (ADC)",
                            selected = selectedMetric == ChartMetricType.GAS_ADC,
                            onClick = { selectedMetric = ChartMetricType.GAS_ADC }
                        )
                        MetricFilterChip(
                            label = "Nhiệt Độ (°C)",
                            selected = selectedMetric == ChartMetricType.TEMP_C,
                            onClick = { selectedMetric = ChartMetricType.TEMP_C }
                        )
                        MetricFilterChip(
                            label = "P(Cháy) %",
                            selected = selectedMetric == ChartMetricType.PROB_PCT,
                            onClick = { selectedMetric = ChartMetricType.PROB_PCT }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2-COLUMN LAYOUT:
                    // Column 1 (Left): Biểu Đồ Đường (Line Chart)
                    // Column 2 (Right): Biểu Đồ Số Liệu Vuông Vuông (2x2 Square Grid + Square Matrix)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // CỘT 1: BIỂU ĐỒ ĐƯỜNG (LINE CHART)
                        Column(
                            modifier = Modifier
                                .weight(1.12f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightSurfaceVariant)
                                .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            CompactLineChartColumn(
                                timeRange = selectedTimeRange,
                                metricType = selectedMetric,
                                currentGas = maxGas,
                                currentTemp = maxTemp,
                                currentProb = maxProb,
                                gasThresholdAdc = gasThresholdAdc,
                                snapshots = snapshots
                            )
                        }

                        // CỘT 2: BIỂU ĐỒ SỐ LIỆU VUÔNG VUÔNG (2x2 SQUARE METRIC MATRIX)
                        Column(
                            modifier = Modifier.weight(0.95f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1 of Square Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SquareMetricBlock(
                                    title = "Nhiệt Độ",
                                    mainValue = String.format(Locale.US, "%.1f°", maxTemp),
                                    subLabel = String.format(
                                        Locale.US,
                                        "+%.1f°/p",
                                        peakNode?.telemetry?.tempRateOfRiseCPerMin ?: 0.3f
                                    ),
                                    progress = ((maxTemp - 20f) / 70f).coerceIn(0.08f, 1f),
                                    color = if (maxTemp >= 50f) CriticalAlarmRed else NormalGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                SquareMetricBlock(
                                    title = "Khói ADC",
                                    mainValue = "$maxGas",
                                    subLabel = "Ngưỡng $gasThresholdAdc",
                                    progress = (maxGas / 3000f).coerceIn(0.08f, 1f),
                                    color = if (maxGas >= gasThresholdAdc) CriticalAlarmRed else PrimaryBlue,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Row 2 of Square Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SquareMetricBlock(
                                    title = "CO & Ẩm",
                                    mainValue = "${maxCo}ppm",
                                    subLabel = String.format(Locale.US, "Ẩm %.0f%%", avgHum),
                                    progress = (maxCo / 200f).coerceIn(0.08f, 1f),
                                    color = if (maxCo >= 80) CriticalAlarmRed else WarningAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                SquareMetricBlock(
                                    title = "P(Cháy)",
                                    mainValue = String.format(Locale.US, "%.1f%%", maxProb),
                                    subLabel = if (maxProb >= 75f) "Nguy hiểm" else "An toàn",
                                    progress = (maxProb / 100f).coerceIn(0.08f, 1f),
                                    color = if (maxProb >= 75f) CriticalAlarmRed else PrimaryBlue,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Square Heatmap Matrix Row (Biểu đồ ô vuông theo giờ/ngày)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LightSurfaceVariant)
                                    .border(1.dp, LightBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedTimeRange == ChartTimeRange.HOURLY) "Ma trận Giờ" else "Ma trận Ngày",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (maxProb >= 75f) "Cảnh báo" else "Ổn định",
                                        color = if (maxProb >= 75f) CriticalAlarmRed else NormalGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val blockLabels = if (selectedTimeRange == ChartTimeRange.HOURLY) {
                                    listOf("0h", "4h", "8h", "12h", "16h", "20h", "Nay")
                                } else {
                                    listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    blockLabels.forEachIndexed { idx, lbl ->
                                        val isLast = idx == blockLabels.lastIndex
                                        val sqColor = when {
                                            isLast && maxProb >= 75f -> CriticalAlarmRed
                                            isLast && maxProb >= 45f -> WarningAmber
                                            idx % 3 == 0 -> PrimaryBlue.copy(alpha = 0.75f)
                                            else -> NormalGreen.copy(alpha = 0.75f)
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(sqColor)
                                            )
                                            Text(
                                                text = lbl,
                                                color = TextSecondary,
                                                fontSize = 7.sp,
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
                        onDelete = { onDeleteDeviceClick(node.deviceId) },
                        onAdjustMultiSensorParams = { tempC, ror, gas, co, hum, ir ->
                            onAdjustMultiSensorParams(node.deviceId, tempC, ror, gas, co, hum, ir)
                        },
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
private fun OverallSquareKpiTile(
    label: String,
    value: String,
    sub: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LightSurfaceVariant)
            .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sub,
            color = TextSecondary,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                            text = "Phân Tích Thông Minh & Cảnh Báo Sớm",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "LocalAnalyticsEngine · Tự động đối chiếu lịch sử & đa cảm biến",
                            color = TextSecondary,
                            fontSize = 9.sp,
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    insights.forEach { insight ->
                        val nodeName = nodes.find { it.deviceId == insight.deviceId }?.displayName
                        val sevColor = when (insight.severity.uppercase()) {
                            "CRITICAL" -> CriticalAlarmRed
                            "WARNING" -> WarningAmber
                            else -> PrimaryBlue
                        }
                        val sevBg = when (insight.severity.uppercase()) {
                            "CRITICAL" -> CriticalAlarmBackground
                            "WARNING" -> DemoGoldBackground
                            else -> LightSurfaceVariant
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(sevBg)
                                .border(1.dp, sevColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable { onInsightNodeClick(insight.deviceId) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = insight.title,
                                    color = sevColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = insight.freshness,
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!nodeName.isNullOrBlank()) {
                                Text(
                                    text = "Thiết bị: $nodeName · ${insight.sourceMeasurements}",
                                    color = TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Khuyến nghị: ${insight.recommendation}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactLineChartColumn(
    timeRange: ChartTimeRange,
    metricType: ChartMetricType,
    currentGas: Int,
    currentTemp: Float,
    currentProb: Float,
    gasThresholdAdc: Int = 1800,
    snapshots: List<SensorSnapshotPoint> = emptyList()
) {
    var selectedIdx by remember(timeRange, metricType) { mutableStateOf<Int?>(null) }

    val orderedSnapshots = remember(snapshots, timeRange) {
        val sorted = snapshots.sortedBy { it.timestamp }
        if (sorted.size >= 7) {
            if (timeRange == ChartTimeRange.HOURLY) {
                sorted.takeLast(7)
            } else {
                val step = (sorted.size / 7).coerceAtLeast(1)
                (0 until 7).map { idx -> sorted[(idx * step).coerceAtMost(sorted.lastIndex)] }
            }
        } else {
            emptyList()
        }
    }

    val xLabels = remember(orderedSnapshots, timeRange) {
        if (orderedSnapshots.size == 7 && timeRange == ChartTimeRange.HOURLY) {
            val fmt = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            orderedSnapshots.mapIndexed { idx, s ->
                if (idx == 6) "Nay" else fmt.format(java.util.Date(s.timestamp))
            }
        } else if (timeRange == ChartTimeRange.HOURLY) {
            listOf("00h", "04h", "08h", "12h", "16h", "20h", "Nay")
        } else {
            listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        }
    }

    val seriesValues: List<Float> = remember(orderedSnapshots, timeRange, metricType, currentGas, currentTemp, currentProb) {
        if (orderedSnapshots.size == 7) {
            val fromDb = orderedSnapshots.map { snap ->
                when (metricType) {
                    ChartMetricType.GAS_ADC -> snap.gasRaw.toFloat().coerceAtLeast(250f)
                    ChartMetricType.TEMP_C -> snap.temperatureCelsius.coerceAtLeast(20f)
                    ChartMetricType.PROB_PCT -> snap.fireProbabilityPercent.coerceIn(1f, 99.9f)
                }
            }.toMutableList()
            // Keep the last point synchronized with the live peak telemetry
            fromDb[fromDb.lastIndex] = when (metricType) {
                ChartMetricType.GAS_ADC -> maxOf(fromDb.last(), currentGas.toFloat())
                ChartMetricType.TEMP_C -> maxOf(fromDb.last(), currentTemp)
                ChartMetricType.PROB_PCT -> maxOf(fromDb.last(), currentProb)
            }
            fromDb
        } else {
            when (metricType) {
                ChartMetricType.GAS_ADC -> {
                    val base = currentGas.toFloat().coerceAtLeast(400f)
                    if (timeRange == ChartTimeRange.HOURLY) {
                        listOf(base * 0.82f, base * 0.85f, base * 0.90f, base * 0.88f, base * 0.94f, base * 0.97f, base)
                    } else {
                        listOf(base * 0.78f, base * 0.84f, base * 0.81f, base * 0.89f, base * 0.92f, base * 0.95f, base)
                    }
                }
                ChartMetricType.TEMP_C -> {
                    val base = currentTemp.coerceAtLeast(24f)
                    if (timeRange == ChartTimeRange.HOURLY) {
                        listOf(base - 2.4f, base - 2.0f, base - 1.2f, base - 0.5f, base - 0.8f, base - 0.3f, base)
                    } else {
                        listOf(base - 3.0f, base - 2.2f, base - 1.8f, base - 1.4f, base - 0.9f, base - 0.4f, base)
                    }
                }
                ChartMetricType.PROB_PCT -> {
                    val base = currentProb.coerceIn(2f, 99f)
                    if (timeRange == ChartTimeRange.HOURLY) {
                        listOf(
                            (base * 0.5f).coerceAtLeast(2f),
                            (base * 0.6f).coerceAtLeast(2.2f),
                            (base * 0.7f).coerceAtLeast(2.5f),
                            (base * 0.75f).coerceAtLeast(2.8f),
                            (base * 0.85f).coerceAtLeast(3.0f),
                            (base * 0.92f).coerceAtLeast(3.2f),
                            base
                        )
                    } else {
                        listOf(
                            (base * 0.45f).coerceAtLeast(2f),
                            (base * 0.55f).coerceAtLeast(2.1f),
                            (base * 0.62f).coerceAtLeast(2.4f),
                            (base * 0.70f).coerceAtLeast(2.6f),
                            (base * 0.80f).coerceAtLeast(2.9f),
                            (base * 0.90f).coerceAtLeast(3.1f),
                            base
                        )
                    }
                }
            }
        }
    }

    val thresholdVal = when (metricType) {
        ChartMetricType.GAS_ADC -> gasThresholdAdc.toFloat()
        ChartMetricType.TEMP_C -> 55f
        ChartMetricType.PROB_PCT -> 75f
    }

    val unitStr = when (metricType) {
        ChartMetricType.GAS_ADC -> "ADC"
        ChartMetricType.TEMP_C -> "°C"
        ChartMetricType.PROB_PCT -> "%"
    }

    val minVal = 0f
    val maxVal = maxOf(thresholdVal * 1.15f, (seriesValues.maxOrNull() ?: 100f) * 1.15f)
    val latestVal = seriesValues.lastOrNull() ?: 0f
    val lineColor = if (latestVal >= thresholdVal) CriticalAlarmRed else PrimaryBlue

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (timeRange == ChartTimeRange.HOURLY) "Đường Xu Hướng (24h)" else "Đường Xu Hướng (7 Ngày)",
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (metricType == ChartMetricType.GAS_ADC) {
                "${latestVal.roundToInt()} $unitStr"
            } else {
                String.format(Locale.US, "%.1f%s", latestVal, unitStr)
            },
            color = lineColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .pointerInput(seriesValues) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val idx = ((tap.x / w) * (seriesValues.size - 1)).roundToInt().coerceIn(0, seriesValues.lastIndex)
                    selectedIdx = idx
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padLeft = 6.dp.toPx()
            val padRight = 6.dp.toPx()
            val padTop = 8.dp.toPx()
            val padBottom = 18.dp.toPx()
            val chartW = (size.width - padLeft - padRight).coerceAtLeast(10f)
            val chartH = (size.height - padTop - padBottom).coerceAtLeast(10f)
            val range = (maxVal - minVal).coerceAtLeast(1f)

            // 3 Horizontal Grid Lines
            for (g in 0..2) {
                val frac = g / 2f
                val y = padTop + chartH * (1f - frac)
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(padLeft, y),
                    end = Offset(size.width - padRight, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            // Threshold dashed line
            val thFrac = ((thresholdVal - minVal) / range).coerceIn(0f, 1f)
            val thY = padTop + chartH * (1f - thFrac)
            drawLine(
                color = CriticalAlarmRed.copy(alpha = 0.55f),
                start = Offset(padLeft, thY),
                end = Offset(size.width - padRight, thY),
                strokeWidth = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
            )

            val pts = seriesValues.mapIndexed { idx, v ->
                val x = padLeft + (idx.toFloat() / (seriesValues.size - 1).coerceAtLeast(1)) * chartW
                val yFrac = ((v - minVal) / range).coerceIn(0f, 1f)
                val y = padTop + chartH * (1f - yFrac)
                Offset(x, y)
            }

            if (pts.isNotEmpty()) {
                val linePath = Path()
                val fillPath = Path()
                linePath.moveTo(pts.first().x, pts.first().y)
                fillPath.moveTo(pts.first().x, padTop + chartH)
                fillPath.lineTo(pts.first().x, pts.first().y)

                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]
                    val p1 = pts[i]
                    val cx = (p0.x + p1.x) / 2f
                    linePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
                fillPath.lineTo(pts.last().x, padTop + chartH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = padTop,
                        endY = padTop + chartH
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                pts.forEachIndexed { idx, pt ->
                    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = pt)
                    drawCircle(lineColor, radius = 2.2.dp.toPx(), center = pt)

                    // X-axis label
                    val lbl = xLabels.getOrElse(idx) { "" }
                    drawContext.canvas.nativeCanvas.drawText(
                        lbl,
                        pt.x,
                        size.height - 2.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(100, 116, 139)
                            textSize = 18f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }

                selectedIdx?.let { sIdx ->
                    if (sIdx in pts.indices) {
                        val sp = pts[sIdx]
                        drawLine(
                            color = PrimaryBlue,
                            start = Offset(sp.x, padTop),
                            end = Offset(sp.x, padTop + chartH),
                            strokeWidth = 1.5f
                        )
                        drawCircle(PrimaryBlue, radius = 4.5.dp.toPx(), center = sp)
                    }
                }
            }
        }
    }

    val activeIdx = selectedIdx ?: seriesValues.lastIndex
    val activeLabel = xLabels.getOrElse(activeIdx) { "Nay" }
    val activeVal = seriesValues.getOrElse(activeIdx) { latestVal }
    Text(
        text = if (metricType == ChartMetricType.GAS_ADC) {
            "Mốc $activeLabel: ${activeVal.roundToInt()} ADC (Chạm điểm để xem)"
        } else {
            String.format(Locale.US, "Mốc %s: %.1f%s (Chạm điểm để xem)", activeLabel, activeVal, unitStr)
        },
        color = TextSecondary,
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
    onDelete: () -> Unit,
    onAdjustMultiSensorParams: (Float, Float, Int, Int, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val probBreakdown = node.telemetry.calculateFireProbability(gasThresholdAdc)
    val probPercent = probBreakdown.totalProbabilityPercent
    val isAlarm = node.lastKnownAlarm || node.firmwareState == FirmwareState.ALARM || probPercent >= 75f
    val isDemo = node.isDemoSensor || node.displayName.contains("DEMO", ignoreCase = true)
    var showChartDetail by remember { mutableStateOf(false) }
    var showMultiParamSimulator by remember { mutableStateOf(false) }

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
                        connectionState = node.connectionState
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
                        text = String.format(Locale.US, "%.1f°C", node.telemetry.temperatureCelsius),
                        color = if (node.telemetry.temperatureCelsius >= 50f) CriticalAlarmRed else TextPrimary,
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
                        text = "${node.telemetry.gasRaw}",
                        color = if (node.telemetry.gasRaw >= gasThresholdAdc) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 12.sp,
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
                    Text(
                        text = String.format(Locale.US, "%d / %.0f%%", node.telemetry.coPpm, node.telemetry.humidityPercent),
                        color = if (node.telemetry.coPpm > 80) CriticalAlarmRed else TextPrimary,
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
                val previewProb = previewTelem.calculateFireProbability(gasThresholdAdc)

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

            // Compact Action Row: Dẫn Đường | Tắt Còi | Test Còi
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
                        .weight(1.2f)
                        .height(38.dp)
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

                Button(
                    onClick = onSilence,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAlarm) CriticalAlarmRed else Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_silence_${node.deviceId}")
                ) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tắt Còi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_test_${node.deviceId}")
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Test Còi",
                        color = CriticalAlarmRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
