package com.example.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.DoorSliding
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.ExtinguisherItem
import com.example.domain.model.FirmwareState
import com.example.domain.model.FloorPlan
import com.example.domain.model.MapEdgeItem
import com.example.domain.model.MapNavigationRequest
import com.example.domain.model.MapNavigationTargetType
import com.example.domain.model.MapNodeItem
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import com.example.domain.model.RoomSpace
import com.example.domain.model.SmartFireNode
import com.example.domain.model.UserPosition
import com.example.domain.pathfinding.NavigationRoute
import com.example.sensor.DeviceMovementTracker
import com.example.ui.theme.CriticalAlarmBackground
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.NormalGreenBackground
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

enum class EmergencyAutoPhase {
    IDLE,
    FETCH_EXTINGUISHER_FIRST,
    ATTACK_FIRE_SOURCE
}

enum class MapStage {
    BLIND_ROOM_SIZING, // Bước 1: Khoanh vùng khu vực nhà trên Google Maps (Không tự chia phòng!)
    SETUP_PLACEMENT,   // Bước 2: Setup Vị Trí Nhận Diện Lửa TRƯỚC làm mốc chuẩn (Anchor) + Đo khoảng cách bằng Tín hiệu RSSI & Cảm biến ĐT -> Sau đó setup Cửa Chính, Cửa đánh số (#1, #2...), Bình CC, Exit
    READY_MAP          // Bước 3: Bản đồ hoàn chỉnh (2 chế độ hiển thị + Giả lập lửa lan theo bán kính tròn khi kêu lâu + Dẫn đường)
}

enum class MapVisualStyle {
    TRANSLUCENT_GOOGLE_MAP, // Chế độ 1: Mờ mờ thấy bản đồ Google Maps bên dưới
    CRISP_WHITE_HOUSE       // Chế độ 2: Trắng tinh để nhìn rõ nhà và vật dụng
}

enum class SetupDrawerMode {
    ADD_FIRE_SENSOR, // Bước chuẩn hóa bắt buộc đầu tiên: Ghim Vị Trí Nhận Diện Lửa làm chuẩn + Đo khoảng cách Tín hiệu RSSI & Cảm biến ĐT
    ADD_OBJECT       // Bước tiếp theo: Ghim Cửa Chính, Cánh Cửa (đánh số #1, #2...), Bình chữa cháy, Cầu thang, Cửa Exit
}

data class PlacedSensorPin(
    val deviceId: String,
    val displayName: String,
    val position: Point2D,
    val isAlarm: Boolean = false,
    val temperatureCelsius: Float = 28.5f,
    val fireProbabilityPercent: Float = 5.0f,
    val rssiDbm: Int = -52,
    val alarmStartedAtMs: Long = 0L,
    val isDemoSensor: Boolean = false,
    val isReferenceAnchor: Boolean = false
)

