package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.FirmwareState
import com.example.domain.model.SmartFireNode
import com.example.ui.theme.CriticalAlarmBackground
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DemoGold
import com.example.ui.theme.DemoGoldBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.NormalGreenBackground
import com.example.ui.theme.NormalGreenBorder
import com.example.ui.theme.OfflineBackground
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBackground
import com.example.ui.theme.WarningBorder
import com.example.ui.theme.WarmingPurple
import com.example.ui.theme.WarmingPurpleLight

@Composable
fun FirmwareStateBadge(
    firmwareState: FirmwareState,
    connectionState: DeviceConnectionState,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, borderColor, textColor, icon) = when {
        connectionState == DeviceConnectionState.OFFLINE -> {
            if (firmwareState == FirmwareState.ALARM) {
                listOf("ALARM (MẤT KẾT NỐI)", CriticalAlarmBackground, CriticalAlarmBorder, CriticalAlarmRed, Icons.Default.Warning)
            } else {
                listOf("MẤT KẾT NỐI (OFFLINE)", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
            }
        }
        connectionState == DeviceConnectionState.STALE -> {
            listOf("CHẬM CẬP NHẬT", WarningBackground, WarningBorder, WarningAmber, Icons.Default.Info)
        }
        firmwareState == FirmwareState.ALARM -> {
            listOf("BÁO ĐỘNG CHÁY!", CriticalAlarmBackground, CriticalAlarmBorder, CriticalAlarmRed, Icons.Default.Warning)
        }
        firmwareState == FirmwareState.SUSPECTED -> {
            listOf("NGHI NGỜ NGUY HIỂM", WarningBackground, WarningBorder, WarningAmber, Icons.Default.Warning)
        }
        firmwareState == FirmwareState.WARMING -> {
            listOf("ĐANG SẤY NÓNG", WarmingPurpleLight, WarmingPurple.copy(alpha = 0.4f), WarmingPurple, Icons.Default.Sensors)
        }
        firmwareState == FirmwareState.UNCONFIGURED -> {
            listOf("CHƯA CẤU HÌNH", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
        firmwareState == FirmwareState.NORMAL -> {
            listOf("AN TOÀN BÌNH THƯỜNG", NormalGreenBackground, NormalGreenBorder, NormalGreen, Icons.Default.CheckCircle)
        }
        else -> {
            listOf("CHƯA XÁC MINH", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor as Color)
            .border(1.dp, borderColor as Color, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
            contentDescription = null,
            tint = textColor as Color,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label as String,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CriticalAlarmOverlay(
    alarmNodes: List<SmartFireNode>,
    onSilenceClick: (String) -> Unit,
    onAckClick: (String) -> Unit,
    onOpenMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (alarmNodes.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val borderAlpha by infiniteTransition.animateColor(
        initialValue = CriticalAlarmRed,
        targetValue = CriticalAlarmRed.copy(alpha = 0.3f),
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarm_pulse_border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("critical_alarm_banner")
            .border(2.dp, borderAlpha, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CriticalAlarmBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CriticalAlarmRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Cảnh báo cháy",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PHÁT HIỆN NGUY CƠ BÁO ĐỘNG CHÁY!",
                            color = CriticalAlarmRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cảm biến tại ${alarmNodes.size} vị trí đang phát tín hiệu",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Show alarm locations
            alarmNodes.forEach { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, CriticalAlarmBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = node.displayName,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: ${node.deviceId} · Khí MQ-2: ${node.telemetry.gasRaw} ADC ${if (node.telemetry.flame) "· LỬA: CÓ" else ""}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row {
                        OutlinedButton(
                            onClick = { onSilenceClick(node.deviceId) },
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_silence_${node.deviceId}"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(WarningAmber))
                        ) {
                            Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(14.dp), tint = WarningAmber)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Im 30s", fontSize = 11.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = { onAckClick(node.deviceId) },
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_ack_${node.deviceId}"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("ACK", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenMapClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_open_emergency_map"),
                colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MỞ BẢN ĐỒ THOÁT NẠN & TÌM BÌNH CHỮA CHÁY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DemoModeBanner(
    onDismissOrSwitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DemoGoldBackground)
            .border(1.dp, DemoGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(DemoGold)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CHẾ ĐỘ TRÌNH DIỄN STEM (DỮ LIỆU MÔ PHỎNG)",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Kịch bản >",
            color = PrimaryBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onDismissOrSwitch() }
        )
    }
}

@Composable
fun LiveAdcSparkline(
    rawValues: List<Int>,
    color: Color = PrimaryBlue,
    modifier: Modifier = Modifier
) {
    if (rawValues.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Đang nhận mẫu...", color = TextSecondary, fontSize = 10.sp)
        }
        return
    }

    Canvas(modifier = modifier) {
        val minVal = (rawValues.minOrNull() ?: 0).toFloat()
        val maxVal = maxOf(minVal + 50.0f, (rawValues.maxOrNull() ?: 100).toFloat())
        val range = maxVal - minVal

        val points = rawValues.mapIndexed { index, value ->
            val x = (index.toFloat() / (rawValues.size - 1)) * size.width
            val normY = (value - minVal) / range
            val y = size.height - (normY * size.height)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
