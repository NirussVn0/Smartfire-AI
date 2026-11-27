package com.example.ui.alert

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.SmartFireApplication
import com.example.domain.model.AlertMode
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.FirmwareState
import com.example.domain.model.MapNavigationRequest
import com.example.domain.model.MapNavigationTargetType
import com.example.domain.model.SmartFireNode
import com.example.domain.model.SmartFireTelemetry
import com.example.service.audio.AlarmAudioPlayer
import com.example.service.notification.SmartFireNotificationManager
import com.example.service.overlay.EmergencyOverlayPopupManager
import com.example.ui.components.forceMaxSystemAlarmVolume
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.SmartFireTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

class FireAlarmAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_ROOM_NAME = "extra_room_name"
        const val EXTRA_GAS_RAW = "extra_gas_raw"
        const val EXTRA_HAS_FLAME = "extra_has_flame"
        const val EXTRA_TEMP_C = "extra_temp_c"
    }

    private data class AlertFallbackExtras(
        val deviceId: String,
        val deviceName: String,
        val roomName: String,
        val gasRaw: Int,
        val hasFlame: Boolean,
        val tempC: Float?
    )

    private val extrasState = mutableStateOf(
        AlertFallbackExtras(
            deviceId = "",
            deviceName = "Cảm biến SmartFire",
            roomName = "Khu vực cảm biến",
            gasRaw = 0,
            hasFlame = false,
            tempC = null
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        configureLockScreenWindow()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extrasState.value = parseExtras(intent)

        setContent {
            SmartFireTheme(darkTheme = false) {
                val app = SmartFireApplication.instance
                val repository = app.repository
                val context = LocalContext.current
                val notificationManager = remember { SmartFireNotificationManager(context.applicationContext) }

                val extras = extrasState.value
                val nodes by repository.allDevices.collectAsState(initial = emptyList())
                val savedConfig by repository.savedEspConfig.collectAsState()
                val mutedMap by repository.mutedUntilByDevice.collectAsState()
                val localMutedUntil by repository.localAlarmMutedUntilMs.collectAsState()
                val offlineLatched by repository.offlineFireLatchedDevices.collectAsState()
                val phoneAlarmSoundEnabled by repository.phoneAlarmSoundEnabled.collectAsState()
                val alertMode by repository.alertMode.collectAsState()
                val alarmSoundType by repository.alarmSoundType.collectAsState()
                val alarmVolumePercent by repository.alarmVolumePercent.collectAsState()

                var activeDeviceId by remember(extras.deviceId) { mutableStateOf(extras.deviceId) }
                var manualBoostTick by remember { mutableIntStateOf(0) }

                val fallbackNode = remember(extras) {
                    SmartFireNode(
                        deviceId = extras.deviceId.ifBlank { extras.deviceName },
                        displayName = extras.deviceName,
                        homeId = "",
                        floorId = "",
                        roomId = "",
                        firmwareRoomId = extras.roomName,
                        ipAddress = "",
                        firmwareState = FirmwareState.ALARM,
                        connectionState = DeviceConnectionState.ONLINE_FRESH,
                        lastKnownAlarm = true,
                        telemetry = SmartFireTelemetry(
                            state = "ALARM",
                            gasRaw = extras.gasRaw,
                            temperatureCelsius = extras.tempC,
                            flame = extras.hasFlame,
                            alarm = true
                        )
                    )
                }

                val liveTargetNode = nodes.find {
                    it.deviceId == activeDeviceId || (activeDeviceId.isBlank() && it.isEffectivelyAlarming(savedConfig))
                }
                val displayNode = liveTargetNode ?: fallbackNode
                val activeAlarmNodes = nodes.filter { node ->
                    node.isEffectivelyAlarming(savedConfig) &&
                        !node.suspectedBurnedOffline &&
                        node.deviceId !in offlineLatched
                }

                LaunchedEffect(nodes, savedConfig, mutedMap, localMutedUntil, offlineLatched, activeDeviceId) {
                    if (nodes.isEmpty()) return@LaunchedEffect

                    val currentNode = nodes.find { it.deviceId == activeDeviceId }
                    val isCurrentStillActive = currentNode != null &&
                        currentNode.isEffectivelyAlarming(savedConfig) &&
                        !currentNode.suspectedBurnedOffline &&
                        currentNode.deviceId !in offlineLatched &&
                        !repository.isDeviceMutedNow(currentNode.deviceId)

                    if (!isCurrentStillActive) {
                        val nextAlarming = nodes.firstOrNull { node ->
                            node.isEffectivelyAlarming(savedConfig) &&
                                !node.suspectedBurnedOffline &&
                                node.deviceId !in offlineLatched &&
                                !repository.isDeviceMutedNow(node.deviceId)
                        }
                        if (nextAlarming != null) {
                            activeDeviceId = nextAlarming.deviceId
                        } else {
                            AlarmAudioPlayer.stopManagedAlert(null)
                            finish()
                        }
                    }
                }

                LaunchedEffect(
                    displayNode.deviceId,
                    phoneAlarmSoundEnabled,
                    alertMode,
                    alarmSoundType,
                    alarmVolumePercent,
                    mutedMap,
                    localMutedUntil,
                    manualBoostTick
                ) {
                    val isMuted = repository.isDeviceMutedNow(displayNode.deviceId)
                    if (phoneAlarmSoundEnabled && alertMode != AlertMode.SILENT && !isMuted) {
                        AlarmAudioPlayer.startManagedAlert(
                            context = applicationContext,
                            sessionId = "bg_alarm_${displayNode.deviceId}",
                            alertMode = alertMode,
                            soundType = alarmSoundType,
                            volumePercent = alarmVolumePercent,
                            isLevel1Warning = false
                        )
                    }
                }

                FireAlarmFullScreenContent(
                    node = displayNode,
                    totalAlarmCount = maxOf(1, activeAlarmNodes.size),
                    onBoostVolume = {
                        forceMaxSystemAlarmVolume(context)
                        manualBoostTick++
                    },
                    onEvacuateExit = {
                        val id = displayNode.deviceId
                        repository.muteDeviceLocally(id, 180_000L)
                        repository.muteLocalNotificationsTemporarily(180_000L)
                        AlarmAudioPlayer.stopManagedAlert(null)
                        notificationManager.cancelFireAlarmNotification(id)
                        EmergencyOverlayPopupManager.dismissOverlay(this@FireAlarmAlertActivity)
                        repository.requestMapNavigation(
                            MapNavigationRequest(
                                targetType = MapNavigationTargetType.NEAREST_EXIT,
                                targetDeviceId = id
                            )
                        )
                        launchMainActivity(evacuateDeviceId = id)
                        finish()
                    },
                    onTwoStageExtinguisherToFire = {
                        val id = displayNode.deviceId
                        repository.muteDeviceLocally(id, 180_000L)
                        repository.muteLocalNotificationsTemporarily(180_000L)
                        AlarmAudioPlayer.stopManagedAlert(null)
                        notificationManager.cancelFireAlarmNotification(id)
                        EmergencyOverlayPopupManager.dismissOverlay(this@FireAlarmAlertActivity)
                        repository.requestMapNavigation(
                            MapNavigationRequest(
                                targetType = MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE,
                                targetDeviceId = id
                            )
                        )
                        launchMainActivity(evacuateDeviceId = null)
                        finish()
                    },
                    onSilence30s = {
                        val id = displayNode.deviceId
                        repository.muteDeviceLocally(id, 30_000L)
                        AlarmAudioPlayer.stopManagedAlert(null)
                        notificationManager.cancelFireAlarmNotification(id)
                        EmergencyOverlayPopupManager.dismissOverlay(this@FireAlarmAlertActivity)
                        app.applicationScope.launch {
                            repository.sendCommand(id, "silence")
                        }
                        finish()
                    },
                    onDisarm = {
                        val id = displayNode.deviceId
                        repository.muteDeviceLocally(id, 300_000L)
                        AlarmAudioPlayer.stopManagedAlert(null)
                        notificationManager.cancelFireAlarmNotification(id)
                        EmergencyOverlayPopupManager.dismissOverlay(this@FireAlarmAlertActivity)
                        app.applicationScope.launch {
                            repository.sendCommand(id, "disarm")
                        }
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extrasState.value = parseExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        EmergencyOverlayPopupManager.dismissOverlay(this)
    }

    override fun onDestroy() {
        if (isFinishing) {
            val id = extrasState.value.deviceId
            if (id.isNotBlank()) {
                AlarmAudioPlayer.stopManagedAlert("bg_alarm_$id")
            }
        }
        super.onDestroy()
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun parseExtras(intent: Intent?): AlertFallbackExtras {
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: deviceId.ifBlank { "Cảm biến SmartFire" }
        val roomName = intent?.getStringExtra(EXTRA_ROOM_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: "Khu vực cảm biến"
        val gasRaw = intent?.getIntExtra(EXTRA_GAS_RAW, 0) ?: 0
        val hasFlame = intent?.getBooleanExtra(EXTRA_HAS_FLAME, false) ?: false
        val tempC = if (intent != null && intent.hasExtra(EXTRA_TEMP_C)) {
            intent.getFloatExtra(EXTRA_TEMP_C, Float.NaN).takeIf { !it.isNaN() }
        } else {
            null
        }
        return AlertFallbackExtras(
            deviceId = deviceId,
            deviceName = deviceName,
            roomName = roomName,
            gasRaw = gasRaw,
            hasFlame = hasFlame,
            tempC = tempC
        )
    }

    private fun launchMainActivity(evacuateDeviceId: String?) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!evacuateDeviceId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_EVACUATE_DEVICE_ID, evacuateDeviceId)
            }
        }
        startActivity(mainIntent)
    }
}

@Composable
private fun FireAlarmFullScreenContent(
    node: SmartFireNode,
    totalAlarmCount: Int,
    onBoostVolume: () -> Unit,
    onEvacuateExit: () -> Unit,
    onTwoStageExtinguisherToFire: () -> Unit,
    onSilence30s: () -> Unit,
    onDisarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lockscreen_alarm_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockscreen_bg_alpha"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockscreen_icon_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF450A0A))
            .background(Color(0xFF991B1B).copy(alpha = flashAlpha))
            .border(6.dp, Color.White.copy(alpha = flashAlpha))
            .systemBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, CriticalAlarmRed, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(iconScale)
                                .clip(CircleShape)
                                .background(CriticalAlarmRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Báo cháy khẩn cấp",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "BÁO CHÁY KHẨN CẤP",
                                color = CriticalAlarmRed,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (totalAlarmCount > 1) {
                                    "Mức 2 · $totalAlarmCount điểm đang báo động"
                                } else {
                                    "Mức 2 · Cảnh báo màn hình khóa"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onBoostVolume,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Tăng âm lượng còi",
                            tint = CriticalAlarmRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Device Name + Room Pill
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = node.displayName,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = node.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                            color = CriticalAlarmRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3 Telemetry Tiles: Nhiệt | Khói | Lửa
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(16.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlertTelemetryTile(
                        value = node.telemetry.temperatureCelsius?.let {
                            String.format(Locale.US, "%.1f°C", it)
                        } ?: "N/A",
                        label = "Nhiệt",
                        valueColor = CriticalAlarmRed,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0xFFFECACA))
                    )
                    AlertTelemetryTile(
                        value = "${node.telemetry.gasRaw} ADC",
                        label = "Khói",
                        valueColor = CriticalAlarmRed,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0xFFFECACA))
                    )
                    AlertTelemetryTile(
                        value = if (node.telemetry.flame) "CÓ" else "KHÔNG",
                        label = "Lửa",
                        valueColor = if (node.telemetry.flame) CriticalAlarmRed else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Navigation Buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlertActionButton(
                        label = "Thoát Hiểm (Exit)",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        containerColor = Color(0xFF0F766E),
                        onClick = onEvacuateExit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AlertActionButton(
                        label = "Bình CC ➔ Lửa",
                        icon = Icons.Default.FireExtinguisher,
                        containerColor = Color(0xFF7C3AED),
                        onClick = onTwoStageExtinguisherToFire,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Silence & Disarm Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AlertActionButton(
                        label = "Im 30s",
                        icon = Icons.Default.NotificationsOff,
                        containerColor = CriticalAlarmRed,
                        onClick = onSilence30s,
                        modifier = Modifier.weight(1f)
                    )
                    AlertActionButton(
                        label = "Tắt Hẳn",
                        icon = Icons.Default.NotificationsOff,
                        containerColor = Color(0xFF1E293B),
                        onClick = onDisarm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertTelemetryTile(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AlertActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