@Composable
fun IndoorMap2DScreen(
    floors: List<FloorPlan>,
    rooms: List<RoomSpace>,
    nodes: List<SmartFireNode>,
    extinguishers: List<ExtinguisherItem>,
    mapObjects: List<MapNodeItem>,
    mapEdges: List<MapEdgeItem>,
    userLocation: UserPosition?,
    initialInSetupMode: Boolean = false,
    navigationRequest: MapNavigationRequest? = null,
    onConsumeNavigationRequest: () -> Unit = {},
    onSaveScannedSetup: (
        List<Point2D>, // sensors
        List<Point2D>, // numbered doors
        List<Point2D>, // stairs
        List<Point2D>, // extinguishers
        List<Point2D>, // exits
        List<Point2D>  // mainDoors (Cửa Chính)
    ) -> Unit,
    onBindSensorPosition: (String, Float, Float) -> Unit = { _, _, _ -> },
    onUpdateFloorDimensions: (String, Float, Float) -> Unit = { _, _, _ -> },
    onClearAllMapConfig: (String) -> Unit = {},
    onSetUserPosition: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentFloor = floors.firstOrNull()
    val floorId = currentFloor?.id ?: "floor_1"

    // Hardware Movement Tracker (Step Detector + Accelerometer + Compass + GPS)
    val movementTracker = remember { DeviceMovementTracker(context, scope) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        movementTracker.refreshGpsLocationNow()
        movementTracker.forceRecognizeMapLocation()
    }

    DisposableEffect(Unit) {
        movementTracker.startTracking()
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        try {
            permissionLauncher.launch(perms.toTypedArray())
        } catch (_: Exception) {
            movementTracker.forceRecognizeMapLocation()
        }
        onDispose { movementTracker.stopTracking() }
    }

    val stepCount by movementTracker.stepCount.collectAsState()
    val compassHeading by movementTracker.compassHeading.collectAsState()
    val isWalking by movementTracker.isWalking.collectAsState()
    val gpsLocation by movementTracker.lastGpsLocation.collectAsState()
    val gpsDeltaMeters by movementTracker.gpsDeltaMeters.collectAsState()

    val hasSavedObjects = extinguishers.isNotEmpty() || mapObjects.isNotEmpty() || (currentFloor != null && currentFloor.widthMeters > 0f)

    var mapStage by remember(initialInSetupMode) {
        mutableStateOf(
            when {
                initialInSetupMode -> MapStage.SETUP_PLACEMENT
                hasSavedObjects -> MapStage.READY_MAP
                else -> MapStage.BLIND_ROOM_SIZING
            }
        )
    }

    var userManuallyClearedToBlindRoom by remember { mutableStateOf(false) }
    LaunchedEffect(hasSavedObjects) {
        if (hasSavedObjects && mapStage == MapStage.BLIND_ROOM_SIZING && !userManuallyClearedToBlindRoom && !initialInSetupMode) {
            mapStage = MapStage.READY_MAP
        }
    }

    // House physical dimensions in meters (calculated from Google Maps Khoanh Vùng polygon)
    var houseWidthMeters by remember(currentFloor?.widthMeters) {
        mutableFloatStateOf(if ((currentFloor?.widthMeters ?: 0f) > 0f) currentFloor!!.widthMeters else 12f)
    }
    var houseLengthMeters by remember(currentFloor?.lengthMeters) {
        mutableFloatStateOf(if ((currentFloor?.lengthMeters ?: 0f) > 0f) currentFloor!!.lengthMeters else 16f)
    }

    // Interactive Google Maps Khoanh Vùng (Polygon points tapped by user on the map)
    val regionPolygonPoints = remember {
        mutableStateListOf(
            Point2D(0.18f, 0.16f),
            Point2D(0.82f, 0.16f),
            Point2D(0.82f, 0.84f),
            Point2D(0.18f, 0.84f)
        )
    }
    var useLiveGoogleMapWebView by remember { mutableStateOf(true) }
    var gpsCalibratedNote by remember {
        mutableStateOf("Chạm lên bản đồ Google Maps để khoanh vùng 4 góc nhà (Không tự ý chia phòng).")
    }

    // Visual mode when Map is Ready (Mode 1: Translucent Map vs Mode 2: Crisp White)
    var visualStyle by remember { mutableStateOf(MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) }

    // Active sub-drawer in Stage 2 (Always starts with ADD_FIRE_SENSOR first as Reference Anchor!)
    var setupDrawer by remember { mutableStateOf(SetupDrawerMode.ADD_FIRE_SENSOR) }
    var selectedSavedNodeId by remember(nodes) {
        mutableStateOf(nodes.firstOrNull()?.deviceId ?: "smartfire-node-01")
    }

    // Placed items during setup & active map
    val placedSensorPins = remember { mutableStateListOf<PlacedSensorPin>() }
    val tempMainDoors = remember {
        mutableStateListOf<Point2D>().apply {
            addAll(mapObjects.filter { it.type == MapObjectType.MAIN_DOOR }.map { it.position })
        }
    }
    val tempDoors = remember {
        mutableStateListOf<Point2D>().apply {
            addAll(mapObjects.filter { it.type == MapObjectType.DOOR }.map { it.position })
        }
    }
    val tempStairs = remember {
        mutableStateListOf<Point2D>().apply {
            addAll(mapObjects.filter { it.type == MapObjectType.STAIRS }.map { it.position })
        }
    }
    val tempExtinguishers = remember {
        mutableStateListOf<Point2D>().apply {
            addAll(extinguishers.map { it.position })
        }
    }
    val tempExits = remember {
        mutableStateListOf<Point2D>().apply {
            addAll(mapObjects.filter { it.type == MapObjectType.EXIT }.map { it.position })
        }
    }

    // Sync DB objects into temp lists when DB loads
    LaunchedEffect(mapObjects, extinguishers) {
        if (tempMainDoors.isEmpty() && mapObjects.any { it.type == MapObjectType.MAIN_DOOR }) {
            tempMainDoors.addAll(mapObjects.filter { it.type == MapObjectType.MAIN_DOOR }.map { it.position })
        }
        if (tempDoors.isEmpty() && mapObjects.any { it.type == MapObjectType.DOOR }) {
            tempDoors.addAll(mapObjects.filter { it.type == MapObjectType.DOOR }.map { it.position })
        }
        if (tempStairs.isEmpty() && mapObjects.any { it.type == MapObjectType.STAIRS }) {
            tempStairs.addAll(mapObjects.filter { it.type == MapObjectType.STAIRS }.map { it.position })
        }
        if (tempExits.isEmpty() && mapObjects.any { it.type == MapObjectType.EXIT }) {
            tempExits.addAll(mapObjects.filter { it.type == MapObjectType.EXIT }.map { it.position })
        }
        if (tempExtinguishers.isEmpty() && extinguishers.isNotEmpty()) {
            tempExtinguishers.addAll(extinguishers.map { it.position })
        }
    }

    // Sync live alarm state, multi-parameter fire probability, RSSI & alarmStartedAtMs from nodes into placedSensorPins
    LaunchedEffect(nodes, hasSavedObjects) {
        if (placedSensorPins.isEmpty() && hasSavedObjects) {
            nodes.forEachIndexed { idx, n ->
                val prob = n.telemetry.calculateFireProbability().totalProbabilityPercent
                val isAlm = n.lastKnownAlarm || n.firmwareState == FirmwareState.ALARM || prob >= 75f
                placedSensorPins.add(
                    PlacedSensorPin(
                        deviceId = n.deviceId,
                        displayName = n.displayName,
                        position = Point2D(n.xNormalized, n.yNormalized),
                        isAlarm = isAlm,
                        temperatureCelsius = n.telemetry.temperatureCelsius,
                        fireProbabilityPercent = prob,
                        rssiDbm = n.telemetry.rssiDbm,
                        alarmStartedAtMs = if (isAlm) (n.telemetry.alarmStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()) else 0L,
                        isDemoSensor = n.isDemoSensor || n.displayName.contains("DEMO", ignoreCase = true),
                        isReferenceAnchor = (idx == 0)
                    )
                )
            }
        } else {
            placedSensorPins.removeAll { pin -> nodes.none { it.deviceId == pin.deviceId } }
            nodes.forEachIndexed { idx, n ->
                val existingIdx = placedSensorPins.indexOfFirst { it.deviceId == n.deviceId }
                val prob = n.telemetry.calculateFireProbability().totalProbabilityPercent
                val isAlm = n.lastKnownAlarm || n.firmwareState == FirmwareState.ALARM || prob >= 75f
                if (existingIdx >= 0) {
                    val prevStart = placedSensorPins[existingIdx].alarmStartedAtMs
                    placedSensorPins[existingIdx] = placedSensorPins[existingIdx].copy(
                        displayName = n.displayName,
                        isAlarm = isAlm,
                        temperatureCelsius = n.telemetry.temperatureCelsius,
                        fireProbabilityPercent = prob,
                        rssiDbm = n.telemetry.rssiDbm,
                        alarmStartedAtMs = if (isAlm) {
                            when {
                                prevStart > 0L -> prevStart
                                n.telemetry.alarmStartedAtMs > 0L -> n.telemetry.alarmStartedAtMs
                                else -> System.currentTimeMillis()
                            }
                        } else 0L,
                        isDemoSensor = n.isDemoSensor || n.displayName.contains("DEMO", ignoreCase = true),
                        isReferenceAnchor = (existingIdx == 0)
                    )
                }
            }
        }
    }

    // Real-time 1-second clock tick for simulating circular fire spread radius as long as alarm rings
    var currentClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hazardNodes = nodes.filter {
        it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM ||
            it.telemetry.calculateFireProbability().totalProbabilityPercent >= 75f
    }
    val isFireActive = hazardNodes.isNotEmpty()

    LaunchedEffect(isFireActive) {
        while (isFireActive) {
            currentClockMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Dynamic User Standing Position (updated via steps/compass, RSSI+IMU fusion, or tap)
    var currentUserPos by remember {
        mutableStateOf(userLocation?.position ?: Point2D(0.50f, 0.78f))
    }

    // Calculate Signal RSSI + Phone Sensor Fused Distance from the Reference Fire Sensor Anchor
    val anchorSensorPin = placedSensorPins.firstOrNull()
    val selectedNodeObj = nodes.find { it.deviceId == selectedSavedNodeId } ?: nodes.firstOrNull()
    val liveRssiDbm = selectedNodeObj?.telemetry?.rssiDbm ?: -52
    val rssiDistanceMeters = selectedNodeObj?.telemetry?.estimateDistanceFromRssiMeters() ?: 3.1f

    val imuPhoneDistanceMeters = if (anchorSensorPin != null) {
        val dxM = (currentUserPos.x - anchorSensorPin.position.x) * houseWidthMeters
        val dyM = (currentUserPos.y - anchorSensorPin.position.y) * houseLengthMeters
        hypot(dxM.toDouble(), dyM.toDouble()).toFloat()
    } else {
        (stepCount * 0.65f).coerceAtLeast(1.2f)
    }

    // Kalman / Complementary Fusion between Signal RSSI Distance (45%) & Phone IMU/Step/GPS Sensor Distance (55%)
    val fusedDistanceMeters = (0.45f * rssiDistanceMeters + 0.55f * imuPhoneDistanceMeters).coerceIn(0.3f, 40f)
    val distanceDiscrepancy = abs(rssiDistanceMeters - imuPhoneDistanceMeters)
    val fusedAccuracyPercent = (98.5f - (distanceDiscrepancy * 3.2f)).coerceIn(82.0f, 99.2f)

    // Active Navigation Route & Emergency Phase
    var activeRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var emergencyPhase by remember { mutableStateOf(EmergencyAutoPhase.IDLE) }

    // Helper function to build door-aware route (passing through numbered doors or main door)
    fun buildDoorAwareRoute(
        start: Point2D,
        target: Point2D,
        targetLabel: String,
        targetType: String,
        notes: String
    ): NavigationRoute {
        val waypoints = mutableListOf<Point2D>()
        waypoints.add(start)

        val allPassableDoors = tempDoors + tempMainDoors
        if (allPassableDoors.isNotEmpty()) {
            val intermediateDoor = allPassableDoors.minByOrNull { door ->
                hypot((door.x - start.x).toDouble(), (door.y - start.y).toDouble()) +
                    hypot((target.x - door.x).toDouble(), (target.y - door.y).toDouble())
            }
            if (intermediateDoor != null) {
                val directDist = hypot((target.x - start.x).toDouble(), (target.y - start.y).toDouble())
                val doorDist = hypot((intermediateDoor.x - start.x).toDouble(), (intermediateDoor.y - start.y).toDouble())
                if (doorDist > 0.04 && directDist > 0.12) {
                    waypoints.add(intermediateDoor)
                }
            }
        }
        waypoints.add(target)

        var totalMeters = 0f
        for (i in 0 until waypoints.size - 1) {
            val dx = (waypoints[i + 1].x - waypoints[i].x) * houseWidthMeters
            val dy = (waypoints[i + 1].y - waypoints[i].y) * houseLengthMeters
            totalMeters += hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }

        return NavigationRoute(
            pathPoints = waypoints,
            totalDistanceMeters = totalMeters.coerceAtLeast(0.5f),
            targetName = targetLabel,
            targetType = targetType,
            crossesHazardRoom = isFireActive,
            isEuclideanOnly = allPassableDoors.isEmpty(),
            notes = notes
        )
    }

    // Advance user position by 1 step (either along active route or along compass heading)
    fun advanceUserStep(steps: Int = 1) {
        val currentRoute = activeRoute
        val nextPos = if (currentRoute != null && currentRoute.pathPoints.size >= 2) {
            movementTracker.advancePositionAlongPath(
                currentUserPos,
                currentRoute.pathPoints,
                (0.70f / houseLengthMeters.coerceAtLeast(8f)) * steps
            )
        } else {
            val rad = Math.toRadians((compassHeading - 90f).toDouble())
            val stepNormX = (0.65f / houseWidthMeters.coerceAtLeast(5f)) * steps
            val stepNormY = (0.65f / houseLengthMeters.coerceAtLeast(5f)) * steps
            Point2D(
                x = (currentUserPos.x + cos(rad).toFloat() * stepNormX).coerceIn(0.08f, 0.92f),
                y = (currentUserPos.y + sin(rad).toFloat() * stepNormY).coerceIn(0.08f, 0.92f)
            )
        }
        currentUserPos = nextPos
        onSetUserPosition(floorId, nextPos.x, nextPos.y)

        if (currentRoute != null && currentRoute.pathPoints.isNotEmpty()) {
            val finalTarget = currentRoute.pathPoints.last()
            activeRoute = buildDoorAwareRoute(
                start = nextPos,
                target = finalTarget,
                targetLabel = currentRoute.targetName,
                targetType = currentRoute.targetType,
                notes = currentRoute.notes
            )
        }
    }

    var lastProcessedStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(stepCount) {
        if (stepCount > lastProcessedStep) {
            val deltaSteps = stepCount - lastProcessedStep
            lastProcessedStep = stepCount
            advanceUserStep(deltaSteps)
        }
    }

    LaunchedEffect(gpsDeltaMeters) {
        if (hypot(gpsDeltaMeters.x.toDouble(), gpsDeltaMeters.y.toDouble()) > 0.35) {
            val normX = (0.50f + (gpsDeltaMeters.x / houseWidthMeters.coerceAtLeast(8f))).coerceIn(0.10f, 0.90f)
            val normY = (0.78f - (gpsDeltaMeters.y / houseLengthMeters.coerceAtLeast(8f))).coerceIn(0.10f, 0.90f)
            currentUserPos = Point2D(normX, normY)
            onSetUserPosition(floorId, normX, normY)
        }
    }

    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "map_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Restart),
        label = "alarm_pulse"
    )

    // Route DIRECTLY to Fire Sensor (prioritizes specificDeviceId -> NEAREST alarming fire sensor -> nearest fire sensor)
    fun routeToFireSensor(specificDeviceId: String? = null) {
        val allCandidatePins = if (placedSensorPins.isNotEmpty()) {
            placedSensorPins.toList()
        } else {
            nodes.mapIndexed { idx, n ->
                val prob = n.telemetry.calculateFireProbability().totalProbabilityPercent
                val isAlm = n.lastKnownAlarm || n.firmwareState == FirmwareState.ALARM || prob >= 75f
                PlacedSensorPin(
                    deviceId = n.deviceId,
                    displayName = n.displayName,
                    position = Point2D(n.xNormalized, n.yNormalized),
                    isAlarm = isAlm,
                    temperatureCelsius = n.telemetry.temperatureCelsius,
                    fireProbabilityPercent = prob,
                    rssiDbm = n.telemetry.rssiDbm,
                    alarmStartedAtMs = if (isAlm) System.currentTimeMillis() else 0L,
                    isDemoSensor = n.isDemoSensor || n.displayName.contains("DEMO", ignoreCase = true),
                    isReferenceAnchor = (idx == 0)
                )
            }
        }

        val specificPin = specificDeviceId?.let { id -> allCandidatePins.find { it.deviceId == id } }
        val nearestAlarmingPin = allCandidatePins
            .filter { it.isAlarm }
            .minByOrNull { pin ->
                hypot((pin.position.x - currentUserPos.x).toDouble(), (pin.position.y - currentUserPos.y).toDouble())
            }
        val nearestAnyPin = allCandidatePins.minByOrNull { pin ->
            hypot((pin.position.x - currentUserPos.x).toDouble(), (pin.position.y - currentUserPos.y).toDouble())
        }

        val chosenPin = specificPin ?: nearestAlarmingPin ?: nearestAnyPin
        val firePos = chosenPin?.position ?: Point2D(0.35f, 0.32f)
        val fireName = chosenPin?.displayName ?: "Vị trí Báo Cháy"
        val tempStr = chosenPin?.let { String.format(Locale.US, "%.1f°C · P=%.0f%%", it.temperatureCelsius, it.fireProbabilityPercent) } ?: ""

        emergencyPhase = EmergencyAutoPhase.ATTACK_FIRE_SOURCE
        activeRoute = buildDoorAwareRoute(
            start = currentUserPos,
            target = firePos,
            targetLabel = if (tempStr.isNotEmpty()) "$fireName ($tempStr)" else fireName,
            targetType = "FIRE_SENSOR",
            notes = if (chosenPin?.isAlarm == true) {
                "KHẨN CẤP: Đang dẫn qua cửa tới điểm cháy gần nhất ($fireName · $tempStr)"
            } else {
                "Đang chỉ đường tới vị trí báo cháy: $fireName"
            }
        )
    }

    fun routeToExtinguisher() {
        val targetExt = tempExtinguishers.minByOrNull { ext ->
            hypot((ext.x - currentUserPos.x).toDouble(), (ext.y - currentUserPos.y).toDouble())
        } ?: Point2D(0.25f, 0.68f)

        emergencyPhase = EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST
        activeRoute = buildDoorAwareRoute(
            start = currentUserPos,
            target = targetExt,
            targetLabel = "Bình Chữa Cháy Gần Nhất",
            targetType = "EXTINGUISHER",
            notes = "Đi qua các cửa đánh số tới vị trí Bình Chữa Cháy gần nhất"
        )
    }

    fun routeToExitDoor() {
        val candidates = tempExits + tempMainDoors
        val targetExit = candidates.minByOrNull { ex ->
            hypot((ex.x - currentUserPos.x).toDouble(), (ex.y - currentUserPos.y).toDouble())
        } ?: Point2D(0.50f, 0.90f)

        emergencyPhase = EmergencyAutoPhase.IDLE
        activeRoute = buildDoorAwareRoute(
            start = currentUserPos,
            target = targetExit,
            targetLabel = "Cửa Exit / Cửa Chính Thoát Hiểm",
            targetType = "EXIT",
            notes = "Đi theo đường xanh lá qua cửa tới Cửa Exit / Cửa Chính gần nhất"
        )
    }

    LaunchedEffect(navigationRequest) {
        if (navigationRequest != null) {
            userManuallyClearedToBlindRoom = false
            mapStage = MapStage.READY_MAP
            when (navigationRequest.targetType) {
                MapNavigationTargetType.NEAREST_FIRE_SENSOR -> routeToFireSensor(null)
                MapNavigationTargetType.SPECIFIC_FIRE_SENSOR -> routeToFireSensor(navigationRequest.targetDeviceId)
                MapNavigationTargetType.NEAREST_EXTINGUISHER -> routeToExtinguisher()
                MapNavigationTargetType.NEAREST_EXIT -> routeToExitDoor()
            }
            onConsumeNavigationRequest()
        }
    }

    LaunchedEffect(isFireActive, hazardNodes.size) {
        if (isFireActive) {
            if (mapStage != MapStage.SETUP_PLACEMENT) {
                mapStage = MapStage.READY_MAP
            }
            if (activeRoute == null || activeRoute?.targetType != "EXTINGUISHER") {
                routeToFireSensor(null)
            }
        }
    }

    fun clearAllConfigurationToBlindRoom() {
        userManuallyClearedToBlindRoom = true
        placedSensorPins.clear()
        tempMainDoors.clear()
        tempDoors.clear()
        tempStairs.clear()
        tempExtinguishers.clear()
        tempExits.clear()
        activeRoute = null
        emergencyPhase = EmergencyAutoPhase.IDLE
        onClearAllMapConfig(floorId)
        onUpdateFloorDimensions(floorId, 0f, 0f)
        mapStage = MapStage.BLIND_ROOM_SIZING
    }

    // Calculate max fire spread radius & area across all alarming pins
    val maxFireSpreadSeconds = if (isFireActive) {
        val earliestStart = placedSensorPins.filter { it.isAlarm && it.alarmStartedAtMs > 0L }
            .minOfOrNull { it.alarmStartedAtMs }
            ?: hazardNodes.minOfOrNull { it.telemetry.alarmStartedAtMs.takeIf { t -> t > 0L } ?: (currentClockMs - 3000L) }
            ?: (currentClockMs - 3000L)
        ((currentClockMs - earliestStart) / 1000L).coerceAtLeast(1L)
    } else 0L

    // Radius grows: starts at 1.0m, expands +0.18m per second of continuous alarm
    val currentFireSpreadRadiusMeters = if (isFireActive) {
        (1.0f + maxFireSpreadSeconds * 0.18f).coerceAtMost(max(houseWidthMeters, houseLengthMeters) * 0.75f)
    } else 0f
    val currentFireSpreadAreaM2 = (PI * currentFireSpreadRadiusMeters * currentFireSpreadRadiusMeters).toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // =========================================================================
        // PERSISTENT TOP HEADER & NAVIGATION / FIRE SPREAD HUD
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isFireActive || activeRoute != null) 2.dp else 1.dp,
                    color = when {
                        isFireActive -> CriticalAlarmRed
                        activeRoute != null -> PrimaryBlue
                        else -> LightBorder
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isFireActive -> CriticalAlarmBackground
                    activeRoute != null -> PrimaryBlueLight
                    else -> LightCard
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFireActive -> CriticalAlarmRed
                                        activeRoute != null -> PrimaryBlue
                                        else -> Color(0xFF0F172A)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isFireActive -> Icons.Default.LocalFireDepartment
                                    activeRoute != null -> Icons.Default.Navigation
                                    else -> Icons.Default.Explore
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    mapStage == MapStage.BLIND_ROOM_SIZING -> "BƯỚC 1: KHOANH VÙNG NHÀ TRÊN GOOGLE MAPS"
                                    mapStage == MapStage.SETUP_PLACEMENT -> {
                                        if (placedSensorPins.isEmpty()) {
                                            "BƯỚC 2A: GHIM VỊ TRÍ NHẬN DIỆN LỬA LÀM MỐC CHUẨN TRƯỚC"
                                        } else {
                                            "BƯỚC 2B: GHIM CỬA CHÍNH, CỬA ĐÁNH SỐ & BÌNH CỨU HỎA"
                                        }
                                    }
                                    activeRoute != null -> "ĐANG DẪN TỚI: ${activeRoute!!.targetName.uppercase()}"
                                    isFireActive -> "CẢNH BÁO LỬA ĐANG LAN RỘNG THEO THỜI GIAN!"
                                    else -> "BẢN ĐỒ 2D · STEM CỦA SOV LABS."
                                },
                                color = when {
                                    isFireActive -> CriticalAlarmRed
                                    activeRoute != null -> PrimaryBlue
                                    else -> TextPrimary
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val stepsRemaining = ((activeRoute?.totalDistanceMeters ?: 0f) / 0.65f).roundToInt()
                            Text(
                                text = when {
                                    isFireActive -> String.format(
                                        Locale.US,
                                        "🔥 Bán kính lửa lan: R=%.2fm (Diện tích: %.1fm²) · Đã cháy %ds · Cách đích: %.1fm",
                                        currentFireSpreadRadiusMeters,
                                        currentFireSpreadAreaM2,
                                        maxFireSpreadSeconds,
                                        activeRoute?.totalDistanceMeters ?: 0f
                                    )
                                    activeRoute != null -> String.format(
                                        Locale.US,
                                        "Khoảng cách: %.1f m (~%d bước) · Tín hiệu+ĐT Acc: %.1f%% · Góc: %d°",
                                        activeRoute!!.totalDistanceMeters,
                                        stepsRemaining,
                                        fusedAccuracyPercent,
                                        compassHeading.roundToInt()
                                    )
                                    mapStage == MapStage.BLIND_ROOM_SIZING -> "Chạm khoanh vùng 4 góc khu vực trên Google Maps (Không tự chia phòng)"
                                    else -> String.format(
                                        Locale.US,
                                        "Khung: %.0fm×%.0fm · K/c chuẩn (RSSI+ĐT): %.2fm (Acc %.1f%%)",
                                        houseWidthMeters,
                                        houseLengthMeters,
                                        fusedDistanceMeters,
                                        fusedAccuracyPercent
                                    )
                                },
                                color = if (isFireActive) CriticalAlarmRed else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isFireActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (mapStage == MapStage.READY_MAP) {
                            IconButton(
                                onClick = { mapStage = MapStage.SETUP_PLACEMENT },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LightSurfaceVariant)
                                    .testTag("btn_edit_map_setup")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Chỉnh sửa bản đồ",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { clearAllConfigurationToBlindRoom() },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEE2E2))
                                .border(1.dp, CriticalAlarmBorder, RoundedCornerShape(8.dp))
                                .testTag("btn_clear_all_map")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Xóa toàn bộ cấu hình bản đồ",
                                tint = CriticalAlarmRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // STAGE 1: GOOGLE MAPS AREA POLYGON / REGION SELECTOR (KHOANH VÙNG BẢN ĐỒ THẬT)
        // =========================================================================
        if (mapStage == MapStage.BLIND_ROOM_SIZING) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, LightBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CropFree, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Khoanh Vùng Khu Vực Trên Google Maps",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = useLiveGoogleMapWebView,
                                    onClick = { useLiveGoogleMapWebView = !useLiveGoogleMapWebView },
                                    label = {
                                        Text(
                                            if (useLiveGoogleMapWebView) "Bản Đồ Thật: BẬT" else "Chế Độ Vệ Tinh",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlueLight,
                                        selectedLabelColor = PrimaryBlue
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Chạm 4 điểm trên bản đồ Google Maps bên dưới để khoanh vùng ranh giới tòa nhà. Mặt bằng mới vào sẽ để trống hoàn toàn (KHÔNG tự ý chia phòng).",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Interactive Google Maps + Polygon Region Drawing Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE2E8F0))
                                .border(1.5.dp, PrimaryBlue, RoundedCornerShape(14.dp))
                        ) {
                            val currentLat = gpsLocation?.latitude ?: 10.7769
                            val currentLng = gpsLocation?.longitude ?: 106.7009

                            // Layer 1: Embedded Google Maps / Leaflet Satellite + Street Map View
                            if (useLiveGoogleMapWebView) {
                                GoogleMapsTileWebView(
                                    latitude = currentLat,
                                    longitude = currentLng,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Layer 2: Interactive Polygon Region Selector Canvas ("Khoanh vùng khu vực")
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { tap ->
                                            val nx = (tap.x / size.width).coerceIn(0.06f, 0.94f)
                                            val ny = (tap.y / size.height).coerceIn(0.06f, 0.94f)
                                            if (regionPolygonPoints.size >= 4) {
                                                // Replace nearest corner or start new 4-corner polygon
                                                val nearestIdx = regionPolygonPoints.indices.minByOrNull { idx ->
                                                    val p = regionPolygonPoints[idx]
                                                    hypot((p.x - nx).toDouble(), (p.y - ny).toDouble())
                                                } ?: 0
                                                regionPolygonPoints[nearestIdx] = Point2D(nx, ny)
                                            } else {
                                                regionPolygonPoints.add(Point2D(nx, ny))
                                            }

                                            // Automatically calculate width & length in meters from polygon bounding box
                                            if (regionPolygonPoints.size >= 2) {
                                                val minX = regionPolygonPoints.minOf { it.x }
                                                val maxX = regionPolygonPoints.maxOf { it.x }
                                                val minY = regionPolygonPoints.minOf { it.y }
                                                val maxY = regionPolygonPoints.maxOf { it.y }
                                                // Full viewport corresponds to ~25m x 32m real-world parcel around GPS
                                                val calcW = ((maxX - minX) * 24f).coerceIn(6f, 45f)
                                                val calcL = ((maxY - minY) * 30f).coerceIn(8f, 60f)
                                                houseWidthMeters = (calcW * 10f).roundToInt() / 10f
                                                houseLengthMeters = (calcL * 10f).roundToInt() / 10f
                                                onUpdateFloorDimensions(floorId, houseWidthMeters, houseLengthMeters)
                                                gpsCalibratedNote = String.format(
                                                    Locale.US,
                                                    "Đã khoanh vùng %d góc trên Google Maps ➔ Rộng %.1fm × Dài %.1fm (%.0f m²)",
                                                    regionPolygonPoints.size,
                                                    houseWidthMeters,
                                                    houseLengthMeters,
                                                    houseWidthMeters * houseLengthMeters
                                                )
                                            }
                                        }
                                    }
                            ) {
                                if (!useLiveGoogleMapWebView) {
                                    drawGoogleMapsUnderlay(
                                        alphaMultiplier = 0.92f,
                                        houseWidthMeters = houseWidthMeters,
                                        houseLengthMeters = houseLengthMeters,
                                        gpsLat = currentLat,
                                        gpsLng = currentLng
                                    )
                                }

                                // Draw Polygon Region ("Vùng Khoanh Khu Vực Nhà")
                                if (regionPolygonPoints.isNotEmpty()) {
                                    val polyPath = Path()
                                    val firstPt = Offset(
                                        regionPolygonPoints.first().x * size.width,
                                        regionPolygonPoints.first().y * size.height
                                    )
                                    polyPath.moveTo(firstPt.x, firstPt.y)
                                    for (i in 1 until regionPolygonPoints.size) {
                                        val pt = Offset(
                                            regionPolygonPoints[i].x * size.width,
                                            regionPolygonPoints[i].y * size.height
                                        )
                                        polyPath.lineTo(pt.x, pt.y)
                                    }
                                    if (regionPolygonPoints.size >= 3) {
                                        polyPath.close()
                                        drawPath(
                                            path = polyPath,
                                            color = Color(0xFF2563EB).copy(alpha = 0.24f),
                                            style = Fill
                                        )
                                    }
                                    drawPath(
                                        path = polyPath,
                                        color = Color(0xFF2563EB),
                                        style = Stroke(
                                            width = 5f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                                        )
                                    )

                                    // Draw numbered corner handles (Góc 1, Góc 2, Góc 3, Góc 4)
                                    regionPolygonPoints.forEachIndexed { idx, pt ->
                                        val c = Offset(pt.x * size.width, pt.y * size.height)
                                        drawCircle(color = Color.White, radius = 18f, center = c)
                                        drawCircle(color = PrimaryBlue, radius = 14f, center = c)
                                        val numPaint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.WHITE
                                            textSize = 18f
                                            isFakeBoldText = true
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            isAntiAlias = true
                                        }
                                        drawContext.canvas.nativeCanvas.drawText(
                                            "G${idx + 1}",
                                            c.x,
                                            c.y + 6f,
                                            numPaint
                                        )
                                    }

                                    // Center measurement stamp inside khoanh vùng polygon
                                    val avgX = regionPolygonPoints.map { it.x }.average().toFloat() * size.width
                                    val avgY = regionPolygonPoints.map { it.y }.average().toFloat() * size.height
                                    drawLabelPill(
                                        center = Offset(avgX, avgY),
                                        text = String.format(
                                            Locale.US,
                                            "VÙNG KHOANH: %.1fm × %.1fm (%.0fm²)",
                                            houseWidthMeters,
                                            houseLengthMeters,
                                            houseWidthMeters * houseLengthMeters
                                        ),
                                        bgColor = Color(0xFF0F172A)
                                    )
                                }
                            }

                            // Top-left Reset Polygon Button
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.92f))
                                    .border(1.dp, LightBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        regionPolygonPoints.clear()
                                        gpsCalibratedNote = "Đã xóa khung khoanh vùng. Hãy chạm 4 góc mới trên bản đồ Google Maps."
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Khoanh Lại 4 Góc", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GPS Auto-Boundary Button
                        Button(
                            onClick = {
                                userManuallyClearedToBlindRoom = false
                                movementTracker.forceRecognizeMapLocation()
                                val lat = gpsLocation?.latitude ?: 10.7769
                                val lng = gpsLocation?.longitude ?: 106.7009
                                regionPolygonPoints.clear()
                                regionPolygonPoints.addAll(
                                    listOf(
                                        Point2D(0.16f, 0.14f),
                                        Point2D(0.84f, 0.14f),
                                        Point2D(0.84f, 0.86f),
                                        Point2D(0.16f, 0.86f)
                                    )
                                )
                                houseWidthMeters = 12f
                                houseLengthMeters = 16f
                                onUpdateFloorDimensions(floorId, houseWidthMeters, houseLengthMeters)
                                gpsCalibratedNote = String.format(
                                    Locale.US,
                                    "Đã khoanh vùng GPS (%.5f, %.5f) ➔ Mặt bằng trống 12m × 16m",
                                    lat,
                                    lng
                                )
                                // Transition to Step 2 where user sets up Fire Sensor Reference Anchor first!
                                mapStage = MapStage.SETUP_PLACEMENT
                                setupDrawer = SetupDrawerMode.ADD_FIRE_SENSOR
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("btn_gps_quick_size"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Khoanh Vùng Nhanh Theo Tọa Độ Google Maps GPS Hiện Tại",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = gpsCalibratedNote,
                            color = NormalGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Proceed to Step 2: Setup Fire Detection Anchor First
                    Button(
                        onClick = {
                            userManuallyClearedToBlindRoom = false
                            onUpdateFloorDimensions(floorId, houseWidthMeters, houseLengthMeters)
                            mapStage = MapStage.SETUP_PLACEMENT
                            setupDrawer = SetupDrawerMode.ADD_FIRE_SENSOR
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_enter_mode_2_setup"),
                        colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "XÁC NHẬN VÙNG KHOANH ➔ BƯỚC 2: SETUP MỐC BÁO CHÁY TRƯỚC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            return@Column
        }

        // =========================================================================
        // STAGE 2 & STAGE 3: INTERACTIVE 2D MAP CANVAS (UNPARTITIONED BY DEFAULT)
        // =========================================================================
        if (mapStage == MapStage.READY_MAP) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { visualStyle = MapVisualStyle.TRANSLUCENT_GOOGLE_MAP },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("btn_map_style_translucent"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (visualStyle == MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) PrimaryBlue else LightSurfaceVariant,
                        contentColor = if (visualStyle == MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1. Mờ Thấy Google Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { visualStyle = MapVisualStyle.CRISP_WHITE_HOUSE },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("btn_map_style_white"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE) PrimaryBlue else LightSurfaceVariant,
                        contentColor = if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE) Color.White else TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2. Trắng Rõ Mặt Bằng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2D Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE && mapStage == MapStage.READY_MAP) {
                        Color.White
                    } else {
                        Color(0xFFEFF6FF)
                    }
                )
                .border(1.5.dp, if (isFireActive) CriticalAlarmRed else LightBorder, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 3.0f)
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-350f, 350f),
                            y = (panOffset.y + pan.y).coerceIn(-350f, 350f)
                        )
                    }
                }
                .pointerInput(mapStage, setupDrawer) {
                    detectTapGestures { tapOffset ->
                        val normX = ((tapOffset.x - panOffset.x) / (size.width * scale)).coerceIn(0.08f, 0.92f)
                        val normY = ((tapOffset.y - panOffset.y) / (size.height * scale)).coerceIn(0.08f, 0.92f)
                        currentUserPos = Point2D(normX, normY)
                        onSetUserPosition(floorId, normX, normY)
                        activeRoute?.let { curRoute ->
                            if (curRoute.pathPoints.isNotEmpty()) {
                                activeRoute = buildDoorAwareRoute(
                                    start = currentUserPos,
                                    target = curRoute.pathPoints.last(),
                                    targetLabel = curRoute.targetName,
                                    targetType = curRoute.targetType,
                                    notes = curRoute.notes
                                )
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                fun toScreen(pt: Point2D): Offset {
                    return Offset(
                        x = pt.x * w * scale + panOffset.x,
                        y = pt.y * h * scale + panOffset.y
                    )
                }

                // 1. Background Layer: Either Translucent Google Map (Mode 1) or Crisp Pure White (Mode 2)
                if (mapStage == MapStage.SETUP_PLACEMENT || visualStyle == MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) {
                    drawGoogleMapsUnderlay(
                        alphaMultiplier = if (mapStage == MapStage.SETUP_PLACEMENT) 0.55f else 0.50f,
                        houseWidthMeters = houseWidthMeters,
                        houseLengthMeters = houseLengthMeters,
                        gpsLat = gpsLocation?.latitude ?: 10.7769,
                        gpsLng = gpsLocation?.longitude ?: 106.7009
                    )
                } else {
                    drawRect(color = Color.White, size = size)
                    val stepX = w / 10f
                    val stepY = h / 12f
                    for (i in 1..9) {
                        drawLine(
                            color = Color(0xFFF1F5F9),
                            start = Offset(i * stepX, 0f),
                            end = Offset(i * stepX, h),
                            strokeWidth = 1.5f
                        )
                    }
                    for (j in 1..11) {
                        drawLine(
                            color = Color(0xFFF1F5F9),
                            start = Offset(0f, j * stepY),
                            end = Offset(w, j * stepY),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 2. House Outer Boundary Wall (Open Unpartitioned Floorplan - KHÔNG TỰ CHIA PHÒNG!)
                val houseTopLeft = toScreen(Point2D(0.08f, 0.08f))
                val houseBotRight = toScreen(Point2D(0.92f, 0.92f))
                val houseRectSize = Size(houseBotRight.x - houseTopLeft.x, houseBotRight.y - houseTopLeft.y)

                drawRoundRect(
                    color = if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE && mapStage == MapStage.READY_MAP) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.76f)
                    },
                    topLeft = houseTopLeft,
                    size = houseRectSize,
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Fill
                )

                drawRoundRect(
                    color = Color(0xFF0F172A),
                    topLeft = houseTopLeft,
                    size = houseRectSize,
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 6f)
                )

                // Dimension label along top wall
                val dimPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#1E293B")
                    textSize = 22f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "VÙNG KHOANH: ${houseWidthMeters}m × ${houseLengthMeters}m (Mặt bằng mở - Không tự chia phòng)",
                    (houseTopLeft.x + houseBotRight.x) / 2f,
                    houseTopLeft.y - 12f,
                    dimPaint
                )

                // 3. Draw Signal + IMU Distance Calibration Line from Reference Fire Sensor Anchor (#1) to User
                if (anchorSensorPin != null && mapStage == MapStage.SETUP_PLACEMENT) {
                    val anchorScreen = toScreen(anchorSensorPin.position)
                    val userScreen = toScreen(currentUserPos)
                    drawLine(
                        color = Color(0xFF7C3AED),
                        start = anchorScreen,
                        end = userScreen,
                        strokeWidth = 3.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                    val midMeasure = Offset((anchorScreen.x + userScreen.x) / 2f, (anchorScreen.y + userScreen.y) / 2f)
                    drawLabelPill(
                        center = midMeasure,
                        text = String.format(
                            Locale.US,
                            "📡 K/c từ Mốc Lửa: %.2fm (Acc %.0f%%)",
                            fusedDistanceMeters,
                            fusedAccuracyPercent
                        ),
                        bgColor = Color(0xFF7C3AED)
                    )
                }

                // 4. Draw Active Navigation Path
                activeRoute?.let { route ->
                    if (route.pathPoints.size >= 2) {
                        val pathColor = when (route.targetType) {
                            "EXTINGUISHER" -> PrimaryBlue
                            "EXIT" -> NormalGreen
                            else -> CriticalAlarmRed
                        }
                        val navPath = Path()
                        val first = toScreen(route.pathPoints.first())
                        navPath.moveTo(first.x, first.y)
                        for (i in 1 until route.pathPoints.size) {
                            val pt = toScreen(route.pathPoints[i])
                            navPath.lineTo(pt.x, pt.y)
                        }

                        drawPath(
                            path = navPath,
                            color = pathColor.copy(alpha = 0.26f),
                            style = Stroke(width = 24f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = navPath,
                            color = pathColor,
                            style = Stroke(
                                width = 8f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 12f), 0f)
                            )
                        )

                        for (i in 0 until route.pathPoints.size - 1) {
                            val p1 = toScreen(route.pathPoints[i])
                            val p2 = toScreen(route.pathPoints[i + 1])
                            val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            drawCircle(color = Color.White, radius = 12f, center = mid)
                            drawCircle(color = pathColor, radius = 9f, center = mid)
                        }
                    }
                }

                // 5. Draw Main Doors (CỬA CHÍNH)
                tempMainDoors.forEachIndexed { idx, pt ->
                    drawMainDoorSymbol(toScreen(pt), idx + 1)
                }

                // 6. Draw Numbered Doors (CỬA ĐÁNH SỐ #1, #2, #3...)
                tempDoors.forEachIndexed { idx, pt ->
                    drawNumberedDoorSymbol(toScreen(pt), idx + 1)
                }

                // 7. Draw Stairs
                tempStairs.forEachIndexed { idx, pt ->
                    drawLargeStairsSymbol(toScreen(pt), "CẦU THANG ${idx + 1}")
                }

                // 8. Draw Exits (Cửa Exit)
                tempExits.forEachIndexed { idx, pt ->
                    drawLargeExitSymbol(toScreen(pt), "CỬA EXIT ${idx + 1}")
                }

                // 9. Draw Fire Extinguishers (Bình Chữa Cháy)
                tempExtinguishers.forEachIndexed { idx, pt ->
                    drawLargeExtinguisherSymbol(toScreen(pt), "BÌNH CC ${idx + 1}")
                }

                // 10. Draw Pinned Fire Sensors + CIRCULAR EXPANDING FIRE SPREAD ZONE WHEN ALARM RINGS LONGER
                val sensorsToRender = if (placedSensorPins.isNotEmpty()) {
                    placedSensorPins
                } else {
                    nodes.mapIndexed { idx, n ->
                        val prob = n.telemetry.calculateFireProbability().totalProbabilityPercent
                        val isAlm = n.lastKnownAlarm || n.firmwareState == FirmwareState.ALARM || prob >= 75f
                        PlacedSensorPin(
                            deviceId = n.deviceId,
                            displayName = n.displayName,
                            position = Point2D(n.xNormalized, n.yNormalized),
                            isAlarm = isAlm,
                            temperatureCelsius = n.telemetry.temperatureCelsius,
                            fireProbabilityPercent = prob,
                            rssiDbm = n.telemetry.rssiDbm,
                            alarmStartedAtMs = if (isAlm) (n.telemetry.alarmStartedAtMs.takeIf { it > 0L } ?: currentClockMs) else 0L,
                            isDemoSensor = n.isDemoSensor || n.displayName.contains("DEMO", ignoreCase = true),
                            isReferenceAnchor = (idx == 0)
                        )
                    }
                }

                sensorsToRender.forEachIndexed { idx, pin ->
                    val centerScreen = toScreen(pin.position)
                    val elapsedSec = if (pin.isAlarm) {
                        val startMs = if (pin.alarmStartedAtMs > 0L) pin.alarmStartedAtMs else (currentClockMs - 3000L)
                        ((currentClockMs - startMs) / 1000L).coerceAtLeast(1L)
                    } else 0L

                    // Radius in meters grows as alarm keeps ringing (+0.18m per second)
                    val spreadRadiusMeters = if (pin.isAlarm) {
                        (1.0f + elapsedSec * 0.18f).coerceAtMost(max(houseWidthMeters, houseLengthMeters) * 0.75f)
                    } else 0f

                    val pixelsPerMeter = (houseRectSize.width / houseWidthMeters.coerceAtLeast(5f)).coerceAtLeast(10f)
                    val spreadRadiusPx = spreadRadiusMeters * pixelsPerMeter

                    if (pin.isAlarm && spreadRadiusPx > 10f) {
                        drawExpandingFireSpreadCircle(
                            center = centerScreen,
                            spreadRadiusPx = spreadRadiusPx,
                            spreadRadiusMeters = spreadRadiusMeters,
                            elapsedSeconds = elapsedSec
                        )
                    }

                    val labelPrefix = if (idx == 0) "[MỐC CHUẨN #1] " else ""
                    val tempAndProb = String.format(
                        Locale.US,
                        "%.1f°C · P=%.0f%%",
                        pin.temperatureCelsius,
                        pin.fireProbabilityPercent
                    )
                    drawLargeFireSensorSymbol(
                        center = centerScreen,
                        label = "$labelPrefix${pin.displayName} ($tempAndProb)",
                        isAlarm = pin.isAlarm,
                        isAnchor = (idx == 0),
                        pulseRadius = pulseRadius
                    )
                }

                // 11. Draw Current Standing User Position
                drawUserStandingBeacon(
                    center = toScreen(currentUserPos),
                    headingDeg = compassHeading,
                    isWalking = isWalking
                )
            }

            // Floating mini helper badge on canvas top-left
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .border(1.dp, LightBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Radar,
                    contentDescription = null,
                    tint = if (placedSensorPins.isNotEmpty()) NormalGreen else CriticalAlarmRed,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (placedSensorPins.isEmpty()) {
                        "Chưa có Mốc Lửa chuẩn · Hãy ghim Báo Cháy trước!"
                    } else {
                        String.format(
                            Locale.US,
                            "RSSI %ddBm (%.1fm) + ĐT (%.1fm) ➔ Chuẩn: %.2fm",
                            liveRssiDbm,
                            rssiDistanceMeters,
                            imuPhoneDistanceMeters,
                            fusedDistanceMeters
                        )
                    },
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Floating Manual Step / Walk Simulator Pill on Top-Right of Canvas
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryBlue)
                    .clickable { advanceUserStep(1) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_simulate_walk_step"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsWalk,
                    contentDescription = "Bước tới 1 bước",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Bước Tới (+1)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // STAGE 2 BOTTOM CONTROLS:
        // BƯỚC 1 TRONG SETUP: GHIM VỊ TRÍ NHẬN DIỆN LỬA TRƯỚC LÀM MỐC CHUẨN + ĐO KHOẢNG CÁCH BẰNG TÍN HIỆU & CẢM BIẾN ĐT
        // BƯỚC 2 TRONG SETUP: GHIM CỬA CHÍNH, CÁC CÁNH CỬA (ĐÁNH SỐ #1, #2...), BÌNH CỨU HỎA, CỬA EXIT
        // =========================================================================
        if (mapStage == MapStage.SETUP_PLACEMENT) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LightBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = LightCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Two ordered step tabs: Step 1 (Fire Sensor Reference Anchor) -> Step 2 (Numbered Doors / Main Door / Extinguisher / Exit)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { setupDrawer = SetupDrawerMode.ADD_FIRE_SENSOR },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("btn_mode2_add_sensor"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (setupDrawer == SetupDrawerMode.ADD_FIRE_SENSOR) CriticalAlarmRed else LightSurfaceVariant,
                                contentColor = if (setupDrawer == SetupDrawerMode.ADD_FIRE_SENSOR) Color.White else TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "1. Mốc Lửa Chuẩn (${placedSensorPins.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { setupDrawer = SetupDrawerMode.ADD_OBJECT },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("btn_mode2_add_object"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (setupDrawer == SetupDrawerMode.ADD_OBJECT) PrimaryBlue else LightSurfaceVariant,
                                contentColor = if (setupDrawer == SetupDrawerMode.ADD_OBJECT) Color.White else TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DoorFront, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "2. Cửa Đánh Số / Bình CC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Signal RSSI + Phone Sensor Distance Calibration Telemetry Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5F3FF))
                            .border(1.dp, Color(0xFFC4B5FD), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "📡 Tín hiệu RSSI: %d dBm (~%.2fm) + 📲 Cảm biến ĐT (%d bước, %d°): ~%.2fm",
                                    liveRssiDbm,
                                    rssiDistanceMeters,
                                    stepCount,
                                    compassHeading.roundToInt(),
                                    imuPhoneDistanceMeters
                                ),
                                color = Color(0xFF5B21B6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "🎯 Khoảng cách kết hợp chuẩn hóa: %.2f m · Độ chính xác tăng cường Acc = %.1f%%",
                                    fusedDistanceMeters,
                                    fusedAccuracyPercent
                                ),
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (anchorSensorPin != null) {
                            OutlinedButton(
                                onClick = {
                                    // Position user at exact fused distance & compass heading from the Fire Sensor Reference Anchor
                                    val rad = Math.toRadians((compassHeading - 90f).toDouble())
                                    val dxNorm = (cos(rad).toFloat() * fusedDistanceMeters) / houseWidthMeters.coerceAtLeast(5f)
                                    val dyNorm = (sin(rad).toFloat() * fusedDistanceMeters) / houseLengthMeters.coerceAtLeast(5f)
                                    val calibratedPos = Point2D(
                                        x = (anchorSensorPin.position.x + dxNorm).coerceIn(0.10f, 0.90f),
                                        y = (anchorSensorPin.position.y + dyNorm).coerceIn(0.10f, 0.90f)
                                    )
                                    currentUserPos = calibratedPos
                                    onSetUserPosition(floorId, calibratedPos.x, calibratedPos.y)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Chuẩn Hóa Vị Trí", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B21B6))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Drawer 1: Setup Fire Detection Position FIRST as Reference Anchor
                    AnimatedVisibility(visible = setupDrawer == SetupDrawerMode.ADD_FIRE_SENSOR) {
                        Column {
                            Text(
                                text = if (placedSensorPins.isEmpty()) {
                                    "BƯỚC CHUẨN HÓA ĐẦU TIÊN: Chọn cảm biến nhận diện lửa và ghim làm MỐC CHUẨN (#1) trước:"
                                } else {
                                    "Đã có Mốc Chuẩn Lửa #1. Bạn có thể ghim thêm báo cháy hoặc chuyển sang mục 2 (Cửa đánh số / Bình CC):"
                                },
                                color = if (placedSensorPins.isEmpty()) CriticalAlarmRed else NormalGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(nodes) { node ->
                                    val isSelected = node.deviceId == selectedSavedNodeId
                                    val prob = node.telemetry.calculateFireProbability().totalProbabilityPercent
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSavedNodeId = node.deviceId },
                                        label = {
                                            Text(
                                                text = String.format(Locale.US, "%s (P=%.0f%%)", node.displayName, prob),
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Sensors,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else CriticalAlarmRed,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CriticalAlarmRed,
                                            selectedLabelColor = Color.White,
                                            containerColor = LightSurfaceVariant,
                                            labelColor = TextPrimary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    val chosenNode = nodes.find { it.deviceId == selectedSavedNodeId } ?: nodes.firstOrNull()
                                    val devId = chosenNode?.deviceId ?: "smartfire-node-${placedSensorPins.size + 1}"
                                    val devName = chosenNode?.displayName ?: "Báo cháy #${placedSensorPins.size + 1}"
                                    val prob = chosenNode?.telemetry?.calculateFireProbability()?.totalProbabilityPercent ?: 5.0f
                                    val isAlm = chosenNode?.let { it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM || prob >= 75f } ?: false
                                    val tempC = chosenNode?.telemetry?.temperatureCelsius ?: 28.5f
                                    val rssi = chosenNode?.telemetry?.rssiDbm ?: -52
                                    val isDemo = chosenNode?.isDemoSensor ?: true

                                    val existingIdx = placedSensorPins.indexOfFirst { it.deviceId == devId }
                                    if (existingIdx >= 0) {
                                        placedSensorPins[existingIdx] = placedSensorPins[existingIdx].copy(position = currentUserPos)
                                    } else {
                                        placedSensorPins.add(
                                            PlacedSensorPin(
                                                deviceId = devId,
                                                displayName = devName,
                                                position = currentUserPos,
                                                isAlarm = isAlm,
                                                temperatureCelsius = tempC,
                                                fireProbabilityPercent = prob,
                                                rssiDbm = rssi,
                                                alarmStartedAtMs = if (isAlm) System.currentTimeMillis() else 0L,
                                                isDemoSensor = isDemo,
                                                isReferenceAnchor = placedSensorPins.isEmpty()
                                            )
                                        )
                                    }
                                    onBindSensorPosition(devId, currentUserPos.x, currentUserPos.y)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("btn_pin_sensor_at_standing_pos"),
                                colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (placedSensorPins.isEmpty()) {
                                        "GHIM VỊ TRÍ NHẬN DIỆN LỬA ĐẦU TIÊN LÀM MỐC CHUẨN (0,0)"
                                    } else {
                                        "Ghim Báo Cháy Đã Chọn Tại Điểm Đang Đứng (${placedSensorPins.size})"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Drawer 2: Add Main Door, Numbered Doors (#1, #2...), Extinguisher, Stairs, Exit Door
                    AnimatedVisibility(visible = setupDrawer == SetupDrawerMode.ADD_OBJECT) {
                        Column {
                            if (placedSensorPins.isEmpty()) {
                                Text(
                                    text = "⚠️ Lưu ý: Bạn nên ghim '1. Mốc Lửa Chuẩn' trước để đo khoảng cách Tín hiệu + Cảm biến ĐT chính xác hơn!",
                                    color = CriticalAlarmRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            } else {
                                Text(
                                    text = "Đang đo khoảng cách từ Mốc Lửa Chuẩn #1 (${String.format(Locale.US, "%.2fm · Acc %.1f%%", fusedDistanceMeters, fusedAccuracyPercent)}). Chọn vật dụng cần ghim:",
                                    color = NormalGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Add Main Door (Cửa Chính)
                                OutlinedButton(
                                    onClick = { tempMainDoors.add(currentUserPos) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_add_main_door_here"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.DoorSliding, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "+ Cửa Chính (${tempMainDoors.size})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED)
                                    )
                                }

                                // 2. Add Numbered Door (Cánh cửa đánh số #1, #2...)
                                OutlinedButton(
                                    onClick = { tempDoors.add(currentUserPos) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_add_door_here"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.DoorFront, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "+ Cửa Số #${tempDoors.size + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 3. Add Fire Extinguisher
                                OutlinedButton(
                                    onClick = { tempExtinguishers.add(currentUserPos) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_add_extinguisher_here"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FireExtinguisher, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+ Bình CC (${tempExtinguishers.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }

                                // 4. Add Cửa Exit
                                OutlinedButton(
                                    onClick = { tempExits.add(currentUserPos) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("btn_add_exit_here"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+ Cửa Exit (${tempExits.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NormalGreen)
                                }

                                // 5. Add Stairs
                                OutlinedButton(
                                    onClick = { tempStairs.add(currentUserPos) },
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(38.dp)
                                        .testTag("btn_add_stairs_here"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Stairs, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("+ Thang (${tempStairs.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save All & Finish Map Button
                    Button(
                        onClick = {
                            userManuallyClearedToBlindRoom = false
                            if (placedSensorPins.isEmpty() && nodes.isNotEmpty()) {
                                val firstNode = nodes.first()
                                placedSensorPins.add(
                                    PlacedSensorPin(
                                        deviceId = firstNode.deviceId,
                                        displayName = firstNode.displayName,
                                        position = Point2D(0.35f, 0.32f),
                                        isAlarm = firstNode.lastKnownAlarm,
                                        temperatureCelsius = firstNode.telemetry.temperatureCelsius,
                                        fireProbabilityPercent = firstNode.telemetry.calculateFireProbability().totalProbabilityPercent,
                                        rssiDbm = firstNode.telemetry.rssiDbm,
                                        isReferenceAnchor = true
                                    )
                                )
                                onBindSensorPosition(firstNode.deviceId, 0.35f, 0.32f)
                            }
                            onSaveScannedSetup(
                                placedSensorPins.map { it.position },
                                tempDoors.toList(),
                                tempStairs.toList(),
                                tempExtinguishers.toList(),
                                tempExits.toList(),
                                tempMainDoors.toList()
                            )
                            mapStage = MapStage.READY_MAP
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_save_map_config"),
                        colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "LƯU XONG BẢN ĐỒ & MỞ CHẾ ĐỘ DẪN ĐƯỜNG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // =========================================================================
        // STAGE 3 BOTTOM CONTROLS: 2 HUGE ACTION BUTTONS + CỬA EXIT / CỬA CHÍNH
        // =========================================================================
        if (mapStage == MapStage.READY_MAP) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { routeToFireSensor(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("btn_huge_route_fire"),
                        colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "DẪN TỚI BÁO CHÁY",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = if (isFireActive) "Ưu tiên vùng lửa đang lan gần nhất!" else "Chỉ đường tới Báo Cháy gần nhất",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { routeToExtinguisher() },
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .testTag("btn_huge_route_extinguisher"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.FireExtinguisher,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "TỚI BÌNH CHỮA CHÁY",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Đi qua các cửa đánh số lấy Bình CC",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { routeToExitDoor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("btn_route_exit_door"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NormalGreenBackground,
                        contentColor = NormalGreen
                    )
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DẪN TỚI CỬA CHÍNH / CỬA EXIT THOÁT HIỂM (${tempMainDoors.size} Cửa Chính · ${tempExits.size} Exit)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NormalGreen
                    )
                }
            }
        }
    }
}

/**
 * Interactive Google Maps / Satellite & Street Map WebView for Step 1 Region Selection (Khoanh vùng khu vực).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GoogleMapsTileWebView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(latitude, longitude) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #e2e8f0; font-family: sans-serif; }
                .badge {
                    position: absolute; bottom: 6px; left: 8px; z-index: 999;
                    background: rgba(15, 23, 42, 0.82); color: #fff; padding: 4px 8px;
                    border-radius: 6px; font-size: 11px; font-weight: bold; pointer-events: none;
                }
            </style>
        </head>
        <body>
            <div class="badge">Google Maps GPS: ${String.format(Locale.US, "%.5f, %.5f", latitude, longitude)}</div>
            <iframe
                width="100%"
                height="100%"
                frameborder="0"
                style="border:0; pointer-events:none;"
                src="https://maps.google.com/maps?q=$latitude,$longitude&t=k&z=19&ie=UTF8&iwloc=&output=embed">
            </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = WebViewClient()
                isClickable = false
                isFocusable = false
                loadDataWithBaseURL("https://maps.google.com", htmlContent, "text/html", "UTF-8", null)
            }
        }
    )
}

// ============================================================================
// CUSTOM ARCHITECTURAL, NUMBERED DOORS, MAIN DOOR & FIRE SPREAD DRAWING HELPERS
// ============================================================================

private fun DrawScope.drawExpandingFireSpreadCircle(
    center: Offset,
    spreadRadiusPx: Float,
    spreadRadiusMeters: Float,
    elapsedSeconds: Long
) {
    val areaM2 = (PI * spreadRadiusMeters * spreadRadiusMeters).toFloat()

    // Outer smoke & heat hazard halo
    drawCircle(
        color = Color(0xFFF97316).copy(alpha = 0.16f),
        radius = spreadRadiusPx * 1.22f,
        center = center
    )
    drawCircle(
        color = Color(0xFFF97316).copy(alpha = 0.55f),
        radius = spreadRadiusPx * 1.22f,
        center = center,
        style = Stroke(
            width = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )

    // Main circular fire spread zone (grows as alarm rings longer)
    drawCircle(
        color = CriticalAlarmRed.copy(alpha = 0.25f),
        radius = spreadRadiusPx,
        center = center
    )
    drawCircle(
        color = CriticalAlarmRed,
        radius = spreadRadiusPx,
        center = center,
        style = Stroke(width = 4.5f)
    )

    // Inner intense flame core ring
    drawCircle(
        color = Color(0xFFDC2626).copy(alpha = 0.35f),
        radius = spreadRadiusPx * 0.55f,
        center = center
    )

    // Radial measurement line from fire center to perimeter
    val radiusEnd = Offset(center.x + spreadRadiusPx, center.y)
    drawLine(
        color = CriticalAlarmRed,
        start = center,
        end = radiusEnd,
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )
    drawCircle(color = CriticalAlarmRed, radius = 6f, center = radiusEnd)

    // Label showing exact Fire Spread Radius (m), Area (m2), and Elapsed Time (s)
    drawLabelPill(
        center = Offset(center.x, center.y + spreadRadiusPx + 22f),
        text = String.format(
            Locale.US,
            "🔥 LỬA LAN TRÒN: R=%.2fm (S=%.1fm²) · %ds",
            spreadRadiusMeters,
            areaM2,
            elapsedSeconds
        ),
        bgColor = Color(0xFFB91C1C)
    )
}

/**
 * Draws a Numbered Door Symbol with a prominent door number badge (#1, #2, #3...) right on the door.
 */
private fun DrawScope.drawNumberedDoorSymbol(center: Offset, doorNumber: Int) {
    // High-contrast circular door icon
    drawCircle(color = Color(0xFFFEF3C7), radius = 27f, center = center)
    drawCircle(color = Color(0xFFD97706), radius = 27f, center = center, style = Stroke(width = 3.8f))

    // Door jamb posts
    drawRect(
        color = Color(0xFF92400E),
        topLeft = Offset(center.x - 15f, center.y - 4f),
        size = Size(6f, 8f)
    )
    drawRect(
        color = Color(0xFF92400E),
        topLeft = Offset(center.x + 9f, center.y - 4f),
        size = Size(6f, 8f)
    )
    // Open door leaf
    drawLine(
        color = Color(0xFFB45309),
        start = Offset(center.x - 10f, center.y + 2f),
        end = Offset(center.x + 5f, center.y - 14f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    // Swing arc
    drawArc(
        color = Color(0xFFD97706),
        startAngle = 290f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(center.x - 14f, center.y - 14f),
        size = Size(28f, 28f),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f))
    )

    // Prominent Number Badge on top-right of the door symbol (#1, #2, #3...)
    val badgeCenter = Offset(center.x + 19f, center.y - 19f)
    drawCircle(color = Color.White, radius = 14f, center = badgeCenter)
    drawCircle(color = Color(0xFFB45309), radius = 12f, center = badgeCenter)
    val numPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 16f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        "#$doorNumber",
        badgeCenter.x,
        badgeCenter.y + 5.5f,
        numPaint
    )

    drawLabelPill(
        center = Offset(center.x, center.y + 39f),
        text = "CỬA SỐ $doorNumber",
        bgColor = Color(0xFFD97706)
    )
}

/**
 * Draws a Main Entrance Door Symbol (CỬA CHÍNH #1) with double-leaf architectural swing.
 */
private fun DrawScope.drawMainDoorSymbol(center: Offset, mainDoorNumber: Int) {
    drawRoundRect(
        color = Color(0xFFEDE9FE),
        topLeft = Offset(center.x - 32f, center.y - 22f),
        size = Size(64f, 44f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFF7C3AED),
        topLeft = Offset(center.x - 32f, center.y - 22f),
        size = Size(64f, 44f),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 4f)
    )

    // Double door leaves
    drawLine(
        color = Color(0xFF5B21B6),
        start = Offset(center.x - 20f, center.y + 6f),
        end = Offset(center.x - 4f, center.y - 12f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF5B21B6),
        start = Offset(center.x + 20f, center.y + 6f),
        end = Offset(center.x + 4f, center.y - 12f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )

    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#5B21B6")
        textSize = 15f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText("CHÍNH #$mainDoorNumber", center.x, center.y + 16f, paint)

    drawLabelPill(
        center = Offset(center.x, center.y + 38f),
        text = "CỬA CHÍNH #$mainDoorNumber",
        bgColor = Color(0xFF7C3AED)
    )
}

private fun DrawScope.drawGoogleMapsUnderlay(
    alphaMultiplier: Float,
    houseWidthMeters: Float,
    houseLengthMeters: Float,
    gpsLat: Double,
    gpsLng: Double
) {
    drawRect(
        color = Color(0xFFE2E8F0).copy(alpha = alphaMultiplier),
        size = size
    )

    drawRoundRect(
        color = Color(0xFFDCFCE7).copy(alpha = alphaMultiplier * 0.90f),
        topLeft = Offset(12f, 12f),
        size = Size(size.width * 0.25f, size.height * 0.22f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = Color(0xFFDBEAFE).copy(alpha = alphaMultiplier * 0.85f),
        topLeft = Offset(size.width * 0.72f, size.height * 0.75f),
        size = Size(size.width * 0.25f, size.height * 0.22f),
        cornerRadius = CornerRadius(12f, 12f)
    )

    val roadColor = Color(0xFFCBD5E1).copy(alpha = alphaMultiplier)
    drawRect(
        color = roadColor,
        topLeft = Offset(0f, size.height * 0.93f),
        size = Size(size.width, size.height * 0.07f)
    )
    drawRect(
        color = roadColor,
        topLeft = Offset(0f, 0f),
        size = Size(size.width * 0.06f, size.height)
    )

    val cols = 8
    val rows = 10
    val dx = size.width / cols
    val dy = size.height / rows
    val gridColor = Color(0xFF94A3B8).copy(alpha = alphaMultiplier * 0.45f)

    for (i in 1 until cols) {
        drawLine(gridColor, Offset(i * dx, 0f), Offset(i * dx, size.height), strokeWidth = 1.2f)
    }
    for (j in 1 until rows) {
        drawLine(gridColor, Offset(0f, j * dy), Offset(size.width, j * dy), strokeWidth = 1.2f)
    }

    val gpsPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#1E293B")
        textSize = 18f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        String.format(Locale.US, "Google Maps GPS: %.5f, %.5f", gpsLat, gpsLng),
        size.width - 18f,
        size.height - 14f,
        gpsPaint
    )
}

private fun DrawScope.drawLargeExtinguisherSymbol(center: Offset, label: String) {
    drawCircle(color = Color(0xFFFEE2E2), radius = 28f, center = center)
    drawCircle(color = CriticalAlarmRed, radius = 28f, center = center, style = Stroke(width = 4f))

    drawRoundRect(
        color = CriticalAlarmRed,
        topLeft = Offset(center.x - 9f, center.y - 8f),
        size = Size(18f, 24f),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(center.x - 9f, center.y),
        size = Size(18f, 5f)
    )
    drawLine(
        color = Color(0xFF1E293B),
        start = Offset(center.x, center.y - 8f),
        end = Offset(center.x, center.y - 16f),
        strokeWidth = 4f
    )
    drawLine(
        color = Color(0xFF1E293B),
        start = Offset(center.x - 7f, center.y - 15f),
        end = Offset(center.x + 8f, center.y - 12f),
        strokeWidth = 3.5f
    )

    drawLabelPill(center = Offset(center.x, center.y + 40f), text = label, bgColor = CriticalAlarmRed)
}

private fun DrawScope.drawLargeStairsSymbol(center: Offset, label: String) {
    drawCircle(color = Color(0xFFE0E7FF), radius = 26f, center = center)
    drawCircle(color = Color(0xFF4F46E5), radius = 26f, center = center, style = Stroke(width = 3.5f))

    val stepPath = Path().apply {
        moveTo(center.x - 12f, center.y + 10f)
        lineTo(center.x - 12f, center.y + 3f)
        lineTo(center.x - 4f, center.y + 3f)
        lineTo(center.x - 4f, center.y - 4f)
        lineTo(center.x + 4f, center.y - 4f)
        lineTo(center.x + 4f, center.y - 11f)
        lineTo(center.x + 12f, center.y - 11f)
    }
    drawPath(stepPath, color = Color(0xFF4F46E5), style = Stroke(width = 4f, cap = StrokeCap.Round))

    drawLabelPill(center = Offset(center.x, center.y + 38f), text = label, bgColor = Color(0xFF4F46E5))
}

private fun DrawScope.drawLargeExitSymbol(center: Offset, label: String) {
    drawRoundRect(
        color = NormalGreen,
        topLeft = Offset(center.x - 28f, center.y - 18f),
        size = Size(56f, 36f),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(center.x - 28f, center.y - 18f),
        size = Size(56f, 36f),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 3f)
    )
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText("EXIT", center.x, center.y + 7f, paint)
    drawLabelPill(center = Offset(center.x, center.y + 34f), text = label, bgColor = NormalGreen)
}

private fun DrawScope.drawLargeFireSensorSymbol(
    center: Offset,
    label: String,
    isAlarm: Boolean,
    isAnchor: Boolean,
    pulseRadius: Float
) {
    if (isAlarm) {
        drawCircle(
            color = CriticalAlarmRed.copy(alpha = 0.30f),
            radius = pulseRadius * 1.4f,
            center = center
        )
        drawCircle(
            color = CriticalAlarmRed.copy(alpha = 0.55f),
            radius = pulseRadius,
            center = center
        )
    }

    val ringColor = when {
        isAlarm -> CriticalAlarmRed
        isAnchor -> Color(0xFF7C3AED)
        else -> Color(0xFFEA580C)
    }
    drawCircle(color = Color.White, radius = 26f, center = center)
    drawCircle(color = ringColor, radius = 26f, center = center, style = Stroke(width = 5f))
    drawCircle(color = ringColor, radius = 12f, center = center)

    drawLabelPill(
        center = Offset(center.x, center.y - 38f),
        text = if (isAlarm) "🔥 ĐANG CHÁY: $label" else "🔔 $label",
        bgColor = ringColor
    )
}

private fun DrawScope.drawUserStandingBeacon(
    center: Offset,
    headingDeg: Float,
    isWalking: Boolean
) {
    drawArc(
        color = PrimaryBlue.copy(alpha = 0.28f),
        startAngle = headingDeg - 120f,
        sweepAngle = 60f,
        useCenter = true,
        topLeft = Offset(center.x - 42f, center.y - 42f),
        size = Size(84f, 84f)
    )

    drawCircle(color = Color.White, radius = 20f, center = center)
    drawCircle(
        color = if (isWalking) NormalGreen else PrimaryBlue,
        radius = 15f,
        center = center
    )
    drawCircle(color = Color.White, radius = 5f, center = center)

    drawLabelPill(
        center = Offset(center.x, center.y + 34f),
        text = "BẠN ĐANG ĐỨNG ĐÂY",
        bgColor = Color(0xFF0F172A)
    )
}

private fun DrawScope.drawLabelPill(center: Offset, text: String, bgColor: Color) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 19f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val textWidth = paint.measureText(text)
    val pillW = textWidth + 20f
    val pillH = 28f

    drawRoundRect(
        color = bgColor,
        topLeft = Offset(center.x - pillW / 2f, center.y - pillH / 2f),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        center.x,
        center.y + 7f,
        paint
    )
}
