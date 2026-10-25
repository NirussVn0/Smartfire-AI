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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.ui.components.ChartPoint
import com.example.ui.components.CriticalAlarmOverlay
import com.example.ui.components.DemoModeBanner
import com.example.ui.components.FirmwareStateBadge
import com.example.ui.components.SensorChart
import com.example.ui.theme.CriticalAlarmBackground
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.CyanAccent
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
import com.example.ui.theme.WarmingPurple

@Composable
fun DashboardScreen(
    nodes: List<SmartFireNode>,
    currentHome: HomeProfile?,
    isDemoMode: Boolean,
    onNodeClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onOpenMapClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onSilenceClick: (String) -> Unit,
    onAckClick: (String) -> Unit,
    onTestClick: (String) -> Unit,
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
            Spacer(modifier = Modifier.height(12.dp))

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
                Column {
                    Text(
                        text = "SmartFire AI",
                        color = PrimaryBlue,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "STEM của SOV labs.",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$totalCount Cảm biến · $onlineCount Hoạt động tốt" + if (alarmNodes.isNotEmpty()) " · ${alarmNodes.size} CẢNH BÁO" else "",
                        color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (alarmNodes.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Add sensor quick button
                Button(
                    onClick = onAddDeviceClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.testTag("btn_dashboard_add_device")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Thêm cảm biến", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Critical Alarm Overlay Banner if any alarm is active
            if (alarmNodes.isNotEmpty()) {
                CriticalAlarmOverlay(
                    alarmNodes = alarmNodes,
                    onSilenceClick = onSilenceClick,
                    onAckClick = onAckClick,
                    onOpenMapClick = onOpenMapClick
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Status Overview Banner
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = if (alarmNodes.isNotEmpty()) "HIỆN ĐANG CÓ BÁO ĐỘNG CHÁY!" else "HỆ THỐNG AN TOÀN",
                            color = if (alarmNodes.isNotEmpty()) CriticalAlarmRed else NormalGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (alarmNodes.isNotEmpty()) "Đang hú còi cảnh báo tại ${alarmNodes.size} cảm biến" else "Tất cả cảm biến đang giám sát bình thường",
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Danh Sách Cảm Biến Báo Cháy",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
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
                        Text("Bấm 'Thêm cảm biến' ở góc trên để kết nối ESP32 nhanh chóng", color = TextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddDeviceClick,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Thêm Cảm Biến Ngay")
                        }
                    }
                }
            }
        } else {
            items(nodes) { node ->
                FriendlySensorCard(
                    node = node,
                    onClick = { onNodeClick(node.deviceId) },
                    onSilence = { onSilenceClick(node.deviceId) },
                    onAck = { onAckClick(node.deviceId) },
                    onTest = { onTestClick(node.deviceId) },
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
    onSilence: () -> Unit,
    onAck: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAlarm = node.lastKnownAlarm || node.firmwareState == FirmwareState.ALARM
    var showChartDetail by remember { mutableStateOf(false) }

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
            // Header Row: Name & Alarm Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = node.displayName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${node.deviceId} · IP: ${node.ipAddress}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                FirmwareStateBadge(
                    firmwareState = node.firmwareState,
                    connectionState = node.connectionState
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prominent Status Bar (HIỆN ĐANG BÁO ĐỘNG vs AN TOÀN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAlarm) CriticalAlarmRed else Color(0xFFF0FDF4))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAlarm) Color.White else NormalGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAlarm) "HIỆN ĐANG BÁO ĐỘNG CHÁY!" else "TRẠNG THÁI: AN TOÀN BÌNH THƯỜNG",
                        color = if (isAlarm) Color.White else NormalGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (node.telemetry.hornRequested) "Còi: ĐANG HÚ" else "Còi: Chờ",
                    color = if (isAlarm) Color.White else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Telemetry values grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MQ-2 reading
                Column {
                    Text("Cảm biến khí MQ-2", color = TextSecondary, fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${node.telemetry.gasRaw}",
                            color = if (node.telemetry.gasRaw > 1800) CriticalAlarmRed else PrimaryBlue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ADC raw", color = TextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = if (node.telemetry.gasReady) "Đã sẵn sàng" else "Đang sấy nóng",
                        color = if (node.telemetry.gasReady) NormalGreen else WarmingPurple,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Flame Sensor
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Cảm biến ngọn lửa", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (node.telemetry.flame) CriticalAlarmRed else Color(0xFFF1F5F9))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (node.telemetry.flame) "CÓ LỬA!" else "Không phát hiện",
                            color = if (node.telemetry.flame) Color.White else NormalGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Toggle Chart Button
                IconButton(
                    onClick = { showChartDetail = !showChartDetail },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showChartDetail) PrimaryBlueLight else LightSurfaceVariant)
                        .size(38.dp)
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = "Xem biểu đồ",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expandable Professional Chart
            AnimatedVisibility(visible = showChartDetail) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val now = System.currentTimeMillis()
                    val chartData = listOf(
                        ChartPoint(now - 45000, (node.telemetry.gasRaw * 0.92).toInt()),
                        ChartPoint(now - 30000, (node.telemetry.gasRaw * 0.96).toInt()),
                        ChartPoint(now - 15000, (node.telemetry.gasRaw * 0.99).toInt()),
                        ChartPoint(now, node.telemetry.gasRaw)
                    )
                    SensorChart(points = chartData, thresholdValue = 1800)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DIRECT ALARM CONTROL BUTTONS (Tắt báo cháy & Báo thử)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAlarm) {
                    // Direct Silence 30s button
                    Button(
                        onClick = onSilence,
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(40.dp)
                            .testTag("btn_silence_${node.deviceId}")
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TẮT BÁO CHÁY (IM 30S)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Direct ACK button
                    Button(
                        onClick = onAck,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(0.7f)
                            .height(40.dp)
                            .testTag("btn_ack_${node.deviceId}")
                    ) {
                        Text("XÁC NHẬN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Safe state: Test horn button
                    OutlinedButton(
                        onClick = onTest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Còi Báo Thử", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onSilence,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tắt Báo Cháy", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
