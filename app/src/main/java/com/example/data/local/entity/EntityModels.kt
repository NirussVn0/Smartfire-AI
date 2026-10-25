package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "homes")
data class HomeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val buildingType: String = "Nhà ở",
    val description: String = "",
    val floorCount: Int = 1,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "floors",
    indices = [Index("homeId")]
)
data class FloorEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val name: String,
    val level: Int,
    val widthMeters: Float = 12.0f,
    val lengthMeters: Float = 16.0f,
    val floorplanUri: String? = null,
    val scaleMetersPerUnit: Float = 12.0f
)

@Entity(
    tableName = "rooms",
    indices = [Index("floorId")]
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val name: String,
    val type: String,
    val polygonJson: String = "[]",
    val colorHex: String = "#1E293B"
)

@Entity(
    tableName = "devices",
    indices = [Index("homeId"), Index("floorId"), Index("roomId")]
)
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val homeId: String,
    val floorId: String,
    val roomId: String,
    val firmwareRoomId: String = "",
    val lastKnownIp: String,
    val lastSeenAt: Long = 0L,
    val bootId: String = "",
    val latestSeq: Long = 0L,
    val lastKnownState: String = "UNKNOWN",
    val lastKnownAlarm: Boolean = false,
    val xNormalized: Float = 0.5f,
    val yNormalized: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "device_snapshots",
    indices = [Index("deviceId"), Index("receivedAt")]
)
data class DeviceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deviceId: String,
    val bootId: String,
    val receivedAt: Long,
    val uptimeMs: Long,
    val state: String,
    val gasRaw: Int,
    val flame: Boolean,
    val gasReady: Boolean,
    val gasFault: Boolean,
    val alarm: Boolean,
    val muted: Boolean,
    val acknowledged: Boolean,
    val testing: Boolean,
    val hornRequested: Boolean,
    val hornEnabled: Boolean,
    val gasCommissioned: Boolean
)

@Entity(
    tableName = "device_events",
    indices = [
        Index(value = ["deviceId", "bootId", "seq"], unique = true),
        Index("receivedAt")
    ]
)
data class DeviceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val deviceId: String,
    val bootId: String,
    val lastPersistedSeq: Long,
    val historyGap: Boolean = false,
    val missingFromSeq: Long = 0L,
    val missingToSeq: Long = 0L,
    val lastSyncAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "extinguishers",
    indices = [Index("floorId"), Index("roomId")]
)
data class ExtinguisherEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val roomId: String,
    val name: String,
    val type: String, // CO2, POWDER_ABC, FOAM, WATER
    val xNormalized: Float,
    val yNormalized: Float,
    val lastInspectionDate: String = "2026-10",
    val notes: String = ""
)

@Entity(
    tableName = "map_objects",
    indices = [Index("floorId")]
)
data class MapObjectEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val name: String,
    val type: String, // DOOR, CORRIDOR_NODE, STAIRS, EXIT, ASSEMBLY_POINT
    val xNormalized: Float,
    val yNormalized: Float
)

@Entity(
    tableName = "map_edges",
    indices = [Index("floorId")]
)
data class MapEdgeEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val distanceMeters: Float,
    val enabled: Boolean = true
)

@Entity(tableName = "user_locations")
data class UserLocationEntity(
    @PrimaryKey val homeId: String,
    val floorId: String,
    val roomId: String?,
    val xNormalized: Float?,
    val yNormalized: Float?,
    val source: String = "MANUAL_TAP",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "analysis_insights",
    indices = [Index("deviceId"), Index("timestamp")]
)
data class AnalysisInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deviceId: String,
    val title: String,
    val explanation: String,
    val sourceMeasurements: String,
    val analysisMethod: String,
    val freshness: String,
    val severity: String,
    val recommendation: String,
    val timestamp: Long = System.currentTimeMillis()
)
