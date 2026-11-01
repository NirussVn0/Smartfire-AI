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
    NEAREST_EXIT
}

data class MapNavigationRequest(
    val targetType: MapNavigationTargetType,
    val targetDeviceId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
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

data class SmartFireTelemetry(
    val uptimeMs: Long = 0L,
    val state: String = "NORMAL",
    val gasRaw: Int = 920,
    val temperatureCelsius: Float = 28.5f,
    val tempRateOfRiseCPerMin: Float = 0.4f,
    val coPpm: Int = 12,
    val humidityPercent: Float = 62.0f,
    val irFlameIntensity: Float = 0.02f, // 0.0 (no IR flame) .. 1.0 (strong IR flame)
    val rssiDbm: Int = -54,              // Signal strength (dBm) for RSSI distance ranging
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
    /**
     * Tính toán xác suất cháy tổng hợp P(Fire) dựa trên mô hình kết hợp xác suất đa thông số
     * (Weighted Logistic / Bayesian Multi-Parameter Sensor Fusion).
     */
    fun calculateFireProbability(): FireProbabilityBreakdown {
        // 1. Chuẩn hóa điểm Nhiệt độ & Tốc độ tăng nhiệt RoR [0..1]
        val tempNorm = ((temperatureCelsius - 26f) / 48f).coerceIn(0f, 1f)
        val rorNorm = (tempRateOfRiseCPerMin / 12f).coerceIn(0f, 1f)
        val thermalScore = (0.72f * tempNorm + 0.28f * rorNorm).coerceIn(0f, 1f)

        // 2. Chuẩn hóa điểm Mật độ Khói & Gas MQ-2 [0..1]
        val smokeScore = ((gasRaw - 650f) / 1850f).coerceIn(0f, 1f)

        // 3. Chuẩn hóa điểm Nồng độ Khí CO (ppm) [0..1]
        val coScore = ((coPpm - 10f) / 160f).coerceIn(0f, 1f)

        // 4. Chuẩn hóa điểm Độ sụt giảm Độ ẩm (Khi cháy nhiệt tăng làm độ ẩm giảm mạnh từ ~65% xuống <25%) [0..1]
        val humidityDropScore = ((65f - humidityPercent) / 42f).coerceIn(0f, 1f)

        // 5. Điểm Bức xạ Hồng ngoại Ngọn lửa IR [0..1]
        val effectiveIr = if (flame && irFlameIntensity < 0.75f) 0.88f else irFlameIntensity.coerceIn(0f, 1f)

        // Trọng số kết hợp đa thông số:
        // Nhiệt & RoR (28%), Khói MQ-2 (24%), Khí CO (18%), Bức xạ IR (20%), Sụt giảm độ ẩm (10%)
        val weightedLinear =
            (0.28f * thermalScore) +
            (0.24f * smokeScore) +
            (0.18f * coScore) +
            (0.20f * effectiveIr) +
            (0.10f * humidityDropScore)

        // Hiệu ứng cộng hưởng xác suất (Synergy bonus khi nhiều thông số cùng tăng cao)
        val highFactorCount = listOf(
            thermalScore > 0.45f,
            smokeScore > 0.45f,
            coScore > 0.40f,
            effectiveIr > 0.40f,
            humidityDropScore > 0.45f
        ).count { it }

        val synergyBoost = when {
            highFactorCount >= 4 -> 0.22f
            highFactorCount == 3 -> 0.14f
            highFactorCount == 2 -> 0.07f
            else -> 0f
        }

        // Hàm Logistic Sigmoid chuyển đổi sang xác suất P(Cháy) %
        val z = ((weightedLinear + synergyBoost) - 0.42f) * 8.5f
        val logisticProb = (1.0f / (1.0f + exp(-z))).coerceIn(0.01f, 0.999f)
        val totalPercent = (logisticProb * 100f).coerceIn(1.2f, 99.8f)

        val riskLabel = when {
            totalPercent >= 75f -> "NGUY CƠ CHÁY CỰC CAO (BÁO ĐỘNG)"
            totalPercent >= 45f -> "NGHI NGỜ CHÁY (CẢNH BÁO SỚM)"
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
     * Với TxPower = -41 dBm tại 1m, hệ số môi trường trong nhà n = 2.2
     */
    fun estimateDistanceFromRssiMeters(txPowerAt1m: Int = -41, pathLossExponent: Float = 2.2f): Float {
        val clampedRssi = rssiDbm.coerceIn(-95, -30)
        val exponent = (txPowerAt1m - clampedRssi).toFloat() / (10f * pathLossExponent)
        return 10.0.pow(exponent.toDouble()).toFloat().coerceIn(0.4f, 35.0f)
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
