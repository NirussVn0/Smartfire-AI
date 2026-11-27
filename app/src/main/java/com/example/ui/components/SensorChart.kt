package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.SensorSnapshotPoint
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBackground
import com.example.ui.theme.WarningBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ChartPoint(
    val timestamp: Long,
    val value: Int
)

enum class MultiChartMode {
    ALL_THREE,
    GAS_ADC,
    TEMP_C,
    FLAME_IR,
    PROB_PCT
}

private val GasLineColor = Color(0xFF2563EB)
private val TempLineColor = Color(0xFFDC2626)
private val FlameLineColor = Color(0xFFF59E0B)
private val ProbLineColor = Color(0xFF7C3AED)

@Composable
fun SensorChart(
    points: List<ChartPoint>,
    thresholdValue: Int = 1800,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val dataPoints = if (points.size < 2) {
        val base = points.firstOrNull()?.value ?: 980
        val now = points.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        listOf(
            ChartPoint(now - 30000, base),
            ChartPoint(now - 20000, base),
            ChartPoint(now - 10000, base),
            ChartPoint(now, base)
        )
    } else points

    val values = dataPoints.map { it.value }
    val minValue = maxOf(0, (values.minOrNull() ?: 0) - 200)
    val maxValue = maxOf(thresholdValue + 400, (values.maxOrNull() ?: 1200) + 200)
    val currentValue = dataPoints.last().value

    val timeAxisFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val tooltipFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val density = LocalDensity.current
    val yLabelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.rgb(148, 163, 184)
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
    }
    val xLabelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.rgb(148, 163, 184)
            textSize = with(density) { 9.5f.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Diễn Biến Nồng Độ Khí MQ-2",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Đơn vị: ADC Raw (0 .. 4095) · Chạm hoặc kéo để xem",
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
                StatBadge("Hiện tại", "$currentValue", if (currentValue >= thresholdValue) CriticalAlarmRed else PrimaryBlue)
                StatBadge("Thấp nhất", "${values.minOrNull() ?: 0}", NormalGreen)
                StatBadge("Cao nhất", "${values.maxOrNull() ?: 0}", WarningAmber)
                StatBadge("Ngưỡng", "$thresholdValue", CriticalAlarmRed)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Canvas Chart (200.dp height) with floating tooltip
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .pointerInput(dataPoints) {
                        detectTapGestures { tapOffset ->
                            val paddingLeft = 42.dp.toPx()
                            val paddingRight = 12.dp.toPx()
                            val chartW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                            val relX = (tapOffset.x - paddingLeft).coerceIn(0f, chartW)
                            val idx = ((relX / chartW) * (dataPoints.size - 1)).roundToInt()
                                .coerceIn(0, dataPoints.size - 1)
                            selectedPointIndex = idx
                        }
                    }
                    .pointerInput(dataPoints) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val paddingLeft = 42.dp.toPx()
                            val paddingRight = 12.dp.toPx()
                            val chartW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                            val relX = (change.position.x - paddingLeft).coerceIn(0f, chartW)
                            val idx = ((relX / chartW) * (dataPoints.size - 1)).roundToInt()
                                .coerceIn(0, dataPoints.size - 1)
                            selectedPointIndex = idx
                        }
                    }
            ) {
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val paddingLeftPx = with(density) { 42.dp.toPx() }
                val paddingRightPx = with(density) { 12.dp.toPx() }
                val chartWidthPx = (containerWidthPx - paddingLeftPx - paddingRightPx).coerceAtLeast(1f)
                var tooltipWidthPx by remember { mutableStateOf(0) }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val paddingTop = 16.dp.toPx()
                    val paddingBottom = 22.dp.toPx()
                    val chartWidth = (canvasWidth - paddingLeftPx - paddingRightPx).coerceAtLeast(1f)
                    val chartHeight = (canvasHeight - paddingTop - paddingBottom).coerceAtLeast(1f)
                    val valueRange = maxOf(1, maxValue - minValue)

                    // Horizontal grid & Y-axis labels
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val yFraction = i.toFloat() / gridSteps
                        val y = paddingTop + chartHeight * (1f - yFraction)
                        val gridVal = (minValue + (yFraction * valueRange)).toInt()

                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(paddingLeftPx, y),
                            end = Offset(canvasWidth - paddingRightPx, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "$gridVal",
                            paddingLeftPx - 6.dp.toPx(),
                            y + 3.5.dp.toPx(),
                            yLabelPaint
                        )
                    }

                    // X-axis labels
                    val xLabelIndices = listOf(0, dataPoints.size / 2, dataPoints.size - 1).distinct()
                    xLabelIndices.forEach { idx ->
                        val x = paddingLeftPx + (idx.toFloat() / (dataPoints.size - 1)) * chartWidth
                        drawContext.canvas.nativeCanvas.drawText(
                            timeAxisFmt.format(Date(dataPoints[idx].timestamp)),
                            x,
                            canvasHeight - 6.dp.toPx(),
                            xLabelPaint
                        )
                    }

                    // Threshold Line
                    val threshFraction = ((thresholdValue - minValue).toFloat() / valueRange).coerceIn(0f, 1f)
                    val threshY = paddingTop + chartHeight * (1f - threshFraction)
                    drawLine(
                        color = CriticalAlarmRed.copy(alpha = 0.65f),
                        start = Offset(paddingLeftPx, threshY),
                        end = Offset(canvasWidth - paddingRightPx, threshY),
                        strokeWidth = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                    )

                    val offsets = dataPoints.mapIndexed { idx, pt ->
                        val x = paddingLeftPx + (idx.toFloat() / (dataPoints.size - 1)) * chartWidth
                        val yFraction = ((pt.value - minValue).toFloat() / valueRange).coerceIn(0f, 1f)
                        val y = paddingTop + chartHeight * (1f - yFraction)
                        Offset(x, y)
                    }

                    if (offsets.isNotEmpty()) {
                        val linePath = Path()
                        val fillPath = Path()
                        val bottomY = paddingTop + chartHeight

                        linePath.moveTo(offsets.first().x, offsets.first().y)
                        fillPath.moveTo(offsets.first().x, bottomY)
                        fillPath.lineTo(offsets.first().x, offsets.first().y)

                        for (i in 1 until offsets.size) {
                            val prev = offsets[i - 1]
                            val curr = offsets[i]
                            val cx = (prev.x + curr.x) / 2f
                            linePath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                            fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }

                        fillPath.lineTo(offsets.last().x, bottomY)
                        fillPath.close()

                        val mainColor = if (currentValue >= thresholdValue) CriticalAlarmRed else PrimaryBlue
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(mainColor.copy(alpha = 0.24f), Color.Transparent),
                                startY = paddingTop,
                                endY = bottomY
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = mainColor,
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        if (offsets.size <= 36) {
                            offsets.forEachIndexed { i, offset ->
                                val ptColor = if (dataPoints[i].value >= thresholdValue) CriticalAlarmRed else PrimaryBlue
                                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = offset)
                                drawCircle(ptColor, radius = 2.2.dp.toPx(), center = offset)
                            }
                        }
                    }

                    // Selected crosshair & marker
                    selectedPointIndex?.let { selIdx ->
                        if (selIdx in offsets.indices) {
                            val selOffset = offsets[selIdx]
                            val selColor = if (dataPoints[selIdx].value >= thresholdValue) CriticalAlarmRed else PrimaryBlue
                            drawLine(
                                color = Color(0xFF0F172A).copy(alpha = 0.45f),
                                start = Offset(selOffset.x, paddingTop),
                                end = Offset(selOffset.x, paddingTop + chartHeight),
                                strokeWidth = 1.4.dp.toPx()
                            )
                            drawCircle(selColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = selOffset)
                            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = selOffset)
                            drawCircle(selColor, radius = 3.dp.toPx(), center = selOffset)
                        }
                    }
                }

                // Floating Tooltip Bubble inside/above the top of the chart
                selectedPointIndex?.let { selIdx ->
                    val pt = dataPoints.getOrNull(selIdx)
                    if (pt != null) {
                        val selX = paddingLeftPx + (selIdx.toFloat() / (dataPoints.size - 1)) * chartWidthPx
                        val marginPx = with(density) { 8.dp.toPx() }
                        val clampedX = (selX - tooltipWidthPx / 2f)
                            .coerceIn(marginPx, (containerWidthPx - tooltipWidthPx - marginPx).coerceAtLeast(marginPx))

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(clampedX.roundToInt(), with(density) { 6.dp.roundToPx() }) }
                                .onSizeChanged { tooltipWidthPx = it.width }
                                .shadow(4.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.94f))
                                .clickable { selectedPointIndex = null }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${tooltipFmt.format(Date(pt.timestamp))} · ${pt.value} ADC",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiMetricSensorChart(
    snapshots: List<SensorSnapshotPoint>,
    currentGas: Int,
    currentTemp: Float?,
    currentFlamePct: Float,
    currentProb: Float,
    gasWarnThreshold: Int = 1800,
    gasAlarmThreshold: Int = 2400,
    tempWarnC: Float = 45f,
    tempAlarmC: Float = 60f,
    selectedMode: MultiChartMode = MultiChartMode.ALL_THREE,
    timeRangeLabel: String = "24h",
    isCachedOffline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val data = remember(snapshots, currentGas, currentTemp, currentFlamePct, currentProb) {
        when {
            snapshots.isEmpty() -> {
                val now = System.currentTimeMillis()
                val fallbackTemp = currentTemp ?: 28f
                listOf(
                    SensorSnapshotPoint(
                        deviceId = "",
                        timestamp = now - 60_000L,
                        gasRaw = currentGas,
                        temperatureCelsius = fallbackTemp,
                        fireProbabilityPercent = currentProb,
                        flamePercent = currentFlamePct
                    ),
                    SensorSnapshotPoint(
                        deviceId = "",
                        timestamp = now,
                        gasRaw = currentGas,
                        temperatureCelsius = fallbackTemp,
                        fireProbabilityPercent = currentProb,
                        flamePercent = currentFlamePct
                    )
                )
            }
            snapshots.size == 1 -> {
                val only = snapshots.first()
                listOf(only.copy(timestamp = only.timestamp - 30_000L), only)
            }
            else -> snapshots
        }
    }

    var selectedIndex by remember(data, selectedMode) { mutableStateOf<Int?>(null) }

    val timeAxisFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val tooltipFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val density = LocalDensity.current

    val yLabelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.rgb(100, 116, 139)
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
    }
    val xLabelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.rgb(100, 116, 139)
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val thresholdLabelPaint = remember(density) {
        Paint().apply {
            textSize = with(density) { 9.sp.toPx() }
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val chartTitle = when (selectedMode) {
        MultiChartMode.ALL_THREE -> "Diễn Biến Đa Thông Số"
        MultiChartMode.GAS_ADC -> "Khói & Khí MQ-2 (ADC)"
        MultiChartMode.TEMP_C -> "Nhiệt Độ Điểm Đo (°C)"
        MultiChartMode.FLAME_IR -> "Ngọn Lửa Hồng Ngoại IR (%)"
        MultiChartMode.PROB_PCT -> "Xác Suất Cháy Tổng Hợp (%)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = LightCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: Title + Time Range Pill + Offline Cached Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chartTitle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCachedOffline) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(WarningBackground)
                                .border(1.dp, WarningBorder, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Dữ liệu cũ (Offline)",
                                color = Color(0xFFB45309),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryBlueLight)
                            .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timeRangeLabel,
                            color = PrimaryBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend Chips right above the Canvas
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (selectedMode) {
                    MultiChartMode.ALL_THREE -> {
                        LegendMetricChip(
                            label = "Khói",
                            valueText = "$currentGas ADC",
                            color = GasLineColor
                        )
                        LegendMetricChip(
                            label = "Nhiệt",
                            valueText = currentTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                            color = TempLineColor
                        )
                        LegendMetricChip(
                            label = "Lửa",
                            valueText = "${currentFlamePct.roundToInt()}%",
                            color = FlameLineColor
                        )
                    }
                    MultiChartMode.GAS_ADC -> {
                        LegendMetricChip("Hiện tại", "$currentGas ADC", GasLineColor)
                        LegendMetricChip("Mức 1", "$gasWarnThreshold", WarningAmber)
                        LegendMetricChip("Mức 2", "$gasAlarmThreshold", CriticalAlarmRed)
                    }
                    MultiChartMode.TEMP_C -> {
                        LegendMetricChip(
                            "Hiện tại",
                            currentTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A",
                            TempLineColor
                        )
                        LegendMetricChip("Mức 1", "${tempWarnC.roundToInt()}°C", WarningAmber)
                        LegendMetricChip("Mức 2", "${tempAlarmC.roundToInt()}°C", CriticalAlarmRed)
                    }
                    MultiChartMode.FLAME_IR -> {
                        LegendMetricChip("Hiện tại", "${currentFlamePct.roundToInt()}%", FlameLineColor)
                        LegendMetricChip("Mức 1", "35%", WarningAmber)
                        LegendMetricChip("Mức 2", "70%", CriticalAlarmRed)
                    }
                    MultiChartMode.PROB_PCT -> {
                        LegendMetricChip("Xác suất", "${currentProb.roundToInt()}%", ProbLineColor)
                        LegendMetricChip("Mức 1", "45%", WarningAmber)
                        LegendMetricChip("Mức 2", "75%", CriticalAlarmRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Full-width 220.dp Interactive Canvas
            val gasUpperBound = maxOf(3000f, gasAlarmThreshold * 1.25f)
            val singleMin: Float
            val singleMax: Float
            val level1Threshold: Float?
            val level2Threshold: Float?

            when (selectedMode) {
                MultiChartMode.ALL_THREE -> {
                    singleMin = 0f
                    singleMax = 100f
                    level1Threshold = null
                    level2Threshold = null
                }
                MultiChartMode.GAS_ADC -> {
                    singleMin = 0f
                    singleMax = maxOf(gasUpperBound, (data.maxOfOrNull { it.gasRaw } ?: 0) + 200f)
                    level1Threshold = gasWarnThreshold.toFloat()
                    level2Threshold = gasAlarmThreshold.toFloat()
                }
                MultiChartMode.TEMP_C -> {
                    val minT = data.minOfOrNull { it.temperatureCelsius } ?: 25f
                    val maxT = data.maxOfOrNull { it.temperatureCelsius } ?: 45f
                    singleMin = minOf(15f, minT - 5f)
                    singleMax = maxOf(95f, tempAlarmC + 15f, maxT + 5f)
                    level1Threshold = tempWarnC
                    level2Threshold = tempAlarmC
                }
                MultiChartMode.FLAME_IR -> {
                    singleMin = 0f
                    singleMax = 100f
                    level1Threshold = 35f
                    level2Threshold = 70f
                }
                MultiChartMode.PROB_PCT -> {
                    singleMin = 0f
                    singleMax = 100f
                    level1Threshold = 45f
                    level2Threshold = 75f
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightSurfaceVariant)
                    .pointerInput(data, selectedMode) {
                        detectTapGestures { tapOffset ->
                            val paddingLeft = 44.dp.toPx()
                            val paddingRight = 12.dp.toPx()
                            val chartW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                            val relX = (tapOffset.x - paddingLeft).coerceIn(0f, chartW)
                            val idx = ((relX / chartW) * (data.size - 1)).roundToInt()
                                .coerceIn(0, data.size - 1)
                            selectedIndex = idx
                        }
                    }
                    .pointerInput(data, selectedMode) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val paddingLeft = 44.dp.toPx()
                            val paddingRight = 12.dp.toPx()
                            val chartW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                            val relX = (change.position.x - paddingLeft).coerceIn(0f, chartW)
                            val idx = ((relX / chartW) * (data.size - 1)).roundToInt()
                                .coerceIn(0, data.size - 1)
                            selectedIndex = idx
                        }
                    }
            ) {
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val paddingLeftPx = with(density) { 44.dp.toPx() }
                val paddingRightPx = with(density) { 12.dp.toPx() }
                val chartWidthPx = (containerWidthPx - paddingLeftPx - paddingRightPx).coerceAtLeast(1f)
                var tooltipWidthPx by remember { mutableStateOf(0) }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val paddingTop = 28.dp.toPx()
                    val paddingBottom = 24.dp.toPx()
                    val chartWidth = (canvasWidth - paddingLeftPx - paddingRightPx).coerceAtLeast(1f)
                    val chartHeight = (canvasHeight - paddingTop - paddingBottom).coerceAtLeast(1f)
                    val bottomY = paddingTop + chartHeight

                    // 1. Horizontal Grid & Left Y-Axis Labels
                    val gridSteps = 4
                    val rangeSpan = (singleMax - singleMin).coerceAtLeast(1f)
                    for (i in 0..gridSteps) {
                        val frac = i.toFloat() / gridSteps
                        val y = paddingTop + chartHeight * (1f - frac)
                        val gridVal = singleMin + frac * rangeSpan

                        drawLine(
                            color = Color(0xFFCBD5E1).copy(alpha = 0.65f),
                            start = Offset(paddingLeftPx, y),
                            end = Offset(canvasWidth - paddingRightPx, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)
                        )

                        val yText = when (selectedMode) {
                            MultiChartMode.ALL_THREE,
                            MultiChartMode.FLAME_IR,
                            MultiChartMode.PROB_PCT -> "${gridVal.roundToInt()}%"
                            MultiChartMode.GAS_ADC -> "${gridVal.roundToInt()}"
                            MultiChartMode.TEMP_C -> "${gridVal.roundToInt()}°"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            yText,
                            paddingLeftPx - 6.dp.toPx(),
                            y + 3.5.dp.toPx(),
                            yLabelPaint
                        )
                    }

                    // 2. X-Axis Timestamps (HH:mm)
                    val xIndices = if (data.size >= 4) {
                        listOf(0, data.size / 3, (data.size * 2) / 3, data.size - 1).distinct()
                    } else {
                        listOf(0, data.size - 1).distinct()
                    }
                    xIndices.forEach { idx ->
                        val rawX = paddingLeftPx + (idx.toFloat() / (data.size - 1)) * chartWidth
                        val safeX = rawX.coerceIn(paddingLeftPx + 14.dp.toPx(), canvasWidth - paddingRightPx - 14.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(
                            timeAxisFmt.format(Date(data[idx].timestamp)),
                            safeX,
                            canvasHeight - 6.dp.toPx(),
                            xLabelPaint
                        )
                    }

                    // 3. Level 1 (Amber) & Level 2 (Red) Threshold Lines in single-metric modes
                    fun drawThreshold(value: Float, color: Color, label: String) {
                        val frac = ((value - singleMin) / rangeSpan).coerceIn(0f, 1f)
                        val y = paddingTop + chartHeight * (1f - frac)
                        drawLine(
                            color = color.copy(alpha = 0.85f),
                            start = Offset(paddingLeftPx, y),
                            end = Offset(canvasWidth - paddingRightPx, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                        thresholdLabelPaint.color = android.graphics.Color.argb(
                            (color.alpha * 255).toInt(),
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            canvasWidth - paddingRightPx - 2.dp.toPx(),
                            (y - 4.dp.toPx()).coerceAtLeast(paddingTop + 8.dp.toPx()),
                            thresholdLabelPaint
                        )
                    }

                    level1Threshold?.let { drawThreshold(it, WarningAmber, "M1") }
                    level2Threshold?.let { drawThreshold(it, CriticalAlarmRed, "M2") }

                    // 4. Compute Offsets & Draw Curves
                    fun mapOffsets(extractor: (SensorSnapshotPoint) -> Float): List<Offset> {
                        return data.mapIndexed { idx, pt ->
                            val x = paddingLeftPx + (idx.toFloat() / (data.size - 1)) * chartWidth
                            val norm = extractor(pt).coerceIn(0f, 1f)
                            val y = paddingTop + chartHeight * (1f - norm)
                            Offset(x, y)
                        }
                    }

                    val activeCurves: List<Pair<Color, List<Offset>>> = when (selectedMode) {
                        MultiChartMode.ALL_THREE -> listOf(
                            GasLineColor to mapOffsets { it.gasRaw.toFloat() / gasUpperBound },
                            TempLineColor to mapOffsets { (it.temperatureCelsius - 20f) / 80f },
                            FlameLineColor to mapOffsets { it.flamePercent / 100f }
                        )
                        MultiChartMode.GAS_ADC -> listOf(
                            GasLineColor to mapOffsets { (it.gasRaw.toFloat() - singleMin) / rangeSpan }
                        )
                        MultiChartMode.TEMP_C -> listOf(
                            TempLineColor to mapOffsets { (it.temperatureCelsius - singleMin) / rangeSpan }
                        )
                        MultiChartMode.FLAME_IR -> listOf(
                            FlameLineColor to mapOffsets { (it.flamePercent - singleMin) / rangeSpan }
                        )
                        MultiChartMode.PROB_PCT -> listOf(
                            ProbLineColor to mapOffsets { (it.fireProbabilityPercent - singleMin) / rangeSpan }
                        )
                    }

                    val drawFill = selectedMode != MultiChartMode.ALL_THREE
                    activeCurves.forEach { (curveColor, offsets) ->
                        drawSmoothCurve(
                            offsets = offsets,
                            color = curveColor,
                            bottomY = bottomY,
                            paddingTop = paddingTop,
                            withGradientFill = drawFill
                        )
                    }

                    // 5. Crosshair & Glowing Point Markers on Selected Index
                    selectedIndex?.let { selIdx ->
                        val firstCurve = activeCurves.firstOrNull()?.second
                        if (firstCurve != null && selIdx in firstCurve.indices) {
                            val selX = firstCurve[selIdx].x
                            drawLine(
                                color = Color(0xFF0F172A).copy(alpha = 0.45f),
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, bottomY),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            activeCurves.forEach { (curveColor, offsets) ->
                                val center = offsets[selIdx]
                                drawCircle(
                                    color = curveColor.copy(alpha = 0.28f),
                                    radius = 8.5.dp.toPx(),
                                    center = center
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.8.dp.toPx(),
                                    center = center
                                )
                                drawCircle(
                                    color = curveColor,
                                    radius = 3.2.dp.toPx(),
                                    center = center
                                )
                            }
                        }
                    }
                }

                // 6. Floating High-Contrast Tooltip Box inside/above the top of the chart
                selectedIndex?.let { selIdx ->
                    val pt = data.getOrNull(selIdx)
                    if (pt != null) {
                        val selX = paddingLeftPx + (selIdx.toFloat() / (data.size - 1)) * chartWidthPx
                        val marginPx = with(density) { 6.dp.toPx() }
                        val clampedX = (selX - tooltipWidthPx / 2f)
                            .coerceIn(marginPx, (containerWidthPx - tooltipWidthPx - marginPx).coerceAtLeast(marginPx))

                        val baseText = "${tooltipFmt.format(Date(pt.timestamp))} · Khói ${pt.gasRaw} ADC · Nhiệt ${String.format(Locale.US, "%.1f", pt.temperatureCelsius)}°C · Lửa ${pt.flamePercent.roundToInt()}%"
                        val fullText = if (selectedMode == MultiChartMode.PROB_PCT) {
                            "$baseText · Cháy ${pt.fireProbabilityPercent.roundToInt()}%"
                        } else {
                            baseText
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(clampedX.roundToInt(), with(density) { 4.dp.roundToPx() }) }
                                .onSizeChanged { tooltipWidthPx = it.width }
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                                .clickable { selectedIndex = null }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = fullText,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSmoothCurve(
    offsets: List<Offset>,
    color: Color,
    bottomY: Float,
    paddingTop: Float,
    withGradientFill: Boolean
) {
    if (offsets.isEmpty()) return
    val linePath = Path()
    val fillPath = Path()

    linePath.moveTo(offsets.first().x, offsets.first().y)
    fillPath.moveTo(offsets.first().x, bottomY)
    fillPath.lineTo(offsets.first().x, offsets.first().y)

    for (i in 1 until offsets.size) {
        val prev = offsets[i - 1]
        val curr = offsets[i]
        val cx = (prev.x + curr.x) / 2f
        linePath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
        fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
    }

    fillPath.lineTo(offsets.last().x, bottomY)
    fillPath.close()

    if (withGradientFill) {
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                startY = paddingTop,
                endY = bottomY
            )
        )
    }

    drawPath(
        path = linePath,
        color = color,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
    )
}

@Composable
private fun LegendMetricChip(
    label: String,
    valueText: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$label:",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = valueText,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
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
