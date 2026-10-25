package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

data class ChartPoint(
    val timestamp: Long,
    val value: Int
)

@Composable
fun SensorChart(
    points: List<ChartPoint>,
    thresholdValue: Int = 1800,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val dataPoints = if (points.size < 2) {
        val now = System.currentTimeMillis()
        listOf(
            ChartPoint(now - 30000, 950),
            ChartPoint(now - 20000, 980),
            ChartPoint(now - 10000, 1020),
            ChartPoint(now, 1000)
        )
    } else points

    val values = dataPoints.map { it.value }
    val minValue = maxOf(0, (values.minOrNull() ?: 0) - 200)
    val maxValue = maxOf(thresholdValue + 400, (values.maxOrNull() ?: 1200) + 200)
    val currentValue = dataPoints.last().value

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = LightCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Chart Header with Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Diễn Biến Nồng Độ Khí MQ-2",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Đơn vị: ADC Raw (0 .. 4095) · Tự động làm mới",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (currentValue >= thresholdValue) CriticalAlarmRed else NormalGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$currentValue ADC",
                        color = if (currentValue >= thresholdValue) CriticalAlarmRed else PrimaryBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge("Hiện tại", "$currentValue", if (currentValue > thresholdValue) CriticalAlarmRed else PrimaryBlue)
                StatBadge("Thấp nhất", "${values.minOrNull() ?: 0}", NormalGreen)
                StatBadge("Cao nhất", "${values.maxOrNull() ?: 0}", WarningAmber)
                StatBadge("Ngưỡng báo động", "$thresholdValue", CriticalAlarmRed)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LightSurfaceVariant)
                    .pointerInput(dataPoints) {
                        detectTapGestures { tapOffset ->
                            val width = size.width
                            val paddingX = 40.dp.toPx()
                            val chartW = width - paddingX - 10.dp.toPx()
                            val relX = (tapOffset.x - paddingX).coerceIn(0f, chartW)
                            val fraction = relX / chartW
                            val index = ((fraction * (dataPoints.size - 1)).toInt()).coerceIn(0, dataPoints.size - 1)
                            selectedPointIndex = index
                        }
                    }
            ) {
                Canvas(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp).fillMaxWidth().height(140.dp)) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val paddingLeft = 40.dp.toPx()
                    val paddingRight = 10.dp.toPx()
                    val chartWidth = canvasWidth - paddingLeft - paddingRight
                    val chartHeight = canvasHeight - 20.dp.toPx()

                    val valueRange = maxOf(1, maxValue - minValue)

                    // Draw Horizontal Grid lines & Y-axis labels
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val yFraction = i.toFloat() / gridSteps
                        val y = chartHeight * (1f - yFraction) + 10.dp.toPx()
                        val gridVal = (minValue + (yFraction * valueRange)).toInt()

                        // Grid line
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(paddingLeft, y),
                            end = Offset(canvasWidth - paddingRight, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        // Y-axis Label
                        drawContext.canvas.nativeCanvas.drawText(
                            "$gridVal",
                            paddingLeft - 8.dp.toPx(),
                            y + 4.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.rgb(148, 163, 184)
                                textSize = 22f
                                textAlign = android.graphics.Paint.Align.RIGHT
                            }
                        )
                    }

                    // Draw Threshold Line (e.g. 1800 ADC Warning)
                    val threshFraction = ((thresholdValue - minValue).toFloat() / valueRange).coerceIn(0f, 1f)
                    val threshY = chartHeight * (1f - threshFraction) + 10.dp.toPx()
                    drawLine(
                        color = CriticalAlarmRed.copy(alpha = 0.5f),
                        start = Offset(paddingLeft, threshY),
                        end = Offset(canvasWidth - paddingRight, threshY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                    )

                    // Calculate point offsets
                    val offsets = dataPoints.mapIndexed { idx, pt ->
                        val x = paddingLeft + (idx.toFloat() / (dataPoints.size - 1)) * chartWidth
                        val yFraction = ((pt.value - minValue).toFloat() / valueRange).coerceIn(0f, 1f)
                        val y = chartHeight * (1f - yFraction) + 10.dp.toPx()
                        Offset(x, y)
                    }

                    // Build Curve Path & Fill Path
                    val linePath = Path()
                    val fillPath = Path()

                    if (offsets.isNotEmpty()) {
                        linePath.moveTo(offsets.first().x, offsets.first().y)
                        fillPath.moveTo(offsets.first().x, chartHeight + 10.dp.toPx())
                        fillPath.lineTo(offsets.first().x, offsets.first().y)

                        for (i in 1 until offsets.size) {
                            val prev = offsets[i - 1]
                            val curr = offsets[i]
                            val cx = (prev.x + curr.x) / 2
                            linePath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                            fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }

                        fillPath.lineTo(offsets.last().x, chartHeight + 10.dp.toPx())
                        fillPath.close()

                        // Draw Gradient Fill
                        val gradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                (if (currentValue >= thresholdValue) CriticalAlarmRed else PrimaryBlue).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = chartHeight + 10.dp.toPx()
                        )
                        drawPath(fillPath, brush = gradientBrush)

                        // Draw Line
                        drawPath(
                            path = linePath,
                            color = if (currentValue >= thresholdValue) CriticalAlarmRed else PrimaryBlue,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw points
                        offsets.forEachIndexed { i, offset ->
                            val ptVal = dataPoints[i].value
                            val ptColor = if (ptVal >= thresholdValue) CriticalAlarmRed else PrimaryBlue
                            drawCircle(Color.White, radius = 4.dp.toPx(), center = offset)
                            drawCircle(ptColor, radius = 2.5.dp.toPx(), center = offset)
                        }
                    }

                    // Draw Selected Tooltip if tapped
                    selectedPointIndex?.let { selIdx ->
                        if (selIdx in offsets.indices) {
                            val selOffset = offsets[selIdx]
                            val selPoint = dataPoints[selIdx]
                            drawLine(
                                color = PrimaryBlue,
                                start = Offset(selOffset.x, 10.dp.toPx()),
                                end = Offset(selOffset.x, chartHeight + 10.dp.toPx()),
                                strokeWidth = 2f
                            )
                            drawCircle(Color.White, radius = 6.dp.toPx(), center = selOffset)
                            drawCircle(PrimaryBlue, radius = 4.dp.toPx(), center = selOffset)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Selected value detail
            selectedPointIndex?.let { idx ->
                val p = dataPoints.getOrNull(idx)
                if (p != null) {
                    val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(p.timestamp))
                    Text(
                        text = "Điểm chọn lúc $timeStr: ${p.value} ADC",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            } ?: Text(
                text = "Mẹo: Chạm vào biểu đồ để kiểm tra số liệu tại từng mốc thời gian",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun StatBadge(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LightSurfaceVariant)
            .border(1.dp, LightBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Text(label, color = TextSecondary, fontSize = 9.sp)
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
