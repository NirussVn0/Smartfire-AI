package com.example.domain.model

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

data class SmartFireTelemetry(
    val uptimeMs: Long = 0L,
    val state: String = "NORMAL",
    val gasRaw: Int = 0,
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
)

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
    val lastCommandType: String = ""
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
    DOOR,
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
