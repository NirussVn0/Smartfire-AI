package com.example.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.DoorSliding
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.example.domain.pathfinding.GeoLatLng
import com.example.domain.pathfinding.GeoMapMath
import com.example.domain.pathfinding.NavigationRoute
import com.example.domain.pathfinding.TurnByTurnNavigator
import com.example.sensor.DeviceMovementTracker
import com.example.sensor.PresenceZone
import com.example.ui.components.TurnByTurnNavigationBanner
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EmergencyAutoPhase {
    IDLE,
    FETCH_EXTINGUISHER_FIRST,
    ATTACK_FIRE_SOURCE
}

enum class MapStage {
    BLIND_ROOM_SIZING, // Bước 1: Khoanh vùng thực tế trên bản đồ (Đa giác bất kỳ / Kéo góc / Vẽ khung -> Cắt đúng hình dạng vừa khoanh!)
    SETUP_PLACEMENT,   // Bước 2: Ghim Mốc Nhận Diện Lửa TRƯỚC làm chuẩn (0,0) + Đo khoảng cách RSSI & Cảm biến ĐT -> Ghim Cửa Chính, Cửa Đánh Số, Bình CC, Exit
    READY_MAP          // Bước 3: Bản đồ hoàn chỉnh đúng hình dạng đã khoanh vùng + Lửa lan tròn theo thời gian + Dẫn đường
}

enum class MapVisualStyle {
    TRANSLUCENT_GOOGLE_MAP, // Chế độ 1: Mờ mờ thấy bản đồ vệ tinh/đường phố bên dưới vùng khoanh
    CRISP_WHITE_HOUSE       // Chế độ 2: Cắt trắng tinh vùng nhà đã khoanh, làm tối bên ngoài để nhìn rõ tuyệt đối
}

enum class SetupDrawerMode {
    ADD_FIRE_SENSOR, // Bước chuẩn hóa bắt buộc đầu tiên: Ghim Vị Trí Nhận Diện Lửa làm chuẩn + Đo khoảng cách Tín hiệu RSSI & Cảm biến ĐT
    ADD_OBJECT       // Bước tiếp theo: Ghim Cửa Chính, Cánh Cửa (đánh số #1, #2...), Bình chữa cháy, Cầu thang, Cửa Exit
}

enum class RegionDrawToolMode {
    DRAW_POLYGON_POINTS, // Chạm trực tiếp trên Google Maps hoặc kéo đỉnh Đ1..Đn khớp mái nhà
    PAN_ZOOM_MAP         // Chỉ kéo & phóng to Google Maps (dùng tâm chữ thập 🎯 như Grab để ghim chính xác)
}

enum class GoogleMapLayerType(val label: String, val lyrsCode: String) {
    HYBRID("Vệ tinh + Đường", "y"),
    SATELLITE("Vệ tinh", "s"),
    ROADMAP("Đường phố", "m")
}

data class PlacedSensorPin(
    val deviceId: String,
    val displayName: String,
    val position: Point2D,
    val isAlarm: Boolean = false,
    val temperatureCelsius: Float? = null,
    val fireProbabilityPercent: Float = 5.0f,
    val rssiDbm: Int = -52,
    val alarmStartedAtMs: Long = 0L,
    val isDemoSensor: Boolean = false,
    val isReferenceAnchor: Boolean = false,
    val suspectedBurnedOffline: Boolean = false
)

enum class SelectedMapElementKind {
    SENSOR,
    MAIN_DOOR,
    NUMBERED_DOOR,
    EXTINGUISHER,
    EXIT,
    STAIRS
}

