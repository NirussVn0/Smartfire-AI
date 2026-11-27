package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
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

private data class BadgeStyle(
    val label: String,
    val bgColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val icon: ImageVector
)

@Composable
fun FirmwareStateBadge(
    firmwareState: FirmwareState,
    connectionState: DeviceConnectionState,
    modifier: Modifier = Modifier,
    suspectedBurnedOffline: Boolean = false
) {
    val style = when {
        suspectedBurnedOffline || (connectionState == DeviceConnectionState.OFFLINE && firmwareState == FirmwareState.ALARM) -> {
            BadgeStyle("CHÁY · OFFLINE", CriticalAlarmBackground, CriticalAlarmBorder, CriticalAlarmRed, Icons.Default.Warning)
        }
        connectionState == DeviceConnectionState.OFFLINE -> {
            BadgeStyle("OFFLINE", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
        connectionState == DeviceConnectionState.STALE -> {
            BadgeStyle("CHẬM", WarningBackground, WarningBorder, WarningAmber, Icons.Default.Info)
        }
        firmwareState == FirmwareState.ALARM -> {
            BadgeStyle("BÁO CHÁY!", CriticalAlarmBackground, CriticalAlarmBorder, CriticalAlarmRed, Icons.Default.Warning)
        }
        firmwareState == FirmwareState.SUSPECTED -> {
            BadgeStyle("NGHI NGỜ", WarningBackground, WarningBorder, WarningAmber, Icons.Default.Warning)
        }
        firmwareState == FirmwareState.FAULT -> {
            BadgeStyle("LỖI MQ-2", WarningBackground, WarningBorder, WarningAmber, Icons.Default.Warning)
        }
        firmwareState == FirmwareState.DISARMED -> {
            BadgeStyle("ĐÃ TẮT HẲN", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
        firmwareState == FirmwareState.WARMING -> {
            BadgeStyle("SẤY NÓNG", WarmingPurpleLight, WarmingPurple.copy(alpha = 0.4f), WarmingPurple, Icons.Default.Sensors)
        }
        firmwareState == FirmwareState.UNCONFIGURED -> {
            BadgeStyle("CHƯA CẤU HÌNH", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
        firmwareState == FirmwareState.NORMAL -> {
            BadgeStyle("AN TOÀN", NormalGreenBackground, NormalGreenBorder, NormalGreen, Icons.Default.CheckCircle)
        }
        else -> {
            BadgeStyle("CHỜ", OfflineBackground, LightBorder, OfflineGray, Icons.Default.Info)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(style.bgColor)
            .border(1.dp, style.borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.textColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = style.label,
            color = style.textColor,
            fontSize = 10.sp,
            maxLines = 1,
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CriticalAlarmRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Cảnh báo cháy",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BÁO ĐỘNG CHÁY (${alarmNodes.size} VỊ TRÍ)",
                        color = CriticalAlarmRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Xác nhận hoặc mở bản đồ thoát nạn ngay",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            alarmNodes.forEach { node ->
                val isOffline = node.connectionState == DeviceConnectionState.OFFLINE
                val tempPart = node.telemetry.temperatureCelsius?.let {
                    String.format(Locale.US, " · %.1f°C", it)
                } ?: ""
                val flamePart = if (node.telemetry.flame) " · LỬA" else ""

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, CriticalAlarmBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = node.displayName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isOffline) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(WarningBackground)
                                        .border(1.dp, WarningBorder, RoundedCornerShape(50))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "OFFLINE",
                                        color = Color(0xFFB45309),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${node.telemetry.gasRaw} ADC$tempPart$flamePart",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onSilenceClick(node.deviceId) },
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_silence_${node.deviceId}"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                            border = BorderStroke(1.dp, WarningAmber)
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = WarningAmber
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Im 30s", fontSize = 11.sp, color = WarningAmber, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onAckClick(node.deviceId) },
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_ack_${node.deviceId}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("ACK", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOpenMapClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_open_emergency_map"),
                colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "MỞ BẢN ĐỒ THOÁT NẠN & BÌNH CHỮA CHÁY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
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
