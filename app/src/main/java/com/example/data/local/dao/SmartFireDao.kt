package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.AnalysisInsightEntity
import com.example.data.local.entity.DeviceEntity
import com.example.data.local.entity.DeviceEventEntity
import com.example.data.local.entity.DeviceSnapshotEntity
import com.example.data.local.entity.ExtinguisherEntity
import com.example.data.local.entity.FloorEntity
import com.example.data.local.entity.HomeEntity
import com.example.data.local.entity.MapEdgeEntity
import com.example.data.local.entity.MapObjectEntity
import com.example.data.local.entity.RoomEntity
import com.example.data.local.entity.SyncCursorEntity
import com.example.data.local.entity.UserLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartFireDao {

    // --- HOMES ---
    @Query("SELECT * FROM homes ORDER BY createdAt ASC")
    fun getAllHomes(): Flow<List<HomeEntity>>

    @Query("SELECT * FROM homes WHERE id = :id LIMIT 1")
    suspend fun getHomeById(id: String): HomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHome(home: HomeEntity)

    @Update
    suspend fun updateHome(home: HomeEntity)

    @Delete
    suspend fun deleteHome(home: HomeEntity)

    // --- FLOORS ---
    @Query("SELECT * FROM floors WHERE homeId = :homeId ORDER BY level ASC")
    fun getFloorsForHome(homeId: String): Flow<List<FloorEntity>>

    @Query("SELECT * FROM floors WHERE id = :id LIMIT 1")
    suspend fun getFloorById(id: String): FloorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFloor(floor: FloorEntity)

    @Update
    suspend fun updateFloor(floor: FloorEntity)

    @Delete
    suspend fun deleteFloor(floor: FloorEntity)

    // --- ROOMS ---
    @Query("SELECT * FROM rooms WHERE floorId = :floorId")
    fun getRoomsForFloor(floorId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id LIMIT 1")
    suspend fun getRoomById(id: String): RoomEntity?

    @Query("SELECT * FROM rooms")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)

    @Update
    suspend fun updateRoom(room: RoomEntity)

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    // --- DEVICES ---
    @Query("SELECT * FROM devices ORDER BY createdAt ASC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE homeId = :homeId")
    fun getDevicesForHome(homeId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE floorId = :floorId")
    fun getDevicesForFloor(floorId: String): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("UPDATE devices SET displayName = :alias WHERE deviceId = :deviceId")
    suspend fun renameDevice(deviceId: String, alias: String)

    @Query("UPDATE devices SET roomId = :roomId, floorId = :floorId WHERE deviceId = :deviceId")
    suspend fun updateDeviceLocation(deviceId: String, floorId: String, roomId: String)

    @Query("UPDATE devices SET lastKnownIp = :ip WHERE deviceId = :deviceId")
    suspend fun updateDeviceIp(deviceId: String, ip: String)

    @Query("UPDATE devices SET xNormalized = :x, yNormalized = :y WHERE deviceId = :deviceId")
    suspend fun updateDeviceMapPosition(deviceId: String, x: Float, y: Float)

    @Query("UPDATE devices SET lastSeenAt = :lastSeenAt, bootId = :bootId, latestSeq = :latestSeq, lastKnownState = :state, lastKnownAlarm = :alarm WHERE deviceId = :deviceId")
    suspend fun updateDeviceTelemetryState(deviceId: String, lastSeenAt: Long, bootId: String, latestSeq: Long, state: String, alarm: Boolean)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    // --- SNAPSHOTS (Retention limit ~500 per device) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: DeviceSnapshotEntity)

    @Query("SELECT * FROM device_snapshots WHERE deviceId = :deviceId ORDER BY receivedAt DESC LIMIT :limit")
    fun getRecentSnapshots(deviceId: String, limit: Int = 100): Flow<List<DeviceSnapshotEntity>>

    @Query("SELECT * FROM device_snapshots WHERE deviceId = :deviceId AND receivedAt >= :sinceTimestamp ORDER BY receivedAt ASC")
    suspend fun getSnapshotsSince(deviceId: String, sinceTimestamp: Long): List<DeviceSnapshotEntity>

    @Query("DELETE FROM device_snapshots WHERE deviceId = :deviceId AND id NOT IN (SELECT id FROM device_snapshots WHERE deviceId = :deviceId ORDER BY receivedAt DESC LIMIT 500)")
    suspend fun pruneOldSnapshots(deviceId: String)

    // --- EVENTS & SYNC CURSOR ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<DeviceEventEntity>)

    @Query("SELECT * FROM device_events WHERE deviceId = :deviceId ORDER BY receivedAt DESC LIMIT :limit")
    fun getEventsForDevice(deviceId: String, limit: Int = 100): Flow<List<DeviceEventEntity>>

    @Query("SELECT * FROM device_events ORDER BY receivedAt DESC LIMIT :limit")
    fun getAllEvents(limit: Int = 200): Flow<List<DeviceEventEntity>>

    @Query("SELECT * FROM sync_cursors WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getSyncCursor(deviceId: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSyncCursor(cursor: SyncCursorEntity)

    @Transaction
    suspend fun syncEventsTransaction(
        deviceId: String,
        bootId: String,
        latestSeqOnDevice: Long,
        oldestSeqOnDevice: Long,
        events: List<DeviceEventEntity>
    ) {
        val currentCursor = getSyncCursor(deviceId)
        val isNewBoot = currentCursor == null || currentCursor.bootId != bootId
        val lastPersisted = if (isNewBoot) 0L else currentCursor.lastPersistedSeq

        val hasGap = !isNewBoot && oldestSeqOnDevice > (lastPersisted + 1)
        val missingFrom = if (hasGap) lastPersisted + 1 else 0L
        val missingTo = if (hasGap) oldestSeqOnDevice - 1 else 0L

        if (events.isNotEmpty()) {
            insertEvents(events)
        }

        val maxPersistedSeq = maxOf(lastPersisted, events.maxOfOrNull { it.seq } ?: latestSeqOnDevice)

        insertOrUpdateSyncCursor(
            SyncCursorEntity(
                deviceId = deviceId,
                bootId = bootId,
                lastPersistedSeq = maxPersistedSeq,
                historyGap = hasGap,
                missingFromSeq = missingFrom,
                missingToSeq = missingTo,
                lastSyncAt = System.currentTimeMillis()
            )
        )
    }

    // --- EXTINGUISHERS ---
    @Query("SELECT * FROM extinguishers WHERE floorId = :floorId")
    fun getExtinguishersForFloor(floorId: String): Flow<List<ExtinguisherEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtinguisher(extinguisher: ExtinguisherEntity)

    @Update
    suspend fun updateExtinguisher(extinguisher: ExtinguisherEntity)

    @Delete
    suspend fun deleteExtinguisher(extinguisher: ExtinguisherEntity)

    @Query("DELETE FROM extinguishers WHERE floorId = :floorId")
    suspend fun clearExtinguishersForFloor(floorId: String)

    // --- MAP OBJECTS & EDGES ---
    @Query("SELECT * FROM map_objects WHERE floorId = :floorId")
    fun getMapObjectsForFloor(floorId: String): Flow<List<MapObjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapObject(mapObject: MapObjectEntity)

    @Delete
    suspend fun deleteMapObject(mapObject: MapObjectEntity)

    @Query("DELETE FROM map_objects WHERE floorId = :floorId")
    suspend fun clearMapObjectsForFloor(floorId: String)

    @Query("SELECT * FROM map_edges WHERE floorId = :floorId")
    fun getMapEdgesForFloor(floorId: String): Flow<List<MapEdgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapEdge(mapEdge: MapEdgeEntity)

    @Delete
    suspend fun deleteMapEdge(mapEdge: MapEdgeEntity)

    @Query("DELETE FROM map_edges WHERE floorId = :floorId")
    suspend fun clearMapEdgesForFloor(floorId: String)

    @Query("DELETE FROM rooms WHERE floorId = :floorId")
    suspend fun clearRoomsForFloor(floorId: String)

    @Query("UPDATE floors SET widthMeters = :width, lengthMeters = :length WHERE id = :floorId")
    suspend fun updateFloorDimensions(floorId: String, width: Float, length: Float)

    @Query("DELETE FROM device_events")
    suspend fun clearAllEvents()

    // --- USER LOCATION ---
    @Query("SELECT * FROM user_locations WHERE homeId = :homeId LIMIT 1")
    fun getUserLocation(homeId: String): Flow<UserLocationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setUserLocation(location: UserLocationEntity)

    // --- ANALYSIS INSIGHTS ---
    @Query("SELECT * FROM analysis_insights WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getInsightsForDevice(deviceId: String, limit: Int = 20): Flow<List<AnalysisInsightEntity>>

    @Query("SELECT * FROM analysis_insights ORDER BY timestamp DESC LIMIT :limit")
    fun getAllInsights(limit: Int = 50): Flow<List<AnalysisInsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: AnalysisInsightEntity)

    @Query("DELETE FROM analysis_insights WHERE deviceId = :deviceId")
    suspend fun deleteInsightsForDevice(deviceId: String)
}
