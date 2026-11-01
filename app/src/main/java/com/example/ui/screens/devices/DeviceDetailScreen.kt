package com.example.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.CommandStatus
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.DeviceEvent
import com.example.domain.model.FirmwareState
import com.example.domain.model.RoomSpace
import com.example.domain.model.SmartFireNode
import com.example.ui.components.ChartPoint
import com.example.ui.components.FirmwareStateBadge
import com.example.ui.components.SensorChart
import com.example.ui.theme.CriticalAlarmBackground
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarmingPurple
import kotlinx.coroutines.launch

@Composable
fun DeviceDetailScreen(
    deviceId: String,
    repository: SmartFireRepository,
    rooms: List<RoomSpace>,
    onBackClick: () -> Unit,
    onReconfigureBleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val allDevices by repository.allDevices.collectAsState(initial = emptyList())
    val node = allDevices.find { it.deviceId == deviceId }
    val deviceEvents by repository.getEventsForDevice(deviceId).collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Tổng quan", "Biểu đồ & Khí", "Báo động & Còi", "Lịch sử", "Cài đặt")

    var showRenameDialog by remember { mutableStateOf(false) }
    var newAliasInput by remember { mutableStateOf(node?.displayName ?: "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (node == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(LightBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Không tìm thấy thiết bị $deviceId", color = TextSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBackClick) { Text("Quay lại") }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface)
                .border(1.dp, LightBorder)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.testTag("btn_detail_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(node.displayName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("STEM của SOV labs. · ${node.deviceId}", color = TextSecondary, fontSize = 11.sp)
            }
            FirmwareStateBadge(
                firmwareState = node.firmwareState,
                connectionState = node.connectionState
            )
        }

        // 5 Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = LightSurface,
            contentColor = PrimaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = PrimaryBlue
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == index) PrimaryBlue else TextSecondary
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> OverviewTab(node)
                1 -> SensorsTab(node)
                2 -> AlarmControlsTab(
                    node = node,
                    onAck = { scope.launch { repository.sendCommand(node.deviceId, "ack") } },
                    onSilence = { scope.launch { repository.sendCommand(node.deviceId, "silence") } },
                    onTest = { scope.launch { repository.sendCommand(node.deviceId, "test") } }
                )
                3 -> HistoryTab(deviceEvents)
                4 -> SettingsTab(
                    node = node,
                    onRenameClick = {
                        newAliasInput = node.displayName
                        showRenameDialog = true
                    },
                    onReconfigureBleClick = onReconfigureBleClick,
                    onDeleteClick = { showDeleteConfirmDialog = true }
                )
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Đổi tên / Gán nhãn cảm biến", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newAliasInput,
                    onValueChange = { newAliasInput = it },
                    label = { Text("Tên hiển thị mới") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.renameDevice(node.deviceId, newAliasInput)
                        showRenameDialog = false
                    }
                }) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Hủy") }
            },
            containerColor = LightCard
        )
    }

    // Delete Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Xác nhận xóa cảm biến", color = CriticalAlarmRed) },
            text = {
                Text(
                    "Bạn có chắc muốn xóa cảm biến '${node.displayName}' khỏi hệ thống STEM của SOV labs.?",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.removeDevice(node.deviceId)
                            showDeleteConfirmDialog = false
                            onBackClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed)
                ) { Text("Xác nhận xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Hủy") }
            },
            containerColor = LightCard
        )
    }
}

