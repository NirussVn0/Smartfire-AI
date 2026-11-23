package com.example.domain.model

import kotlin.math.exp
import kotlin.math.pow

enum class FirmwareState {
    WARMING,
    UNCONFIGURED,
    NORMAL,
    SUSPECTED,
    ALARM,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): FirmwareState {
            return when (value?.uppercase()) {
                "WARMING" -> WARMING
                "UNCONFIGURED" -> UNCONFIGURED
                "NORMAL" -> NORMAL
                "SUSPECTED" -> SUSPECTED
                "ALARM" -> ALARM
                else -> UNKNOWN
            }
        }
    }
}

enum class DeviceConnectionState {
    ONLINE_FRESH, // Received status within <= 3.5s
    STALE,        // Received status 3.5s - 10s ago, or frozen uptime
    OFFLINE,      // > 10s or unreachable
    UNVERIFIED    // Not yet polled
}

enum class CommandStatus {
    IDLE,
    PENDING,
    APPLIED,
    REJECTED,
    EXPIRED,
    UNKNOWN_OUTCOME
}

enum class MapNavigationTargetType {
    NEAREST_FIRE_SENSOR,
    SPECIFIC_FIRE_SENSOR,
    NEAREST_EXTINGUISHER,
    TWO_STAGE_EXTINGUISHER_TO_FIRE,
    NEAREST_EXIT
}

