package com.example.ui.screens.settings

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.backup.EspConfigBackupManager
import com.example.data.backup.ExportedAppSettings
import com.example.data.backup.ExportedEspDevice
import com.example.data.backup.SmartFireConfigBackup
import com.example.data.local.entity.DeviceEntity
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.AlarmSoundType
import com.example.domain.model.AlertMode
import com.example.domain.model.DeviceEvent
import com.example.domain.model.HomeProfile
import com.example.domain.model.SmartFireNode
import com.example.service.audio.AlarmAudioPlayer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    val savedEspConfig by repository.savedEspConfig.collectAsState()
    val pollingIntervalMs by repository.pollingIntervalMs.collectAsState()
    val phoneAlarmSound by repository.phoneAlarmSoundEnabled.collectAsState()
    val autoRouteOnAlarm by repository.autoRouteOnAlarm.collectAsState()
    val stepStrideMeters by repository.stepStrideMeters.collectAsState()
    val alertMode by repository.alertMode.collectAsState()
    val alarmSoundType by repository.alarmSoundType.collectAsState()
    val alarmVolumePercent by repository.alarmVolumePercent.collectAsState()
    val level1PopupEnabled by repository.level1PopupEnabled.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewLevel by remember { mutableStateOf<Int?>(null) }
    var previewSessionNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewLevel, previewSessionNonce, alertMode, alarmSoundType, alarmVolumePercent) {
        val lvl = previewLevel ?: return@LaunchedEffect
        val myNonce = previewSessionNonce
        try {
            withTimeoutOrNull(2500L) {
                AlarmAudioPlayer.runAlertLoop(
                    context = context,
                    alertMode = alertMode,
                    soundType = alarmSoundType,
                    volumePercent = alarmVolumePercent,
                    isLevel1Warning = (lvl == 1)
                )
            }
        } finally {
            if (isActive && previewSessionNonce == myNonce) {
                previewLevel = null
            }
        }
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)
    fun hasFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() == true
        } else {
            true
        }
    }

    var canDrawOverlays by remember { mutableStateOf(hasOverlayPermission()) }
    var canUseFullScreenIntent by remember { mutableStateOf(hasFullScreenIntentPermission()) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = hasOverlayPermission()
                canUseFullScreenIntent = hasFullScreenIntentPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var includeTokensInExport by remember { mutableStateOf(true) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }

    suspend fun buildBackupJson(includeTokens: Boolean): Pair<String, Int> {
        val entitiesWithTokens = repository.getAllDeviceEntitiesWithTokens()
        val exportedDevices = entitiesWithTokens.map { (entity, token) ->
            ExportedEspDevice(
                deviceId = entity.deviceId,
                displayName = entity.displayName,
                ipAddress = entity.lastKnownIp,
                bearerToken = if (includeTokens) token else null,
                homeId = entity.homeId,
                floorId = entity.floorId,
                roomId = entity.roomId,
                firmwareRoomId = entity.firmwareRoomId,
                xNormalized = entity.xNormalized,
                yNormalized = entity.yNormalized,
                isDemoSensor = entity.firmwareRoomId.startsWith("demo") ||
                    entity.firmwareRoomId.startsWith("sov-") ||
                    entity.displayName.contains("DEMO", ignoreCase = true)
            )
        }
        val backup = SmartFireConfigBackup(
            includeTokens = includeTokens,
            devices = exportedDevices,
            espConfig = repository.savedEspConfig.value,
            appSettings = ExportedAppSettings(
                pollingIntervalMs = repository.pollingIntervalMs.value,
                httpTimeoutSeconds = repository.httpTimeoutSeconds.value,
                alertMode = repository.alertMode.value.name,
                alarmSoundType = repository.alarmSoundType.value.name,
                alarmVolumePercent = repository.alarmVolumePercent.value,
                level1PopupEnabled = repository.level1PopupEnabled.value,
                autoRouteOnAlarm = repository.autoRouteOnAlarm.value,
                stepStrideMeters = repository.stepStrideMeters.value
            )
        )
        return EspConfigBackupManager.toJson(backup) to exportedDevices.size
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (json, deviceCount) = buildBackupJson(includeTokensInExport)
            val result = withContext(Dispatchers.IO) {
                EspConfigBackupManager.writeToUri(context, uri, json)
            }
            if (result.isSuccess) {
                backupStatusMessage = "Đã xuất cấu hình $deviceCount thiết bị ESP32 ra file JSON!"
                backupStatusIsError = false
            } else {
                backupStatusMessage = result.exceptionOrNull()?.message ?: "Không thể xuất file cấu hình JSON."
                backupStatusIsError = true
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val readResult = withContext(Dispatchers.IO) {
                EspConfigBackupManager.readFromUri(context, uri)
            }
            val rawJson = readResult.getOrElse { err ->
                backupStatusMessage = err.message ?: "Không thể đọc file cấu hình JSON."
                backupStatusIsError = true
                return@launch
            }
            val parsedResult = EspConfigBackupManager.fromJson(rawJson)
            val backup = parsedResult.getOrElse { err ->
                backupStatusMessage = err.message ?: "Tệp JSON không hợp lệ."
                backupStatusIsError = true
                return@launch
            }

            try {
                repository.saveEspConfigLocally(backup.espConfig)
                repository.updatePollingIntervalMs(backup.appSettings.pollingIntervalMs)
                repository.updateHttpTimeoutSeconds(backup.appSettings.httpTimeoutSeconds)
                runCatching { AlertMode.valueOf(backup.appSettings.alertMode) }.getOrNull()
                    ?.let { repository.updateAlertMode(it) }
                runCatching { AlarmSoundType.valueOf(backup.appSettings.alarmSoundType) }.getOrNull()
                    ?.let { repository.updateAlarmSoundType(it) }
                repository.updateAlarmVolumePercent(backup.appSettings.alarmVolumePercent)
                repository.updateLevel1PopupEnabled(backup.appSettings.level1PopupEnabled)
                repository.updateAutoRouteOnAlarm(backup.appSettings.autoRouteOnAlarm)
                repository.updateStepStrideMeters(backup.appSettings.stepStrideMeters)

                var importedCount = 0
                backup.devices.forEach { exported ->
                    val entity = DeviceEntity(
                        deviceId = exported.deviceId,
                        displayName = exported.displayName,
                        homeId = exported.homeId,
                        floorId = exported.floorId,
                        roomId = exported.roomId,
                        firmwareRoomId = exported.firmwareRoomId,
                        lastKnownIp = exported.ipAddress,
                        xNormalized = exported.xNormalized,
                        yNormalized = exported.yNormalized
                    )
                    repository.importDeviceFromBackup(entity, exported.bearerToken)
                    importedCount++
                }
                backupStatusMessage = "Đã nhập $importedCount thiết bị ESP32 và cấu hình hệ thống!"
                backupStatusIsError = false
            } catch (e: Exception) {
                backupStatusMessage = e.message ?: "Lỗi khi khôi phục cấu hình từ tệp JSON."
                backupStatusIsError = true
            }
        }
    }

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bật Demo sẽ thêm node mô phỏng mà KHÔNG làm mất cảm biến ESP32 thật.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

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
                            Text("Cài Đặt Báo Động & Ngưỡng 2 Mức", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Tách biệt Mức 1 Cảnh báo & Mức 2 Báo cháy + Bật/Bỏ qua từng cảm biến", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LightSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Cấu hình đang áp dụng (chỉnh chi tiết & Push ở thẻ ngay bên dưới):",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (savedEspConfig.mq2Enabled) {
                                "• MQ-2 (Khói/Gas): BẬT · Mức 1=${savedEspConfig.gasWarn} ADC · Mức 2=${savedEspConfig.gasOn} ADC"
                            } else {
                                "• MQ-2 (Khói/Gas): ĐANG BỎ QUA"
                            },
                            color = if (savedEspConfig.mq2Enabled) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (savedEspConfig.tempEnabled) {
                                "• Nhiệt độ: BẬT · Mức 1=${savedEspConfig.tempWarnC.roundToInt()}°C · Mức 2=${savedEspConfig.tempAlarmC.roundToInt()}°C"
                            } else {
                                "• Nhiệt độ: ĐANG BỎ QUA"
                            },
                            color = if (savedEspConfig.tempEnabled) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (savedEspConfig.flameEnabled) {
                                "• Cảm biến Lửa (IR): BẬT · Mức 1=${savedEspConfig.flameWarnMs}ms · Mức 2=${savedEspConfig.flameAlarmMs}ms"
                            } else {
                                "• Cảm biến Lửa (IR): ĐANG BỎ QUA"
                            },
                            color = if (savedEspConfig.flameEnabled) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- CHẾ ĐỘ ÂM THANH / RUNG / ĐỘ LỚN / KIỂU CHUÔNG ---
                    Text("Chế độ cảnh báo trên điện thoại:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val modeOptions = listOf(
                            "Chuông + Rung" to AlertMode.SOUND_AND_VIBRATE,
                            "Chỉ Rung" to AlertMode.VIBRATE_ONLY,
                            "Im Lặng" to AlertMode.SILENT
                        )
                        modeOptions.forEach { (label, mode) ->
                            val selected = alertMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { repository.updateAlertMode(mode) },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = LightSurfaceVariant,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f).testTag("chip_alert_mode_${mode.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kiểu âm thanh cảnh báo:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val soundRow1 = listOf(
                        "Còi Hú PCCC" to AlarmSoundType.FIRE_SIREN,
                        "Bíp Điện Tử" to AlarmSoundType.URGENT_BEEP
                    )
                    val soundRow2 = listOf(
                        "Chuông Báo Thức" to AlarmSoundType.SYSTEM_ALARM,
                        "Thông Báo Máy" to AlarmSoundType.SYSTEM_NOTIFICATION
                    )
                    listOf(soundRow1, soundRow2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { (label, sType) ->
                                val selected = alarmSoundType == sType
                                FilterChip(
                                    selected = selected,
                                    enabled = alertMode == AlertMode.SOUND_AND_VIBRATE,
                                    onClick = { repository.updateAlarmSoundType(sType) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CriticalAlarmRed,
                                        selectedLabelColor = Color.White,
                                        containerColor = LightSurfaceVariant,
                                        labelColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1f).testTag("chip_sound_type_${sType.name}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Độ lớn âm thanh cảnh báo:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$alarmVolumePercent%", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Slider(
                        value = alarmVolumePercent.toFloat(),
                        onValueChange = { repository.updateAlarmVolumePercent(it.roundToInt()) },
                        enabled = alertMode == AlertMode.SOUND_AND_VIBRATE,
                        valueRange = 10f..100f,
                        colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue),
                        modifier = Modifier.testTag("slider_alarm_volume")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (previewLevel == 1) {
                                    previewLevel = null
                                } else {
                                    previewSessionNonce++
                                    previewLevel = 1
                                }
                            },
                            modifier = Modifier.weight(1f).height(38.dp).testTag("btn_test_sound_l1"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (previewLevel == 1) "Đang phát Mức 1..." else "Nghe thử Mức 1",
                                color = Color(0xFFD97706),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (previewLevel == 2) {
                                    previewLevel = null
                                } else {
                                    previewSessionNonce++
                                    previewLevel = 2
                                }
                            },
                            modifier = Modifier.weight(1f).height(38.dp).testTag("btn_test_sound_l2"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (previewLevel == 2) "Đang phát Mức 2..." else "Nghe thử Mức 2",
                                color = CriticalAlarmRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hiện popup & chuông Mức 1 (Cảnh báo sớm)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Thông báo & cảnh báo nhẹ ngay khi vượt ngưỡng Mức 1", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = level1PopupEnabled,
                            onCheckedChange = { repository.updateLevel1PopupEnabled(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFD97706)),
                            modifier = Modifier.testTag("switch_level1_popup")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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

        // 2A. EXTERNAL FIRE POPUP & FULL-SCREEN PERMISSIONS
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
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Popup Báo Cháy Bên Ngoài Ứng Dụng", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Hiển thị cảnh báo khẩn cấp khi đang mở app khác hoặc khóa màn hình", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LightSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Popup nổi trên app khác: ${if (canDrawOverlays) "Đã cấp" else "Chưa cấp"}",
                                color = if (canDrawOverlays) Color(0xFF047857) else CriticalAlarmRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (canDrawOverlays) NormalGreen else CriticalAlarmRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (Build.VERSION.SDK_INT >= 34) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Toàn màn hình khi khóa máy: ${if (canUseFullScreenIntent) "Đã cấp" else "Chưa cấp"}",
                                    color = if (canUseFullScreenIntent) Color(0xFF047857) else CriticalAlarmRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (canUseFullScreenIntent) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (canUseFullScreenIntent) NormalGreen else CriticalAlarmRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Toàn màn hình khi khóa máy: Đã cấp (mặc định < Android 14)",
                                color = Color(0xFF047857),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Không thể mở cài đặt Popup nổi trên thiết bị này", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_grant_overlay_permission"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canDrawOverlays) "Cài đặt quyền Popup nổi (Đã bật)" else "Cấp quyền Popup nổi trên app khác",
                            color = PrimaryBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (Build.VERSION.SDK_INT >= 34) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Không thể mở cài đặt Toàn màn hình trên thiết bị này", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("btn_grant_fullscreen_intent"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (canUseFullScreenIntent) "Cài đặt Toàn màn hình (Đã bật)" else "Cấp quyền Toàn màn hình khi khóa máy",
                                color = PrimaryBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2B. ESP32 REMOTE CONFIG & WI-FI OTA (SAVE + PUSH BUTTONS)
        item {
            com.example.ui.components.EspWifiConfigAndOtaCard(
                repository = repository,
                targetNode = null,
                allNodes = nodes
            )
        }

        // 2C. EXPORT / IMPORT ESP32 CONFIG BACKUP (.JSON)
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
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Xuất / Nhập Cấu Hình Báo Cháy ESP32 (.json)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Sao lưu danh sách thiết bị ESP32, ngưỡng báo cháy & cài đặt ứng dụng", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Bao gồm mã API Token bảo mật", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tắt tùy chọn này nếu bạn muốn chia sẻ file cấu hình công khai", color = TextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = includeTokensInExport,
                            onCheckedChange = { includeTokensInExport = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                            modifier = Modifier.testTag("switch_include_tokens_export")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportBackupLauncher.launch(EspConfigBackupManager.defaultFileName())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_export_esp_config_file"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Xuất File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val (json, deviceCount) = buildBackupJson(includeTokensInExport)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Cấu hình SmartFire ESP32")
                                        putExtra(Intent.EXTRA_TEXT, json)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ cấu hình SmartFire ESP32"))
                                        backupStatusMessage = "Đã chuẩn bị chia sẻ cấu hình $deviceCount thiết bị ESP32."
                                        backupStatusIsError = false
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Không tìm thấy ứng dụng chia sẻ", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_share_esp_config"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Chia sẻ", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_import_esp_config_file"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhập Cấu Hình Từ File (.json)", color = NormalGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    backupStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (backupStatusIsError) Color(0xFFFEF2F2) else Color(0xFFECFDF5))
                                .border(
                                    1.dp,
                                    if (backupStatusIsError) CriticalAlarmRed else NormalGreen,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = msg,
                                color = if (backupStatusIsError) CriticalAlarmRed else Color(0xFF047857),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterKind by remember { mutableStateOf<String?>(null) }
    val filtered = if (filterKind == null) events else events.filter { it.kind.equals(filterKind, ignoreCase = true) }

    fun buildCsvContent(items: List<DeviceEvent>): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val sb = StringBuilder()
        sb.appendLine("Seq,Time,DeviceId,DeviceName,Kind,State,Result,Payload")
        items.forEach { ev ->
            val nodeName = (nodes.find { it.deviceId == ev.deviceId }?.displayName ?: ev.deviceId).replace(",", " ")
            val timeStr = dateFormat.format(java.util.Date(ev.receivedAt))
            val safePayload = ev.rawPayload.replace("\"", "'").replace("\n", " ")
            sb.appendLine("${ev.seq},\"$timeStr\",\"${ev.deviceId}\",\"$nodeName\",\"${ev.kind}\",\"${ev.state}\",\"${ev.result}\",\"$safePayload\"")
        }
        return sb.toString()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }

                if (events.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val csv = buildCsvContent(filtered)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "SmartFire AI - Nhật ký sự kiện báo cháy (${filtered.size} bản ghi)")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(sendIntent, "Xuất / Chia sẻ Nhật ký CSV"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Không tìm thấy ứng dụng chia sẻ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(36.dp)
                                .testTag("btn_export_logs_csv")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Xuất CSV / Chia sẻ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = buildCsvContent(filtered)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("SmartFire Event Logs CSV", csv))
                                Toast.makeText(context, "Đã sao chép ${filtered.size} dòng nhật ký CSV!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(36.dp)
                                .testTag("btn_copy_logs_csv")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sao chép", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch { repository.clearAllEvents() }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("btn_clear_all_logs")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
