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
import com.example.domain.model.SensorSnapshotPoint
import com.example.domain.model.SmartFireNode
import com.example.domain.model.SmartFireTelemetry
import com.example.domain.model.UserPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    companion object {
        const val DEFAULT_TOKEN = "b57701282c9b134039851e5ba993be6a3f025322f437644f"
    }

    private val db = SmartFireDatabase.getInstance(context)
    private val dao = db.smartFireDao()
    private val tokenStore = SecureTokenStore(context)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs = context.getSharedPreferences("smartfire_settings", Context.MODE_PRIVATE)

    // --- SIMULATED LOGIN STATE (Giả lập đăng nhập đàng hoàng cho STEM của SOV labs.) ---
    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean("is_logged_in_session", false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInEmail = MutableStateFlow(prefs.getString("logged_in_email", "admin@sovlabs.edu.vn") ?: "admin@sovlabs.edu.vn")
    val loggedInEmail: StateFlow<String> = _loggedInEmail.asStateFlow()

    private val _loggedInRole = MutableStateFlow(prefs.getString("logged_in_role", "Kỹ sư STEM · SOV Labs") ?: "Kỹ sư STEM · SOV Labs")
    val loggedInRole: StateFlow<String> = _loggedInRole.asStateFlow()

    fun loginSimulated(email: String, role: String) {
        val cleanEmail = email.trim().ifBlank { "admin@sovlabs.edu.vn" }
        val cleanRole = role.trim().ifBlank { "Kỹ sư STEM · SOV Labs" }
        _loggedInEmail.value = cleanEmail
        _loggedInRole.value = cleanRole
        _isLoggedIn.value = true
        prefs.edit()
            .putBoolean("is_logged_in_session", true)
            .putString("logged_in_email", cleanEmail)
            .putString("logged_in_role", cleanRole)
            .apply()
    }

    fun logoutSimulated() {
        _isLoggedIn.value = false
        prefs.edit().putBoolean("is_logged_in_session", false).apply()
    }

    // --- CROSS-SCREEN MAP NAVIGATION INTENT ---
    private val _mapNavigationRequest = MutableStateFlow<com.example.domain.model.MapNavigationRequest?>(null)
    val mapNavigationRequest: StateFlow<com.example.domain.model.MapNavigationRequest?> = _mapNavigationRequest.asStateFlow()

    fun requestMapNavigation(request: com.example.domain.model.MapNavigationRequest?) {
        _mapNavigationRequest.value = request
    }

    // --- SYSTEM SETTINGS STATE FLOWS ---
    private val _gasThresholdAdc = MutableStateFlow(prefs.getInt("gas_threshold_adc", 1800))
    val gasThresholdAdc: StateFlow<Int> = _gasThresholdAdc.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(prefs.getLong("polling_interval_ms", 1500L))
    val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()

    private val _httpTimeoutSeconds = MutableStateFlow(prefs.getInt("http_timeout_sec", 2))
    val httpTimeoutSeconds: StateFlow<Int> = _httpTimeoutSeconds.asStateFlow()

    private val _phoneAlarmSoundEnabled = MutableStateFlow(prefs.getBoolean("phone_alarm_sound", true))
    val phoneAlarmSoundEnabled: StateFlow<Boolean> = _phoneAlarmSoundEnabled.asStateFlow()

    private val _autoRouteOnAlarm = MutableStateFlow(prefs.getBoolean("auto_route_on_alarm", true))
    val autoRouteOnAlarm: StateFlow<Boolean> = _autoRouteOnAlarm.asStateFlow()

    private val _stepStrideMeters = MutableStateFlow(prefs.getFloat("step_stride_m", 0.65f))
    val stepStrideMeters: StateFlow<Float> = _stepStrideMeters.asStateFlow()

    fun updateGasThresholdAdc(value: Int) {
        val clamped = value.coerceIn(1000, 3200)
        _gasThresholdAdc.value = clamped
        prefs.edit().putInt("gas_threshold_adc", clamped).apply()
    }

    fun updatePollingIntervalMs(value: Long) {
        val clamped = value.coerceIn(800L, 10000L)
        _pollingIntervalMs.value = clamped
        prefs.edit().putLong("polling_interval_ms", clamped).apply()
    }

    fun updateHttpTimeoutSeconds(value: Int) {
        val clamped = value.coerceIn(1, 10)
        _httpTimeoutSeconds.value = clamped
        prefs.edit().putInt("http_timeout_sec", clamped).apply()
    }

    fun updatePhoneAlarmSound(enabled: Boolean) {
        _phoneAlarmSoundEnabled.value = enabled
        prefs.edit().putBoolean("phone_alarm_sound", enabled).apply()
    }

    fun updateAutoRouteOnAlarm(enabled: Boolean) {
        _autoRouteOnAlarm.value = enabled
        prefs.edit().putBoolean("auto_route_on_alarm", enabled).apply()
    }

    fun updateStepStrideMeters(stride: Float) {
        val clamped = stride.coerceIn(0.35f, 1.2f)
        _stepStrideMeters.value = clamped
        prefs.edit().putFloat("step_stride_m", clamped).apply()
    }

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
            startDynamicMultiSensorEngine()
        }
    }

    /**
     * Động cơ cập nhật đa thông số thời gian thực (Multi-Parameter Probabilistic Telemetry Engine)
     * Kết hợp Nhiệt độ (°C), Tốc độ tăng nhiệt (RoR), Khói MQ-2 (ADC), Khí CO (ppm), Độ ẩm (%RH),
     * Bức xạ hồng ngoại (IR) và Tín hiệu RSSI (dBm) để tính xác suất cháy tổng hợp P(Fire).
     */
    private suspend fun startDynamicMultiSensorEngine() {
        while (true) {
            delay(2500L)
            val now = System.currentTimeMillis()
            if (_isDemoMode.value) {
                val updatedDemo = _demoNodes.value.map { node ->
                    val cur = node.telemetry
                    val isCurrentlyAlarm = node.lastKnownAlarm || cur.alarm
                    val jitterTemp = ((-3..4).random() * 0.1f)
                    val jitterGas = (-18..22).random()
                    val jitterCo = (-2..3).random()
                    val jitterHum = ((-4..4).random() * 0.15f)
                    val jitterRssi = (-2..2).random()

                    val baseTemp = cur.temperatureCelsius ?: 28.5f
                    val baseRor = cur.tempRateOfRiseCPerMin ?: 0.4f
                    val baseCo = cur.coPpm ?: 12
                    val baseHum = cur.humidityPercent ?: 60.0f
                    val baseIr = cur.irFlameIntensity ?: 0.03f

                    val nextTemp = if (isCurrentlyAlarm) {
                        (baseTemp + 0.45f).coerceIn(58f, 105f)
                    } else {
                        (baseTemp + jitterTemp).coerceIn(26.5f, 34.0f)
                    }
                    val nextRor = if (isCurrentlyAlarm) {
                        (baseRor + 0.3f).coerceIn(6.5f, 18.0f)
                    } else {
                        (0.3f + ((-2..3).random() * 0.1f)).coerceIn(0.1f, 1.8f)
                    }
                    val nextGas = if (isCurrentlyAlarm) {
                        (cur.gasRaw + 25).coerceIn(1850, 3600)
                    } else {
                        (cur.gasRaw + jitterGas).coerceIn(720, 1280)
                    }
                    val nextCo = if (isCurrentlyAlarm) {
                        (baseCo + 4).coerceIn(95, 320)
                    } else {
                        (baseCo + jitterCo).coerceIn(6, 28)
                    }
                    val nextHum = if (isCurrentlyAlarm) {
                        (baseHum - 0.6f).coerceIn(18f, 38f)
                    } else {
                        (baseHum + jitterHum).coerceIn(52f, 68f)
                    }
                    val nextIr = if (isCurrentlyAlarm) {
                        (baseIr + 0.02f).coerceIn(0.78f, 0.99f)
                    } else {
                        0.03f
                    }
                    val nextRssi = (cur.rssiDbm + jitterRssi).coerceIn(-78, -42)
                    val alarmStart = if (isCurrentlyAlarm) {
                        if (cur.alarmStartedAtMs > 0L) cur.alarmStartedAtMs else now - 3000L
                    } else {
                        0L
                    }

                    val candidateTelem = cur.copy(
                        temperatureCelsius = nextTemp,
                        tempRateOfRiseCPerMin = nextRor,
                        gasRaw = nextGas,
                        coPpm = nextCo,
                        humidityPercent = nextHum,
                        irFlameIntensity = nextIr,
                        rssiDbm = nextRssi,
                        alarmStartedAtMs = alarmStart,
                        receivedAt = now
                    )
                    val prob = candidateTelem.calculateFireProbability(_gasThresholdAdc.value).totalProbabilityPercent
                    val probAlarm = prob >= 75f
                    val probState = when {
                        prob >= 75f -> "ALARM"
                        prob >= 45f -> "SUSPECTED"
                        else -> "NORMAL"
                    }
                    val finalTelem = candidateTelem.copy(
                        alarm = probAlarm,
                        flame = probAlarm || nextIr >= 0.65f,
                        hornRequested = probAlarm && !candidateTelem.muted,
                        state = probState,
                        alarmStartedAtMs = if (probAlarm) (if (alarmStart > 0L) alarmStart else now) else 0L
                    )
                    dao.insertSnapshot(
                        DeviceSnapshotEntity(
                            deviceId = node.deviceId,
                            bootId = node.bootId.ifBlank { "boot-demo" },
                            receivedAt = now,
                            uptimeMs = finalTelem.uptimeMs + 2500L,
                            state = finalTelem.state,
                            gasRaw = finalTelem.gasRaw,
                            flame = finalTelem.flame,
                            gasReady = true,
                            gasFault = false,
                            alarm = finalTelem.alarm,
                            muted = finalTelem.muted,
                            acknowledged = finalTelem.acknowledged,
                            testing = finalTelem.testing,
                            hornRequested = finalTelem.hornRequested,
                            hornEnabled = true,
                            gasCommissioned = true
                        )
                    )
                    node.copy(
                        firmwareState = FirmwareState.fromString(probState),
                        lastKnownAlarm = probAlarm,
                        telemetry = finalTelem
                    )
                }
                _demoNodes.value = updatedDemo
            }
        }
    }

    /**
     * Cho phép điều chỉnh tổ hợp đa thông số (Nhiệt độ, Khói MQ-2, Khí CO, Độ ẩm, Bức xạ IR)
     * để kiểm nghiệm mô hình tính xác suất kết hợp (Multi-Parameter Probabilistic Sensor Fusion).
     */
    suspend fun simulateMultiSensorParameters(
        deviceId: String,
        temperatureCelsius: Float,
        tempRateOfRiseCPerMin: Float,
        gasRaw: Int,
        coPpm: Int,
        humidityPercent: Float,
        irFlameIntensity: Float
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val tempTelem = SmartFireTelemetry(
            uptimeMs = 120_000L,
            temperatureCelsius = temperatureCelsius,
            tempRateOfRiseCPerMin = tempRateOfRiseCPerMin,
            gasRaw = gasRaw,
            coPpm = coPpm,
            humidityPercent = humidityPercent,
            irFlameIntensity = irFlameIntensity,
            rssiDbm = -50,
            gasReady = true,
            hornEnabled = true,
            gasCommissioned = true,
            receivedAt = now
        )
        val breakdown = tempTelem.calculateFireProbability(_gasThresholdAdc.value)
        val isAlarm = breakdown.totalProbabilityPercent >= 75f
        val newState = when {
            breakdown.totalProbabilityPercent >= 75f -> "ALARM"
            breakdown.totalProbabilityPercent >= 45f -> "SUSPECTED"
            else -> "NORMAL"
        }
        val finalTelem = tempTelem.copy(
            state = newState,
            alarm = isAlarm,
            flame = isAlarm || irFlameIntensity >= 0.65f,
            hornRequested = isAlarm,
            alarmStartedAtMs = if (isAlarm) now else 0L
        )

        dao.insertSnapshot(
            DeviceSnapshotEntity(
                deviceId = deviceId,
                bootId = "boot-sim",
                receivedAt = now,
                uptimeMs = 120_000L,
                state = newState,
                gasRaw = gasRaw,
                flame = finalTelem.flame,
                gasReady = true,
                gasFault = false,
                alarm = isAlarm,
                muted = false,
                acknowledged = false,
                testing = false,
                hornRequested = isAlarm,
                hornEnabled = true,
                gasCommissioned = true
            )
        )

        val simInsight = AiInsight(
            id = now,
            deviceId = deviceId,
            title = when {
                isAlarm -> "BÁO ĐỘNG ĐA THÔNG SỐ: P(Cháy)=${String.format(java.util.Locale.US, "%.1f%%", breakdown.totalProbabilityPercent)} tại $deviceId"
                breakdown.totalProbabilityPercent >= 45f -> "CẢNH BÁO SỚM: Biến động nhiệt (${String.format(java.util.Locale.US, "%.1f°C", temperatureCelsius)}) & khói ($gasRaw ADC)"
                else -> "Phân tích đa cảm biến ổn định (P=${String.format(java.util.Locale.US, "%.1f%%", breakdown.totalProbabilityPercent)})"
            },
            explanation = "Tổ hợp 5 thông số: Nhiệt=${String.format(java.util.Locale.US, "%.1f°C", temperatureCelsius)} (+${String.format(java.util.Locale.US, "%.1f°/p", tempRateOfRiseCPerMin)}), MQ-2=$gasRaw ADC (Ngưỡng ${_gasThresholdAdc.value}), CO=$coPpm ppm, Ẩm=${String.format(java.util.Locale.US, "%.0f%%", humidityPercent)}.",
            sourceMeasurements = "Temp=${String.format(java.util.Locale.US, "%.1f°C", temperatureCelsius)}, Gas=$gasRaw ADC, CO=${coPpm}ppm, IR=${(irFlameIntensity * 100).toInt()}%",
            analysisMethod = "Mô hình kết hợp xác suất Bayesian / Logistic 5 thông số",
            freshness = "Vừa cập nhật",
            severity = when {
                isAlarm -> "CRITICAL"
                breakdown.totalProbabilityPercent >= 45f -> "WARNING"
                else -> "INFO"
            },
            recommendation = if (isAlarm) {
                "Bấm 'Dẫn đường 2 chặng (Bình CC ➔ Đám cháy)' trên Bản đồ 2D để xử lý ngay."
            } else {
                "Tiếp tục giám sát tự động qua mạng LAN."
            },
            timestamp = now
        )

        dao.deleteInsightsForDevice(deviceId)
        dao.insertInsight(
            AnalysisInsightEntity(
                deviceId = simInsight.deviceId,
                title = simInsight.title,
                explanation = simInsight.explanation,
                sourceMeasurements = simInsight.sourceMeasurements,
                analysisMethod = simInsight.analysisMethod,
                freshness = simInsight.freshness,
                severity = simInsight.severity,
                recommendation = simInsight.recommendation,
                timestamp = simInsight.timestamp
            )
        )

        if (_isDemoMode.value) {
            _demoNodes.value = _demoNodes.value.map { n ->
                if (n.deviceId == deviceId) {
                    n.copy(
                        firmwareState = FirmwareState.fromString(newState),
                        lastKnownAlarm = isAlarm,
                        telemetry = finalTelem
                    )
                } else n
            }
            _demoInsights.value = listOf(simInsight) + _demoInsights.value.filterNot { it.deviceId == deviceId }.take(8)
        } else {
            _liveTelemetry.value = _liveTelemetry.value + (deviceId to finalTelem)
            val device = dao.getDeviceById(deviceId)
            if (device != null) {
                dao.updateDeviceTelemetryState(
                    deviceId = deviceId,
                    lastSeenAt = now,
                    bootId = device.bootId,
                    latestSeq = device.latestSeq + 1,
                    state = newState,
                    alarm = isAlarm
                )
            }
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

    suspend fun updateFloorDimensions(floorId: String, widthMeters: Float, lengthMeters: Float) = withContext(Dispatchers.IO) {
        if (widthMeters <= 0f || lengthMeters <= 0f) {
            dao.updateFloorDimensions(floorId, 0f, 0f)
        } else {
            dao.updateFloorDimensions(floorId, widthMeters.coerceIn(3f, 200f), lengthMeters.coerceIn(3f, 200f))
        }
    }

    suspend fun saveBoundaryPolygon(
        floorId: String,
        polygon: List<Point2D>,
        widthMeters: Float,
        lengthMeters: Float
    ) = withContext(Dispatchers.IO) {
        dao.updateFloorDimensions(floorId, widthMeters.coerceIn(3f, 200f), lengthMeters.coerceIn(3f, 200f))
        dao.clearRoomsForFloor(floorId)
        if (polygon.size >= 3) {
            dao.insertRoom(
                RoomEntity(
                    id = "boundary_$floorId",
                    floorId = floorId,
                    name = "Khung Khoanh Vùng Nhà",
                    type = "MAIN_BOUNDARY",
                    polygonJson = serializePolygon(polygon),
                    colorHex = "#2563EB"
                )
            )
        }
    }

    suspend fun clearObjectsOnlyForFloor(floorId: String) = withContext(Dispatchers.IO) {
        dao.clearMapObjectsForFloor(floorId)
        dao.clearExtinguishersForFloor(floorId)
        dao.clearMapEdgesForFloor(floorId)
    }

    suspend fun clearMapConfiguration(floorId: String) = withContext(Dispatchers.IO) {
        dao.clearMapObjectsForFloor(floorId)
        dao.clearExtinguishersForFloor(floorId)
        dao.clearMapEdgesForFloor(floorId)
        dao.clearRoomsForFloor(floorId)
    }

    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        dao.clearAllEvents()
        _demoEvents.value = emptyList()
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
        _isDemoMode,
        _demoNodes
    ) { dbDevices, telemetryMap, connMap, isDemo, demoNodesList ->
        if (isDemo) {
            demoNodesList
        } else {
            val cmdMap = _liveCommandStatus.value
            val reqMap = _liveCommandRequestIds.value
            dbDevices.map { entity ->
                val isDemoFlag = entity.firmwareRoomId.startsWith("demo") ||
                    entity.firmwareRoomId.startsWith("sov-") ||
                    entity.displayName.contains("DEMO", ignoreCase = true)

                val telem = telemetryMap[entity.deviceId] ?: SmartFireTelemetry(
                    gasRaw = if (entity.lastKnownAlarm) 2350 else 960,
                    temperatureCelsius = if (entity.lastKnownAlarm) 74.8f else 28.6f,
                    flame = entity.lastKnownAlarm,
                    gasReady = true,
                    alarm = entity.lastKnownAlarm,
                    hornRequested = entity.lastKnownAlarm,
                    hornEnabled = true,
                    state = entity.lastKnownState.ifBlank { "NORMAL" }
                )
                val conn = connMap[entity.deviceId] ?: DeviceConnectionState.ONLINE_FRESH
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
                    lastCommandRequestId = reqId,
                    isDemoSensor = isDemoFlag
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
        token: String,
        xNormalized: Float = 0.5f,
        yNormalized: Float = 0.5f,
        isDemo: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = deviceId.trim().ifBlank { "smartfire-node-${UUID.randomUUID().toString().take(4)}" }
        val rawName = displayName.trim().ifBlank { cleanId }
        val cleanName = if (isDemo && !rawName.contains("[DEMO]", ignoreCase = true)) "[DEMO] $rawName" else rawName
        val cleanIp = ipAddress.trim().ifBlank { "192.168.1.150" }
        val cleanToken = token.trim().ifBlank { DEFAULT_TOKEN }

        if (_isDemoMode.value) {
            val newNode = SmartFireNode(
                deviceId = cleanId,
                displayName = cleanName,
                homeId = homeId,
                floorId = floorId,
                roomId = roomId,
                firmwareRoomId = if (isDemo) "demo-node" else "user-node",
                ipAddress = cleanIp,
                lastSeenAt = System.currentTimeMillis(),
                bootId = "boot-${UUID.randomUUID().toString().take(4)}",
                latestSeq = 1L,
                firmwareState = FirmwareState.NORMAL,
                connectionState = DeviceConnectionState.ONLINE_FRESH,
                lastKnownAlarm = false,
                telemetry = SmartFireTelemetry(
                    uptimeMs = 60_000L,
                    gasRaw = 940,
                    temperatureCelsius = 28.4f,
                    flame = false,
                    gasReady = true,
                    alarm = false,
                    hornEnabled = true,
                    gasCommissioned = true,
                    state = "NORMAL",
                    receivedAt = System.currentTimeMillis()
                ),
                xNormalized = xNormalized,
                yNormalized = yNormalized,
                isDemoSensor = isDemo || cleanName.contains("DEMO", ignoreCase = true)
            )
            _demoNodes.value = _demoNodes.value + newNode
        }

        try {
            tokenStore.saveToken(cleanId, cleanToken)
        } catch (_: Throwable) {
            // Ignore keystore edge cases on emulators
        }

        // Attempt live HTTP check first, but fallback gracefully to local registration if ESP32 is offline
        try {
            val service = SmartFireHttpClientFactory.getService(cleanIp, cleanToken)
            val response = service.getStatus()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val finalId = body.deviceId.ifBlank { cleanId }
                try {
                    tokenStore.saveToken(finalId, cleanToken)
                } catch (_: Throwable) {}
                val now = System.currentTimeMillis()
                val st = body.status
                val entity = DeviceEntity(
                    deviceId = finalId,
                    displayName = cleanName,
                    homeId = homeId,
                    floorId = floorId,
                    roomId = roomId,
                    firmwareRoomId = body.roomId ?: (if (isDemo) "demo-node" else "user-node"),
                    lastKnownIp = cleanIp,
                    lastSeenAt = now,
                    bootId = body.bootId,
                    latestSeq = body.latestSeq,
                    lastKnownState = st.state,
                    lastKnownAlarm = st.alarm,
                    xNormalized = xNormalized,
                    yNormalized = yNormalized
                )
                dao.insertDevice(entity)
                _liveTelemetry.value = _liveTelemetry.value + (finalId to SmartFireTelemetry(
                    uptimeMs = st.uptimeMs,
                    temperatureCelsius = st.temperatureCelsius,
                    humidityPercent = st.humidityPercent,
                    coPpm = st.coPpm,
                    tempRateOfRiseCPerMin = st.tempRateOfRise,
                    irFlameIntensity = if (st.flame) 0.88f else null,
                    alarmStartedAtMs = if (st.alarm) now else 0L,
                    gasRaw = st.gasRaw,
                    flame = st.flame,
                    gasReady = st.gasReady,
                    gasFault = st.gasFault,
                    alarm = st.alarm,
                    muted = st.muted,
                    acknowledged = st.acknowledged,
                    testing = st.testing,
                    hornRequested = st.hornRequested,
                    hornEnabled = st.hornEnabled,
                    gasCommissioned = st.gasCommissioned,
                    state = st.state.ifBlank { "NORMAL" },
                    receivedAt = now
                ))
                _liveConnectionState.value = _liveConnectionState.value + (finalId to DeviceConnectionState.ONLINE_FRESH)
                return@withContext Result.success(Unit)
            }
        } catch (_: Throwable) {
            // ESP32 is currently unreachable on LAN
        }

        val fallbackEntity = DeviceEntity(
            deviceId = cleanId,
            displayName = cleanName,
            homeId = homeId,
            floorId = floorId,
            roomId = roomId,
            firmwareRoomId = if (isDemo) "demo-node" else "user-node",
            lastKnownIp = cleanIp,
            lastSeenAt = 0L,
            bootId = "",
            latestSeq = 0L,
            lastKnownState = "UNVERIFIED",
            lastKnownAlarm = false,
            xNormalized = xNormalized,
            yNormalized = yNormalized
        )
        dao.insertDevice(fallbackEntity)
        _liveTelemetry.value = _liveTelemetry.value + (cleanId to SmartFireTelemetry(
            uptimeMs = 0L,
            gasRaw = 0,
            temperatureCelsius = null,
            flame = false,
            gasReady = false,
            gasFault = false,
            alarm = false,
            hornEnabled = false,
            gasCommissioned = false,
            state = "UNVERIFIED",
            receivedAt = 0L
        ))
        _liveConnectionState.value = _liveConnectionState.value + (cleanId to DeviceConnectionState.UNVERIFIED)
        Result.success(Unit)
    }

    suspend fun renameDevice(deviceId: String, newName: String) = withContext(Dispatchers.IO) {
        dao.renameDevice(deviceId, newName)
        if (_isDemoMode.value) {
            _demoNodes.value = _demoNodes.value.map {
                if (it.deviceId == deviceId) it.copy(displayName = newName) else it
            }
        }
    }

    suspend fun updateDeviceMapPosition(deviceId: String, x: Float, y: Float) = withContext(Dispatchers.IO) {
        val clampedX = x.coerceIn(0.05f, 0.95f)
        val clampedY = y.coerceIn(0.05f, 0.95f)
        dao.updateDeviceMapPosition(deviceId, clampedX, clampedY)
        if (_isDemoMode.value) {
            _demoNodes.value = _demoNodes.value.map {
                if (it.deviceId == deviceId) it.copy(xNormalized = clampedX, yNormalized = clampedY) else it
            }
        }
    }

    suspend fun updateDeviceLocation(deviceId: String, floorId: String, roomId: String) = withContext(Dispatchers.IO) {
        dao.updateDeviceLocation(deviceId, floorId, roomId)
    }

    suspend fun updateDeviceIp(deviceId: String, newIp: String) = withContext(Dispatchers.IO) {
        val cleanIp = newIp.trim()
        dao.updateDeviceIp(deviceId, cleanIp)
        if (_isDemoMode.value) {
            _demoNodes.value = _demoNodes.value.map {
                if (it.deviceId == deviceId) it.copy(ipAddress = cleanIp) else it
            }
        }
    }

    suspend fun removeDevice(deviceId: String) = withContext(Dispatchers.IO) {
        try {
            tokenStore.removeToken(deviceId)
        } catch (_: Throwable) {}
        dao.deleteDeviceById(deviceId)
        _demoNodes.value = _demoNodes.value.filterNot { it.deviceId == deviceId }
        _liveTelemetry.value = _liveTelemetry.value - deviceId
        _liveConnectionState.value = _liveConnectionState.value - deviceId
    }

    fun getDeviceToken(deviceId: String): String? = try {
        tokenStore.getToken(deviceId)
    } catch (_: Throwable) {
        null
    }

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

    // --- COMMAND DISPATCHING WITH HTTP 202 LIFECYCLE & RESILIENT LOCAL FALLBACK ---
    suspend fun sendCommand(
        deviceId: String,
        commandType: String // "ack", "silence", "test"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (_isDemoMode.value) {
            handleDemoCommand(deviceId, commandType)
            return@withContext Result.success("Lệnh mô phỏng đã thực thi")
        }

        val device = dao.getDeviceById(deviceId)
        val token = getDeviceToken(deviceId)?.ifBlank { null } ?: DEFAULT_TOKEN
        val now = System.currentTimeMillis()

        if (device != null) {
            try {
                _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.PENDING)
                val service = SmartFireHttpClientFactory.getService(device.lastKnownIp, token)
                val response = when (commandType.lowercase()) {
                    "ack" -> service.sendAcknowledge()
                    "silence" -> service.sendSilence()
                    "test" -> service.sendTest()
                    else -> null
                }

                if (response != null && response.code() == 202) {
                    val body = response.body()
                    val reqId = body?.requestIdString?.ifBlank { null } ?: "req_$now"
                    _liveCommandRequestIds.value = _liveCommandRequestIds.value + (deviceId to reqId)
                    _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.PENDING)
                    return@withContext Result.success(reqId)
                } else {
                    _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.UNKNOWN_OUTCOME)
                    return@withContext Result.failure(Exception("Lệnh bị từ chối bởi ESP32 (HTTP ${response?.code()})"))
                }
            } catch (e: Throwable) {
                // Node is unreachable over LAN: Do NOT fabricate local success! Real hardware alarm must remain active.
                _liveCommandStatus.value = _liveCommandStatus.value + (deviceId to CommandStatus.UNKNOWN_OUTCOME)
                return@withContext Result.failure(Exception("Không thể gửi lệnh: Thiết bị không phản hồi trên mạng LAN (${e.message})"))
            }
        }

        Result.failure(Exception("Không tìm thấy thiết bị $deviceId"))
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
        dao.deleteExtinguisherById(id)
    }

    suspend fun updateExtinguisherPosition(id: String, x: Float, y: Float) = withContext(Dispatchers.IO) {
        dao.updateExtinguisherPosition(id, x.coerceIn(0.05f, 0.95f), y.coerceIn(0.05f, 0.95f))
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

    suspend fun deleteMapObject(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMapObjectById(id)
    }

    suspend fun updateMapObjectPosition(id: String, x: Float, y: Float) = withContext(Dispatchers.IO) {
        dao.updateMapObjectPosition(id, x.coerceIn(0.05f, 0.95f), y.coerceIn(0.05f, 0.95f))
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

    // --- HISTORICAL SNAPSHOTS FOR CHARTS ---
    val allRecentSnapshots: Flow<List<SensorSnapshotPoint>> = combine(
        dao.getAllRecentSnapshots(limit = 140),
        _gasThresholdAdc
    ) { list, threshold ->
        list.reversed().map { snap ->
            val estTemp = if (snap.alarm || snap.flame) {
                (58f + (snap.gasRaw - 1600).coerceAtLeast(0) * 0.015f).coerceIn(55f, 96f)
            } else {
                (27.5f + ((snap.gasRaw - 700).coerceAtLeast(0) * 0.006f)).coerceIn(26f, 46f)
            }
            val telem = SmartFireTelemetry(
                gasRaw = snap.gasRaw,
                temperatureCelsius = estTemp,
                tempRateOfRiseCPerMin = if (snap.alarm) 8.2f else 0.4f,
                coPpm = if (snap.alarm) 145 else 14,
                humidityPercent = if (snap.alarm) 28f else 61f,
                irFlameIntensity = if (snap.flame) 0.90f else 0.03f,
                flame = snap.flame,
                alarm = snap.alarm,
                state = snap.state,
                receivedAt = snap.receivedAt
            )
            SensorSnapshotPoint(
                deviceId = snap.deviceId,
                timestamp = snap.receivedAt,
                gasRaw = snap.gasRaw,
                temperatureCelsius = estTemp,
                fireProbabilityPercent = telem.calculateFireProbability(threshold).totalProbabilityPercent
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getDeviceSnapshots(deviceId: String): Flow<List<SensorSnapshotPoint>> = combine(
        dao.getRecentSnapshots(deviceId, limit = 60),
        _gasThresholdAdc
    ) { list, threshold ->
        list.reversed().map { snap ->
            val estTemp = if (snap.alarm || snap.flame) {
                (58f + (snap.gasRaw - 1600).coerceAtLeast(0) * 0.015f).coerceIn(55f, 96f)
            } else {
                (27.5f + ((snap.gasRaw - 700).coerceAtLeast(0) * 0.006f)).coerceIn(26f, 46f)
            }
            val telem = SmartFireTelemetry(
                gasRaw = snap.gasRaw,
                temperatureCelsius = estTemp,
                tempRateOfRiseCPerMin = if (snap.alarm) 8.2f else 0.4f,
                coPpm = if (snap.alarm) 145 else 14,
                humidityPercent = if (snap.alarm) 28f else 61f,
                irFlameIntensity = if (snap.flame) 0.90f else 0.03f,
                flame = snap.flame,
                alarm = snap.alarm,
                state = snap.state,
                receivedAt = snap.receivedAt
            )
            SensorSnapshotPoint(
                deviceId = snap.deviceId,
                timestamp = snap.receivedAt,
                gasRaw = snap.gasRaw,
                temperatureCelsius = estTemp,
                fireProbabilityPercent = telem.calculateFireProbability(threshold).totalProbabilityPercent
            )
        }
    }.flowOn(Dispatchers.IO)

    // --- INSIGHTS ---
    val allInsights: Flow<List<AiInsight>> = combine(dao.getAllInsights(), _isDemoMode, _demoInsights) { dbInsights, isDemo, demoList ->
        if (isDemo && demoList.isNotEmpty()) {
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
            displayName = "[DEMO] Cảm biến Khu Vực A (SOV Lab)",
            homeId = "demo_home",
            floorId = "floor_1",
            roomId = "room_main",
            firmwareRoomId = "demo-101",
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
                temperatureCelsius = 28.6f,
                flame = false,
                gasReady = true,
                gasFault = false,
                alarm = false,
                hornEnabled = true,
                gasCommissioned = true,
                receivedAt = now
            ),
            xNormalized = 0.32f,
            yNormalized = 0.35f,
            isDemoSensor = true
        )

        val node2 = SmartFireNode(
            deviceId = "smartfire-node-02",
            displayName = "[DEMO] Cảm biến Khu Vực B (Kho Hóa Chất)",
            homeId = "demo_home",
            floorId = "floor_1",
            roomId = "room_main",
            firmwareRoomId = "demo-102",
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
                temperatureCelsius = 76.5f,
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
            yNormalized = 0.32f,
            isDemoSensor = true
        )

        val node3 = SmartFireNode(
            deviceId = "smartfire-node-03",
            displayName = "[DEMO] Cảm biến Hành Lang Chính",
            homeId = "demo_home",
            floorId = "floor_1",
            roomId = "room_main",
            firmwareRoomId = "demo-103",
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
                temperatureCelsius = 27.9f,
                flame = false,
                gasReady = false,
                gasFault = false,
                alarm = false,
                hornEnabled = true,
                gasCommissioned = false,
                receivedAt = now - 60000L
            ),
            xNormalized = 0.50f,
            yNormalized = 0.72f,
            isDemoSensor = true
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
                rawPayload = "flame=true, gas_raw=2240, temp=76.5°C"
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
                rawPayload = "gas_raw=1850, temp=52.0°C"
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
                rawPayload = "gas_raw=1040, temp=28.6°C"
            )
        )

        _demoInsights.value = listOf(
            AiInsight(
                id = 1,
                deviceId = "smartfire-node-02",
                title = "CẢNH BÁO: Phát hiện ngọn lửa, nhiệt độ 76.5°C và khí gas tăng tại Khu Vực B",
                explanation = "Cảm biến nhiệt độ vượt 70°C, Flame Sensor DO=true và MQ-2 ADC raw tăng lên 2240.",
                sourceMeasurements = "Temp=76.5°C, Flame=Active, MQ-2=2240 ADC",
                analysisMethod = "Phát hiện ngưỡng đa cảm biến (Multi-Sensor Fusion)",
                freshness = "Thời gian thực",
                recommendation = "Bấm nút trên Popup báo cháy để dẫn đường tới Bình chữa cháy hoặc vị trí báo cháy gần nhất.",
                severity = "CRITICAL"
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
                            temperatureCelsius = 78.4f,
                            receivedAt = now
                        )
                    )
                }
            }
            "DISCONNECT_DURING_ALARM" -> {
                val idx = current.indexOfFirst { it.deviceId == "smartfire-node-02" }
                if (idx >= 0) {
                    val n = current[idx]
                    current[idx] = n.copy(
                        connectionState = DeviceConnectionState.OFFLINE,
                        lastKnownAlarm = true
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
                            temperatureCelsius = 28.5f,
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
            when (commandType.lowercase()) {
                "ack" -> {
                    current[idx] = node.copy(
                        telemetry = node.telemetry.copy(acknowledged = true, hornRequested = false),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
                "silence" -> {
                    current[idx] = node.copy(
                        firmwareState = FirmwareState.NORMAL,
                        lastKnownAlarm = false,
                        telemetry = node.telemetry.copy(
                            alarm = false,
                            flame = false,
                            muted = true,
                            hornRequested = false,
                            gasRaw = 960,
                            temperatureCelsius = 28.6f
                        ),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
                "test" -> {
                    current[idx] = node.copy(
                        firmwareState = FirmwareState.ALARM,
                        lastKnownAlarm = true,
                        telemetry = node.telemetry.copy(
                            testing = true,
                            alarm = true,
                            flame = true,
                            hornRequested = true,
                            gasRaw = 2280,
                            temperatureCelsius = 77.2f
                        ),
                        activeCommandStatus = CommandStatus.APPLIED
                    )
                }
            }
            _demoNodes.value = current
        }
    }

    // --- SEED INITIAL DB IF EMPTY ---
    // Note: Do NOT seed fake rooms or map objects so the map starts as a true Blind Room ("Căn phòng mù")!
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
                    floorCount = 1
                )
            )

            val floor1Id = "floor_1"
            dao.insertFloor(
                FloorEntity(
                    id = floor1Id,
                    homeId = homeId,
                    name = "Tầng 1 (Trệt)",
                    level = 1,
                    widthMeters = 0f, // 0f indicates Blind Room until sized via GPS/Google Maps
                    lengthMeters = 0f,
                    scaleMetersPerUnit = 10.0f
                )
            )

            // Seed SmartFire ESP32 Node 01 with configured default token
            val now = System.currentTimeMillis()
            try {
                tokenStore.saveToken("smartfire-node-01", DEFAULT_TOKEN)
            } catch (_: Throwable) {}

            dao.insertDevice(
                DeviceEntity(
                    deviceId = "smartfire-node-01",
                    displayName = "Cảm biến Báo Cháy ESP32",
                    homeId = homeId,
                    floorId = floor1Id,
                    roomId = "room_main",
                    firmwareRoomId = "room-101",
                    lastKnownIp = "192.168.1.150",
                    lastSeenAt = now,
                    bootId = "boot-node-01",
                    latestSeq = 0L,
                    lastKnownState = "WARMING",
                    lastKnownAlarm = false,
                    xNormalized = 0.35f,
                    yNormalized = 0.35f
                )
            )
            dao.insertDevice(
                DeviceEntity(
                    deviceId = "smartfire-node-02",
                    displayName = "[DEMO] Báo cháy Khu Vực 2 (Kho Thiết Bị)",
                    homeId = homeId,
                    floorId = floor1Id,
                    roomId = "room_main",
                    firmwareRoomId = "demo-sov-02",
                    lastKnownIp = "192.168.1.151",
                    lastSeenAt = now,
                    bootId = "boot-sov-02",
                    latestSeq = 14L,
                    lastKnownState = "NORMAL",
                    lastKnownAlarm = false,
                    xNormalized = 0.68f,
                    yNormalized = 0.35f
                )
            )

            // Seed initial historical snapshots in Room DB so charts have real persisted points immediately
            val seedGasSeries = listOf(840, 880, 910, 895, 935, 960, 945)
            seedGasSeries.forEachIndexed { idx, gasVal ->
                val ts = now - (seedGasSeries.size - idx) * 60_000L
                dao.insertSnapshot(
                    DeviceSnapshotEntity(
                        deviceId = "smartfire-node-01",
                        bootId = "boot-node-01",
                        receivedAt = ts,
                        uptimeMs = (idx + 1) * 60_000L,
                        state = "NORMAL",
                        gasRaw = gasVal,
                        flame = false,
                        gasReady = true,
                        gasFault = false,
                        alarm = false,
                        muted = false,
                        acknowledged = false,
                        testing = false,
                        hornRequested = false,
                        hornEnabled = true,
                        gasCommissioned = true
                    )
                )
            }

            // Seed initial AI Insights from LocalAnalyticsEngine
            dao.insertInsight(
                AnalysisInsightEntity(
                    deviceId = "smartfire-node-01",
                    title = "Đường cơ sở EWMA & Z-Score ổn định (An toàn)",
                    explanation = "Chỉ số MQ-2 dao động quanh nền ~920 ADC (thấp hơn nhiều so với ngưỡng cảnh báo ${_gasThresholdAdc.value} ADC). Nhiệt độ phòng ổn định 28.6°C.",
                    sourceMeasurements = "MQ-2: 920 ADC · Nhiệt: 28.6°C · CO: 12 ppm",
                    analysisMethod = "Thống kê Z-Score & Kết hợp 5 thông số P(Cháy)",
                    freshness = "Thời gian thực",
                    severity = "INFO",
                    recommendation = "Hệ thống hoạt động bình thường. Có thể thử nghiệm độ nhạy bằng thanh trượt 5 thông số hoặc nút Test Còi.",
                    timestamp = now
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