data class MapNavigationRequest(
    val targetType: MapNavigationTargetType,
    val targetDeviceId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SensorSnapshotPoint(
    val deviceId: String,
    val timestamp: Long,
    val gasRaw: Int,
    val temperatureCelsius: Float,
    val fireProbabilityPercent: Float
)

/**
 * Chi tiết xác suất cháy đa thông số (Multi-Sensor Probabilistic Fusion Breakdown)
 * Kết hợp 5 thông số vật lý thay vì phụ thuộc cố định vào 1 ngưỡng đơn lẻ:
 * 1. Nhiệt độ (°C) & Tốc độ tăng nhiệt (RoR °C/phút)
 * 2. Mật độ Khói / Khí cháy MQ-2 (ADC)
 * 3. Nồng độ Khí CO độc hại (ppm)
 * 4. Độ sụt giảm Độ ẩm tương đối (%RH)
 * 5. Cường độ bức xạ hồng ngoại ngọn lửa (IR Flame Index 0..1)
 */
data class FireProbabilityBreakdown(
    val totalProbabilityPercent: Float, // 0.0% .. 99.9%
    val tempContributionPercent: Float,
    val smokeGasContributionPercent: Float,
    val coContributionPercent: Float,
    val humidityContributionPercent: Float,
    val irFlameContributionPercent: Float,
    val riskLevelLabel: String
)

enum class SensorAlertLevel {
    NORMAL,
    WARNING_LEVEL_1,
    ALARM_LEVEL_2
}

data class EspDeviceConfig(
    val mq2Enabled: Boolean = true,
    val gasWarn: Int = 1800,
    val gasOn: Int = 2400,
    val gasOff: Int = 1600,
    val tempEnabled: Boolean = true,
    val tempWarnC: Float = 45.0f,
    val tempAlarmC: Float = 60.0f,
    val flameEnabled: Boolean = true,
    val flameWarnMs: Int = 150,
    val flameAlarmMs: Int = 500,
    val hornEnabled: Boolean = false,
    val gasCommissioned: Boolean = false,
    val txPower1m: Int = -45,
    val pathLossN: Float = 2.4f,
    val firmwareVersion: String = ""
)

/**
 * 1D Median + Kalman filter for Wi-Fi RSSI stabilization so multipath jitter
 * doesn't cause ±5m distance jumps on every poll.
 */
class RssiKalmanFilter(
    private val processNoiseQ: Float = 0.65f,
    private val measurementNoiseR: Float = 6.5f
) {
    private val window = ArrayDeque<Int>(5)
    private var estimate: Float? = null
    private var errorCovariance: Float = 10.0f

    fun update(rawRssiDbm: Int): Int {
        val clamped = rawRssiDbm.coerceIn(-100, -25)
        if (window.size >= 5) window.removeFirst()
        window.addLast(clamped)
        val sorted = window.sorted()
        val median = sorted[sorted.size / 2].toFloat()

        val prev = estimate
        if (prev == null) {
            estimate = median
            errorCovariance = 4.0f
            return median.toInt()
        }
        val predictedError = errorCovariance + processNoiseQ
        val kalmanGain = predictedError / (predictedError + measurementNoiseR)
        val next = prev + kalmanGain * (median - prev)
        estimate = next
        errorCovariance = (1f - kalmanGain) * predictedError
        return kotlin.math.round(next).toInt()
    }
}

data class SmartFireTelemetry(
    val uptimeMs: Long = 0L,
    val state: String = "NORMAL",
    val gasRaw: Int = 0,
    val temperatureCelsius: Float? = null,
    val tempRateOfRiseCPerMin: Float? = null,
    val coPpm: Int? = null,
    val humidityPercent: Float? = null,
    val irFlameIntensity: Float? = null, // 0.0 (no IR flame) .. 1.0 (strong IR flame)
    val rssiDbm: Int = -54,              // Signal strength (dBm) for RSSI distance ranging
    val wifiReconnects: Long = 0L,
    val txPower1m: Int = -45,
    val pathLossN: Float = 2.4f,
    val mq2Enabled: Boolean = true,
    val gasWarnThreshold: Int = 1800,
    val gasOnThreshold: Int = 2400,
    val gasOffThreshold: Int = 1600,
    val tempEnabled: Boolean = true,
    val tempWarnC: Float = 45.0f,
    val tempAlarmC: Float = 60.0f,
    val flameEnabled: Boolean = true,
    val flameWarnMs: Int = 150,
    val flameAlarmMs: Int = 500,
    val firmwareVersion: String = "",
    val alarmStartedAtMs: Long = 0L,     // Timestamp when continuous alarm started (for circular fire spread calculation)
    val flame: Boolean = false,
    val gasReady: Boolean = false,
    val gasFault: Boolean = false,
    val alarm: Boolean = false,
    val muted: Boolean = false,
    val acknowledged: Boolean = false,
    val testing: Boolean = false,
    val hornRequested: Boolean = false,
    val hornEnabled: Boolean = false,
    val gasCommissioned: Boolean = false,
    val receivedAt: Long = 0L
) {
    val isTemperatureInstalled: Boolean get() = temperatureCelsius != null
    val isCoInstalled: Boolean get() = coPpm != null
    val isHumidityInstalled: Boolean get() = humidityPercent != null

    private fun evaluateLevelInternal(
        effMq2: Boolean,
        effTemp: Boolean,
        effFlame: Boolean,
        warnGas: Int,
        alarmGas: Int,
        warnTemp: Float,
        alarmTemp: Float
    ): SensorAlertLevel {
        val gasLevel2 = effMq2 && gasRaw >= alarmGas
        val tempLevel2 = effTemp && temperatureCelsius != null && temperatureCelsius >= alarmTemp
        val flameLevel2 = effFlame && (flame || (irFlameIntensity ?: 0f) >= 0.70f)
        if (gasLevel2 || tempLevel2 || flameLevel2) {
            return SensorAlertLevel.ALARM_LEVEL_2
        }

        val gasLevel1 = effMq2 && gasRaw >= warnGas
        val tempLevel1 = effTemp && temperatureCelsius != null && temperatureCelsius >= warnTemp
        val flameLevel1 = effFlame && ((irFlameIntensity ?: 0f) >= 0.35f)
        if (gasLevel1 || tempLevel1 || flameLevel1) {
            return SensorAlertLevel.WARNING_LEVEL_1
        }

        return SensorAlertLevel.NORMAL
    }

    fun evaluateTwoLevelStatus(config: EspDeviceConfig? = null): SensorAlertLevel {
        return evaluateLevelInternal(
            effMq2 = config?.mq2Enabled ?: mq2Enabled,
            effTemp = config?.tempEnabled ?: tempEnabled,
            effFlame = config?.flameEnabled ?: flameEnabled,
            warnGas = config?.gasWarn ?: gasWarnThreshold,
            alarmGas = config?.gasOn ?: gasOnThreshold,
            warnTemp = config?.tempWarnC ?: tempWarnC,
            alarmTemp = config?.tempAlarmC ?: tempAlarmC
        )
    }

    /**
     * Tính toán xác suất cháy tổng hợp P(Fire) dựa trên mô hình kết hợp xác suất đa thông số
     * (Weighted Logistic / Bayesian Multi-Parameter Sensor Fusion), hỗ trợ bật/bỏ qua từng cảm biến
     * và 2 mức ngưỡng độc lập (Mức 1 Cảnh báo sớm vs Mức 2 Báo cháy khẩn cấp).
     */
    fun calculateFireProbability(
        gasThresholdAdc: Int = 1800,
        config: EspDeviceConfig? = null
    ): FireProbabilityBreakdown {
        val effMq2 = config?.mq2Enabled ?: mq2Enabled
        val effTemp = config?.tempEnabled ?: tempEnabled
        val effFlame = config?.flameEnabled ?: flameEnabled

        val alarmTemp = (config?.tempAlarmC ?: tempAlarmC).coerceIn(35f, 120f)
        val warnTemp = (config?.tempWarnC ?: tempWarnC).coerceIn(25f, (alarmTemp - 2f).coerceAtLeast(25f))

        // 1. Chuẩn hóa điểm Nhiệt độ & Tốc độ tăng nhiệt RoR [0..1]
        val thermalScore = if (effTemp && temperatureCelsius != null) {
            val tempSpan = (alarmTemp - 26f).coerceAtLeast(15f)
            val tempNorm = ((temperatureCelsius - 26f) / tempSpan).coerceIn(0f, 1f)
            val rorNorm = ((tempRateOfRiseCPerMin ?: 0.4f) / 12f).coerceIn(0f, 1f)
            val rawTherm = (0.76f * tempNorm + 0.24f * rorNorm).coerceIn(0f, 1f)
            when {
                temperatureCelsius >= alarmTemp -> maxOf(rawTherm, 0.85f)
                temperatureCelsius >= warnTemp -> maxOf(rawTherm, 0.55f)
                else -> rawTherm
            }
        } else {
            0f
        }

        // 2. Chuẩn hóa điểm Mật độ Khói & Gas MQ-2 [0..1] theo 2 mức ngưỡng
        val alarmGas = (config?.gasOn ?: gasThresholdAdc).coerceIn(800, 3900)
        val warnGas = (config?.gasWarn ?: minOf(gasWarnThreshold, (alarmGas * 0.78f).toInt()))
            .coerceIn(500, (alarmGas - 100).coerceAtLeast(500))
        val smokeScore = if (effMq2) {
            val baselineGas = 550f
            val span = (alarmGas.toFloat() - baselineGas).coerceAtLeast(450f) * 1.25f
            val rawSmokeNorm = ((gasRaw - baselineGas) / span).coerceIn(0f, 1f)
            when {
                gasRaw >= alarmGas -> maxOf(rawSmokeNorm, 0.85f)
                gasRaw >= warnGas -> maxOf(rawSmokeNorm, 0.55f)
                else -> rawSmokeNorm
            }
        } else {
            0f
        }

        // 3. Chuẩn hóa điểm Nồng độ Khí CO (ppm) [0..1]
        val coScore = if (coPpm != null) {
            ((coPpm.toFloat() - 10f) / 160f).coerceIn(0f, 1f)
        } else {
            0f
        }

        // 4. Chuẩn hóa điểm Độ sụt giảm Độ ẩm [0..1]
        val humidityDropScore = if (humidityPercent != null) {
            ((65f - humidityPercent) / 42f).coerceIn(0f, 1f)
        } else {
            0f
        }

        // 5. Điểm Bức xạ Hồng ngoại Ngọn lửa IR [0..1]
        val effectiveIr = if (!effFlame) {
            0f
        } else if (flame) {
            0.88f
        } else {
            (irFlameIntensity ?: 0f).coerceIn(0f, 1f)
        }

        // Điều chỉnh trọng số tùy theo cảm biến thực tế có sẵn và được bật:
        val hasAuxSensors = (effTemp && temperatureCelsius != null) || coPpm != null || humidityPercent != null
        val weightedLinear = if (hasAuxSensors) {
            (0.28f * thermalScore) +
            (0.24f * smokeScore) +
            (0.18f * coScore) +
            (0.20f * effectiveIr) +
            (0.10f * humidityDropScore)
        } else {
            (0.55f * smokeScore) + (0.45f * effectiveIr)
        }

        val highFactorCount = listOf(
            thermalScore > 0.45f,
            smokeScore > 0.45f,
            coScore > 0.40f,
            effectiveIr > 0.40f,
            humidityDropScore > 0.45f
        ).count { it }

        val thresholdExceededBoost = if (effMq2 && gasRaw >= alarmGas && (thermalScore > 0.35f || effectiveIr > 0.35f || (effFlame && flame))) 0.15f else 0f

        val synergyBoost = when {
            effFlame && flame && effMq2 && gasRaw >= alarmGas -> 0.30f
            highFactorCount >= 4 -> 0.22f
            highFactorCount == 3 -> 0.14f
            highFactorCount == 2 -> 0.07f
            else -> 0f
        } + thresholdExceededBoost

        // Hàm Logistic Sigmoid chuyển đổi sang xác suất P(Cháy) %
        val z = ((weightedLinear + synergyBoost) - 0.42f) * 8.5f
        val logisticProb = (1.0f / (1.0f + exp(-z))).coerceIn(0.01f, 0.999f)
        val rawLogisticPercent = (logisticProb * 100f).coerceIn(1.2f, 99.8f)

        val level = evaluateLevelInternal(
            effMq2 = effMq2,
            effTemp = effTemp,
            effFlame = effFlame,
            warnGas = warnGas,
            alarmGas = alarmGas,
            warnTemp = warnTemp,
            alarmTemp = alarmTemp
        )

        val totalPercent = when {
            effFlame && flame && effMq2 && gasRaw >= alarmGas -> 99.5f
            level == SensorAlertLevel.ALARM_LEVEL_2 -> maxOf(rawLogisticPercent, 82.0f).coerceAtMost(99.8f)
            level == SensorAlertLevel.WARNING_LEVEL_1 -> rawLogisticPercent.coerceIn(48.0f, 72.0f)
            else -> rawLogisticPercent.coerceAtMost(42.0f)
        }

        val riskLabel = when {
            level == SensorAlertLevel.ALARM_LEVEL_2 || totalPercent >= 75f -> "MỨC 2: BÁO CHÁY KHẨN CẤP"
            level == SensorAlertLevel.WARNING_LEVEL_1 || totalPercent >= 45f -> "MỨC 1: CẢNH BÁO SỚM"
            totalPercent >= 22f -> "THEO DÕI BIẾN ĐỘNG NHIỆT/KHÓI"
            else -> "AN TOÀN (XÁC SUẤT THẤP)"
        }

        return FireProbabilityBreakdown(
            totalProbabilityPercent = totalPercent,
            tempContributionPercent = (thermalScore * 28f),
            smokeGasContributionPercent = (smokeScore * 24f),
            coContributionPercent = (coScore * 18f),
            humidityContributionPercent = (humidityDropScore * 10f),
            irFlameContributionPercent = (effectiveIr * 20f),
            riskLevelLabel = riskLabel
        )
    }

    /**
     * Ước lượng khoảng cách từ điện thoại tới cảm biến dựa trên suy hao tín hiệu RSSI (Log-Distance Path Loss Model):
     * d = 10 ^ ((TxPower - RSSI) / (10 * n))
     * Sử dụng tham số hiệu chuẩn thực tế từ ESP32 (mặc định TxPower = -45 dBm tại 1m, n = 2.4).
     */
    fun estimateDistanceFromRssiMeters(
        txPowerAt1m: Int = this.txPower1m,
        pathLossExponent: Float = this.pathLossN
    ): Float {
        val clampedRssi = rssiDbm.coerceIn(-98, -28)
        val safeN = pathLossExponent.coerceIn(1.6f, 4.5f)
        val exponent = (txPowerAt1m - clampedRssi).toFloat() / (10f * safeN)
        return 10.0.pow(exponent.toDouble()).toFloat().coerceIn(0.35f, 80.0f)
    }
}

data class SmartFireNode(
    val deviceId: String,
    val displayName: String,
    val homeId: String,
    val floorId: String,
    val roomId: String,
    val firmwareRoomId: String = "",
    val ipAddress: String,
    val lastSeenAt: Long = 0L,
    val bootId: String = "",
    val latestSeq: Long = 0L,
    val firmwareState: FirmwareState = FirmwareState.UNKNOWN,
    val connectionState: DeviceConnectionState = DeviceConnectionState.UNVERIFIED,
    val lastKnownAlarm: Boolean = false,
    val telemetry: SmartFireTelemetry = SmartFireTelemetry(),
    val xNormalized: Float = 0.5f,
    val yNormalized: Float = 0.5f,
    val activeCommandStatus: CommandStatus = CommandStatus.IDLE,
    val lastCommandRequestId: String = "",
    val lastCommandType: String = "",
    val isDemoSensor: Boolean = false
)

data class DeviceEvent(
    val id: Long = 0L,
    val deviceId: String,
    val bootId: String,
    val seq: Long,
    val receivedAt: Long,
    val kind: String,
    val requestId: String = "",
    val result: String = "",
    val state: String = "",
    val rawPayload: String = ""
)

data class HomeProfile(
    val id: String,
    val name: String,
    val buildingType: String = "Nhà ở",
    val description: String = "",
    val floorCount: Int = 1,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class FloorPlan(
    val id: String,
    val homeId: String,
    val name: String,
    val level: Int,
    val widthMeters: Float = 12.0f,
    val lengthMeters: Float = 16.0f,
    val floorplanUri: String? = null,
    val scaleMetersPerUnit: Float = 12.0f // Scale calibration
)

data class Point2D(
    val x: Float, // Normalized [0..1]
    val y: Float  // Normalized [0..1]
)

data class RoomSpace(
    val id: String,
    val floorId: String,
    val name: String,
    val type: String, // LIVING_ROOM, KITCHEN, BEDROOM, CLASSROOM, CORRIDOR, OTHER
    val polygon: List<Point2D> = emptyList(),
    val colorHex: String = "#1E293B"
)

enum class ExtinguisherType {
    CO2,
    POWDER_ABC,
    FOAM,
    WATER
}

data class ExtinguisherItem(
    val id: String,
    val floorId: String,
    val roomId: String,
    val name: String,
    val type: ExtinguisherType = ExtinguisherType.POWDER_ABC,
    val position: Point2D = Point2D(0.5f, 0.5f),
    val lastInspectionDate: String = "2026-10",
    val notes: String = ""
)

enum class MapObjectType {
    MAIN_DOOR,      // Cửa Chính
    DOOR,           // Cánh cửa thường (đánh số Cửa #1, #2, #3...)
    CORRIDOR_NODE,
    STAIRS,
    EXIT,
    ASSEMBLY_POINT
}

data class MapNodeItem(
    val id: String,
    val floorId: String,
    val name: String,
    val type: MapObjectType,
    val position: Point2D
)

data class MapEdgeItem(
    val id: String,
    val floorId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Float = 1.0f,
    val enabled: Boolean = true
)

data class UserPosition(
    val homeId: String,
    val floorId: String,
    val roomId: String?,
    val position: Point2D?,
    val source: String = "MANUAL_TAP", // MANUAL_TAP, QR_ROOM, UNKNOWN
    val updatedAt: Long = System.currentTimeMillis()
)

data class AiInsight(
    val id: Long = 0L,
    val deviceId: String,
    val title: String,
    val explanation: String,
    val sourceMeasurements: String,
    val analysisMethod: String,
    val freshness: String,
    val severity: String, // INFO, WARNING, CRITICAL
    val recommendation: String,
    val timestamp: Long = System.currentTimeMillis()
)
