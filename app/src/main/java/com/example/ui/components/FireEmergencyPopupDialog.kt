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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.TextSecondary
import java.util.Locale

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
    onDisarmAllAlarms: () -> Unit = onSilenceAllAlarms
) {
    val context = LocalContext.current
    var manualBoostCount by remember { mutableIntStateOf(0) }

    // Screen flashing & huge icon pulsing animations
    val infiniteTransition = rememberInfiniteTransition(label = "emergency_popup_flash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320),
            repeatMode = RepeatMode.Reverse
        ),
        label = "screen_flash_alpha"
    )

    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320),
            repeatMode = RepeatMode.Reverse
        ),
        label = "huge_icon_scale"
    )

    // Customizable Fire Siren / Beep / Ringtone + Vibration via AlarmAudioPlayer
    // Stops immediately when Popup is closed (by pressing Navigation or Silence Alarm)
    LaunchedEffect(soundEnabled, alertMode, soundType, volumePercent, nearestAlarmNode.deviceId, manualBoostCount) {
        if (!soundEnabled || alertMode == AlertMode.SILENT) return@LaunchedEffect
        AlarmAudioPlayer.runAlertLoop(
            context = context,
            alertMode = alertMode,
            soundType = soundType,
            volumePercent = volumePercent,
            isLevel1Warning = false
        )
    }

    Dialog(
        onDismissRequest = { /* Keep open until user chooses Extinguisher, Fire Sensor, or Silence */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        // Full-Screen Flashing Emergency Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF991B1B).copy(alpha = flashAlpha))
                .border(8.dp, Color.White.copy(alpha = flashAlpha))
                .padding(18.dp)
                .testTag("fire_emergency_popup"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .border(4.dp, CriticalAlarmRed, RoundedCornerShape(26.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // HUGE Pulsing Fire Siren Icon
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(iconScale)
                            .clip(CircleShape)
                            .background(CriticalAlarmRed)
                            .border(4.dp, Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Báo động cháy khẩn cấp",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Flashing Emergency Badge + Max Volume Boost Indicator
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CriticalAlarmRed)
                            .clickable {
                                forceMaxSystemAlarmVolume(context)
                                manualBoostCount++
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("btn_boost_alarm_volume")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CÒI HÚ CỰC ĐẠI 100% (+22dB BOOST) · CHẠM KÍCH LOA",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = nearestAlarmNode.displayName,
                        color = CriticalAlarmRed,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = String.format(
                            Locale.US,
                            "Điểm báo cháy gần bạn nhất (~%.1fm) · Tự động bật dẫn đường trên bản đồ",
                            distanceMeters
                        ),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Temperature & Gas Telemetry Box inside Popup
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Thermostat,
                                    contentDescription = null,
                                    tint = CriticalAlarmRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = nearestAlarmNode.telemetry.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                                    color = CriticalAlarmRed,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text("Nhiệt độ điểm cháy", color = TextSecondary, fontSize = 11.sp)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(Color(0xFFFECACA))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${nearestAlarmNode.telemetry.gasRaw} ADC",
                                color = CriticalAlarmRed,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("Nồng độ khói / gas", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // HUGE BUTTON 0: DẪN ĐƯỜNG 2 CHẶNG (LẤY BÌNH CC -> ĐÁM CHÁY)
                    Button(
                        onClick = onRouteTwoStageExtinguisherToFire,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btn_popup_route_two_stage"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.FireExtinguisher,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "LỘ TRÌNH 2 CHẶNG: LẤY BÌNH CC ➔ ĐÁM CHÁY",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tự động chỉ đường qua Bình CC gần nhất rồi tiến tới dập lửa",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HUGE BUTTON 1: DẪN TỚI BÌNH CHỮA CHÁY (Stops siren & routes to extinguisher)
                    Button(
                        onClick = onRouteToExtinguisher,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_popup_route_extinguisher"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.FireExtinguisher,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "CHỈ DẪN TỚI BÌNH CHỮA CHÁY",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Ngừng hú màn hình & chỉ đường lấy bình chữa cháy",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HUGE BUTTON 2: DẪN TỚI BÁO CHÁY ĐANG CHÁY GẦN NHẤT
                    Button(
                        onClick = onRouteToNearestFireSensor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_popup_route_fire_sensor"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "DẪN TỚI BÁO CHÁY ĐANG CHÁY GẦN NHẤT",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Đi thẳng tới vị trí ${nearestAlarmNode.displayName}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HUGE BUTTON 2B: DẪN TỚI CỬA THOÁT HIỂM (EXIT)
                    Button(
                        onClick = onRouteToExit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_popup_route_exit"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "DẪN TỚI CỬA THOÁT HIỂM (EXIT)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Tạm tắt còi máy này & hiện biển chỉ đường thoát nạn",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // TWO BUTTONS: TẮT TẠM 30s vs TẮT HẲN (Giữ nút ESP32 5s hoặc Bật Lại trên app)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSilenceAllAlarms,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_popup_silence_alarm"),
                            colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (totalAlarmCount > 1) "TẮT 30s ($totalAlarmCount)" else "TẮT TẠM 30s",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Button(
                            onClick = onDisarmAllAlarms,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_popup_disarm_alarm"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TẮT HẲN",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tắt hẳn sẽ tắt còi & app hoàn toàn · Giữ nút ESP32 5s hoặc bấm Bật Lại trên app để bật",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
