package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.EspDeviceConfig
import com.example.domain.model.SmartFireNode
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reusable Card for editing ESP32 parameters, saving locally (SAVE button),
 * pushing runtime config over Wi-Fi (PUSH button), and uploading ESP32 firmware .bin over Wi-Fi (OTA PUSH).
 */
@Composable
fun EspWifiConfigAndOtaCard(
    repository: SmartFireRepository,
    targetNode: SmartFireNode?, // null = push config to all nodes, OTA to first node
    allNodes: List<SmartFireNode>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedCfg by repository.savedEspConfig.collectAsState()

    var mq2EnabledDraft by remember(savedCfg.mq2Enabled, targetNode?.telemetry?.mq2Enabled) {
        mutableStateOf(targetNode?.telemetry?.mq2Enabled ?: savedCfg.mq2Enabled)
    }
    var gasWarnDraft by remember(savedCfg.gasWarn, targetNode?.telemetry?.gasWarnThreshold) {
        mutableIntStateOf(targetNode?.telemetry?.gasWarnThreshold?.takeIf { it in 500..3500 } ?: savedCfg.gasWarn)
    }
    var gasOnDraft by remember(savedCfg.gasOn, targetNode?.telemetry?.gasOnThreshold) {
        mutableIntStateOf(targetNode?.telemetry?.gasOnThreshold?.takeIf { it in 800..3900 } ?: savedCfg.gasOn)
    }
    var gasOffDraft by remember(savedCfg.gasOff, targetNode?.telemetry?.gasOffThreshold) {
        mutableIntStateOf(targetNode?.telemetry?.gasOffThreshold?.takeIf { it in 400..3400 } ?: savedCfg.gasOff)
    }

    var tempEnabledDraft by remember(savedCfg.tempEnabled, targetNode?.telemetry?.tempEnabled) {
        mutableStateOf(targetNode?.telemetry?.tempEnabled ?: savedCfg.tempEnabled)
    }
    var tempWarnDraft by remember(savedCfg.tempWarnC, targetNode?.telemetry?.tempWarnC) {
        mutableFloatStateOf(targetNode?.telemetry?.tempWarnC?.takeIf { it in 30f..85f } ?: savedCfg.tempWarnC)
    }
    var tempAlarmDraft by remember(savedCfg.tempAlarmC, targetNode?.telemetry?.tempAlarmC) {
        mutableFloatStateOf(targetNode?.telemetry?.tempAlarmC?.takeIf { it in 40f..110f } ?: savedCfg.tempAlarmC)
    }

    var flameEnabledDraft by remember(savedCfg.flameEnabled, targetNode?.telemetry?.flameEnabled) {
        mutableStateOf(targetNode?.telemetry?.flameEnabled ?: savedCfg.flameEnabled)
    }
    var flameWarnMsDraft by remember(savedCfg.flameWarnMs, targetNode?.telemetry?.flameWarnMs) {
        mutableIntStateOf(targetNode?.telemetry?.flameWarnMs?.takeIf { it in 0..2500 } ?: savedCfg.flameWarnMs)
    }
    var flameAlarmMsDraft by remember(savedCfg.flameAlarmMs, targetNode?.telemetry?.flameAlarmMs) {
        mutableIntStateOf(targetNode?.telemetry?.flameAlarmMs?.takeIf { it in 100..5000 } ?: savedCfg.flameAlarmMs)
    }

    var txPowerDraft by remember(savedCfg.txPower1m, targetNode?.telemetry?.txPower1m) {
        mutableIntStateOf(targetNode?.telemetry?.txPower1m ?: savedCfg.txPower1m)
    }
    var pathLossNDraft by remember(savedCfg.pathLossN, targetNode?.telemetry?.pathLossN) {
        mutableFloatStateOf(targetNode?.telemetry?.pathLossN ?: savedCfg.pathLossN)
    }
    var hornEnabledDraft by remember(savedCfg.hornEnabled, targetNode?.telemetry?.hornEnabled) {
        mutableStateOf(targetNode?.telemetry?.hornEnabled ?: savedCfg.hornEnabled)
    }
    var gasCommissionedDraft by remember(savedCfg.gasCommissioned, targetNode?.telemetry?.gasCommissioned) {
        mutableStateOf(targetNode?.telemetry?.gasCommissioned ?: savedCfg.gasCommissioned)
    }

    var isPushingConfig by remember { mutableStateOf(false) }
    var isPushingOta by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    var selectedFirmwareUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFirmwareName by remember { mutableStateOf<String?>(null) }

    val otaNode = targetNode ?: allNodes.firstOrNull { !it.isDemoSensor } ?: allNodes.firstOrNull()

    val firmwarePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedFirmwareUri = uri
            var displayName = "firmware.bin"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: "firmware.bin"
                    }
                }
            } catch (_: Exception) {}
            selectedFirmwareName = displayName
            statusMessage = "Đã chọn file firmware: $displayName. Nhấn 'Push OTA' để nạp qua Wi-Fi."
            statusIsError = false
        }
    }

    fun currentDraftConfig(): EspDeviceConfig {
        val safeGasOn = gasOnDraft.coerceIn(800, 3900)
        val safeGasWarn = gasWarnDraft.coerceIn(500, (safeGasOn - 150).coerceAtLeast(500))
        val safeGasOff = gasOffDraft.coerceIn(400, (safeGasWarn - 100).coerceAtLeast(400))
        val safeTempAlarm = tempAlarmDraft.coerceIn(35f, 120f)
        val safeTempWarn = tempWarnDraft.coerceIn(25f, (safeTempAlarm - 4f).coerceAtLeast(25f))
        val safeFlameAlarm = flameAlarmMsDraft.coerceIn(100, 5000)
        val safeFlameWarn = flameWarnMsDraft.coerceIn(0, safeFlameAlarm)
        return EspDeviceConfig(
            mq2Enabled = mq2EnabledDraft,
            gasWarn = safeGasWarn,
            gasOn = safeGasOn,
            gasOff = safeGasOff,
            tempEnabled = tempEnabledDraft,
            tempWarnC = safeTempWarn,
            tempAlarmC = safeTempAlarm,
            flameEnabled = flameEnabledDraft,
            flameWarnMs = safeFlameWarn,
            flameAlarmMs = safeFlameAlarm,
            hornEnabled = hornEnabledDraft,
            gasCommissioned = gasCommissionedDraft,
            txPower1m = txPowerDraft,
            pathLossN = (pathLossNDraft * 10f).roundToInt() / 10f
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    Icon(Icons.Default.Memory, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (targetNode != null) "Cấu Hình & OTA Wi-Fi (${targetNode.displayName})" else "Cấu Hình & Cập Nhật ESP32 Qua Wi-Fi",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Lưu cấu hình trên app (Save) & Đẩy cấu hình / Firmware .bin lên ESP32 (Push)",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val warnOrange = Color(0xFFD97706)

            // === KHỐI 1: CẢM BIẾN KHÍ / KHÓI MQ-2 (Bật/Bỏ qua + Mức 1 Cảnh báo & Mức 2 Báo cháy) ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mq2EnabledDraft) "1. Cảm biến Khí / Khói (MQ-2)" else "1. Cảm biến MQ-2 (ĐANG BỎ QUA)",
                            color = if (mq2EnabledDraft) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mq2EnabledDraft) "Mức 1 Cảnh báo: $gasWarnDraft · Mức 2 Báo cháy: $gasOnDraft ADC" else "Đã tắt MQ-2: không kích hoạt cảnh báo/cháy từ khí gas",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = mq2EnabledDraft,
                        onCheckedChange = { mq2EnabledDraft = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                        modifier = Modifier.testTag("switch_enable_mq2")
                    )
                }

                // Mức 1 — Cảnh báo MQ-2
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 1 · Cảnh báo (gas_warn):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("$gasWarnDraft ADC", color = warnOrange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = gasWarnDraft.toFloat().coerceIn(600f, 3500f),
                    onValueChange = {
                        gasWarnDraft = it.roundToInt()
                        if (gasOnDraft < gasWarnDraft + 150) {
                            gasOnDraft = (gasWarnDraft + 150).coerceAtMost(3900)
                        }
                        if (gasOffDraft >= gasWarnDraft - 100) {
                            gasOffDraft = (gasWarnDraft - 150).coerceAtLeast(400)
                        }
                    },
                    enabled = mq2EnabledDraft,
                    valueRange = 600f..3500f,
                    colors = SliderDefaults.colors(thumbColor = warnOrange, activeTrackColor = warnOrange),
                    modifier = Modifier.testTag("slider_gas_warn")
                )

                // Mức 2 — Báo cháy MQ-2
                val minGasOn = (gasWarnDraft + 150).coerceIn(800, 3800).toFloat()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 2 · Báo cháy (gas_on):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("$gasOnDraft ADC", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = gasOnDraft.toFloat().coerceIn(minGasOn, 3900f),
                    onValueChange = { gasOnDraft = it.roundToInt() },
                    enabled = mq2EnabledDraft,
                    valueRange = minGasOn..3900f,
                    colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                    modifier = Modifier.testTag("slider_gas_on")
                )

                // Ngưỡng hồi phục an toàn
                val maxGasOff = (gasWarnDraft - 100).coerceIn(500, 3400).toFloat()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hồi phục an toàn (gas_off):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("$gasOffDraft ADC", color = NormalGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = gasOffDraft.toFloat().coerceIn(400f, maxGasOff),
                    onValueChange = { gasOffDraft = it.roundToInt() },
                    enabled = mq2EnabledDraft,
                    valueRange = 400f..maxGasOff,
                    colors = SliderDefaults.colors(thumbColor = NormalGreen, activeTrackColor = NormalGreen),
                    modifier = Modifier.testTag("slider_gas_off")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === KHỐI 2: CẢM BIẾN NHIỆT ĐỘ (Bật/Bỏ qua + Mức 1 Cảnh báo & Mức 2 Báo cháy) ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (tempEnabledDraft) "2. Cảm biến Nhiệt Độ (°C)" else "2. Cảm biến Nhiệt Độ (ĐANG BỎ QUA)",
                            color = if (tempEnabledDraft) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (tempEnabledDraft) "Mức 1 Cảnh báo: ${tempWarnDraft.roundToInt()}°C · Mức 2 Báo cháy: ${tempAlarmDraft.roundToInt()}°C" else "Đã tắt Nhiệt độ: bỏ qua dữ liệu nhiệt khi đánh giá cháy",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = tempEnabledDraft,
                        onCheckedChange = { tempEnabledDraft = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                        modifier = Modifier.testTag("switch_enable_temp")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 1 · Cảnh báo nhiệt (temp_warn_c):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${tempWarnDraft.roundToInt()}°C", color = warnOrange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = tempWarnDraft.coerceIn(30f, 85f),
                    onValueChange = {
                        tempWarnDraft = it.roundToInt().toFloat()
                        if (tempAlarmDraft < tempWarnDraft + 5f) {
                            tempAlarmDraft = (tempWarnDraft + 5f).coerceAtMost(110f)
                        }
                    },
                    enabled = tempEnabledDraft,
                    valueRange = 30f..85f,
                    colors = SliderDefaults.colors(thumbColor = warnOrange, activeTrackColor = warnOrange),
                    modifier = Modifier.testTag("slider_temp_warn")
                )

                val minTempAlarm = (tempWarnDraft + 5f).coerceIn(40f, 105f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 2 · Báo cháy nhiệt (temp_alarm_c):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${tempAlarmDraft.roundToInt()}°C", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = tempAlarmDraft.coerceIn(minTempAlarm, 110f),
                    onValueChange = { tempAlarmDraft = it.roundToInt().toFloat() },
                    enabled = tempEnabledDraft,
                    valueRange = minTempAlarm..110f,
                    colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                    modifier = Modifier.testTag("slider_temp_alarm")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // === KHỐI 3: CẢM BIẾN LỬA / CHÁY FLAME IR (Bật/Bỏ qua + Mức 1 Cảnh báo & Mức 2 Báo cháy) ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (flameEnabledDraft) "3. Cảm biến Lửa / Cháy (Flame IR)" else "3. Cảm biến Lửa (ĐANG BỎ QUA)",
                            color = if (flameEnabledDraft) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (flameEnabledDraft) "Mức 1 Cảnh báo: ${flameWarnMsDraft}ms · Mức 2 Báo cháy: ${flameAlarmMsDraft}ms" else "Đã tắt cảm biến Lửa: bỏ qua tín hiệu tia lửa / IR",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = flameEnabledDraft,
                        onCheckedChange = { flameEnabledDraft = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                        modifier = Modifier.testTag("switch_enable_flame")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 1 · Cảnh báo tia lửa (flame_warn_ms):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${flameWarnMsDraft} ms", color = warnOrange, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = flameWarnMsDraft.toFloat().coerceIn(0f, 2500f),
                    onValueChange = {
                        flameWarnMsDraft = ((it / 50f).roundToInt() * 50).coerceIn(0, 2500)
                        if (flameAlarmMsDraft < flameWarnMsDraft) {
                            flameAlarmMsDraft = flameWarnMsDraft.coerceIn(100, 5000)
                        }
                    },
                    enabled = flameEnabledDraft,
                    valueRange = 0f..2500f,
                    colors = SliderDefaults.colors(thumbColor = warnOrange, activeTrackColor = warnOrange),
                    modifier = Modifier.testTag("slider_flame_warn_ms")
                )

                val minFlameAlarm = maxOf(100, flameWarnMsDraft).coerceAtMost(4900).toFloat()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mức 2 · Báo cháy ngọn lửa (flame_alarm_ms):", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${flameAlarmMsDraft} ms", color = CriticalAlarmRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = flameAlarmMsDraft.toFloat().coerceIn(minFlameAlarm, 5000f),
                    onValueChange = {
                        flameAlarmMsDraft = ((it / 50f).roundToInt() * 50).coerceIn(minFlameAlarm.toInt(), 5000)
                    },
                    enabled = flameEnabledDraft,
                    valueRange = minFlameAlarm..5000f,
                    colors = SliderDefaults.colors(thumbColor = CriticalAlarmRed, activeTrackColor = CriticalAlarmRed),
                    modifier = Modifier.testTag("slider_flame_alarm_ms")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. RSSI Distance Calibration (TxPower at 1m & Path-Loss Exponent n)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hiệu chuẩn RSSI tại 1m (tx_power_1m):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("$txPowerDraft dBm", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = txPowerDraft.toFloat(),
                onValueChange = { txPowerDraft = it.roundToInt() },
                valueRange = -65f..-30f,
                colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hệ số suy hao tường nhà (path_loss_n):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(String.format(Locale.US, "%.1f", pathLossNDraft), color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Slider(
                value = pathLossNDraft,
                onValueChange = { pathLossNDraft = (it * 10f).roundToInt() / 10f },
                valueRange = 1.8f..4.0f,
                colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
            )

            // 4. Hardware switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bật còi 12V vật lý (horn_enabled)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = hornEnabledDraft,
                    onCheckedChange = { hornEnabledDraft = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = CriticalAlarmRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Đã hiệu chuẩn MQ-2 (gas_commissioned)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = gasCommissionedDraft,
                    onCheckedChange = { gasCommissionedDraft = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = NormalGreen)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SAVE & PUSH BUTTONS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val saved = repository.saveEspConfigLocally(currentDraftConfig())
                        statusMessage = "Đã lưu cấu hình 2 mức (MQ-2=${if (saved.mq2Enabled) "${saved.gasWarn}/${saved.gasOn}" else "TẮT"}, Nhiệt=${if (saved.tempEnabled) "${saved.tempWarnC.toInt()}/${saved.tempAlarmC.toInt()}°C" else "TẮT"}, Lửa=${if (saved.flameEnabled) "${saved.flameWarnMs}/${saved.flameAlarmMs}ms" else "TẮT"})."
                        statusIsError = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_save_esp_config"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("LƯU (SAVE)", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = {
                        if (isPushingConfig) return@Button
                        isPushingConfig = true
                        statusMessage = "Đang đẩy cấu hình lên ESP32 qua Wi-Fi..."
                        statusIsError = false
                        scope.launch {
                            val cfg = currentDraftConfig()
                            val result = if (targetNode != null) {
                                repository.pushEspConfigToNode(targetNode.deviceId, cfg)
                            } else {
                                repository.pushEspConfigToAllNodes(cfg)
                            }
                            isPushingConfig = false
                            if (result.isSuccess) {
                                statusMessage = result.getOrNull()
                                statusIsError = false
                            } else {
                                statusMessage = result.exceptionOrNull()?.message ?: "Push cấu hình thất bại"
                                statusIsError = true
                            }
                        }
                    },
                    enabled = !isPushingConfig,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(44.dp)
                        .testTag("btn_push_esp_config"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isPushingConfig) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("ĐẨY LÊN ESP (PUSH)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // OTA FIRMWARE UPDATE OVER WI-FI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cập Nhật Code ESP32 Qua Wi-Fi (OTA .bin)",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedFirmwareName != null) {
                        "File đã chọn: $selectedFirmwareName · Đích: ${otaNode?.displayName ?: "Chưa có node"}"
                    } else {
                        "Chọn file firmware.bin đã build từ PlatformIO để nạp không dây vào ESP32 (${otaNode?.ipAddress ?: "Chưa có IP"})"
                    },
                    color = TextSecondary,
                    fontSize = 10.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            firmwarePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_pick_esp_ota_bin"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Chọn file .bin", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Button(
                        onClick = {
                            val uri = selectedFirmwareUri
                            val node = otaNode
                            if (uri == null) {
                                statusMessage = "Vui lòng chọn file firmware.bin trước khi Push OTA."
                                statusIsError = true
                                return@Button
                            }
                            if (node == null) {
                                statusMessage = "Chưa có thiết bị ESP32 nào để nạp OTA."
                                statusIsError = true
                                return@Button
                            }
                            isPushingOta = true
                            statusMessage = "Đang nạp OTA firmware qua Wi-Fi tới ${node.ipAddress}..."
                            statusIsError = false
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (bytes == null) {
                                    isPushingOta = false
                                    statusMessage = "Không thể đọc file firmware.bin từ bộ nhớ điện thoại."
                                    statusIsError = true
                                    return@launch
                                }
                                val res = repository.pushOtaFirmwareToNode(
                                    deviceId = node.deviceId,
                                    firmwareBytes = bytes,
                                    fileName = selectedFirmwareName ?: "firmware.bin"
                                )
                                isPushingOta = false
                                if (res.isSuccess) {
                                    statusMessage = res.getOrNull()
                                    statusIsError = false
                                } else {
                                    statusMessage = res.exceptionOrNull()?.message ?: "Nạp OTA thất bại"
                                    statusIsError = true
                                }
                            }
                        },
                        enabled = !isPushingOta && selectedFirmwareUri != null,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(38.dp)
                            .testTag("btn_push_esp_ota"),
                        colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isPushingOta) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PUSH OTA", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (statusIsError) Color(0xFFFEF2F2) else Color(0xFFECFDF5))
                        .border(
                            1.dp,
                            if (statusIsError) CriticalAlarmRed else NormalGreen,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = msg,
                        color = if (statusIsError) CriticalAlarmRed else Color(0xFF047857),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
