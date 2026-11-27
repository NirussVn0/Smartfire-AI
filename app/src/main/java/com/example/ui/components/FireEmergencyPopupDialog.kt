package com.example.ui.components

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AlarmSoundType
import com.example.domain.model.AlertMode
import com.example.domain.model.SmartFireNode
import com.example.service.audio.AlarmAudioPlayer
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Forces Android STREAM_MUSIC, STREAM_ALARM, and STREAM_NOTIFICATION to 100% maximum volume
 * so the fire alarm siren is as loud as the hardware and streaming emulator allow.
 */
fun forceMaxSystemAlarmVolume(context: Context) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_RING
        )
        for (stream in streams) {
            try {
                val maxVol = audioManager.getStreamMaxVolume(stream)
                if (maxVol > 0) {
                    audioManager.setStreamVolume(stream, maxVol, 0)
                }
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}
}

@Composable
fun FireEmergencyPopupDialog(
    nearestAlarmNode: SmartFireNode,
    totalAlarmCount: Int,
    distanceMeters: Float,
    soundEnabled: Boolean,
    alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATE,
    soundType: AlarmSoundType = AlarmSoundType.FIRE_SIREN,
    volumePercent: Int = 85,
    onRouteToExtinguisher: () -> Unit,
    onRouteToNearestFireSensor: () -> Unit,
    onRouteTwoStageExtinguisherToFire: () -> Unit = onRouteToExtinguisher,
    onRouteToExit: () -> Unit = onRouteToExtinguisher,
    onSilenceAllAlarms: () -> Unit,
    onDisarmAllAlarms: () -> Unit = onSilenceAllAlarms,
    allAlarmNodes: List<SmartFireNode> = listOf(nearestAlarmNode),
    isOfflineBurned: Boolean = false
) {
    val context = LocalContext.current
    var manualBoostCount by remember { mutableIntStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "emergency_popup_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "screen_flash_alpha"
    )

    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "compact_icon_scale"
    )

    LaunchedEffect(
        soundEnabled,
        isOfflineBurned,
        alertMode,
        soundType,
        volumePercent,
        nearestAlarmNode.deviceId,
        manualBoostCount
    ) {
        if (!soundEnabled || isOfflineBurned || alertMode == AlertMode.SILENT) return@LaunchedEffect
        AlarmAudioPlayer.runAlertLoop(
            context = context,
            alertMode = alertMode,
            soundType = soundType,
            volumePercent = volumePercent,
            isLevel1Warning = false
        )
    }

    Dialog(
        onDismissRequest = { /* Keep open until user selects a route or silences/disarms */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF991B1B).copy(alpha = flashAlpha))
                .border(6.dp, Color.White.copy(alpha = flashAlpha))
                .padding(16.dp)
                .testTag("fire_emergency_popup"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(3.dp, CriticalAlarmRed, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Header Row: Compact Pulsing Icon + Bold Title + Speaker Boost IconButton
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
                                    .size(44.dp)
                                    .scale(iconScale)
                                    .clip(CircleShape)
                                    .background(CriticalAlarmRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Báo cháy khẩn cấp",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "BÁO CHÁY KHẨN CẤP",
                                color = CriticalAlarmRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                forceMaxSystemAlarmVolume(context)
                                manualBoostCount++
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2))
                                .testTag("btn_boost_alarm_volume")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Tăng âm lượng còi",
                                tint = CriticalAlarmRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Location Line + Distance Pill + Offline Burned Pill + Multi-room Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = nearestAlarmNode.displayName,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            val distText = if (distanceMeters < 10f) {
                                String.format(Locale.US, "~%.1fm", distanceMeters)
                            } else {
                                "~${distanceMeters.roundToInt()}m"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFEF2F2))
                                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = distText,
                                    color = CriticalAlarmRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isOfflineBurned) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFFFFBEB))
                                        .border(1.dp, WarningAmber, RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Mất kết nối khi cháy",
                                        color = Color(0xFFB45309),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (allAlarmNodes.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(allAlarmNodes, key = { it.deviceId }) { node ->
                                    val isNearest = node.deviceId == nearestAlarmNode.deviceId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isNearest) CriticalAlarmRed else Color(0xFFFEE2E2))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = node.displayName,
                                            color = if (isNearest) Color.White else CriticalAlarmRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 3-Column Compact Telemetry Box: Nhiệt (°C) | Khói (ADC) | Lửa (CÓ / KHÔNG)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryCell(
                            value = nearestAlarmNode.telemetry.temperatureCelsius?.let {
                                String.format(Locale.US, "%.1f°C", it)
                            } ?: "N/A",
                            label = "Nhiệt (°C)",
                            valueColor = CriticalAlarmRed,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(Color(0xFFFECACA))
                        )
                        TelemetryCell(
                            value = "${nearestAlarmNode.telemetry.gasRaw}",
                            label = "Khói (ADC)",
                            valueColor = CriticalAlarmRed,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(Color(0xFFFECACA))
                        )
                        TelemetryCell(
                            value = if (nearestAlarmNode.telemetry.flame) "CÓ" else "KHÔNG",
                            label = "Lửa (CÓ / KHÔNG)",
                            valueColor = if (nearestAlarmNode.telemetry.flame) CriticalAlarmRed else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 4. 2x2 Clean Grid of Navigation Buttons (concise 1-line labels)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EmergencyRouteButton(
                                label = "Thoát Hiểm",
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                containerColor = Color(0xFF0F766E),
                                onClick = onRouteToExit,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_popup_route_exit")
                            )
                            EmergencyRouteButton(
                                label = "Bình CC ➔ Lửa",
                                icon = Icons.Default.FireExtinguisher,
                                containerColor = Color(0xFF7C3AED),
                                onClick = onRouteTwoStageExtinguisherToFire,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_popup_route_two_stage")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EmergencyRouteButton(
                                label = "Tìm Bình CC",
                                icon = Icons.Default.FireExtinguisher,
                                containerColor = PrimaryBlue,
                                onClick = onRouteToExtinguisher,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_popup_route_extinguisher")
                            )
                            EmergencyRouteButton(
                                label = "Tới Điểm Cháy",
                                icon = Icons.Default.LocalFireDepartment,
                                containerColor = Color(0xFFEA580C),
                                onClick = onRouteToNearestFireSensor,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_popup_route_fire_sensor")
                            )
                        }
                    }

                    // 5. Bottom Row: Im 30s (Red) | Tắt Hẳn (Dark Slate)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSilenceAllAlarms,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_popup_silence_alarm"),
                            colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (totalAlarmCount > 1) "Im 30s ($totalAlarmCount)" else "Im 30s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = onDisarmAllAlarms,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_popup_disarm_alarm"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tắt Hẳn",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(
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
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun EmergencyRouteButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