data class SelectedMapElement(
    val kind: SelectedMapElementKind,
    val index: Int,
    val label: String,
    val position: Point2D,
    val deviceIdOrDbId: String? = null
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
    gasThresholdAdc: Int = 1800,
    stepStrideMeters: Float = 0.65f,
    autoRouteOnAlarm: Boolean = true,
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
    onSaveBoundaryPolygon: (String, List<Point2D>, Float, Float) -> Unit = { _, _, _, _ -> },
    onBindSensorPosition: (String, Float, Float) -> Unit = { _, _, _ -> },
    onDeleteExtinguisher: (String) -> Unit = {},
    onUpdateExtinguisherPosition: (String, Float, Float) -> Unit = { _, _, _ -> },
    onDeleteMapObject: (String) -> Unit = {},
    onUpdateMapObjectPosition: (String, Float, Float) -> Unit = { _, _, _ -> },
    onUpdateFloorDimensions: (String, Float, Float) -> Unit = { _, _, _ -> },
    onClearAllMapConfig: (String) -> Unit = {},
    onSetUserPosition: (String, Float, Float) -> Unit,
    onMuteLocalNotifications: () -> Unit = {},
    sharedMovementTracker: DeviceMovementTracker? = null,
    isLocalAlarmMuted: Boolean = false,
    onBackToDashboardWithNav: () -> Unit = {},
    onStopGlobalNavigation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentFloor = floors.firstOrNull()
    val floorId = currentFloor?.id ?: "floor_1"

    // Hardware Movement Tracker (reuses app-wide tracker if provided so Dashboard Turn-by-Turn & 2D Map stay in sync)
    val movementTracker = remember(sharedMovementTracker) {
        sharedMovementTracker ?: DeviceMovementTracker(context, scope)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        movementTracker.refreshGpsLocationNow()
        movementTracker.forceRecognizeMapLocation()
    }

    DisposableEffect(movementTracker, sharedMovementTracker) {
        if (sharedMovementTracker == null) {
            movementTracker.startTracking()
        } else {
            movementTracker.refreshGpsLocationNow()
        }
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
        onDispose {
            if (sharedMovementTracker == null) {
                movementTracker.stopTracking()
            }
        }
    }

    val stepCount by movementTracker.stepCount.collectAsState()
    val compassHeading by movementTracker.compassHeading.collectAsState()
    val isWalking by movementTracker.isWalking.collectAsState()
    val gpsLocation by movementTracker.lastGpsLocation.collectAsState()
    val gpsAccuracyMeters by movementTracker.gpsAccuracyMeters.collectAsState()
    val hasRealGpsFix by movementTracker.hasRealGpsFix.collectAsState()
    val gpsDeltaMeters by movementTracker.gpsDeltaMeters.collectAsState()

    // Check if there is a saved boundary polygon in rooms
    val savedBoundaryRoom = rooms.firstOrNull { it.type == "MAIN_BOUNDARY" && it.polygon.size >= 3 }
    val hasSavedObjects = savedBoundaryRoom != null || extinguishers.isNotEmpty() || mapObjects.isNotEmpty() || (currentFloor != null && currentFloor.widthMeters > 0f)

    var mapStage by remember(initialInSetupMode) {
        mutableStateOf(
            when {
                initialInSetupMode -> MapStage.BLIND_ROOM_SIZING
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

    // House physical dimensions in meters (computed directly from real Google Maps GeoLatLng vertices!)
    var houseWidthMeters by remember(currentFloor?.widthMeters) {
        mutableFloatStateOf(if ((currentFloor?.widthMeters ?: 0f) > 0f) currentFloor!!.widthMeters else 14f)
    }
    var houseLengthMeters by remember(currentFloor?.lengthMeters) {
        mutableFloatStateOf(if ((currentFloor?.lengthMeters ?: 0f) > 0f) currentFloor!!.lengthMeters else 18f)
    }

    // Normalized polygon for 2D Stage 2/3 rendering & DB persistence
    val regionPolygonPoints = remember {
        mutableStateListOf<Point2D>().apply {
            if (savedBoundaryRoom != null) {
                addAll(savedBoundaryRoom.polygon)
            }
        }
    }

    // True Geo-Anchored Roof Corners (lat, lng) pinned directly onto Google Maps in Stage 1
    val defaultCenterLatLng = GeoLatLng(
        lat = gpsLocation?.latitude ?: 10.776889,
        lng = gpsLocation?.longitude ?: 106.700806
    )
    var mapCenterLatLng by remember { mutableStateOf(defaultCenterLatLng) }
    var googleMapLayer by remember { mutableStateOf(GoogleMapLayerType.HYBRID) }
    var searchAddressQuery by remember { mutableStateOf("") }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var addressSearchNotice by remember { mutableStateOf<String?>(null) }
    var flyToTrigger by remember { mutableIntStateOf(0) }
    var flyToTarget by remember { mutableStateOf(defaultCenterLatLng) }
    var flyToZoom by remember { mutableFloatStateOf(20f) }

    val geoPolygonPoints = remember {
        mutableStateListOf<GeoLatLng>().apply {
            if (savedBoundaryRoom != null && savedBoundaryRoom.polygon.size >= 3) {
                addAll(
                    GeoMapMath.normalizedPointsToGeoPolygon(
                        normPoints = savedBoundaryRoom.polygon,
                        center = defaultCenterLatLng,
                        widthMeters = houseWidthMeters,
                        lengthMeters = houseLengthMeters
                    )
                )
            }
        }
    }

    // Sync savedBoundaryRoom when DB finishes loading
    LaunchedEffect(savedBoundaryRoom) {
        if (regionPolygonPoints.isEmpty() && savedBoundaryRoom != null && !userManuallyClearedToBlindRoom) {
            regionPolygonPoints.addAll(savedBoundaryRoom.polygon)
            if (geoPolygonPoints.isEmpty()) {
                geoPolygonPoints.addAll(
                    GeoMapMath.normalizedPointsToGeoPolygon(
                        normPoints = savedBoundaryRoom.polygon,
                        center = mapCenterLatLng,
                        widthMeters = houseWidthMeters,
                        lengthMeters = houseLengthMeters
                    )
                )
            }
        }
    }

    var regionDrawToolMode by remember { mutableStateOf(RegionDrawToolMode.DRAW_POLYGON_POINTS) }

    fun syncNormalizedFromGeoPolygon() {
        if (geoPolygonPoints.size >= 3) {
            val metrics = GeoMapMath.calculateGeoPolygonMetrics(geoPolygonPoints.toList())
            houseWidthMeters = metrics.widthMeters
            houseLengthMeters = metrics.lengthMeters
            regionPolygonPoints.clear()
            regionPolygonPoints.addAll(GeoMapMath.projectGeoPolygonToNormalized(geoPolygonPoints.toList()))
        } else {
            regionPolygonPoints.clear()
        }
    }

    val geoMetrics = remember(geoPolygonPoints.toList()) {
        if (geoPolygonPoints.size >= 2) {
            GeoMapMath.calculateGeoPolygonMetrics(geoPolygonPoints.toList())
        } else null
    }

    val calcWidthM = geoMetrics?.widthMeters ?: houseWidthMeters
    val calcLengthM = geoMetrics?.lengthMeters ?: houseLengthMeters
    val calcPolygonAreaM2 = geoMetrics?.areaM2 ?: 0f

    // Normalized floorplan polygon aligned strictly North-Up from pinned Google Maps vertices
    val activeFloorplanPolygon: List<Point2D> = remember(regionPolygonPoints.toList(), geoPolygonPoints.toList()) {
        if (geoPolygonPoints.size >= 3) {
            GeoMapMath.projectGeoPolygonToNormalized(geoPolygonPoints.toList())
        } else if (regionPolygonPoints.size >= 3) {
            val minX = regionPolygonPoints.minOf { it.x }
            val maxX = regionPolygonPoints.maxOf { it.x }
            val minY = regionPolygonPoints.minOf { it.y }
            val maxY = regionPolygonPoints.maxOf { it.y }
            val spanX = (maxX - minX).coerceAtLeast(0.08f)
            val spanY = (maxY - minY).coerceAtLeast(0.08f)
            val maxSpan = max(spanX, spanY)
            val scaleFactor = 0.78f / maxSpan
            val centerPolyX = (minX + maxX) / 2f
            val centerPolyY = (minY + maxY) / 2f
            regionPolygonPoints.map { pt ->
                Point2D(
                    x = (0.50f + (pt.x - centerPolyX) * scaleFactor).coerceIn(0.06f, 0.94f),
                    y = (0.50f + (pt.y - centerPolyY) * scaleFactor).coerceIn(0.06f, 0.94f)
                )
            }
        } else {
            listOf(
                Point2D(0.10f, 0.10f),
                Point2D(0.90f, 0.10f),
                Point2D(0.90f, 0.90f),
                Point2D(0.10f, 0.90f)
            )
        }
    }

    var visualStyle by remember { mutableStateOf(MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) }
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
    LaunchedEffect(nodes, hasSavedObjects, gasThresholdAdc) {
        if (placedSensorPins.isEmpty() && hasSavedObjects) {
            nodes.forEachIndexed { idx, n ->
                val prob = n.telemetry.calculateFireProbability(gasThresholdAdc, n.effectiveConfig()).totalProbabilityPercent
                val isAlm = n.isEffectivelyAlarming() || n.suspectedBurnedOffline
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
                        isReferenceAnchor = (idx == 0),
                        suspectedBurnedOffline = n.suspectedBurnedOffline
                    )
                )
            }
        } else {
            placedSensorPins.removeAll { pin -> nodes.none { it.deviceId == pin.deviceId } }
            nodes.forEachIndexed { idx, n ->
                val existingIdx = placedSensorPins.indexOfFirst { it.deviceId == n.deviceId }
                val prob = n.telemetry.calculateFireProbability(gasThresholdAdc, n.effectiveConfig()).totalProbabilityPercent
                val isAlm = n.isEffectivelyAlarming() || n.suspectedBurnedOffline
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
                        isReferenceAnchor = (existingIdx == 0),
                        suspectedBurnedOffline = n.suspectedBurnedOffline
                    )
                }
            }
        }
    }

    // Real-time 1-second clock tick for simulating circular fire spread radius as long as alarm rings
    var currentClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hazardNodes = nodes.filter { it.isEffectivelyAlarming() || it.suspectedBurnedOffline }
    val isFireActive = hazardNodes.isNotEmpty()

    LaunchedEffect(isFireActive) {
        while (isFireActive) {
            currentClockMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    var currentUserPos by remember {
        mutableStateOf(userLocation?.position ?: Point2D(0.50f, 0.72f))
    }

    // Tri-Sensor Indoor Fusion (Phone IMU Steps + Phone Compass/Gyro + ESP32 RSSI -> 0% GPS drift indoors)
    val anchorSensorPin = placedSensorPins.firstOrNull()
    val selectedNodeObj = nodes.find { it.deviceId == selectedSavedNodeId } ?: nodes.firstOrNull()
    val liveRssiDbm = selectedNodeObj?.telemetry?.rssiDbm ?: -52
    val rssiDistanceMeters = selectedNodeObj?.telemetry?.estimateDistanceFromRssiMeters() ?: 3.1f

    val imuPhoneDistanceMeters = if (anchorSensorPin != null) {
        val dxM = (currentUserPos.x - anchorSensorPin.position.x) * houseWidthMeters
        val dyM = (currentUserPos.y - anchorSensorPin.position.y) * houseLengthMeters
        hypot(dxM.toDouble(), dyM.toDouble()).toFloat()
    } else {
        (stepCount * stepStrideMeters).coerceAtLeast(1.2f)
    }

    val triFusionSnapshot = remember(
        currentUserPos,
        compassHeading,
        houseWidthMeters,
        houseLengthMeters,
        anchorSensorPin,
        rssiDistanceMeters,
        liveRssiDbm
    ) {
        DeviceMovementTracker.fuseIndoorPosition(
            currentPos = currentUserPos,
            stepsDelta = 0,
            stepStrideMeters = stepStrideMeters,
            compassHeadingDeg = compassHeading,
            houseWidthMeters = houseWidthMeters,
            houseLengthMeters = houseLengthMeters,
            anchorPos = anchorSensorPin?.position,
            rssiDistanceMeters = rssiDistanceMeters,
            rssiDbm = liveRssiDbm,
            activePath = null
        )
    }

    val fusedDistanceMeters = triFusionSnapshot.fusedDistanceToAnchorMeters
    val fusedAccuracyPercent = triFusionSnapshot.accuracyPercent
    val fusedErrorMeters = triFusionSnapshot.estimatedErrorMeters

    // Real-time Indoor vs Outdoor Presence Classification (Polygon + GPS Geo-fence + ESP32 Wi-Fi RSSI)
    val houseGeoCenter = remember(geoPolygonPoints.size, mapCenterLatLng) {
        if (geoPolygonPoints.size >= 3) {
            GeoLatLng(
                lat = geoPolygonPoints.map { it.lat }.average(),
                lng = geoPolygonPoints.map { it.lng }.average()
            )
        } else {
            mapCenterLatLng
        }
    }
    val gpsDistFromHouseCenterM = remember(gpsLocation, houseGeoCenter, hasRealGpsFix) {
        if (hasRealGpsFix && gpsLocation != null) {
            GeoMapMath.haversineDistanceMeters(
                GeoLatLng(gpsLocation!!.latitude, gpsLocation!!.longitude),
                houseGeoCenter
            )
        } else null
    }
    val allRealNodesOffline = nodes.isNotEmpty() &&
        nodes.none { it.isDemoSensor } &&
        nodes.all { it.connectionState == com.example.domain.model.DeviceConnectionState.OFFLINE }
    val presenceState = remember(
        currentUserPos,
        activeFloorplanPolygon,
        houseWidthMeters,
        houseLengthMeters,
        gpsDistFromHouseCenterM,
        gpsAccuracyMeters,
        hasRealGpsFix,
        liveRssiDbm,
        rssiDistanceMeters,
        allRealNodesOffline
    ) {
        DeviceMovementTracker.evaluatePresenceZone(
            userPos = currentUserPos,
            housePolygon = activeFloorplanPolygon,
            houseWidthMeters = houseWidthMeters,
            houseLengthMeters = houseLengthMeters,
            gpsDistanceFromHouseCenterMeters = gpsDistFromHouseCenterM,
            gpsAccuracyMeters = gpsAccuracyMeters,
            hasRealGpsFix = hasRealGpsFix,
            rssiDbm = selectedNodeObj?.telemetry?.rssiDbm,
            rssiDistanceMeters = if (selectedNodeObj != null) rssiDistanceMeters else null,
            allRealNodesOffline = allRealNodesOffline
        )
    }
    val isUserOutsideHouse = presenceState.zone == PresenceZone.OUTSIDE_HOUSE
    var localMutedForNav by remember { mutableStateOf(false) }

    var activeRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var emergencyPhase by remember { mutableStateOf(EmergencyAutoPhase.IDLE) }
    var selectedMapElement by remember { mutableStateOf<SelectedMapElement?>(null) }

    // Helper to check distance from a point to a line segment in meters
    fun pointToSegmentDistanceMeters(pt: Point2D, a: Point2D, b: Point2D): Float {
        val px = pt.x * houseWidthMeters
        val py = pt.y * houseLengthMeters
        val ax = a.x * houseWidthMeters
        val ay = a.y * houseLengthMeters
        val bx = b.x * houseWidthMeters
        val by = b.y * houseLengthMeters
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }

    fun buildDoorAwareRoute(
        start: Point2D,
        target: Point2D,
        targetLabel: String,
        targetType: String,
        notes: String
    ): NavigationRoute {
        val waypoints = mutableListOf<Point2D>()
        waypoints.add(start)

        val alarmingPins = placedSensorPins.filter { it.isAlarm }
        val activeHazardRadiusM = if (isFireActive) {
            (1.2f + ((currentClockMs - (alarmingPins.minOfOrNull { it.alarmStartedAtMs.takeIf { t -> t > 0L } ?: currentClockMs } ?: currentClockMs)) / 1000L).coerceAtLeast(1L) * 0.18f)
                .coerceAtMost(max(houseWidthMeters, houseLengthMeters) * 0.45f)
        } else 0f

        var avoidedHazard = false
        val allPassableDoors = tempDoors + tempMainDoors

        // Check if direct line cuts through an active fire spread zone (when not intentionally attacking that exact fire point)
        val blockingFirePin = if (targetType != "FIRE_SENSOR" && alarmingPins.isNotEmpty()) {
            alarmingPins.firstOrNull { pin ->
                val distToTarget = hypot(((pin.position.x - target.x) * houseWidthMeters).toDouble(), ((pin.position.y - target.y) * houseLengthMeters).toDouble()).toFloat()
                distToTarget > 1.0f && pointToSegmentDistanceMeters(pin.position, start, target) < (activeHazardRadiusM + 0.6f)
            }
        } else null

        if (allPassableDoors.isNotEmpty()) {
            // Prefer doors that stay clear of active fire hazard zones when routing to Extinguisher or Exit
            val safeDoors = if (blockingFirePin != null) {
                allPassableDoors.filter { door ->
                    val dM = hypot(((door.x - blockingFirePin.position.x) * houseWidthMeters).toDouble(), ((door.y - blockingFirePin.position.y) * houseLengthMeters).toDouble()).toFloat()
                    dM >= activeHazardRadiusM
                }.ifEmpty { allPassableDoors }
            } else {
                allPassableDoors
            }

            val intermediateDoor = safeDoors.minByOrNull { door ->
                hypot((door.x - start.x).toDouble(), (door.y - start.y).toDouble()) +
                    hypot((target.x - door.x).toDouble(), (target.y - door.y).toDouble())
            }
            if (intermediateDoor != null) {
                val directDist = hypot((target.x - start.x).toDouble(), (target.y - start.y).toDouble())
                val doorDist = hypot((intermediateDoor.x - start.x).toDouble(), (intermediateDoor.y - start.y).toDouble())
                if (doorDist > 0.04 && directDist > 0.12) {
                    waypoints.add(intermediateDoor)
                    if (blockingFirePin != null) avoidedHazard = true
                }
            }
        } else if (blockingFirePin != null) {
            // Compute a safe perimeter detour waypoint around the circular fire spread zone
            val midX = (start.x + target.x) / 2f
            val midY = (start.y + target.y) / 2f
            val vx = midX - blockingFirePin.position.x
            val vy = midY - blockingFirePin.position.y
            val norm = hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(0.01f)
            val safeRadiusNorm = ((activeHazardRadiusM + 1.2f) / min(houseWidthMeters, houseLengthMeters).coerceAtLeast(6f)).coerceIn(0.14f, 0.35f)
            val detourCandidate = clampPointIntoPolygonBounds(
                Point2D(
                    x = blockingFirePin.position.x + (vx / norm) * safeRadiusNorm,
                    y = blockingFirePin.position.y + (vy / norm) * safeRadiusNorm
                ),
                activeFloorplanPolygon
            )
            waypoints.add(detourCandidate)
            avoidedHazard = true
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
            crossesHazardRoom = isFireActive && !avoidedHazard,
            isEuclideanOnly = allPassableDoors.isEmpty(),
            notes = if (avoidedHazard) "$notes · Đã né vùng lửa lan" else notes,
            avoidsFireZone = avoidedHazard
        )
    }

    fun getCandidateSensorPins(): List<PlacedSensorPin> {
        return if (placedSensorPins.isNotEmpty()) {
            placedSensorPins.toList()
        } else {
            nodes.mapIndexed { idx, n ->
                val prob = n.telemetry.calculateFireProbability(gasThresholdAdc, n.effectiveConfig()).totalProbabilityPercent
                val isAlm = n.isEffectivelyAlarming() || n.suspectedBurnedOffline
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
                    isReferenceAnchor = (idx == 0),
                    suspectedBurnedOffline = n.suspectedBurnedOffline
                )
            }
        }
    }

    fun routeToFireSensor(specificDeviceId: String? = null) {
        val allCandidatePins = getCandidateSensorPins()
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
        val firePos = chosenPin?.position ?: Point2D(0.45f, 0.35f)
        val fireName = chosenPin?.displayName ?: "Vị trí Báo Cháy"
        val tempStr = chosenPin?.let { pin ->
            pin.temperatureCelsius?.let { String.format(Locale.US, "%.1f°C · P=%.0f%%", it, pin.fireProbabilityPercent) }
                ?: String.format(Locale.US, "P=%.0f%%", pin.fireProbabilityPercent)
        } ?: ""

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
        } ?: Point2D(0.35f, 0.65f)

        emergencyPhase = EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST
        activeRoute = buildDoorAwareRoute(
            start = currentUserPos,
            target = targetExt,
            targetLabel = "Bình Chữa Cháy Gần Nhất",
            targetType = "EXTINGUISHER",
            notes = "Đi qua các cửa đánh số tới vị trí Bình Chữa Cháy gần nhất"
        )
    }

    fun routeTwoStageExtinguisherToFire(specificDeviceId: String? = null) {
        val targetExt = tempExtinguishers.minByOrNull { ext ->
            hypot((ext.x - currentUserPos.x).toDouble(), (ext.y - currentUserPos.y).toDouble())
        } ?: Point2D(0.35f, 0.65f)

        val allCandidatePins = getCandidateSensorPins()
        val specificPin = specificDeviceId?.let { id -> allCandidatePins.find { it.deviceId == id } }
        val nearestAlarmingPin = allCandidatePins.filter { it.isAlarm }.minByOrNull { pin ->
            hypot((pin.position.x - targetExt.x).toDouble(), (pin.position.y - targetExt.y).toDouble())
        }
        val chosenPin = specificPin ?: nearestAlarmingPin ?: allCandidatePins.firstOrNull()
        val firePos = chosenPin?.position ?: Point2D(0.45f, 0.35f)
        val fireName = chosenPin?.displayName ?: "Đám Cháy"

        val stage1 = buildDoorAwareRoute(
            start = currentUserPos,
            target = targetExt,
            targetLabel = "Chặng 1: Bình CC",
            targetType = "EXTINGUISHER",
            notes = "Lấy Bình CC"
        )
        val stage2 = buildDoorAwareRoute(
            start = targetExt,
            target = firePos,
            targetLabel = "Chặng 2: $fireName",
            targetType = "FIRE_SENSOR",
            notes = "Tiến tới đám cháy"
        )

        val combinedPoints = mutableListOf<Point2D>()
        combinedPoints.addAll(stage1.pathPoints)
        if (stage2.pathPoints.size > 1) {
            combinedPoints.addAll(stage2.pathPoints.drop(1))
        }

        emergencyPhase = EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST
        activeRoute = NavigationRoute(
            pathPoints = combinedPoints,
            totalDistanceMeters = (stage1.totalDistanceMeters + stage2.totalDistanceMeters).coerceAtLeast(0.8f),
            targetName = "2 Chặng: Bình CC ➔ $fireName",
            targetType = "TWO_STAGE",
            crossesHazardRoom = stage1.crossesHazardRoom,
            isEuclideanOnly = stage1.isEuclideanOnly && stage2.isEuclideanOnly,
            notes = "Chặng 1 (${String.format(Locale.US, "%.1fm", stage1.totalDistanceMeters)}): Lấy Bình CC · Chặng 2 (${String.format(Locale.US, "%.1fm", stage2.totalDistanceMeters)}): Dập lửa tại $fireName",
            stage1Points = stage1.pathPoints,
            stage2Points = stage2.pathPoints,
            avoidsFireZone = stage1.avoidsFireZone
        )
    }

    fun routeToExitDoor() {
        val candidates = tempExits + tempMainDoors
        val targetExit = candidates.minByOrNull { ex ->
            hypot((ex.x - currentUserPos.x).toDouble(), (ex.y - currentUserPos.y).toDouble())
        } ?: Point2D(0.50f, 0.85f)

        emergencyPhase = EmergencyAutoPhase.IDLE
        activeRoute = buildDoorAwareRoute(
            start = currentUserPos,
            target = targetExit,
            targetLabel = "Cửa Exit / Cửa Chính Thoát Hiểm",
            targetType = "EXIT",
            notes = "Đi theo đường xanh lá qua cửa tới Cửa Exit / Cửa Chính gần nhất"
        )
    }

    fun advanceUserStep(steps: Int = 1) {
        val currentRoute = activeRoute
        val strideM = stepStrideMeters.coerceIn(0.45f, 0.95f)
        val activeSubPath = if (currentRoute != null && currentRoute.pathPoints.size >= 2) {
            if (currentRoute.targetType == "TWO_STAGE" && currentRoute.stage1Points.size >= 2) {
                currentRoute.stage1Points
            } else {
                currentRoute.pathPoints
            }
        } else null

        val fusionStepResult = DeviceMovementTracker.fuseIndoorPosition(
            currentPos = currentUserPos,
            stepsDelta = steps.coerceAtLeast(1),
            stepStrideMeters = strideM,
            compassHeadingDeg = compassHeading,
            houseWidthMeters = houseWidthMeters,
            houseLengthMeters = houseLengthMeters,
            anchorPos = anchorSensorPin?.position,
            rssiDistanceMeters = rssiDistanceMeters,
            rssiDbm = liveRssiDbm,
            activePath = activeSubPath,
            allowOutsideBounds = true
        )

        // Do NOT force-clamp into house polygon so walking outside actually moves the user icon outside the house!
        currentUserPos = Point2D(
            x = fusionStepResult.fusedPosition.x.coerceIn(-0.22f, 1.22f),
            y = fusionStepResult.fusedPosition.y.coerceIn(-0.22f, 1.22f)
        )
        onSetUserPosition(floorId, currentUserPos.x, currentUserPos.y)

        if (currentRoute != null && currentRoute.pathPoints.isNotEmpty()) {
            // Check if in Stage 1 of 2-stage routing and user reached the extinguisher (within 1.2m)
            if (currentRoute.targetType == "TWO_STAGE" && currentRoute.stage1Points.isNotEmpty()) {
                val extTarget = currentRoute.stage1Points.last()
                val distToExtMeters = hypot(
                    ((currentUserPos.x - extTarget.x) * houseWidthMeters).toDouble(),
                    ((currentUserPos.y - extTarget.y) * houseLengthMeters).toDouble()
                ).toFloat()
                if (distToExtMeters <= 1.2f) {
                    // Automatically transition from Stage 1 (Extinguisher) to Stage 2 (Attack Fire Source)!
                    routeToFireSensor(null)
                } else {
                    routeTwoStageExtinguisherToFire(null)
                }
            } else {
                val finalTarget = currentRoute.pathPoints.last()
                activeRoute = buildDoorAwareRoute(
                    start = currentUserPos,
                    target = finalTarget,
                    targetLabel = currentRoute.targetName,
                    targetType = currentRoute.targetType,
                    notes = currentRoute.notes
                )
            }
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

    // Hybrid Indoor/Outdoor GPS Fusion:
    // - Indoors: GPS carries 0% weight so indoor jitter never corrupts IMU + Compass + ESP32 RSSI.
    // - Outdoors: When real GPS confirms user moved OUTSIDE the house geo-fence, project their position
    //   outside the house boundary on the 2D canvas so the icon never stays stuck indoors!
    LaunchedEffect(gpsLocation, gpsDeltaMeters, gpsAccuracyMeters, hasRealGpsFix, houseGeoCenter) {
        if (hasRealGpsFix && gpsLocation != null && gpsAccuracyMeters <= 25f) {
            val dLatM = (gpsLocation!!.latitude - houseGeoCenter.lat) * 111_320.0
            val dLngM = (gpsLocation!!.longitude - houseGeoCenter.lng) * 111_320.0 * cos(Math.toRadians(houseGeoCenter.lat))
            val distFromCenterM = hypot(dLngM, dLatM).toFloat()
            val houseRadiusM = hypot((houseWidthMeters / 2f).toDouble(), (houseLengthMeters / 2f).toDouble()).toFloat()
            val gpsOutsideMargin = (gpsAccuracyMeters * 0.45f).coerceAtLeast(2.5f)

            if (distFromCenterM > houseRadiusM + gpsOutsideMargin) {
                // User is clearly OUTSIDE the house -> place icon outside the house polygon along true GPS vector
                val normX = (0.50f + (dLngM.toFloat() / houseWidthMeters.coerceAtLeast(6f))).coerceIn(-0.18f, 1.18f)
                val normY = (0.50f - (dLatM.toFloat() / houseLengthMeters.coerceAtLeast(6f))).coerceIn(-0.18f, 1.18f)
                currentUserPos = Point2D(normX, normY)
                onSetUserPosition(floorId, currentUserPos.x, currentUserPos.y)
            } else {
                val isIndoorFusionActive = anchorSensorPin != null || stepCount > 0 || gpsAccuracyMeters > 4.5f
                if (!isIndoorFusionActive && hypot(gpsDeltaMeters.x.toDouble(), gpsDeltaMeters.y.toDouble()) > 0.65) {
                    val normX = (0.50f + (gpsDeltaMeters.x / houseWidthMeters.coerceAtLeast(8f))).coerceIn(0.08f, 0.92f)
                    val normY = (0.72f - (gpsDeltaMeters.y / houseLengthMeters.coerceAtLeast(8f))).coerceIn(0.08f, 0.92f)
                    currentUserPos = Point2D(normX, normY)
                    onSetUserPosition(floorId, currentUserPos.x, currentUserPos.y)
                }
            }
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

    LaunchedEffect(navigationRequest) {
        if (navigationRequest != null) {
            userManuallyClearedToBlindRoom = false
            mapStage = MapStage.READY_MAP
            when (navigationRequest.targetType) {
                MapNavigationTargetType.NEAREST_FIRE_SENSOR -> routeToFireSensor(null)
                MapNavigationTargetType.SPECIFIC_FIRE_SENSOR -> routeToFireSensor(navigationRequest.targetDeviceId)
                MapNavigationTargetType.NEAREST_EXTINGUISHER -> routeToExtinguisher()
                MapNavigationTargetType.NEAREST_EXIT -> routeToExitDoor()
                MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE -> routeTwoStageExtinguisherToFire(navigationRequest.targetDeviceId)
            }
            onConsumeNavigationRequest()
        }
    }

    LaunchedEffect(isFireActive, hazardNodes.size, autoRouteOnAlarm) {
        if (isFireActive && autoRouteOnAlarm) {
            if (mapStage != MapStage.SETUP_PLACEMENT && mapStage != MapStage.BLIND_ROOM_SIZING) {
                mapStage = MapStage.READY_MAP
            }
            if (activeRoute == null) {
                if (tempExtinguishers.isNotEmpty()) {
                    routeTwoStageExtinguisherToFire(null)
                } else {
                    routeToFireSensor(null)
                }
            }
        }
    }

    fun clearAllConfigurationToBlindRoom() {
        userManuallyClearedToBlindRoom = true
        geoPolygonPoints.clear()
        regionPolygonPoints.clear()
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

    val maxFireSpreadSeconds = if (isFireActive) {
        val earliestStart = placedSensorPins.filter { it.isAlarm && it.alarmStartedAtMs > 0L }
            .minOfOrNull { it.alarmStartedAtMs }
            ?: hazardNodes.minOfOrNull { it.telemetry.alarmStartedAtMs.takeIf { t -> t > 0L } ?: (currentClockMs - 3000L) }
            ?: (currentClockMs - 3000L)
        ((currentClockMs - earliestStart) / 1000L).coerceAtLeast(1L)
    } else 0L

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
                                    mapStage == MapStage.BLIND_ROOM_SIZING -> "BƯỚC 1: CĂN GOOGLE MAPS TỪ TRÊN CAO & GHIM GÓC NHÀ"
                                    mapStage == MapStage.SETUP_PLACEMENT -> {
                                        if (placedSensorPins.isEmpty()) {
                                            "BƯỚC 2A: GHIM MỐC NHẬN DIỆN LỬA (#1) TRƯỚC"
                                        } else {
                                            "BƯỚC 2B: GHIM CỬA CHÍNH, CỬA ĐÁNH SỐ & BÌNH CC"
                                        }
                                    }
                                    activeRoute != null -> "ĐANG DẪN TỚI: ${activeRoute!!.targetName.uppercase()}"
                                    isFireActive -> "CẢNH BÁO LỬA ĐANG LAN RỘNG THEO THỜI GIAN!"
                                    else -> "BẢN ĐỒ 2D (${activeFloorplanPolygon.size} GÓC CHUẨN BẮC-NAM)"
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
                            val stepsRemaining = ((activeRoute?.totalDistanceMeters ?: 0f) / stepStrideMeters.coerceAtLeast(0.45f)).roundToInt()
                            Text(
                                text = when {
                                    isFireActive -> String.format(
                                        Locale.US,
                                        "🔥 Bán kính lửa lan: R=%.2fm (S=%.1fm²) · %ds · Acc %.1f%%",
                                        currentFireSpreadRadiusMeters,
                                        currentFireSpreadAreaM2,
                                        maxFireSpreadSeconds,
                                        fusedAccuracyPercent
                                    )
                                    activeRoute != null -> String.format(
                                        Locale.US,
                                        "K/c: %.1fm (~%d bước) · Acc %.1f%% (±%.2fm · GPS 0%%) · %d°",
                                        activeRoute!!.totalDistanceMeters,
                                        stepsRemaining,
                                        fusedAccuracyPercent,
                                        fusedErrorMeters,
                                        compassHeading.roundToInt()
                                    )
                                    mapStage == MapStage.BLIND_ROOM_SIZING -> {
                                        if (geoPolygonPoints.size < 3) {
                                            "Đã ghim ${geoPolygonPoints.size} góc · Kéo bản đồ vệ tinh tới nóc nhà & ghim ít nhất 3 góc!"
                                        } else {
                                            String.format(
                                                Locale.US,
                                                "Đã neo %d góc thật · Rộng %.1fm × Dài %.1fm · Diện tích: %.1f m²",
                                                geoPolygonPoints.size,
                                                calcWidthM,
                                                calcLengthM,
                                                calcPolygonAreaM2
                                            )
                                        }
                                    }
                                    else -> String.format(
                                        Locale.US,
                                        "IMU+La bàn+RSSI: %.2fm (Acc %.1f%% · ±%.2fm) · GPS: 0%% nhiễu",
                                        fusedDistanceMeters,
                                        fusedAccuracyPercent,
                                        fusedErrorMeters
                                    )
                                },
                                color = if (isFireActive) CriticalAlarmRed else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isFireActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (mapStage != MapStage.BLIND_ROOM_SIZING) {
                            // Button to re-edit Khoanh Vùng Polygon directly
                            IconButton(
                                onClick = { mapStage = MapStage.BLIND_ROOM_SIZING },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryBlueLight)
                                    .testTag("btn_re_khoanh_vung")
                            ) {
                                Icon(
                                    Icons.Default.CropFree,
                                    contentDescription = "Khoanh vùng lại hình dạng nhà",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

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
                                    contentDescription = "Chỉnh sửa vật dụng bản đồ",
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
        // STAGE 1: GOOGLE MAPS TOP-DOWN ALIGNMENT + GRAB-STYLE CROSSHAIR CORNER PINNING
        // Căn chỉnh trực tiếp trên Google Maps Vệ tinh từ trên cao xuống, khóa tọa độ GPS từng góc mái nhà!
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
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Address / Coordinates Search Bar + Fly-to-GPS Button (Grab / Maps style)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchAddressQuery,
                            onValueChange = {
                                searchAddressQuery = it
                                addressSearchNotice = null
                            },
                            placeholder = {
                                Text(
                                    "Tìm địa chỉ, tên đường hoặc lat, lng...",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("input_map_address_search"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = LightBorder,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = LightSurfaceVariant.copy(alpha = 0.45f)
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val q = searchAddressQuery.trim()
                                        if (q.isNotEmpty() && !isSearchingAddress) {
                                            isSearchingAddress = true
                                            addressSearchNotice = null
                                            scope.launch {
                                                val parsedCoord = GeoMapMath.parseCoordinatesOrNull(q)
                                                val foundGeo = parsedCoord ?: withContext(Dispatchers.IO) {
                                                    try {
                                                        @Suppress("DEPRECATION")
                                                        val list = Geocoder(context, Locale("vi", "VN")).getFromLocationName(q, 1)
                                                        list?.firstOrNull()?.let { addr ->
                                                            GeoLatLng(addr.latitude, addr.longitude)
                                                        }
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                }
                                                isSearchingAddress = false
                                                if (foundGeo != null) {
                                                    mapCenterLatLng = foundGeo
                                                    flyToTarget = foundGeo
                                                    flyToZoom = 20f
                                                    flyToTrigger += 1
                                                    addressSearchNotice = String.format(
                                                        Locale.US,
                                                        "Đã tới: %.5f, %.5f",
                                                        foundGeo.lat,
                                                        foundGeo.lng
                                                    )
                                                } else {
                                                    addressSearchNotice = "Không tìm thấy địa chỉ. Thử nhập rõ hơn hoặc tọa độ lat, lng."
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_search_map_address")
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Tìm địa điểm trên Google Maps",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                movementTracker.refreshGpsLocationNow()
                                val gpsLatLng = GeoLatLng(
                                    gpsLocation?.latitude ?: 10.776889,
                                    gpsLocation?.longitude ?: 106.700806
                                )
                                mapCenterLatLng = gpsLatLng
                                flyToTarget = gpsLatLng
                                flyToZoom = 20f
                                flyToTrigger += 1
                                addressSearchNotice = "Đã đưa tâm bản đồ về GPS hiện tại"
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryBlueLight)
                                .border(1.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .testTag("btn_recenter_gps")
                        ) {
                            Icon(
                                Icons.Default.GpsFixed,
                                contentDescription = "Về vị trí GPS hiện tại",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (addressSearchNotice != null) {
                        Text(
                            text = addressSearchNotice!!,
                            color = PrimaryBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // 2. Top-Down Google Map Layer Switcher + Tool Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoogleMapLayerType.entries.forEach { layer ->
                            val isSelected = googleMapLayer == layer
                            FilterChip(
                                selected = isSelected,
                                onClick = { googleMapLayer = layer },
                                label = {
                                    Text(
                                        text = layer.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0F172A),
                                    selectedLabelColor = Color.White,
                                    containerColor = LightSurfaceVariant,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("chip_map_layer_${layer.lyrsCode}")
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Tap-map mode vs Pure Pan mode (crosshair still works in both!)
                        FilterChip(
                            selected = regionDrawToolMode == RegionDrawToolMode.DRAW_POLYGON_POINTS,
                            onClick = {
                                regionDrawToolMode = if (regionDrawToolMode == RegionDrawToolMode.DRAW_POLYGON_POINTS) {
                                    RegionDrawToolMode.PAN_ZOOM_MAP
                                } else {
                                    RegionDrawToolMode.DRAW_POLYGON_POINTS
                                }
                            },
                            label = {
                                Text(
                                    text = if (regionDrawToolMode == RegionDrawToolMode.DRAW_POLYGON_POINTS) "Chạm Map: Bật" else "Chạm Map: Khóa",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = LightSurfaceVariant,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Real Top-Down Google Maps View with Geo-Locked Polygon Vertices & Grab-Style Center Crosshair
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFCBD5E1))
                            .border(
                                2.dp,
                                if (geoPolygonPoints.size >= 3) NormalGreen else PrimaryBlue,
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        val gpsLat = gpsLocation?.latitude ?: 10.776889
                        val gpsLng = gpsLocation?.longitude ?: 106.700806

                        GoogleMapsAlignWebView(
                            initialLat = flyToTarget.lat,
                            initialLng = flyToTarget.lng,
                            flyToTrigger = flyToTrigger,
                            flyToLat = flyToTarget.lat,
                            flyToLng = flyToTarget.lng,
                            flyToZoom = flyToZoom,
                            gpsLat = gpsLat,
                            gpsLng = gpsLng,
                            layerType = googleMapLayer,
                            tapToPinEnabled = (regionDrawToolMode == RegionDrawToolMode.DRAW_POLYGON_POINTS),
                            geoPoints = geoPolygonPoints.toList(),
                            onCenterMoved = { cLat, cLng ->
                                mapCenterLatLng = GeoLatLng(cLat, cLng)
                            },
                            onMapTappedAddPoint = { pLat, pLng ->
                                geoPolygonPoints.add(GeoLatLng(pLat, pLng))
                                syncNormalizedFromGeoPolygon()
                            },
                            onPointDragged = { idx, nLat, nLng ->
                                if (idx in geoPolygonPoints.indices) {
                                    geoPolygonPoints[idx] = GeoLatLng(nLat, nLng)
                                    syncNormalizedFromGeoPolygon()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Grab / Ride-hailing Fixed Center Crosshair Target (Non-blocking so user smoothly pans map beneath!)
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(54.dp)) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    radius = 18f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 4.5f)
                                )
                                drawCircle(
                                    color = Color(0xFFDC2626),
                                    radius = 18f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.4f)
                                )
                                drawLine(
                                    color = Color(0xFFDC2626),
                                    start = Offset(cx - 25f, cy),
                                    end = Offset(cx - 7f, cy),
                                    strokeWidth = 2.5f
                                )
                                drawLine(
                                    color = Color(0xFFDC2626),
                                    start = Offset(cx + 7f, cy),
                                    end = Offset(cx + 25f, cy),
                                    strokeWidth = 2.5f
                                )
                                drawLine(
                                    color = Color(0xFFDC2626),
                                    start = Offset(cx, cy - 25f),
                                    end = Offset(cx, cy - 7f),
                                    strokeWidth = 2.5f
                                )
                                drawLine(
                                    color = Color(0xFFDC2626),
                                    start = Offset(cx, cy + 7f),
                                    end = Offset(cx, cy + 25f),
                                    strokeWidth = 2.5f
                                )
                                drawCircle(color = Color(0xFFDC2626), radius = 4f, center = Offset(cx, cy))
                            }
                        }

                        // Live Center Coordinates + True North Pill at Top-Left
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.84f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "↑ Bắc · Tâm: %.5f, %.5f",
                                    mapCenterLatLng.lat,
                                    mapCenterLatLng.lng
                                ),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Real-time Geo-Area Summary Pill at Bottom-Start
                        if (geoPolygonPoints.size >= 3) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF059669).copy(alpha = 0.92f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%d góc · %.1fm × %.1fm (%.1f m²)",
                                        geoPolygonPoints.size,
                                        calcWidthM,
                                        calcLengthM,
                                        calcPolygonAreaM2
                                    ),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Kéo bản đồ đặt dấu chữ thập (+) lên từng góc mái nhà rồi bấm Ghim Góc!",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // 4. Grab-Style Precision Pin Button + Undo + Clear Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                geoPolygonPoints.add(mapCenterLatLng)
                                syncNormalizedFromGeoPolygon()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("btn_pin_center_crosshair"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ghim Góc Tại Tâm (+) [Đ${geoPolygonPoints.size + 1}]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                if (geoPolygonPoints.isNotEmpty()) {
                                    geoPolygonPoints.removeAt(geoPolygonPoints.lastIndex)
                                    syncNormalizedFromGeoPolygon()
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LightSurfaceVariant)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Hoàn tác 1 góc", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = {
                                geoPolygonPoints.clear()
                                regionPolygonPoints.clear()
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFEE2E2))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa làm lại", tint = CriticalAlarmRed, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. Quick Geo-Templates Centered at Map Crosshair
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                userManuallyClearedToBlindRoom = false
                                movementTracker.forceRecognizeMapLocation()
                                geoPolygonPoints.clear()
                                geoPolygonPoints.addAll(GeoMapMath.createGeoTemplateRect(mapCenterLatLng, 10.0, 16.0))
                                syncNormalizedFromGeoPolygon()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_gps_quick_size"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Khung 10×16m Tại Tâm", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }

                        OutlinedButton(
                            onClick = {
                                userManuallyClearedToBlindRoom = false
                                geoPolygonPoints.clear()
                                geoPolygonPoints.addAll(GeoMapMath.createGeoTemplateLShape(mapCenterLatLng, 14.0, 16.0))
                                syncNormalizedFromGeoPolygon()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Mẫu Chữ L Tại Tâm (6 Đỉnh)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 6. Confirm Geo-Anchored Polygon -> Cut North-Up Floorplan for Stage 2 & 3!
                    Button(
                        onClick = {
                            userManuallyClearedToBlindRoom = false
                            if (geoPolygonPoints.size < 3) {
                                geoPolygonPoints.clear()
                                geoPolygonPoints.addAll(GeoMapMath.createGeoTemplateRect(mapCenterLatLng, 10.0, 16.0))
                            }
                            syncNormalizedFromGeoPolygon()
                            onSaveBoundaryPolygon(floorId, regionPolygonPoints.toList(), houseWidthMeters, houseLengthMeters)
                            currentUserPos = clampPointIntoPolygonBounds(currentUserPos, activeFloorplanPolygon)
                            mapStage = MapStage.SETUP_PLACEMENT
                            setupDrawer = SetupDrawerMode.ADD_FIRE_SENSOR
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_enter_mode_2_setup"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (geoPolygonPoints.size >= 3) NormalGreen else PrimaryBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (geoPolygonPoints.size >= 3) {
                                String.format(
                                    Locale.US,
                                    "KHÓA %d GÓC GOOGLE MAPS (%.0fm²) ➔ BƯỚC 2",
                                    geoPolygonPoints.size,
                                    calcPolygonAreaM2
                                )
                            } else {
                                "GHIM ÍT NHẤT 3 GÓC MÁI NHÀ (HOẶC LẤY KHUNG MẪU)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            return@Column
        }

        // =========================================================================
        // STAGE 2 & STAGE 3: INTERACTIVE 2D MAP CANVAS SHAPED BY THE EXACT KHOANH VÙNG POLYGON!
        // =========================================================================
        if (mapStage == MapStage.READY_MAP) {
            // Indoor / Outdoor Live Presence Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (presenceState.zone) {
                            PresenceZone.OUTSIDE_HOUSE -> Color(0xFFFFF7ED)
                            PresenceZone.NEAR_BOUNDARY -> Color(0xFFFEFCE8)
                            PresenceZone.INSIDE_HOUSE -> Color(0xFFECFDF5)
                        }
                    )
                    .border(
                        1.dp,
                        when (presenceState.zone) {
                            PresenceZone.OUTSIDE_HOUSE -> Color(0xFFEA580C)
                            PresenceZone.NEAR_BOUNDARY -> WarningAmber
                            PresenceZone.INSIDE_HOUSE -> NormalGreen
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (presenceState.zone) {
                        PresenceZone.OUTSIDE_HOUSE -> String.format(Locale.US, "📍 NGOÀI NHÀ (~%.1fm) · %s", presenceState.distanceOutsideMeters, presenceState.reason)
                        PresenceZone.NEAR_BOUNDARY -> "🚪 SÁT RÌA NHÀ · ${presenceState.reason}"
                        PresenceZone.INSIDE_HOUSE -> "🏠 TRONG NHÀ · RSSI ${liveRssiDbm} dBm (~${String.format(Locale.US, "%.1fm", rssiDistanceMeters)})"
                    },
                    color = when (presenceState.zone) {
                        PresenceZone.OUTSIDE_HOUSE -> Color(0xFFC2410C)
                        PresenceZone.NEAR_BOUNDARY -> Color(0xFFA16207)
                        PresenceZone.INSIDE_HOUSE -> Color(0xFF047857)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Google Maps-Style Turn-by-Turn Guidance Sign when navigating on 2D Map
            activeRoute?.let { route ->
                val guidance = TurnByTurnNavigator.buildGuidance(
                    route = route,
                    userHeadingDeg = compassHeading,
                    houseWidthMeters = houseWidthMeters,
                    houseLengthMeters = houseLengthMeters,
                    isOutsideHouse = isUserOutsideHouse,
                    outdoorOverrideDistanceMeters = if (isUserOutsideHouse) presenceState.distanceToHouseCenterMeters else null
                )
                TurnByTurnNavigationBanner(
                    guidance = guidance,
                    isMapCurrentlyVisible = true,
                    isLocalAlarmMuted = isLocalAlarmMuted || localMutedForNav,
                    onToggleMapView = { onBackToDashboardWithNav() },
                    onStopNavigation = {
                        activeRoute = null
                        onStopGlobalNavigation()
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

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
                    Text("1. Mờ Thấy Bản Đồ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("2. Trắng Rõ Vùng Khoanh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2D Interactive Canvas shaped by activeFloorplanPolygon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFCBD5E1))
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
                .pointerInput(mapStage, setupDrawer, activeFloorplanPolygon) {
                    detectTapGestures { tapOffset ->
                        val normX = ((tapOffset.x - panOffset.x) / (size.width * scale)).coerceIn(0.05f, 0.95f)
                        val normY = ((tapOffset.y - panOffset.y) / (size.height * scale)).coerceIn(0.05f, 0.95f)
                        val tapPt = Point2D(normX, normY)

                        // Check if user tapped on an existing Map Object or Sensor Pin (hit radius ~0.065f)
                        val hitRadius = 0.065f
                        var hitItem: SelectedMapElement? = null

                        // 1. Check Sensors
                        placedSensorPins.forEachIndexed { idx, pin ->
                            if (hypot((pin.position.x - tapPt.x).toDouble(), (pin.position.y - tapPt.y).toDouble()) < hitRadius) {
                                hitItem = SelectedMapElement(
                                    kind = SelectedMapElementKind.SENSOR,
                                    index = idx,
                                    label = pin.displayName,
                                    position = pin.position,
                                    deviceIdOrDbId = pin.deviceId
                                )
                            }
                        }
                        // 2. Check Extinguishers
                        if (hitItem == null) {
                            tempExtinguishers.forEachIndexed { idx, pt ->
                                if (hypot((pt.x - tapPt.x).toDouble(), (pt.y - tapPt.y).toDouble()) < hitRadius) {
                                    val dbId = extinguishers.getOrNull(idx)?.id
                                    hitItem = SelectedMapElement(
                                        kind = SelectedMapElementKind.EXTINGUISHER,
                                        index = idx,
                                        label = "Bình CC #${idx + 1}",
                                        position = pt,
                                        deviceIdOrDbId = dbId
                                    )
                                }
                            }
                        }
                        // 3. Check Numbered Doors
                        if (hitItem == null) {
                            val doorObjs = mapObjects.filter { it.type == MapObjectType.DOOR }
                            tempDoors.forEachIndexed { idx, pt ->
                                if (hypot((pt.x - tapPt.x).toDouble(), (pt.y - tapPt.y).toDouble()) < hitRadius) {
                                    hitItem = SelectedMapElement(
                                        kind = SelectedMapElementKind.NUMBERED_DOOR,
                                        index = idx,
                                        label = "Cửa Số #${idx + 1}",
                                        position = pt,
                                        deviceIdOrDbId = doorObjs.getOrNull(idx)?.id
                                    )
                                }
                            }
                        }
                        // 4. Check Main Doors
                        if (hitItem == null) {
                            val mainDoorObjs = mapObjects.filter { it.type == MapObjectType.MAIN_DOOR }
                            tempMainDoors.forEachIndexed { idx, pt ->
                                if (hypot((pt.x - tapPt.x).toDouble(), (pt.y - tapPt.y).toDouble()) < hitRadius) {
                                    hitItem = SelectedMapElement(
                                        kind = SelectedMapElementKind.MAIN_DOOR,
                                        index = idx,
                                        label = "Cửa Chính #${idx + 1}",
                                        position = pt,
                                        deviceIdOrDbId = mainDoorObjs.getOrNull(idx)?.id
                                    )
                                }
                            }
                        }
                        // 5. Check Exits
                        if (hitItem == null) {
                            val exitObjs = mapObjects.filter { it.type == MapObjectType.EXIT }
                            tempExits.forEachIndexed { idx, pt ->
                                if (hypot((pt.x - tapPt.x).toDouble(), (pt.y - tapPt.y).toDouble()) < hitRadius) {
                                    hitItem = SelectedMapElement(
                                        kind = SelectedMapElementKind.EXIT,
                                        index = idx,
                                        label = "Cửa Exit #${idx + 1}",
                                        position = pt,
                                        deviceIdOrDbId = exitObjs.getOrNull(idx)?.id
                                    )
                                }
                            }
                        }
                        // 6. Check Stairs
                        if (hitItem == null) {
                            val stairObjs = mapObjects.filter { it.type == MapObjectType.STAIRS }
                            tempStairs.forEachIndexed { idx, pt ->
                                if (hypot((pt.x - tapPt.x).toDouble(), (pt.y - tapPt.y).toDouble()) < hitRadius) {
                                    hitItem = SelectedMapElement(
                                        kind = SelectedMapElementKind.STAIRS,
                                        index = idx,
                                        label = "Cầu Thang #${idx + 1}",
                                        position = pt,
                                        deviceIdOrDbId = stairObjs.getOrNull(idx)?.id
                                    )
                                }
                            }
                        }

                        if (hitItem != null) {
                            selectedMapElement = hitItem
                        } else {
                            selectedMapElement = null
                            // Allow positioning both inside AND outside the house polygon
                            val freePt = Point2D(tapPt.x.coerceIn(0.01f, 0.99f), tapPt.y.coerceIn(0.01f, 0.99f))
                            currentUserPos = freePt
                            onSetUserPosition(floorId, freePt.x, freePt.y)
                            activeRoute?.let { curRoute ->
                                if (curRoute.targetType == "TWO_STAGE") {
                                    routeTwoStageExtinguisherToFire(null)
                                } else if (curRoute.pathPoints.isNotEmpty()) {
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

                // 1. Build the exact Path of the User's Khoanh Vùng Polygon!
                val housePolygonPath = Path()
                if (activeFloorplanPolygon.isNotEmpty()) {
                    val firstScreen = toScreen(activeFloorplanPolygon.first())
                    housePolygonPath.moveTo(firstScreen.x, firstScreen.y)
                    for (i in 1 until activeFloorplanPolygon.size) {
                        val ptScreen = toScreen(activeFloorplanPolygon[i])
                        housePolygonPath.lineTo(ptScreen.x, ptScreen.y)
                    }
                    housePolygonPath.close()
                }

                // 2. Outside Background Layer
                if (mapStage == MapStage.SETUP_PLACEMENT || visualStyle == MapVisualStyle.TRANSLUCENT_GOOGLE_MAP) {
                    drawGoogleMapsUnderlay(
                        alphaMultiplier = 0.65f,
                        houseWidthMeters = houseWidthMeters,
                        houseLengthMeters = houseLengthMeters,
                        gpsLat = gpsLocation?.latitude ?: 10.7769,
                        gpsLng = gpsLocation?.longitude ?: 106.7009
                    )
                    // Dim outside the khoanh vùng polygon so the cut-out house polygon stands out sharply
                    drawRect(color = Color(0xFF0F172A).copy(alpha = 0.18f), size = size)
                } else {
                    // Mode 2: Outside is slate-gray mask so the white polygon house pops 100%
                    drawRect(color = Color(0xFFCBD5E1), size = size)
                }

                // 3. Inside of the User's Khoanh Vùng Polygon (Clipped strictly to housePolygonPath!)
                drawPath(
                    path = housePolygonPath,
                    color = if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE && mapStage == MapStage.READY_MAP) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.84f)
                    },
                    style = Fill
                )

                clipPath(housePolygonPath) {
                    // Draw 1-meter architectural grid strictly INSIDE the user's khoanh vùng polygon
                    val stepX = w / 12f
                    val stepY = h / 14f
                    val gridLineColor = if (visualStyle == MapVisualStyle.CRISP_WHITE_HOUSE && mapStage == MapStage.READY_MAP) {
                        Color(0xFFE2E8F0)
                    } else {
                        Color(0xFFCBD5E1).copy(alpha = 0.55f)
                    }
                    for (i in 1..11) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(i * stepX, 0f),
                            end = Offset(i * stepX, h),
                            strokeWidth = 1.5f
                        )
                    }
                    for (j in 1..13) {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, j * stepY),
                            end = Offset(w, j * stepY),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 4. Draw Thick Architectural Outer Wall along the User's Exact Khoanh Vùng Polygon!
                drawPath(
                    path = housePolygonPath,
                    color = Color(0xFF0F172A),
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw Wall Segment Lengths (m) & Corner Badges (G1, G2...) along the Polygon Perimeter
                for (i in activeFloorplanPolygon.indices) {
                    val p1 = activeFloorplanPolygon[i]
                    val p2 = activeFloorplanPolygon[(i + 1) % activeFloorplanPolygon.size]
                    val s1 = toScreen(p1)
                    val s2 = toScreen(p2)
                    val dxM = (p2.x - p1.x) * houseWidthMeters / 0.78f
                    val dyM = (p2.y - p1.y) * houseLengthMeters / 0.78f
                    val segMeters = hypot(dxM.toDouble(), dyM.toDouble()).toFloat().coerceAtLeast(1.0f)

                    val midWall = Offset((s1.x + s2.x) / 2f, (s1.y + s2.y) / 2f)
                    drawLabelPill(
                        center = midWall,
                        text = String.format(Locale.US, "%.1fm", segMeters),
                        bgColor = Color(0xFF334155)
                    )

                    // Corner vertex dot
                    drawCircle(color = Color.White, radius = 9f, center = s1)
                    drawCircle(color = PrimaryBlue, radius = 6f, center = s1)
                }

                // 5. Draw Signal + IMU Distance Calibration Line from Reference Fire Sensor Anchor (#1) to User
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

                // 6. Draw Active Navigation Path (Supports Single-Stage & 2-Stage Extinguisher -> Fire)
                activeRoute?.let { route ->
                    if (route.targetType == "TWO_STAGE" && route.stage1Points.size >= 2 && route.stage2Points.size >= 2) {
                        // Stage 1: User -> Nearest Extinguisher (Blue)
                        val s1Path = Path()
                        val s1First = toScreen(route.stage1Points.first())
                        s1Path.moveTo(s1First.x, s1First.y)
                        for (i in 1 until route.stage1Points.size) {
                            val pt = toScreen(route.stage1Points[i])
                            s1Path.lineTo(pt.x, pt.y)
                        }
                        drawPath(s1Path, color = PrimaryBlue.copy(alpha = 0.25f), style = Stroke(width = 24f, cap = StrokeCap.Round))
                        drawPath(
                            s1Path,
                            color = PrimaryBlue,
                            style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 12f), 0f))
                        )

                        // Stage 2: Extinguisher -> Fire Source (Orange-Red)
                        val s2Path = Path()
                        val s2First = toScreen(route.stage2Points.first())
                        s2Path.moveTo(s2First.x, s2First.y)
                        for (i in 1 until route.stage2Points.size) {
                            val pt = toScreen(route.stage2Points[i])
                            s2Path.lineTo(pt.x, pt.y)
                        }
                        drawPath(s2Path, color = Color(0xFFEA580C).copy(alpha = 0.25f), style = Stroke(width = 24f, cap = StrokeCap.Round))
                        drawPath(
                            s2Path,
                            color = Color(0xFFEA580C),
                            style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
                        )

                        val extPivot = toScreen(route.stage1Points.last())
                        drawLabelPill(
                            center = Offset(extPivot.x, extPivot.y - 42f),
                            text = "CHẶNG 1: LẤY BÌNH CC TẠI ĐÂY ➔ CHẶNG 2: DẬP LỬA",
                            bgColor = Color(0xFF7C3AED)
                        )
                    } else if (route.pathPoints.size >= 2) {
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

                // Highlight ring around currently selected map element (if any)
                selectedMapElement?.let { sel ->
                    val selScreen = toScreen(sel.position)
                    drawCircle(
                        color = Color(0xFF7C3AED).copy(alpha = 0.28f),
                        radius = 44f,
                        center = selScreen
                    )
                    drawCircle(
                        color = Color(0xFF7C3AED),
                        radius = 44f,
                        center = selScreen,
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f))
                    )
                }

                // 7. Draw Main Doors (CỬA CHÍNH)
                tempMainDoors.forEachIndexed { idx, pt ->
                    drawMainDoorSymbol(toScreen(pt), idx + 1)
                }

                // 8. Draw Numbered Doors (CỬA ĐÁNH SỐ #1, #2, #3...)
                tempDoors.forEachIndexed { idx, pt ->
                    drawNumberedDoorSymbol(toScreen(pt), idx + 1)
                }

                // 9. Draw Stairs
                tempStairs.forEachIndexed { idx, pt ->
                    drawLargeStairsSymbol(toScreen(pt), "CẦU THANG ${idx + 1}")
                }

                // 10. Draw Exits (Cửa Exit)
                tempExits.forEachIndexed { idx, pt ->
                    drawLargeExitSymbol(toScreen(pt), "CỬA EXIT ${idx + 1}")
                }

                // 11. Draw Fire Extinguishers (Bình Chữa Cháy)
                tempExtinguishers.forEachIndexed { idx, pt ->
                    drawLargeExtinguisherSymbol(toScreen(pt), "BÌNH CC ${idx + 1}")
                }

                // 12. Draw Pinned Fire Sensors + CIRCULAR EXPANDING FIRE SPREAD ZONE WHEN ALARM RINGS LONGER
                val sensorsToRender = if (placedSensorPins.isNotEmpty()) {
                    placedSensorPins
                } else {
                    nodes.mapIndexed { idx, n ->
                        val prob = n.telemetry.calculateFireProbability(gasThresholdAdc, n.effectiveConfig()).totalProbabilityPercent
                        val isAlm = n.isEffectivelyAlarming() || n.suspectedBurnedOffline
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
                            isReferenceAnchor = (idx == 0),
                            suspectedBurnedOffline = n.suspectedBurnedOffline
                        )
                    }
                }

                sensorsToRender.forEachIndexed { idx, pin ->
                    val centerScreen = toScreen(pin.position)
                    val elapsedSec = if (pin.isAlarm) {
                        val startMs = if (pin.alarmStartedAtMs > 0L) pin.alarmStartedAtMs else (currentClockMs - 3000L)
                        ((currentClockMs - startMs) / 1000L).coerceAtLeast(1L)
                    } else 0L

                    val spreadRadiusMeters = if (pin.isAlarm) {
                        (1.0f + elapsedSec * 0.18f).coerceAtMost(max(houseWidthMeters, houseLengthMeters) * 0.75f)
                    } else 0f

                    val pixelsPerMeter = ((w * 0.78f * scale) / houseWidthMeters.coerceAtLeast(5f)).coerceAtLeast(10f)
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
                    val tempAndProb = if (pin.temperatureCelsius != null) {
                        String.format(
                            Locale.US,
                            "%.1f°C · P=%.0f%%",
                            pin.temperatureCelsius,
                            pin.fireProbabilityPercent
                        )
                    } else {
                        String.format(Locale.US, "P=%.0f%%", pin.fireProbabilityPercent)
                    }
                    val offlineSuffix = if (pin.suspectedBurnedOffline) " · MẤT KẾT NỐI" else ""
                    drawLargeFireSensorSymbol(
                        center = centerScreen,
                        label = "$labelPrefix${pin.displayName}$offlineSuffix ($tempAndProb)",
                        isAlarm = pin.isAlarm,
                        isAnchor = (idx == 0),
                        pulseRadius = pulseRadius
                    )
                }

                // 13. Draw Current Standing User Position (clamped to visible canvas border when outside so icon never disappears off-screen)
                val rawUserScreen = toScreen(currentUserPos)
                val visibleUserScreen = Offset(
                    x = rawUserScreen.x.coerceIn(28f, (w - 28f).coerceAtLeast(28f)),
                    y = rawUserScreen.y.coerceIn(28f, (h - 48f).coerceAtLeast(28f))
                )
                drawUserStandingBeacon(
                    center = visibleUserScreen,
                    headingDeg = compassHeading,
                    isWalking = isWalking,
                    isOutsideHouse = isUserOutsideHouse,
                    outsideDistanceMeters = presenceState.distanceOutsideMeters
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
                            "IMU %d%% + La bàn %d%% + RSSI %d%% (GPS 0%%) · Acc %.1f%% (±%.2fm)",
                            triFusionSnapshot.imuWeightPct,
                            triFusionSnapshot.compassWeightPct,
                            triFusionSnapshot.rssiWeightPct,
                            fusedAccuracyPercent,
                            fusedErrorMeters
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

            // Contextual Individual Map Element Inspector / Editor Bar (Move / Route / Delete single item)
            selectedMapElement?.let { sel ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0xFF7C3AED), RoundedCornerShape(14.dp))
                        .testTag("selected_map_element_bar"),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Đang chọn: ${sel.label}",
                                color = Color(0xFF5B21B6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = { selectedMapElement = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    activeRoute = buildDoorAwareRoute(
                                        start = currentUserPos,
                                        target = sel.position,
                                        targetLabel = sel.label,
                                        targetType = if (sel.kind == SelectedMapElementKind.EXIT || sel.kind == SelectedMapElementKind.MAIN_DOOR) "EXIT" else "EXTINGUISHER",
                                        notes = "Đang chỉ đường tới ${sel.label}"
                                    )
                                    selectedMapElement = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("btn_selected_element_route")
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dẫn Tới Đây", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val newPos = currentUserPos
                                    when (sel.kind) {
                                        SelectedMapElementKind.SENSOR -> {
                                            if (sel.index in placedSensorPins.indices) {
                                                val pin = placedSensorPins[sel.index]
                                                placedSensorPins[sel.index] = pin.copy(position = newPos)
                                                onBindSensorPosition(pin.deviceId, newPos.x, newPos.y)
                                            }
                                        }
                                        SelectedMapElementKind.EXTINGUISHER -> {
                                            if (sel.index in tempExtinguishers.indices) {
                                                tempExtinguishers[sel.index] = newPos
                                                sel.deviceIdOrDbId?.let { onUpdateExtinguisherPosition(it, newPos.x, newPos.y) }
                                            }
                                        }
                                        SelectedMapElementKind.NUMBERED_DOOR -> {
                                            if (sel.index in tempDoors.indices) {
                                                tempDoors[sel.index] = newPos
                                                sel.deviceIdOrDbId?.let { onUpdateMapObjectPosition(it, newPos.x, newPos.y) }
                                            }
                                        }
                                        SelectedMapElementKind.MAIN_DOOR -> {
                                            if (sel.index in tempMainDoors.indices) {
                                                tempMainDoors[sel.index] = newPos
                                                sel.deviceIdOrDbId?.let { onUpdateMapObjectPosition(it, newPos.x, newPos.y) }
                                            }
                                        }
                                        SelectedMapElementKind.EXIT -> {
                                            if (sel.index in tempExits.indices) {
                                                tempExits[sel.index] = newPos
                                                sel.deviceIdOrDbId?.let { onUpdateMapObjectPosition(it, newPos.x, newPos.y) }
                                            }
                                        }
                                        SelectedMapElementKind.STAIRS -> {
                                            if (sel.index in tempStairs.indices) {
                                                tempStairs[sel.index] = newPos
                                                sel.deviceIdOrDbId?.let { onUpdateMapObjectPosition(it, newPos.x, newPos.y) }
                                            }
                                        }
                                    }
                                    selectedMapElement = sel.copy(position = newPos)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(34.dp)
                                    .testTag("btn_selected_element_move_here")
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dời Tới Điểm Đứng", color = Color(0xFF7C3AED), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            if (sel.kind != SelectedMapElementKind.SENSOR) {
                                Button(
                                    onClick = {
                                        when (sel.kind) {
                                            SelectedMapElementKind.EXTINGUISHER -> {
                                                if (sel.index in tempExtinguishers.indices) tempExtinguishers.removeAt(sel.index)
                                                sel.deviceIdOrDbId?.let { onDeleteExtinguisher(it) }
                                            }
                                            SelectedMapElementKind.NUMBERED_DOOR -> {
                                                if (sel.index in tempDoors.indices) tempDoors.removeAt(sel.index)
                                                sel.deviceIdOrDbId?.let { onDeleteMapObject(it) }
                                            }
                                            SelectedMapElementKind.MAIN_DOOR -> {
                                                if (sel.index in tempMainDoors.indices) tempMainDoors.removeAt(sel.index)
                                                sel.deviceIdOrDbId?.let { onDeleteMapObject(it) }
                                            }
                                            SelectedMapElementKind.EXIT -> {
                                                if (sel.index in tempExits.indices) tempExits.removeAt(sel.index)
                                                sel.deviceIdOrDbId?.let { onDeleteMapObject(it) }
                                            }
                                            SelectedMapElementKind.STAIRS -> {
                                                if (sel.index in tempStairs.indices) tempStairs.removeAt(sel.index)
                                                sel.deviceIdOrDbId?.let { onDeleteMapObject(it) }
                                            }
                                            SelectedMapElementKind.SENSOR -> {}
                                        }
                                        selectedMapElement = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(0.75f)
                                        .height(34.dp)
                                        .testTag("btn_selected_element_delete")
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Xóa", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // STAGE 2 BOTTOM CONTROLS:
        // 1. GHIM VỊ TRÍ NHẬN DIỆN LỬA TRƯỚC LÀM MỐC CHUẨN + ĐO KHOẢNG CÁCH BẰNG TÍN HIỆU & CẢM BIẾN ĐT
        // 2. GHIM CỬA CHÍNH, CÁC CÁNH CỬA (ĐÁNH SỐ #1, #2...), BÌNH CỨU HỎA, CỬA EXIT
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
                                    "📡 ESP32 RSSI %ddBm (~%.2fm) + 📲 IMU (%d bước, %d°): ~%.2fm",
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
                                    "🎯 Kết hợp 3 cảm biến: %.2fm · Acc = %.1f%% (±%.2fm) · GPS: 0%% nhiễu",
                                    fusedDistanceMeters,
                                    fusedAccuracyPercent,
                                    fusedErrorMeters
                                ),
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        if (anchorSensorPin != null) {
                            OutlinedButton(
                                onClick = {
                                    val rad = Math.toRadians((compassHeading - 90f).toDouble())
                                    val dxNorm = (cos(rad).toFloat() * fusedDistanceMeters) / houseWidthMeters.coerceAtLeast(5f)
                                    val dyNorm = (sin(rad).toFloat() * fusedDistanceMeters) / houseLengthMeters.coerceAtLeast(5f)
                                    val rawPos = Point2D(
                                        x = (anchorSensorPin.position.x + dxNorm).coerceIn(0.10f, 0.90f),
                                        y = (anchorSensorPin.position.y + dyNorm).coerceIn(0.10f, 0.90f)
                                    )
                                    val calibratedPos = clampPointIntoPolygonBounds(rawPos, activeFloorplanPolygon)
                                    currentUserPos = calibratedPos
                                    onSetUserPosition(floorId, calibratedPos.x, calibratedPos.y)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Đo Chuẩn", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B21B6))
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
                                    val prob = node.telemetry.calculateFireProbability(gasThresholdAdc).totalProbabilityPercent
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
                                    val prob = chosenNode?.let {
                                        it.telemetry.calculateFireProbability(gasThresholdAdc, it.effectiveConfig()).totalProbabilityPercent
                                    } ?: 5.0f
                                    val isAlm = chosenNode?.let { it.isEffectivelyAlarming() || it.suspectedBurnedOffline } ?: false
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
                                                isReferenceAnchor = placedSensorPins.isEmpty(),
                                                suspectedBurnedOffline = chosenNode?.suspectedBurnedOffline ?: false
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

                    Button(
                        onClick = {
                            userManuallyClearedToBlindRoom = false
                            if (placedSensorPins.isEmpty() && nodes.isNotEmpty()) {
                                val firstNode = nodes.first()
                                val defaultAnchorPos = clampPointIntoPolygonBounds(Point2D(0.45f, 0.35f), activeFloorplanPolygon)
                                placedSensorPins.add(
                                    PlacedSensorPin(
                                        deviceId = firstNode.deviceId,
                                        displayName = firstNode.displayName,
                                        position = defaultAnchorPos,
                                        isAlarm = firstNode.isEffectivelyAlarming() || firstNode.suspectedBurnedOffline,
                                        temperatureCelsius = firstNode.telemetry.temperatureCelsius,
                                        fireProbabilityPercent = firstNode.telemetry.calculateFireProbability(gasThresholdAdc, firstNode.effectiveConfig()).totalProbabilityPercent,
                                        rssiDbm = firstNode.telemetry.rssiDbm,
                                        isReferenceAnchor = true,
                                        suspectedBurnedOffline = firstNode.suspectedBurnedOffline
                                    )
                                )
                                onBindSensorPosition(firstNode.deviceId, defaultAnchorPos.x, defaultAnchorPos.y)
                            }
                            // Persist all pinned sensor positions to Room DB
                            placedSensorPins.forEach { pin ->
                                onBindSensorPosition(pin.deviceId, pin.position.x, pin.position.y)
                            }
                            if (regionPolygonPoints.size >= 3) {
                                onSaveBoundaryPolygon(floorId, regionPolygonPoints.toList(), houseWidthMeters, houseLengthMeters)
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
                        onClick = {
                            onMuteLocalNotifications()
                            localMutedForNav = true
                            routeToFireSensor(null)
                        },
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
                        onClick = {
                            onMuteLocalNotifications()
                            localMutedForNav = true
                            routeToExtinguisher()
                        },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            onMuteLocalNotifications()
                            localMutedForNav = true
                            routeTwoStageExtinguisherToFire(null)
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(38.dp)
                            .testTag("btn_route_two_stage_ext_to_fire"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) {
                        Icon(Icons.Default.FireExtinguisher, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "2 Chặng: Bình CC ➔ Lửa",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onMuteLocalNotifications()
                            localMutedForNav = true
                            routeToExitDoor()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("btn_route_exit_door"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NormalGreenBackground,
                            contentColor = NormalGreen
                        )
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Thoát Hiểm (${tempMainDoors.size + tempExits.size} Cửa)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NormalGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ray-casting point-in-polygon check + projection to keep points inside the user's khoanh vùng polygon.
 */
private fun isPointInPolygon(pt: Point2D, polygon: List<Point2D>): Boolean {
    if (polygon.size < 3) return true
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val intersect = ((yi > pt.y) != (yj > pt.y)) &&
            (pt.x < (xj - xi) * (pt.y - yi) / ((yj - yi).takeIf { abs(it) > 0.00001f } ?: 0.00001f) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private fun clampPointIntoPolygonBounds(pt: Point2D, polygon: List<Point2D>): Point2D {
    if (polygon.size < 3 || isPointInPolygon(pt, polygon)) {
        return Point2D(pt.x.coerceIn(0.06f, 0.94f), pt.y.coerceIn(0.06f, 0.94f))
    }
    // Pull point slightly toward the polygon centroid until inside
    val cx = polygon.map { it.x }.average().toFloat()
    val cy = polygon.map { it.y }.average().toFloat()
    for (step in 1..10) {
        val t = step / 10f
        val candidate = Point2D(
            x = pt.x + (cx - pt.x) * t,
            y = pt.y + (cy - pt.y) * t
        )
        if (isPointInPolygon(candidate, polygon)) {
            return candidate
        }
    }
    return Point2D(cx, cy)
}

/**
 * Interactive Top-Down Google Maps (Hybrid Satellite + Labels / Satellite / Roadmap) Alignment WebView.
 * Pins polygon corners as true geographic GeoLatLng points directly inside Leaflet so vertices stay
 * locked onto the exact roof corners while panning/zooming, with automatic Esri/OSM fallback and
 * renderer crash protection (software layer + onRenderProcessGone).
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun GoogleMapsAlignWebView(
    initialLat: Double,
    initialLng: Double,
    flyToTrigger: Int,
    flyToLat: Double,
    flyToLng: Double,
    flyToZoom: Float,
    gpsLat: Double,
    gpsLng: Double,
    layerType: GoogleMapLayerType,
    tapToPinEnabled: Boolean,
    geoPoints: List<GeoLatLng>,
    onCenterMoved: (Double, Double) -> Unit,
    onMapTappedAddPoint: (Double, Double) -> Unit,
    onPointDragged: (Int, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var renderProcessCrashed by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastFlyTrigger by remember { mutableIntStateOf(flyToTrigger) }
    var lastLayerCode by remember { mutableStateOf(layerType.lyrsCode) }
    var lastPointsSignature by remember { mutableStateOf("") }
    var lastTapEnabled by remember { mutableStateOf(tapToPinEnabled) }
    val uiScope = rememberCoroutineScope()

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onCenterChanged(lat: Double, lng: Double) {
                uiScope.launch { onCenterMoved(lat, lng) }
            }

            @JavascriptInterface
            fun onMapClicked(lat: Double, lng: Double) {
                uiScope.launch { onMapTappedAddPoint(lat, lng) }
            }

            @JavascriptInterface
            fun onVertexDragged(index: Int, lat: Double, lng: Double) {
                uiScope.launch { onPointDragged(index, lat, lng) }
            }
        }
    }

    val initialHtmlContent = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #cbd5e1; font-family: sans-serif; }
                .vertex-pin {
                    width: 26px; height: 26px; border-radius: 50%;
                    background: #2563eb; border: 2.5px solid #ffffff;
                    color: #ffffff; font-size: 10px; font-weight: 800;
                    display: flex; align-items: center; justify-content: center;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.45);
                }
                .vertex-pin.first { background: #dc2626; }
                .edge-label {
                    background: rgba(15, 23, 42, 0.88); color: #ffffff;
                    border: none; border-radius: 4px; padding: 1px 5px;
                    font-size: 10px; font-weight: bold; box-shadow: none;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                window.__smartFireMap = null;
                window.__smartFireTileLayer = null;
                window.__smartFireFallbackAdded = false;
                window.__smartFireGpsMarker = null;
                window.__smartFirePolygon = null;
                window.__smartFireMarkers = [];
                window.__smartFireEdgeLabels = [];
                window.__tapToPinEnabled = ${if (tapToPinEnabled) "true" else "false"};

                function __googleTileUrl(lyrs) {
                    return 'https://mt1.google.com/vt/lyrs=' + lyrs + '&hl=vi&x={x}&y={y}&z={z}';
                }

                window.__setMapLayer = function(lyrs) {
                    if (!window.__smartFireMap || typeof L === 'undefined') return;
                    if (window.__smartFireTileLayer) {
                        window.__smartFireMap.removeLayer(window.__smartFireTileLayer);
                    }
                    var tileLayer = L.tileLayer(__googleTileUrl(lyrs), {
                        maxNativeZoom: 21,
                        maxZoom: 22,
                        updateWhenIdle: true,
                        keepBuffer: 1
                    });
                    tileLayer.on('tileerror', function() {
                        if (!window.__smartFireFallbackAdded && window.__smartFireMap) {
                            window.__smartFireFallbackAdded = true;
                            L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                                maxNativeZoom: 18, maxZoom: 21, updateWhenIdle: true, keepBuffer: 1
                            }).addTo(window.__smartFireMap);
                        }
                    });
                    tileLayer.addTo(window.__smartFireMap);
                    window.__smartFireTileLayer = tileLayer;
                };

                function __haversineM(a, b) {
                    var R = 6371000;
                    var dLat = (b[0] - a[0]) * Math.PI / 180;
                    var dLng = (b[1] - a[1]) * Math.PI / 180;
                    var s1 = Math.sin(dLat / 2), s2 = Math.sin(dLng / 2);
                    var h = s1 * s1 + Math.cos(a[0] * Math.PI / 180) * Math.cos(b[0] * Math.PI / 180) * s2 * s2;
                    return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
                }

                window.__syncPolygon = function(pts) {
                    if (!window.__smartFireMap || typeof L === 'undefined') return;
                    var map = window.__smartFireMap;

                    for (var i = 0; i < window.__smartFireMarkers.length; i++) {
                        map.removeLayer(window.__smartFireMarkers[i]);
                    }
                    window.__smartFireMarkers = [];

                    for (var k = 0; k < window.__smartFireEdgeLabels.length; k++) {
                        map.removeLayer(window.__smartFireEdgeLabels[k]);
                    }
                    window.__smartFireEdgeLabels = [];

                    if (window.__smartFirePolygon) {
                        map.removeLayer(window.__smartFirePolygon);
                        window.__smartFirePolygon = null;
                    }

                    if (!pts || pts.length === 0) return;

                    if (pts.length >= 3) {
                        window.__smartFirePolygon = L.polygon(pts, {
                            color: '#059669',
                            weight: 3.5,
                            fillColor: '#10b981',
                            fillOpacity: 0.28
                        }).addTo(map);
                    } else if (pts.length === 2) {
                        window.__smartFirePolygon = L.polyline(pts, {
                            color: '#2563eb',
                            weight: 3.5
                        }).addTo(map);
                    }

                    var edgeCount = pts.length >= 3 ? pts.length : pts.length - 1;
                    for (var e = 0; e < edgeCount; e++) {
                        var p1 = pts[e];
                        var p2 = pts[(e + 1) % pts.length];
                        var mid = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2];
                        var distM = __haversineM(p1, p2);
                        var lbl = L.tooltip({
                            permanent: true,
                            direction: 'center',
                            className: 'edge-label'
                        }).setLatLng(mid).setContent(distM.toFixed(1) + 'm').addTo(map);
                        window.__smartFireEdgeLabels.push(lbl);
                    }

                    pts.forEach(function(pt, idx) {
                        var icon = L.divIcon({
                            className: '',
                            html: '<div class="vertex-pin ' + (idx === 0 ? 'first' : '') + '">Đ' + (idx + 1) + '</div>',
                            iconSize: [26, 26],
                            iconAnchor: [13, 13]
                        });
                        var m = L.marker(pt, { icon: icon, draggable: true }).addTo(map);
                        m.on('dragend', function(ev) {
                            var pos = ev.target.getLatLng();
                            if (window.AndroidMapBridge) {
                                window.AndroidMapBridge.onVertexDragged(idx, pos.lat, pos.lng);
                            }
                        });
                        window.__smartFireMarkers.push(m);
                    });
                };

                try {
                    if (typeof L !== 'undefined') {
                        var map = L.map('map', {
                            zoomControl: true,
                            attributionControl: false,
                            preferCanvas: true,
                            fadeAnimation: false,
                            zoomAnimation: false,
                            markerZoomAnimation: false
                        }).setView([$initialLat, $initialLng], 20);

                        window.__smartFireMap = map;
                        window.__setMapLayer('${layerType.lyrsCode}');

                        window.__smartFireGpsMarker = L.circleMarker([$gpsLat, $gpsLng], {
                            radius: 6,
                            fillColor: '#2563eb',
                            color: '#ffffff',
                            weight: 2,
                            fillOpacity: 0.9
                        }).addTo(map);

                        map.on('move', function() {
                            var c = map.getCenter();
                            if (window.AndroidMapBridge) {
                                window.AndroidMapBridge.onCenterChanged(c.lat, c.lng);
                            }
                        });

                        map.on('click', function(ev) {
                            if (window.__tapToPinEnabled && window.AndroidMapBridge) {
                                window.AndroidMapBridge.onMapClicked(ev.latlng.lat, ev.latlng.lng);
                            }
                        });
                    }
                } catch (e) {
                    console.warn('GoogleMapsAlignWebView init error:', e);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.onPause()
                    wv.removeAllViews()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                } catch (_: Throwable) {}
            }
            webViewInstance = null
        }
    }

    if (renderProcessCrashed) {
        Box(
            modifier = modifier.background(Color(0xFFCBD5E1)),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                    .clickable { renderProcessCrashed = false }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bản đồ nền an toàn (Chạm tải lại Google Maps)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                addJavascriptInterface(bridge, "AndroidMapBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val ptsJson = geoPoints.joinToString(
                            prefix = "[",
                            postfix = "]",
                            separator = ","
                        ) { pt -> String.format(Locale.US, "[%.7f,%.7f]", pt.lat, pt.lng) }
                        view?.evaluateJavascript(
                            "if(window.__syncPolygon){window.__syncPolygon($ptsJson);}",
                            null
                        )
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        renderProcessCrashed = true
                        try {
                            view?.stopLoading()
                            (view?.parent as? ViewGroup)?.removeView(view)
                            view?.destroy()
                        } catch (_: Throwable) {}
                        webViewInstance = null
                        return true
                    }
                }
                loadDataWithBaseURL("https://unpkg.com", initialHtmlContent, "text/html", "UTF-8", null)
                webViewInstance = this
            }
        },
        update = { webView ->
            if (lastTapEnabled != tapToPinEnabled) {
                lastTapEnabled = tapToPinEnabled
                webView.evaluateJavascript(
                    "window.__tapToPinEnabled=${if (tapToPinEnabled) "true" else "false"};",
                    null
                )
            }
            if (lastLayerCode != layerType.lyrsCode) {
                lastLayerCode = layerType.lyrsCode
                webView.evaluateJavascript(
                    "if(window.__setMapLayer){window.__setMapLayer('${layerType.lyrsCode}');}",
                    null
                )
            }
            if (lastFlyTrigger != flyToTrigger) {
                lastFlyTrigger = flyToTrigger
                val flyJs = String.format(
                    Locale.US,
                    "if(window.__smartFireMap){window.__smartFireMap.setView([%.7f,%.7f],%.1f,{animate:false});}",
                    flyToLat,
                    flyToLng,
                    flyToZoom
                )
                webView.evaluateJavascript(flyJs, null)
            }

            val ptsJson = geoPoints.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ","
            ) { pt -> String.format(Locale.US, "[%.7f,%.7f]", pt.lat, pt.lng) }
            if (lastPointsSignature != ptsJson) {
                lastPointsSignature = ptsJson
                webView.evaluateJavascript(
                    "if(window.__syncPolygon){window.__syncPolygon($ptsJson);}",
                    null
                )
            }
        },
        onRelease = { webView ->
            try {
                webView.removeJavascriptInterface("AndroidMapBridge")
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.onPause()
                webView.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            } catch (_: Throwable) {}
            if (webViewInstance === webView) {
                webViewInstance = null
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

    drawCircle(
        color = Color(0xFFDC2626).copy(alpha = 0.35f),
        radius = spreadRadiusPx * 0.55f,
        center = center
    )

    val radiusEnd = Offset(center.x + spreadRadiusPx, center.y)
    drawLine(
        color = CriticalAlarmRed,
        start = center,
        end = radiusEnd,
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )
    drawCircle(color = CriticalAlarmRed, radius = 6f, center = radiusEnd)

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

private fun DrawScope.drawNumberedDoorSymbol(center: Offset, doorNumber: Int) {
    drawCircle(color = Color(0xFFFEF3C7), radius = 27f, center = center)
    drawCircle(color = Color(0xFFD97706), radius = 27f, center = center, style = Stroke(width = 3.8f))

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
    drawLine(
        color = Color(0xFFB45309),
        start = Offset(center.x - 10f, center.y + 2f),
        end = Offset(center.x + 5f, center.y - 14f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )
    drawArc(
        color = Color(0xFFD97706),
        startAngle = 290f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(center.x - 14f, center.y - 14f),
        size = Size(28f, 28f),
        style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f))
    )

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
        String.format(Locale.US, "GPS: %.5f, %.5f", gpsLat, gpsLng),
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
    isWalking: Boolean,
    isOutsideHouse: Boolean = false,
    outsideDistanceMeters: Float = 0f
) {
    val coneColor = if (isOutsideHouse) Color(0xFFEA580C) else PrimaryBlue
    val dotColor = when {
        isOutsideHouse -> Color(0xFFEA580C)
        isWalking -> NormalGreen
        else -> PrimaryBlue
    }
    drawArc(
        color = coneColor.copy(alpha = 0.28f),
        startAngle = headingDeg - 120f,
        sweepAngle = 60f,
        useCenter = true,
        topLeft = Offset(center.x - 42f, center.y - 42f),
        size = Size(84f, 84f)
    )

    drawCircle(color = Color.White, radius = 20f, center = center)
    drawCircle(
        color = dotColor,
        radius = 15f,
        center = center
    )
    drawCircle(color = Color.White, radius = 5f, center = center)

    val label = if (isOutsideHouse) {
        if (outsideDistanceMeters >= 1f) {
            String.format(Locale.US, "NGOÀI NHÀ (~%.0fm)", outsideDistanceMeters)
        } else {
            "BẠN (NGOÀI NHÀ)"
        }
    } else {
        "BẠN ĐANG ĐỨNG ĐÂY"
    }
    drawLabelPill(
        center = Offset(center.x, center.y + 34f),
        text = label,
        bgColor = if (isOutsideHouse) Color(0xFFC2410C) else Color(0xFF0F172A)
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
