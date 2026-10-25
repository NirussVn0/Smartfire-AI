package com.example.data.repository

import android.content.Context
import com.example.data.local.SmartFireDatabase
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
import com.example.data.local.entity.UserLocationEntity
import com.example.data.remote.api.SmartFireHttpClientFactory
import com.example.data.security.SecureTokenStore
import com.example.domain.analytics.LocalAnalyticsEngine
import com.example.domain.model.AiInsight
import com.example.domain.model.CommandStatus
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.DeviceEvent
import com.example.domain.model.ExtinguisherItem
import com.example.domain.model.ExtinguisherType
import com.example.domain.model.FirmwareState
import com.example.domain.model.FloorPlan
import com.example.domain.model.HomeProfile
import com.example.domain.model.MapEdgeItem
import com.example.domain.model.MapNodeItem
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import com.example.domain.model.RoomSpace
import com.example.domain.model.SmartFireNode
import com.example.domain.model.SmartFireTelemetry
import com.example.domain.model.UserPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SmartFireRepository(context: Context) {

    private val db = SmartFireDatabase.getInstance(context)
    private val dao = db.smartFireDao()
    private val tokenStore = SecureTokenStore(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- STEM DEMO MODE STATE ---
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _demoNodes = MutableStateFlow<List<SmartFireNode>>(emptyList())
    private val _demoEvents = MutableStateFlow<List<DeviceEvent>>(emptyList())
    private val _demoInsights = MutableStateFlow<List<AiInsight>>(emptyList())

    // In-memory live telemetry map for registered nodes
    private val _liveTelemetry = MutableStateFlow<Map<String, SmartFireTelemetry>>(emptyMap())
    private val _liveConnectionState = MutableStateFlow<Map<String, DeviceConnectionState>>(emptyMap())
    private val _liveCommandStatus = MutableStateFlow<Map<String, CommandStatus>>(emptyMap())
    private val _liveCommandRequestIds = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        repositoryScope.launch {
            seedDefaultStructureIfEmpty()
        }
    }

    // --- HOMES & FLOORS & ROOMS ---
    val allHomes: Flow<List<HomeProfile>> = dao.getAllHomes().map { list ->
        list.map {
            HomeProfile(
                id = it.id,
                name = it.name,
                buildingType = it.buildingType,
                description = it.description,
                floorCount = it.floorCount,
                address = it.address,
                createdAt = it.createdAt
            )
        }
    }

    fun getFloorsForHome(homeId: String): Flow<List<FloorPlan>> = dao.getFloorsForHome(homeId).map { list ->
        list.map {
            FloorPlan(
                id = it.id,
                homeId = it.homeId,
                name = it.name,
                level = it.level,
                widthMeters = it.widthMeters,
                lengthMeters = it.lengthMeters,
                floorplanUri = it.floorplanUri,
                scaleMetersPerUnit = it.scaleMetersPerUnit
            )
        }
    }

    fun getRoomsForFloor(floorId: String): Flow<List<RoomSpace>> = dao.getRoomsForFloor(floorId).map { list ->
        list.map { entity ->
            RoomSpace(
                id = entity.id,
                floorId = entity.floorId,
                name = entity.name,
                type = entity.type,
                polygon = parsePolygonJson(entity.polygonJson),
                colorHex = entity.colorHex
            )
        }
    }

    val allRooms: Flow<List<RoomSpace>> = dao.getAllRooms().map { list ->
        list.map { entity ->
            RoomSpace(
                id = entity.id,
                floorId = entity.floorId,
                name = entity.name,
                type = entity.type,
                polygon = parsePolygonJson(entity.polygonJson),
                colorHex = entity.colorHex
            )
        }
    }

    suspend fun addHome(name: String, buildingType: String, description: String): String = withContext(Dispatchers.IO) {
        val id = "home_${UUID.randomUUID().toString().take(8)}"
        dao.insertHome(
            HomeEntity(
                id = id,
                name = name,
                buildingType = buildingType,
                description = description,
                floorCount = 1
            )
        )
        // Add Floor 1 automatically
        val floorId = "floor_${UUID.randomUUID().toString().take(8)}"
        dao.insertFloor(
            FloorEntity(
                id = floorId,
                homeId = id,
                name = "Tầng 1",
                level = 1
            )
        )
        id
    }

    suspend fun addFloor(homeId: String, name: String, level: Int): String = withContext(Dispatchers.IO) {
        val id = "floor_${UUID.randomUUID().toString().take(8)}"
        dao.insertFloor(
            FloorEntity(
                id = id,
                homeId = homeId,
                name = name,
                level = level
            )
        )
        id
    }

    suspend fun addRoom(floorId: String, name: String, type: String, polygon: List<Point2D>, colorHex: String): String = withContext(Dispatchers.IO) {
        val id = "room_${UUID.randomUUID().toString().take(8)}"
        dao.insertRoom(
            RoomEntity(
                id = id,
                floorId = floorId,
                name = name,
                type = type,
                polygonJson = serializePolygon(polygon),
                colorHex = colorHex
            )
        )
        id
    }

    suspend fun updateRoomPolygon(roomId: String, polygon: List<Point2D>) = withContext(Dispatchers.IO) {
        val current = dao.getRoomById(roomId) ?: return@withContext
        dao.updateRoom(current.copy(polygonJson = serializePolygon(polygon)))
    }

    suspend fun deleteRoom(roomId: String) = withContext(Dispatchers.IO) {
        val current = dao.getRoomById(roomId) ?: return@withContext
        dao.deleteRoom(current)
    }

    // --- DEVICES ---
    val allDevices: Flow<List<SmartFireNode>> = combine(
        dao.getAllDevices(),
        _liveTelemetry,
        _liveConnectionState,
        _isDemoMode
    ) { dbDevices, telemetryMap, connMap, isDemo ->
        if (isDemo) {
            _demoNodes.value
        } else {
            val cmdMap = _liveCommandStatus.value
            val reqMap = _liveCommandRequestIds.value
            dbDevices.map { entity ->
                val telem = telemetryMap[entity.deviceId] ?: SmartFireTelemetry(
                    gasRaw = 0,
                    alarm = entity.lastKnownAlarm,
                    state = entity.lastKnownState
                )
                val conn = connMap[entity.deviceId] ?: DeviceConnectionState.UNVERIFIED
                val cmd = cmdMap[entity.deviceId] ?: CommandStatus.IDLE
                val reqId = reqMap[entity.deviceId] ?: ""

                SmartFireNode(
                    deviceId = entity.deviceId,
                    displayName = entity.displayName,
                    homeId = entity.homeId,
                    floorId = entity.floorId,
                    roomId = entity.roomId,
                    firmwareRoomId = entity.firmwareRoomId,
                    ipAddress = entity.lastKnownIp,
                    lastSeenAt = entity.lastSeenAt,
                    bootId = entity.bootId,
                    latestSeq = entity.latestSeq,
                    firmwareState = FirmwareState.fromString(telem.state.ifBlank { entity.lastKnownState }),
                    connectionState = conn,
                    lastKnownAlarm = entity.lastKnownAlarm || telem.alarm,
                    telemetry = telem,
                    xNormalized = entity.xNormalized,
                    yNormalized = entity.yNormalized,
                    activeCommandStatus = cmd,
                    lastCommandRequestId = reqId
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun registerDevice(
        deviceId: String,
        displayName: String,
        homeId: String,
        floorId: String,
        roomId: String,
        ipAddress: String,
        token: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save token securely in keystore-backed store
            tokenStore.saveToken(deviceId, token)

            // Verify with real GET /v1/status
            val service = SmartFireHttpClientFactory.getService(ipAddress, token)
            val response = service.getStatus()

            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(Exception("HTTP ${response.code()}: Không thể xác thực hoặc kết nối tới node"))
            }

            val body = response.body()!!
            if (!body.deviceId.equals(deviceId, ignoreCase = true)) {
                return@withContext Result.failure(Exception("Định danh không khớp: mong đợi '$deviceId' nhưng node trả về '${body.deviceId}'"))
            }

            val entity = DeviceEntity(
                deviceId = body.deviceId,
                displayName = displayName.ifBlank { body.deviceId },
                homeId = homeId,
                floorId = floorId,
                roomId = roomId,
                firmwareRoomId = body.roomId ?: "",
                lastKnownIp = ipAddress,
                lastSeenAt = System.currentTimeMillis(),
                bootId = body.bootId,
                latestSeq = body.latestSeq,
                lastKnownState = body.status.state,
                lastKnownAlarm = body.status.alarm,
                xNormalized = 0.5f,
                yNormalized = 0.5f
            )

            dao.insertDevice(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameDevice(deviceId: String, newName: String) = withContext(Dispatchers.IO) {
        dao.renameDevice(deviceId, newName)
    }

    suspend fun updateDeviceMapPosition(deviceId: String, x: Float, y: Float) = withContext(Dispatchers.IO) {
        dao.updateDeviceMapPosition(deviceId, x, y)
    }

    suspend fun updateDeviceLocation(deviceId: String, floorId: String, roomId: String) = withContext(Dispatchers.IO) {
        dao.updateDeviceLocation(deviceId, floorId, roomId)
    }

    suspend fun updateDeviceIp(deviceId: String, newIp: String) = withContext(Dispatchers.IO) {
        dao.updateDeviceIp(deviceId, newIp)
    }

    suspend fun removeDevice(deviceId: String) = withContext(Dispatchers.IO) {
        tokenStore.removeToken(deviceId)
        dao.deleteDeviceById(deviceId)
    }

    fun getDeviceToken(deviceId: String): String? = tokenStore.getToken(deviceId)

    // --- REPOSITORY TELEMETRY & POLLING COORDINATION ---
    suspend fun updateTelemetryFromPoller(
        deviceId: String,
        telemetry: SmartFireTelemetry,
        connState: DeviceConnectionState,
        bootId: String,
        latestSeq: Long
    ) = withContext(Dispatchers.IO) {
        _liveTelemetry.value = _liveTelemetry.value + (deviceId to telemetry)
        _liveConnectionState.value = _liveConnectionState.value + (deviceId to connState)

        // Persist snapshot
        dao.insertSnapshot(
            DeviceSnapshotEntity(
                deviceId = deviceId,
                bootId = bootId,
                receivedAt = telemetry.receivedAt,
                uptimeMs = telemetry.uptimeMs,
                state = telemetry.state,
                gasRaw = telemetry.gasRaw,
                flame = telemetry.flame,
                gasReady = telemetry.gasReady,
                gasFault = telemetry.gasFault,
                alarm = telemetry.alarm,
                muted = telemetry.muted,
                acknowledged = telemetry.acknowledged,
                testing = telemetry.testing,
                hornRequested = telemetry.hornRequested,
                hornEnabled = telemetry.hornEnabled,
                gasCommissioned = telemetry.gasCommissioned
            )
        )

        // Update device in DB
        dao.updateDeviceTelemetryState(
            deviceId = deviceId,
            lastSeenAt = telemetry.receivedAt,
            bootId = bootId,
            latestSeq = latestSeq,
            state = telemetry.state,
            alarm = telemetry.alarm
        )

        // Prune old snapshots occasionally
        if (System.currentTimeMillis() % 10 == 0L) {
            dao.pruneOldSnapshots(deviceId)
        }

        // Run local analytics
        val recentSnapshots = dao.getSnapshotsSince(deviceId, System.currentTimeMillis() - 3600_000L)
        val insights = LocalAnalyticsEngine.analyzeSnapshots(deviceId, deviceId, recentSnapshots)
        insights.forEach { ins ->
            dao.insertInsight(
                AnalysisInsightEntity(
                    deviceId = ins.deviceId,
                    title = ins.title,
                    explanation = ins.explanation,
                    sourceMeasurements = ins.sourceMeasurements,
                    analysisMethod = ins.analysisMethod,
                    freshness = ins.freshness,
                    severity = ins.severity,
                    recommendation = ins.recommendation,
                    timestamp = ins.timestamp
                )
            )
        }
    }

    suspend fun updateConnectionOnly(deviceId: String, connState: DeviceConnectionState) = withContext(Dispatchers.IO) {
        _liveConnectionState.value = _liveConnectionState.value + (deviceId to connState)
    }

    // --- COMMAND DISPATCHING WITH HTTP 202 LIFECYCLE ---
    suspend fun sendCommand(
        deviceId: String,
        commandType: String // "ack", "silence", "test"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (_isDemoMode.value) {
            handleDemoCommand(deviceId, commandType)
            return@withContext Result.success("Lệnh mô phỏng đã thực thi")
        }

        val device = dao.getDeviceById(deviceId) ?: return@withContext Result.failure(Exception("Thiết bị không tồn tại"))
        val token = tokenStore.getToken(deviceId) ?: return@withContext Result.failure(Exception("Thiếu token xác thực của node"))

        val service = SmartFireHttpClientFactory.getService(device.lastKnownIp, token)
        try {
            _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.PENDING)

            val response = when (commandType.lowercase()) {
                "ack" -> service.sendAcknowledge()
                "silence" -> service.sendSilence()
                "test" -> service.sendTest()
                else -> return@withContext Result.failure(Exception("Lệnh không hỗ trợ: $commandType"))
            }

            if (response.code() == 202) {
                val body = response.body()
                val reqId = body?.requestId ?: "req_${System.currentTimeMillis()}"
                _liveCommandRequestIds.value = _liveCommandRequestIds.value + (deviceId to reqId)
                Result.success(reqId)
            } else {
                _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.REJECTED)
                Result.failure(Exception("Lỗi gửi lệnh HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.UNKNOWN_OUTCOME)
            Result.failure(e)
        }
    }

    fun updateCommandResolution(deviceId: String, status: CommandStatus) {
        _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to status)
    }

    // --- EVENTS ---
    fun getEventsForDevice(deviceId: String): Flow<List<DeviceEvent>> {
        return if (_isDemoMode.value) {
            _demoEvents.map { it.filter { ev -> ev.deviceId == deviceId } }
        } else {
            dao.getEventsForDevice(deviceId).map { list ->
                list.map {
                    DeviceEvent(
                        id = it.id,
                        deviceId = it.deviceId,
                        bootId = it.bootId,
                        seq = it.seq,
                        receivedAt = it.receivedAt,
                        kind = it.kind,
                        requestId = it.requestId,
                        result = it.result,
                        state = it.state,
                        rawPayload = it.rawPayload
                    )
                }
            }
        }
    }

    val allEvents: Flow<List<DeviceEvent>> = combine(dao.getAllEvents(), _isDemoMode, _demoEvents) { dbEvents, isDemo, demoList ->
        if (isDemo) {
            demoList
        } else {
            dbEvents.map {
                DeviceEvent(
                    id = it.id,
                    deviceId = it.deviceId,
                    bootId = it.bootId,
                    seq = it.seq,
                    receivedAt = it.receivedAt,
                    kind = it.kind,
                    requestId = it.requestId,
                    result = it.result,
                    state = it.state,
                    rawPayload = it.rawPayload
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun syncEvents(
        deviceId: String,
        bootId: String,
        latestSeq: Long,
        oldestSeq: Long,
        events: List<DeviceEventEntity>
    ) = withContext(Dispatchers.IO) {
        dao.syncEventsTransaction(deviceId, bootId, latestSeq, oldestSeq, events)
    }

    // --- EXTINGUISHERS & MAP OBJECTS ---
    fun getExtinguishersForFloor(floorId: String): Flow<List<ExtinguisherItem>> {
        return dao.getExtinguishersForFloor(floorId).map { list ->
            list.map {
                ExtinguisherItem(
                    id = it.id,
                    floorId = it.floorId,
                    roomId = it.roomId,
                    name = it.name,
                    type = try { ExtinguisherType.valueOf(it.type) } catch (_: Exception) { ExtinguisherType.POWDER_ABC },
                    position = Point2D(it.xNormalized, it.yNormalized),
                    lastInspectionDate = it.lastInspectionDate,
                    notes = it.notes
                )
            }
        }
    }

    suspend fun addExtinguisher(
        floorId: String,
        roomId: String,
        name: String,
        type: ExtinguisherType,
        x: Float,
        y: Float
    ) = withContext(Dispatchers.IO) {
        dao.insertExtinguisher(
            ExtinguisherEntity(
                id = "ext_${UUID.randomUUID().toString().take(8)}",
                floorId = floorId,
                roomId = roomId,
                name = name,
                type = type.name,
                xNormalized = x,
                yNormalized = y
            )
        )
    }

    suspend fun deleteExtinguisher(id: String) = withContext(Dispatchers.IO) {
        val list = dao.getExtinguishersForFloor("").firstOrNull() // query by id delete
        // Direct query or helper
    }

    fun getMapObjectsForFloor(floorId: String): Flow<List<MapNodeItem>> {
        return dao.getMapObjectsForFloor(floorId).map { list ->
            list.map {
                MapNodeItem(
                    id = it.id,
                    floorId = it.floorId,
                    name = it.name,
                    type = try { MapObjectType.valueOf(it.type) } catch (_: Exception) { MapObjectType.CORRIDOR_NODE },
                    position = Point2D(it.xNormalized, it.yNormalized)
                )
            }
        }
    }

    suspend fun addMapObject(floorId: String, name: String, type: MapObjectType, x: Float, y: Float) = withContext(Dispatchers.IO) {
        dao.insertMapObject(
            MapObjectEntity(
                id = "node_${UUID.randomUUID().toString().take(8)}",
                floorId = floorId,
                name = name,
                type = type.name,
                xNormalized = x,
                yNormalized = y
            )
        )
    }

    fun getMapEdgesForFloor(floorId: String): Flow<List<MapEdgeItem>> {
        return dao.getMapEdgesForFloor(floorId).map { list ->
            list.map {
                MapEdgeItem(
                    id = it.id,
                    floorId = it.floorId,
                    fromNodeId = it.fromNodeId,
                    toNodeId = it.toNodeId,
                    distanceMeters = it.distanceMeters,
                    enabled = it.enabled
                )
            }
        }
    }

    suspend fun addMapEdge(floorId: String, fromId: String, toId: String, distMeters: Float) = withContext(Dispatchers.IO) {
        dao.insertMapEdge(
            MapEdgeEntity(
                id = "edge_${UUID.randomUUID().toString().take(8)}",
                floorId = floorId,
                fromNodeId = fromId,
                toNodeId = toId,
                distanceMeters = distMeters
            )
        )
    }

    // --- USER LOCATION ---
    fun getUserLocation(homeId: String): Flow<UserPosition?> {
        return dao.getUserLocation(homeId).map { entity ->
            entity?.let {
                UserPosition(
                    homeId = it.homeId,
                    floorId = it.floorId,
                    roomId = it.roomId,
                    position = if (it.xNormalized != null && it.yNormalized != null) Point2D(it.xNormalized, it.yNormalized) else null,
                    source = it.source,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    suspend fun setUserLocation(homeId: String, floorId: String, roomId: String?, x: Float?, y: Float?, source: String) = withContext(Dispatchers.IO) {
        dao.setUserLocation(
            UserLocationEntity(
                homeId = homeId,
                floorId = floorId,
                roomId = roomId,
                xNormalized = x,
                yNormalized = y,
                source = source,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // --- INSIGHTS ---
    val allInsights: Flow<List<AiInsight>> = combine(dao.getAllInsights(), _isDemoMode, _demoInsights) { dbInsights, isDemo, demoList ->
        if (isDemo) {
            demoList
        } else {
            dbInsights.map {
                AiInsight(
                    id = it.id,
                    deviceId = it.deviceId,
                    title = it.title,
                    explanation = it.explanation,
                    sourceMeasurements = it.sourceMeasurements,
                    analysisMethod = it.analysisMethod,
                    freshness = it.freshness,
                    severity = it.severity,
                    recommendation = it.recommendation,
                    timestamp = it.timestamp
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    // --- STEM DEMO MODE IMPLEMENTATION ---
    fun toggleDemoMode(active: Boolean) {
        _isDemoMode.value = active
        if (active) {
            initializeDemoScenario()
        }
    }

    private fun initializeDemoScenario() {
        val now = System.currentTimeMillis()
        val node1 = SmartFireNode(
            deviceId = "smartfire-node-01",
            displayName = "Cảm biến Phòng Khách",
            homeId = "demo_home",
            floorId = "demo_floor_1",
            roomId = "demo_room_living",
            firmwareRoomId = "room-101",
            ipAddress = "192.168.1.150",
            lastSeenAt = now,
            bootId = "boot-stem-01",
            latestSeq = 12L,
            firmwareState = FirmwareState.NORMAL,
            connectionState = DeviceConnectionState.ONLINE_FRESH,
            lastKnownAlarm = false,
            telemetry = SmartFireTelemetry(
                uptimeMs = 345000L,
                gasRaw = 1040,
                flame = false,
                gasReady = true,
                gasFault = false,
                alarm = false,
                hornEnabled = true,
                gasCommissioned = true,
                receivedAt = now
            ),
            xNormalized = 0.35f,
            yNormalized = 0.40f
        )

        val node2 = SmartFireNode(
            deviceId = "smartfire-node-02",
            displayName = "Cảm biến Nhà Bếp",
            homeId = "demo_home",
            floorId = "demo_floor_1",
            roomId = "demo_room_kitchen",
            firmwareRoomId = "room-102",
            ipAddress = "192.168.1.151",
            lastSeenAt = now,
            bootId = "boot-stem-02",
            latestSeq = 24L,
            firmwareState = FirmwareState.ALARM,
            connectionState = DeviceConnectionState.ONLINE_FRESH,
            lastKnownAlarm = true,
            telemetry = SmartFireTelemetry(
                uptimeMs = 890000L,
                gasRaw = 2240,
                flame = true,
                gasReady = true,
                gasFault = false,
                alarm = true,
                hornRequested = true,
                hornEnabled = true,
                gasCommissioned = true,
                receivedAt = now
            ),
            xNormalized = 0.72f,
            yNormalized = 0.35f
        )

        val node3 = SmartFireNode(
            deviceId = "smartfire-node-03",
            displayName = "Cảm biến Phòng Học STEM",
            homeId = "demo_home",
            floorId = "demo_floor_1",
            roomId = "demo_room_lab",
            firmwareRoomId = "room-103",
            ipAddress = "192.168.1.152",
            lastSeenAt = now - 60000L,
            bootId = "boot-stem-03",
            latestSeq = 5L,
            firmwareState = FirmwareState.WARMING,
            connectionState = DeviceConnectionState.STALE,
            lastKnownAlarm = false,
            telemetry = SmartFireTelemetry(
                uptimeMs = 12000L,
                gasRaw = 890,
                flame = false,
                gasReady = false,
                gasFault = false,
                alarm = false,
                hornEnabled = true,
                gasCommissioned = false,
                receivedAt = now - 60000L
            ),
            xNormalized = 0.50f,
            yNormalized = 0.75f
        )

        _demoNodes.value = listOf(node1, node2, node3)

        _demoEvents.value = listOf(
            DeviceEvent(
                id = 1,
                deviceId = "smartfire-node-02",
                bootId = "boot-stem-02",
                seq = 24,
                receivedAt = now,
                kind = "state",
                result = "applied",
                state = "ALARM",
                rawPayload = "flame=true, gas_raw=2240"
            ),
            DeviceEvent(
                id = 2,
                deviceId = "smartfire-node-02",
                bootId = "boot-stem-02",
                seq = 23,
                receivedAt = now - 15000L,
                kind = "state",
                result = "applied",
                state = "SUSPECTED",
                rawPayload = "gas_raw=1850"
            ),
            DeviceEvent(
                id = 3,
                deviceId = "smartfire-node-01",
                bootId = "boot-stem-01",
                seq = 12,
                receivedAt = now - 30000L,
                kind = "state",
                result = "applied",
                state = "NORMAL",
                rawPayload = "gas_raw=1040"
            )
        )

        _demoInsights.value = listOf(
            AiInsight(
                id = 1,
                deviceId = "smartfire-node-02",
                title = "CẢNH BÁO: Phát hiện ngọn lửa và nồng độ khí gia tăng tại Nhà Bếp",
                explanation = "Cảm biến Flame Sensor DO=true và MQ-2 ADC raw tăng lên 2240 (vượt đường cơ sở 1100). Node ESP32 đã tự động kích hoạt còi báo động 12V.",
                sourceMeasurements = "Flame=Active, MQ-2=2240 ADC (+103% baseline)",
                analysisMethod = "Phát hiện ngưỡng cảm biến kép (Dual-Sensor Fusion)",
                freshness = "Thời gian thực",
                severity = "CRITICAL",
                recommendation = "Kiểm tra nhà bếp ngay lập tức. Nếu có đám cháy, sử dụng Bình Bột Hành Lang theo bản đồ chỉ dẫn."
            ),
            AiInsight(
                id = 2,
                deviceId = "smartfire-node-03",
                title = "Node Phòng Học STEM đang trong chu kỳ sấy nóng",
                explanation = "gas_ready=false. Node mới khởi động lại 12 giây trước và đang gia nhiệt cảm biến MQ-2.",
                sourceMeasurements = "Uptime=12s, gas_ready=false",
                analysisMethod = "Kiểm tra trạng thái khởi động firmware",
                freshness = "1 phút trước",
                severity = "INFO",
                recommendation = "Đợi 30 giây để hoàn tất sấy nóng."
            )
        )
    }

    fun triggerDemoScenario(scenario: String) {
        val now = System.currentTimeMillis()
        val current = _demoNodes.value.toMutableList()
        when (scenario) {
            "KITCHEN_ALARM" -> {
                val idx = current.indexOfFirst { it.deviceId == "smartfire-node-02" }
                if (idx >= 0) {
                    val n = current[idx]
                    current[idx] = n.copy(
                        firmwareState = FirmwareState.ALARM,
                        connectionState = DeviceConnectionState.ONLINE_FRESH,
                        lastKnownAlarm = true,
                        telemetry = n.telemetry.copy(
                            alarm = true,
                            flame = true,
                            gasRaw = 2500,
                            receivedAt = now
                        )
                    )
                }
            }
            "DISCONNECT_DURING_ALARM" -> {
                // Important invariant test: When disconnected during ALARM, stay ALARM + OFFLINE
                val idx = current.indexOfFirst { it.deviceId == "smartfire-node-02" }
                if (idx >= 0) {
                    val n = current[idx]
                    current[idx] = n.copy(
                        connectionState = DeviceConnectionState.OFFLINE,
                        lastKnownAlarm = true // STILL ALARM! Never revert to green normal
                    )
                }
            }
            "CLEAR_NORMAL" -> {
                val idx = current.indexOfFirst { it.deviceId == "smartfire-node-02" }
                if (idx >= 0) {
                    val n = current[idx]
                    current[idx] = n.copy(
                        firmwareState = FirmwareState.NORMAL,
                        connectionState = DeviceConnectionState.ONLINE_FRESH,
                        lastKnownAlarm = false,
                        telemetry = n.telemetry.copy(
                            alarm = false,
                            flame = false,
                            gasRaw = 950,
                            receivedAt = now
                        )
                    )
                }
            }
            "SIMULATE_REBOOT" -> {
                val idx = current.indexOfFirst { it.deviceId == "smartfire-node-01" }
                if (idx >= 0) {
                    val n = current[idx]
                    current[idx] = n.copy(
                        bootId = "boot-new-${UUID.randomUUID().toString().take(6)}",
                        latestSeq = 1,
                        firmwareState = FirmwareState.WARMING,
                        telemetry = n.telemetry.copy(uptimeMs = 1500, gasReady = false)
                    )
                }
            }
        }
        _demoNodes.value = current
    }

    private fun handleDemoCommand(deviceId: String, commandType: String) {
        val current = _demoNodes.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceId == deviceId }
        if (idx >= 0) {
            val node = current[idx]
            val now = System.currentTimeMillis()
            when (commandType.lowercase()) {
                "ack" -> {
                    current[idx] = node.copy(
                        telemetry = node.telemetry.copy(acknowledged = true),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
                "silence" -> {
                    current[idx] = node.copy(
                        telemetry = node.telemetry.copy(muted = true),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
                "test" -> {
                    current[idx] = node.copy(
                        telemetry = node.telemetry.copy(testing = true),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
            }
            _demoNodes.value = current
        }
    }

    // --- SEED INITIAL DB IF EMPTY ---
    private suspend fun seedDefaultStructureIfEmpty() {
        val existingHomes = dao.getAllHomes().firstOrNull()
        if (existingHomes.isNullOrEmpty()) {
            val homeId = "home_main"
            dao.insertHome(
                HomeEntity(
                    id = homeId,
                    name = "STEM của SOV labs.",
                    buildingType = "Phòng thí nghiệm STEM",
                    description = "Hệ thống cảnh báo cháy thông minh",
                    floorCount = 2
                )
            )

            val floor1Id = "floor_1"
            dao.insertFloor(
                FloorEntity(
                    id = floor1Id,
                    homeId = homeId,
                    name = "Tầng 1 (Trệt)",
                    level = 1,
                    widthMeters = 10.0f,
                    lengthMeters = 15.0f,
                    scaleMetersPerUnit = 10.0f
                )
            )

            // Seed 3 rooms
            val roomLiving = "room_living"
            val roomKitchen = "room_kitchen"
            val roomCorridor = "room_corridor"

            dao.insertRoom(
                RoomEntity(
                    id = roomLiving,
                    floorId = floor1Id,
                    name = "Phòng Khách",
                    type = "LIVING_ROOM",
                    polygonJson = serializePolygon(
                        listOf(
                            Point2D(0.1f, 0.1f),
                            Point2D(0.55f, 0.1f),
                            Point2D(0.55f, 0.6f),
                            Point2D(0.1f, 0.6f)
                        )
                    ),
                    colorHex = "#1E293B"
                )
            )

            dao.insertRoom(
                RoomEntity(
                    id = roomKitchen,
                    floorId = floor1Id,
                    name = "Nhà Bếp",
                    type = "KITCHEN",
                    polygonJson = serializePolygon(
                        listOf(
                            Point2D(0.6f, 0.1f),
                            Point2D(0.95f, 0.1f),
                            Point2D(0.95f, 0.6f),
                            Point2D(0.6f, 0.6f)
                        )
                    ),
                    colorHex = "#331E1E"
                )
            )

            dao.insertRoom(
                RoomEntity(
                    id = roomCorridor,
                    floorId = floor1Id,
                    name = "Hành lang & Lối ra",
                    type = "CORRIDOR",
                    polygonJson = serializePolygon(
                        listOf(
                            Point2D(0.1f, 0.65f),
                            Point2D(0.95f, 0.65f),
                            Point2D(0.95f, 0.95f),
                            Point2D(0.1f, 0.95f)
                        )
                    ),
                    colorHex = "#0F172A"
                )
            )

            // Seed default extinguisher
            dao.insertExtinguisher(
                ExtinguisherEntity(
                    id = "ext_1",
                    floorId = floor1Id,
                    roomId = roomCorridor,
                    name = "Bình Bột ABC Hành Lang",
                    type = "POWDER_ABC",
                    xNormalized = 0.5f,
                    yNormalized = 0.75f,
                    lastInspectionDate = "2026-10",
                    notes = "Bình 4kg kiểm định định kỳ"
                )
            )

            // Seed Map Object Exit
            dao.insertMapObject(
                MapObjectEntity(
                    id = "exit_main",
                    floorId = floor1Id,
                    name = "Cửa Thoát Hiểm Chính",
                    type = "EXIT",
                    xNormalized = 0.5f,
                    yNormalized = 0.95f
                )
            )
        }
    }

    private fun parsePolygonJson(json: String): List<Point2D> {
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<Point2D>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Point2D(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat()))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializePolygon(points: List<Point2D>): String {
        val array = JSONArray()
        points.forEach {
            val obj = JSONObject()
            obj.put("x", it.x)
            obj.put("y", it.y)
            array.put(obj)
        }
        return array.toString()
    }
}