@Composable
fun OverviewTab(node: SmartFireNode) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thông Tin Cảm Biến", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow("Gán nhãn / Tên", node.displayName)
                    DetailRow("Phân loại máy", if (node.isDemoSensor || node.displayName.contains("DEMO", ignoreCase = true)) "MÁY BÁO CHÁY DEMO [DEMO]" else "Cảm biến ESP32 Thực Tế")
                    DetailRow("Nhiệt độ hiện tại", String.format(java.util.Locale.US, "%.1f °C", node.telemetry.temperatureCelsius))
                    DetailRow("Device ID", node.deviceId)
                    DetailRow("Địa chỉ IP mạng LAN", node.ipAddress)
                    DetailRow("Thời gian hoạt động", "${node.telemetry.uptimeMs / 1000}s (${node.telemetry.uptimeMs} ms)")
                    DetailRow("Mã phiên Boot ID", node.bootId.ifBlank { "N/A" })
                    DetailRow("Event Sequence", "#${node.latestSeq}")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thông Tin Phần Cứng STEM (Thế Hệ Mới)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• Cảm biến nhiệt độ môi trường: Đo nhiệt độ thực tế (°C), cảnh báo khi vượt ngưỡng 55°C.\n" +
                                "• Cảm biến khí MQ-2: Đo giá trị analog ADC thô [0..4095].\n" +
                                "• Cảm biến lửa: Đầu ra số (DO) quang học nhạy bức xạ hồng ngoại ngọn lửa.\n" +
                                "• Còi 12V: Điều khiển qua MOSFET D4184 GPIO26.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SensorsTab(node: SmartFireNode) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val now = System.currentTimeMillis()
            val points = listOf(
                ChartPoint(now - 60000, (node.telemetry.gasRaw * 0.90).toInt()),
                ChartPoint(now - 45000, (node.telemetry.gasRaw * 0.94).toInt()),
                ChartPoint(now - 30000, (node.telemetry.gasRaw * 0.97).toInt()),
                ChartPoint(now - 15000, (node.telemetry.gasRaw * 1.01).toInt()),
                ChartPoint(now, node.telemetry.gasRaw)
            )
            SensorChart(points = points, thresholdValue = 1800)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chi Tiết Đo Đạc Thời Gian Thực", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow("Nhiệt độ đo được", String.format(java.util.Locale.US, "%.1f °C", node.telemetry.temperatureCelsius))
                    DetailRow("Khí dễ cháy MQ-2", "${node.telemetry.gasRaw} ADC raw")
                    DetailRow("Trạng thái làm nóng", if (node.telemetry.gasReady) "Sẵn sàng (gas_ready=true)" else "Đang sấy nóng")
                    DetailRow("Ngọn lửa quang học", if (node.telemetry.flame) "CÓ LỬA (DO=true)" else "Bình thường")
                    DetailRow("Lỗi cảm biến (gas_fault)", if (node.telemetry.gasFault) "PHÁT HIỆN LỖI" else "Không")
                    DetailRow("Commissioning EEPROM", if (node.telemetry.gasCommissioned) "Đã hoàn thành" else "Chưa commissioning")
                }
            }
        }
    }
}

@Composable
fun AlarmControlsTab(
    node: SmartFireNode,
    onAck: () -> Unit,
    onSilence: () -> Unit,
    onTest: () -> Unit
) {
    val isAlarm = node.lastKnownAlarm || node.firmwareState == FirmwareState.ALARM

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlarm) CriticalAlarmBackground else LightCard
                ),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAlarm) "HIỆN ĐANG BÁO ĐỘNG CHÁY!" else "TRẠNG THÁI BÌNH THƯỜNG",
                        color = if (isAlarm) CriticalAlarmRed else NormalGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAlarm) "Còi báo động đang kích hoạt tại node này. Hãy kiểm tra hiện trường hoặc nhấn Tắt Báo Cháy phía dưới." else "Cảm biến đang hoạt động an toàn, còi ở chế độ chờ.",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    DetailRow("Cờ alarm của node", "${node.telemetry.alarm}")
                    DetailRow("Còi đang yêu cầu hú", "${node.telemetry.hornRequested}")
                    DetailRow("Còi kích hoạt (horn_enabled)", "${node.telemetry.hornEnabled}")
                    DetailRow("Đang im tạm thời (muted)", "${node.telemetry.muted}")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nút Điều Khiển Báo Động & Còi", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Silence 30s
                    Button(
                        onClick = onSilence,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TẮT BÁO CHÁY (IM 30 GIÂY)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAck,
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Xác Nhận (ACK)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onTest,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Còi Báo Thử", color = PrimaryBlue, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(events: List<DeviceEvent>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (events.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Chưa có sự kiện nào cho node này", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            items(events) { ev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightCard),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("#${ev.seq} · Loại: ${ev.kind}", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (ev.state.isNotBlank()) {
                                Text("Trạng thái: ${ev.state}", color = WarningAmber, fontSize = 11.sp)
                            }
                            Text(ev.rawPayload, color = TextSecondary, fontSize = 10.sp)
                        }
                        Text(
                            text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ev.receivedAt)),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    node: SmartFireNode,
    onRenameClick: () -> Unit,
    onReconfigureBleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tùy Chỉnh", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onRenameClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LightSurfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đổi tên / Gán nhãn cảm biến", color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onReconfigureBleClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cấu hình lại Wi-Fi qua Bluetooth (BLE)")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Xóa Thiết Bị", color = CriticalAlarmRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDeleteClick,
                        modifier = Modifier.fillMaxWidth().testTag("btn_delete_device"),
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa Node Khỏi Hệ Thống")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
