package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.example.domain.model.SmartFireNode
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.sin

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

/**
 * Generates a full-scale (±32767) high-penetration square/harmonic piezo fire siren buffer
 * alternating between 1050 Hz and 1650 Hz with a 2900 Hz resonant overtone for maximum loudness.
 */
private fun buildHighPowerSirenPcmBuffer(sampleRate: Int = 22050): ShortArray {
    val totalSamples = sampleRate // 1.0 second full cycle (0.5s high-pierce + 0.5s low-pierce)
    val buffer = ShortArray(totalSamples)
    val twoPi = 2.0 * Math.PI

    for (i in 0 until totalSamples) {
        val t = i.toDouble() / sampleRate.toDouble()
        // Alternate frequency rapidly (3 sweeps per second) for urgent fire-alarm piezo effect
        val cyclePhase = (t * 3.0) % 1.0
        val baseFreq = if (cyclePhase < 0.5) 1580.0 else 1080.0
        val overtoneFreq = baseFreq * 2.0

        val s1 = sin(twoPi * baseFreq * t)
        val s2 = sin(twoPi * overtoneFreq * t)
        // Clipped square-wave synthesis for maximum RMS acoustic power (+3 dB over sine wave)
        val rawWave = if (s1 >= 0.0) 0.78 else -0.78
        val harmonic = if (s2 >= 0.0) 0.22 else -0.22
        val combined = (rawWave + harmonic).coerceIn(-1.0, 1.0)
        buffer[i] = (combined * Short.MAX_VALUE).toInt().toShort()
    }
    return buffer
}

@Composable
fun FireEmergencyPopupDialog(
    nearestAlarmNode: SmartFireNode,
    totalAlarmCount: Int,
    distanceMeters: Float,
    soundEnabled: Boolean,
    onRouteToExtinguisher: () -> Unit,
    onRouteToNearestFireSensor: () -> Unit,
    onSilenceAllAlarms: () -> Unit
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

    // Ultra-Loud Continuous Fire Siren (Full-scale PCM AudioTrack + +20dB LoudnessEnhancer + Dual ToneGenerator + Vibrator)
    // Stops immediately when Popup is closed (by pressing Extinguisher, Fire Sensor, or Silence Alarm)
    LaunchedEffect(soundEnabled, nearestAlarmNode.deviceId, manualBoostCount) {
        if (!soundEnabled) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            var audioTrack: AudioTrack? = null
            var loudnessEnhancer: LoudnessEnhancer? = null
            var alarmToneGen: ToneGenerator? = null
            var mediaToneGen: ToneGenerator? = null

            try {
                // 1. Force all system audio streams (MUSIC + ALARM + RING) to 100% Max Volume
                forceMaxSystemAlarmVolume(context)

                // 2. Build full-scale 16-bit PCM piezo siren on USAGE_MEDIA (for full WebRTC & speaker output)
                val sampleRate = 22050
                val pcmData = buildHighPowerSirenPcmBuffer(sampleRate)
                val byteCount = pcmData.size * 2

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteCount)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcmData, 0, pcmData.size)
                audioTrack.setLoopPoints(0, pcmData.size, -1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioTrack.setVolume(AudioTrack.getMaxVolume())
                }

                // Attach hardware/DSP LoudnessEnhancer (+2000 mB = +20 dB boost)
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioTrack.audioSessionId).apply {
                        setTargetGain(2200)
                        enabled = true
                    }
                } catch (_: Throwable) {}

                audioTrack.play()

                // 3. Also layer dual ToneGenerators on STREAM_ALARM and STREAM_MUSIC at MAX_VOLUME (100)
                try {
                    alarmToneGen = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
                } catch (_: Throwable) {}
                try {
                    mediaToneGen = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
                } catch (_: Throwable) {}

                while (isActive) {
                    forceMaxSystemAlarmVolume(context)
                    try {
                        alarmToneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                        mediaToneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    } catch (_: Throwable) {}

                    try {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                            vm?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(320, 255))
                        }
                    } catch (_: Throwable) {}

                    delay(500L)
                }
            } catch (_: Throwable) {
                // Ignore audio hardware init errors on headless environments
            } finally {
                try {
                    loudnessEnhancer?.enabled = false
                    loudnessEnhancer?.release()
                } catch (_: Throwable) {}
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Throwable) {}
                try {
                    alarmToneGen?.stopTone()
                    alarmToneGen?.release()
                } catch (_: Throwable) {}
                try {
                    mediaToneGen?.stopTone()
                    mediaToneGen?.release()
                } catch (_: Throwable) {}
            }
        }
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
                                    text = String.format(Locale.US, "%.1f°C", nearestAlarmNode.telemetry.temperatureCelsius),
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

                    // HUGE BUTTON 1: DẪN TỚI BÌNH CHỮA CHÁY (Stops siren & routes to extinguisher)
                    Button(
                        onClick = onRouteToExtinguisher,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("btn_popup_route_extinguisher"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.FireExtinguisher,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "DẪN TỚI BÌNH CHỮA CHÁY",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Ngừng hú màn hình & chỉ đường lấy bình chữa cháy",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HUGE BUTTON 2: DẪN TỚI BÁO CHÁY ĐANG CHÁY GẦN NHẤT
                    Button(
                        onClick = onRouteToNearestFireSensor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btn_popup_route_fire_sensor"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // HUGE BUTTON 3: TẮT BÁO CHÁY NGAY
                    Button(
                        onClick = onSilenceAllAlarms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_popup_silence_alarm"),
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (totalAlarmCount > 1) "TẮT TẤT CẢ BÁO CHÁY ($totalAlarmCount)" else "TẮT BÁO CHÁY NGAY",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
