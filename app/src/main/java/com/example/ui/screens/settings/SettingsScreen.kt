package com.example.ui.screens.settings

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import com.example.domain.model.DeviceEvent
import com.example.domain.model.HomeProfile
import com.example.domain.model.SmartFireNode
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.DemoGold
import com.example.ui.theme.DemoGoldBackground
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    repository: SmartFireRepository,
    isDemoMode: Boolean,
    currentHome: HomeProfile?,
    nodes: List<SmartFireNode>,
    events: List<DeviceEvent>,
    onTriggerScanMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Cài Đặt Hệ Thống", "Nhật Ký Sự Kiện (${events.size})")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = LightSurface,
            contentColor = PrimaryBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = PrimaryBlue
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) PrimaryBlue else TextSecondary
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> SettingsTabContent(
                    repository = repository,
                    isDemoMode = isDemoMode,
                    nodes = nodes,
                    onTriggerScanMap = onTriggerScanMap
                )
                1 -> LogsTabContent(
                    repository = repository,
                    events = events,
                    nodes = nodes
                )
            }
        }
    }
}

@Composable
fun SettingsTabContent(
    repository: SmartFireRepository,
    isDemoMode: Boolean,
    nodes: List<SmartFireNode>,
    onTriggerScanMap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var lastTriggeredScenario by remember { mutableStateOf<String?>(null) }

    val gasThreshold by repository.gasThresholdAdc.collectAsState()
    val pollingIntervalMs by repository.pollingIntervalMs.collectAsState()
    val phoneAlarmSound by repository.phoneAlarmSoundEnabled.collectAsState()
    val autoRouteOnAlarm by repository.autoRouteOnAlarm.collectAsState()
    val stepStrideMeters by repository.stepStrideMeters.collectAsState()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. STEM DEMO MODE (SOV LABS)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, DemoGold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DemoGoldBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = DemoGold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Chế Độ Trình Diễn STEM", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("STEM của SOV labs. · Thuyết trình & Kiểm thử", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = { active ->
                                repository.toggleDemoMode(active)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DemoGold
                            ),
                            modifier = Modifier.testTag("switch_demo_mode")
                        )
                    }

                    if (isDemoMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Kích hoạt nhanh kịch bản kiểm thử:",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    repository.triggerDemoScenario("KITCHEN_ALARM")
                                    lastTriggeredScenario = "Kích hoạt Cháy Khu Vực B (ALARM, flame=true, gas=2500)"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kịch bản 1: Báo Động Cháy (ALARM)")
                            }

                            Button(
                                onClick = {
                                    repository.triggerDemoScenario("DISCONNECT_DURING_ALARM")
                                    lastTriggeredScenario = "Mô phỏng Mất Wi-Fi khi đang ALARM (Giữ nguyên cảnh báo)"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kịch bản 2: Mất Mạng Wi-Fi Khi Đang Cháy")
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        repository.triggerDemoScenario("CLEAR_NORMAL")
                                        lastTriggeredScenario = "Bình thường hóa an toàn (NORMAL)"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("An Toàn (NORMAL)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        repository.triggerDemoScenario("SIMULATE_REBOOT")
                                        lastTriggeredScenario = "Khởi động lại Node (Boot ID mới)"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reboot Node", fontSize = 11.sp)
                                }
                            }
                        }

                        lastTriggeredScenario?.let { sc ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Đã kích hoạt: $sc", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. ALARM & THRESHOLD SETTINGS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background( Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Cài Đặt Báo Động & Ngưỡng Khí Gas", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Tuỳ chỉnh độ nhạy MQ-2 ADC & phản ứng tự động", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ngưỡng cảnh báo khí MQ-2:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$gasThreshold ADC", color = CriticalAlarmRed, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    Slider(
                        value = gasThreshold.toFloat(),
                        onValueChange = { repository.updateGasThresholdAdc(it.roundToInt()) },
                        valueRange = 1200f..2800f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = CriticalAlarmRed,
                            activeTrackColor = CriticalAlarmRed
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Âm thanh & rung cảnh báo trên điện thoại", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Phát còi khẩn cấp khi có node báo cháy", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = phoneAlarmSound,
                            onCheckedChange = { repository.updatePhoneAlarmSound(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tự động dẫn đường Bình CC ➔ Đám cháy", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tự động tính lộ trình 2 chặng ngay khi báo cháy", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoRouteOnAlarm,
                            onCheckedChange = { repository.updateAutoRouteOnAlarm(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = NormalGreen)
                        )
                    }
                }
            }
        }

        // 3. PERFORMANCE, POLLING & STEP SENSOR CALIBRATION
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Tối Ưu Kết Nối & Cảm Biến Di Chuyển", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Chống lag, chống treo request HTTP & hiệu chuẩn bước chân", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Tốc độ cập nhật dữ liệu ESP32 (Polling Interval):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pollOptions = listOf(
                            "1.0s (Nhanh)" to 1000L,
                            "1.5s (Chuẩn)" to 1500L,
                            "3.0s (Siêu Mượt)" to 3000L
                        )
                        pollOptions.forEach { (label, ms) ->
                            val selected = pollingIntervalMs == ms
                            FilterChip(
                                selected = selected,
                                onClick = { repository.updatePollingIntervalMs(ms) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = LightSurfaceVariant,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Chiều dài sải bước chân (Step Detector):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val strideOptions = listOf(
                            "0.55m (Ngắn)" to 0.55f,
                            "0.65m (Chuẩn)" to 0.65f,
                            "0.75m (Dài)" to 0.75f
                        )
                        strideOptions.forEach { (label, stride) ->
                            val selected = kotlin.math.abs(stepStrideMeters - stride) < 0.03f
                            FilterChip(
                                selected = selected,
                                onClick = { repository.updateStepStrideMeters(stride) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NormalGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = LightSurfaceVariant,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 4. RESET / RE-SCAN 2D MAP SETUP
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Bản Đồ 2D & Thiết Lập Căn Phòng", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Ghim lại vị trí Báo Cháy, Cửa, Bình Chữa Cháy hoặc xóa về Căn Phòng Mù", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onTriggerScanMap,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vào Chế Độ 2: Ghim Báo Cháy & Vật Dụng", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.clearMapConfiguration("floor_1")
                                repository.updateFloorDimensions("floor_1", 0f, 0f)
                                onTriggerScanMap()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa Toàn Bộ Cấu Hình Bản Đồ (Về Căn Phòng Mù)", color = CriticalAlarmRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 5. SYSTEM INFO & CREDITS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thông Tin Hệ Thống", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingDetailRow("Đơn vị sở hữu", "STEM của SOV labs.")
                    SettingDetailRow("Số cảm biến quản lý", "${nodes.size} nodes")
                    SettingDetailRow("Chế độ chống lỗi Request", "Offline-First Auto Fallback (Bật)")
                    SettingDetailRow("Cơ sở dữ liệu", "Room SQLite + Android Keystore AES")
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun LogsTabContent(
    repository: SmartFireRepository,
    events: List<DeviceEvent>,
    nodes: List<SmartFireNode>
) {
    val scope = rememberCoroutineScope()
    var filterKind by remember { mutableStateOf<String?>(null) }
    val filtered = if (filterKind == null) events else events.filter { it.kind.equals(filterKind, ignoreCase = true) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val kinds = listOf("Tất cả", "state", "ack", "silence", "test")
                    items(kinds) { k ->
                        val isSelected = (k == "Tất cả" && filterKind == null) || (k == filterKind)
                        FilterChip(
                            selected = isSelected,
                            onClick = { filterKind = if (k == "Tất cả") null else k },
                            label = { Text(k, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = LightCard,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                if (events.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch { repository.clearAllEvents() }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Chưa có sự kiện nào trong nhật ký", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            items(filtered) { ev ->
                val nodeName = nodes.find { it.deviceId == ev.deviceId }?.displayName ?: ev.deviceId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightCard),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${ev.seq}", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(nodeName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("Loại: ${ev.kind} ${if (ev.state.isNotBlank()) "· ${ev.state}" else ""}", color = TextSecondary, fontSize = 11.sp)
                            if (ev.rawPayload.isNotBlank()) {
                                Text(ev.rawPayload, color = TextSecondary, fontSize = 10.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (ev.result.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (ev.result == "applied") NormalGreen else LightSurfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(ev.result.uppercase(), color = if (ev.result == "applied") Color.White else TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date(ev.receivedAt)),
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
