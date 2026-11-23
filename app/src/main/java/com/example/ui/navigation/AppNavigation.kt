package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.SmartFireApplication
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.ExtinguisherType
import com.example.domain.model.FirmwareState
import com.example.domain.model.MapNavigationRequest
import com.example.domain.model.MapNavigationTargetType
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import com.example.domain.pathfinding.NavigationRoute
import com.example.domain.pathfinding.TurnByTurnNavigator
import com.example.sensor.DeviceMovementTracker
import com.example.sensor.PresenceZone
import com.example.ui.components.FireEmergencyPopupDialog
import com.example.ui.components.TurnByTurnNavigationBanner
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.devices.AddDeviceWizardDialog
import com.example.ui.screens.devices.DeviceDetailScreen
import com.example.ui.screens.map.IndoorMap2DScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightSurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextSecondary
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class StreamlinedTab {
    DASHBOARD, // Tổng quan: Xem toàn bộ cảm biến (nhiệt độ °C, khói ADC, lửa), add/xóa cảm biến, tắt/test báo cháy, biểu đồ
    MAP_2D,    // Bản đồ 2D: Căn phòng mù -> Ghim báo cháy & vật dụng (Cửa, Bình CC, Cầu thang, Cửa Exit) -> Dẫn đường
    SETTINGS   // Cài đặt: Nhật ký sự kiện & Setting hệ thống
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val repository = SmartFireApplication.instance.repository
    val scope = rememberCoroutineScope()

    val isLoggedIn by repository.isLoggedIn.collectAsState()
    val loggedInEmail by repository.loggedInEmail.collectAsState()
    val loggedInRole by repository.loggedInRole.collectAsState()
    val phoneAlarmSoundEnabled by repository.phoneAlarmSoundEnabled.collectAsState()
    val mapNavigationRequest by repository.mapNavigationRequest.collectAsState()

    // 1. Show Simulated Login Screen if user has not logged in yet
    if (!isLoggedIn) {
        LoginScreen(
            onLoginSuccess = { email, role, enableDemo ->
                repository.loginSimulated(email, role)
                if (enableDemo) {
                    repository.toggleDemoMode(true)
                }
            },
            modifier = modifier
        )
        return
    }

    var currentTab by remember { mutableStateOf(StreamlinedTab.DASHBOARD) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var mapInSetupMode by remember { mutableStateOf(false) }

    val nodes by repository.allDevices.collectAsState(initial = emptyList())
    val homes by repository.allHomes.collectAsState(initial = emptyList())
    val currentHome = homes.firstOrNull()
    val floors by repository.getFloorsForHome(currentHome?.id ?: "home_main").collectAsState(initial = emptyList())
    val rooms by repository.allRooms.collectAsState(initial = emptyList())
    val currentFloor = floors.firstOrNull()
    val extinguishers by repository.getExtinguishersForFloor(currentFloor?.id ?: "").collectAsState(initial = emptyList())
    val mapObjects by repository.getMapObjectsForFloor(currentFloor?.id ?: "").collectAsState(initial = emptyList())
    val mapEdges by repository.getMapEdgesForFloor(currentFloor?.id ?: "").collectAsState(initial = emptyList())
    val userLocation by repository.getUserLocation(currentHome?.id ?: "").collectAsState(initial = null)
    val events by repository.allEvents.collectAsState(initial = emptyList())
    val isDemoMode by repository.isDemoMode.collectAsState()
    val gasThresholdAdc by repository.gasThresholdAdc.collectAsState()
    val stepStrideMeters by repository.stepStrideMeters.collectAsState()
    val autoRouteOnAlarm by repository.autoRouteOnAlarm.collectAsState()
    val recentSnapshots by repository.allRecentSnapshots.collectAsState(initial = emptyList())
    val aiInsights by repository.allInsights.collectAsState(initial = emptyList())
    val localAlarmMutedUntilMs by repository.localAlarmMutedUntilMs.collectAsState()
    var isLocalAlarmMuted by remember(localAlarmMutedUntilMs) {
        mutableStateOf(System.currentTimeMillis() < localAlarmMutedUntilMs)
    }
    LaunchedEffect(localAlarmMutedUntilMs) {
        val remaining = localAlarmMutedUntilMs - System.currentTimeMillis()
        if (remaining > 0L) {
            isLocalAlarmMuted = true
            delay(remaining)
            isLocalAlarmMuted = false
        } else {
            isLocalAlarmMuted = false
        }
    }

    // Shared app-wide movement tracker so live compass heading & outdoor GPS distance power
    // the floating Google Maps-style Turn-by-Turn popup even while on Dashboard/Settings
    val context = LocalContext.current
    val sharedMovementTracker = remember { DeviceMovementTracker(context, scope) }
    DisposableEffect(sharedMovementTracker) {
        sharedMovementTracker.startTracking()
        onDispose { sharedMovementTracker.stopTracking() }
    }
    val liveCompassHeading by sharedMovementTracker.compassHeading.collectAsState()
    val liveGpsAccuracyMeters by sharedMovementTracker.gpsAccuracyMeters.collectAsState()
    val liveHasRealGpsFix by sharedMovementTracker.hasRealGpsFix.collectAsState()
    val liveGpsDeltaMeters by sharedMovementTracker.gpsDeltaMeters.collectAsState()
    val liveGpsDistFromAnchorM = if (liveHasRealGpsFix) {
        hypot(liveGpsDeltaMeters.x.toDouble(), liveGpsDeltaMeters.y.toDouble()).toFloat()
    } else null

    // Active Google Maps-style Turn-by-Turn floating popup request (stays visible on any tab until stopped)
    var activeGuidanceRequest by remember { mutableStateOf<MapNavigationRequest?>(null) }

    // Helper to build a lightweight Turn-by-Turn route for the floating Google Maps-style banner
    fun buildOverlayGuidance(req: MapNavigationRequest): com.example.domain.pathfinding.TurnByTurnGuidance {
        val wM = (currentFloor?.widthMeters ?: 12f).coerceAtLeast(5f)
        val lM = (currentFloor?.lengthMeters ?: 16f).coerceAtLeast(5f)
        val uPos = userLocation?.position ?: Point2D(0.50f, 0.78f)
        val doors = mapObjects.filter { it.type == MapObjectType.DOOR || it.type == MapObjectType.MAIN_DOOR }.map { it.position }
        val exits = mapObjects.filter { it.type == MapObjectType.EXIT || it.type == MapObjectType.MAIN_DOOR }.map { it.position }
        val boundaryPoly = rooms.firstOrNull { it.type == "MAIN_BOUNDARY" && it.polygon.size >= 3 }?.polygon ?: emptyList()
        val primaryNode = nodes.firstOrNull { !it.isDemoSensor } ?: nodes.firstOrNull()
        val presence = DeviceMovementTracker.evaluatePresenceZone(
            userPos = uPos,
            housePolygon = boundaryPoly,
            houseWidthMeters = wM,
            houseLengthMeters = lM,
            gpsDistanceFromHouseCenterMeters = liveGpsDistFromAnchorM,
            gpsAccuracyMeters = liveGpsAccuracyMeters,
            hasRealGpsFix = liveHasRealGpsFix,
            rssiDbm = primaryNode?.telemetry?.rssiDbm,
            rssiDistanceMeters = primaryNode?.telemetry?.estimateDistanceFromRssiMeters(),
            allRealNodesOffline = nodes.isNotEmpty() && nodes.none { it.isDemoSensor } &&
                nodes.all { it.connectionState == com.example.domain.model.DeviceConnectionState.OFFLINE }
        )

        val (targetPos, targetName, targetType) = when (req.targetType) {
            MapNavigationTargetType.NEAREST_EXIT -> {
                val exitPt = exits.minByOrNull { hypot((it.x - uPos.x).toDouble(), (it.y - uPos.y).toDouble()) }
                    ?: Point2D(0.50f, 0.90f)
                Triple(exitPt, "Cửa Thoát Hiểm (Exit)", "EXIT")
            }
            MapNavigationTargetType.NEAREST_EXTINGUISHER -> {
                val ext = extinguishers.minByOrNull { hypot((it.position.x - uPos.x).toDouble(), (it.position.y - uPos.y).toDouble()) }
                Triple(ext?.position ?: Point2D(0.35f, 0.65f), ext?.name ?: "Bình Chữa Cháy Gần Nhất", "EXTINGUISHER")
            }
            MapNavigationTargetType.SPECIFIC_FIRE_SENSOR -> {
                val n = nodes.find { it.deviceId == req.targetDeviceId } ?: nodes.firstOrNull()
                Triple(
                    n?.let { Point2D(it.xNormalized, it.yNormalized) } ?: Point2D(0.45f, 0.35f),
                    n?.displayName ?: "Báo Cháy",
                    "FIRE_SENSOR"
                )
            }
            MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE -> {
                val ext = extinguishers.minByOrNull { hypot((it.position.x - uPos.x).toDouble(), (it.position.y - uPos.y).toDouble()) }
                val fireN = nodes.filter { it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM }.firstOrNull() ?: nodes.firstOrNull()
                Triple(
                    fireN?.let { Point2D(it.xNormalized, it.yNormalized) } ?: Point2D(0.45f, 0.35f),
                    "Bình CC ➔ ${fireN?.displayName ?: "Đám Cháy"}",
                    "TWO_STAGE"
                )
            }
            MapNavigationTargetType.NEAREST_FIRE_SENSOR -> {
                val fireN = nodes.filter { it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM }
                    .minByOrNull { hypot((it.xNormalized - uPos.x).toDouble(), (it.yNormalized - uPos.y).toDouble()) }
                    ?: nodes.firstOrNull()
                Triple(
                    fireN?.let { Point2D(it.xNormalized, it.yNormalized) } ?: Point2D(0.45f, 0.35f),
                    fireN?.displayName ?: "Báo Cháy Gần Nhất",
                    "FIRE_SENSOR"
                )
            }
        }

        val waypoints = mutableListOf(uPos)
        if (req.targetType == MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE) {
            extinguishers.minByOrNull { hypot((it.position.x - uPos.x).toDouble(), (it.position.y - uPos.y).toDouble()) }
                ?.let { waypoints.add(it.position) }
        } else if (doors.isNotEmpty()) {
            val midDoor = doors.minByOrNull { d ->
                hypot((d.x - uPos.x).toDouble(), (d.y - uPos.y).toDouble()) +
                    hypot((targetPos.x - d.x).toDouble(), (targetPos.y - d.y).toDouble())
            }
            if (midDoor != null &&
                hypot((midDoor.x - uPos.x).toDouble(), (midDoor.y - uPos.y).toDouble()) > 0.05 &&
                hypot((midDoor.x - targetPos.x).toDouble(), (midDoor.y - targetPos.y).toDouble()) > 0.05
            ) {
                waypoints.add(midDoor)
            }
        }
        waypoints.add(targetPos)

        var totalM = 0f
        for (i in 0 until waypoints.size - 1) {
            totalM += hypot(((waypoints[i + 1].x - waypoints[i].x) * wM).toDouble(), ((waypoints[i + 1].y - waypoints[i].y) * lM).toDouble()).toFloat()
        }
        val route = NavigationRoute(
            pathPoints = waypoints,
            totalDistanceMeters = totalM.coerceAtLeast(0.5f),
            targetName = targetName,
            targetType = targetType,
            crossesHazardRoom = false
        )
        return TurnByTurnNavigator.buildGuidance(
            route = route,
            userHeadingDeg = liveCompassHeading,
            houseWidthMeters = wM,
            houseLengthMeters = lM,
            isOutsideHouse = presence.zone == PresenceZone.OUTSIDE_HOUSE,
            outdoorOverrideDistanceMeters = if (presence.zone == PresenceZone.OUTSIDE_HOUSE) presence.distanceToHouseCenterMeters else null
        )
    }

    fun startNavigationWithLocalSilence(req: MapNavigationRequest, openMapDirectly: Boolean = false) {
        // Always temporarily silence notifications & alarm on THIS phone first!
        repository.muteLocalNotificationsTemporarily()
        activeGuidanceRequest = req
        repository.requestMapNavigation(req)
        if (openMapDirectly) {
            selectedDeviceId = null
            mapInSetupMode = false
            currentTab = StreamlinedTab.MAP_2D
        }
    }

    // Track active fire alarms and compute nearest alarming sensor to user's standing position
    val alarmNodes = nodes.filter {
        it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM ||
            it.telemetry.calculateFireProbability(gasThresholdAdc).totalProbabilityPercent >= 75f
    }
    val userPos = userLocation?.position ?: Point2D(0.50f, 0.78f)
    val nearestAlarmNode = alarmNodes.minByOrNull { n ->
        hypot((n.xNormalized - userPos.x).toDouble(), (n.yNormalized - userPos.y).toDouble())
    } ?: alarmNodes.firstOrNull()

    val nearestAlarmDistMeters = nearestAlarmNode?.let { n ->
        val wM = (currentFloor?.widthMeters ?: 12f).coerceAtLeast(5f)
        val lM = (currentFloor?.lengthMeters ?: 16f).coerceAtLeast(5f)
        val dx = (n.xNormalized - userPos.x) * wM
        val dy = (n.yNormalized - userPos.y) * lM
        hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.5f)
    } ?: 3.5f

    // Full-screen flashing & beeping emergency popup stays active while fire alarm is active,
    // until the user presses "Dẫn tới Bình Chữa Cháy", "Dẫn tới Báo Cháy", or "Tắt Báo Cháy"
    var popupDismissedForCurrentAlarm by remember { mutableStateOf(false) }
    LaunchedEffect(alarmNodes.size, autoRouteOnAlarm) {
        if (alarmNodes.isEmpty()) {
            popupDismissedForCurrentAlarm = false
        } else if (autoRouteOnAlarm) {
            // Auto-activate 2-stage route (Extinguisher -> Fire) when autoRouteOnAlarm is enabled
            repository.requestMapNavigation(
                MapNavigationRequest(targetType = MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE)
            )
        }
    }

    BackHandler(enabled = selectedDeviceId != null) {
        selectedDeviceId = null
    }

    Scaffold(
        bottomBar = {
            if (selectedDeviceId == null) {
                NavigationBar(
                    containerColor = LightSurface,
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == StreamlinedTab.DASHBOARD,
                        onClick = { currentTab = StreamlinedTab.DASHBOARD },
                        icon = {
                            Icon(
                                if (currentTab == StreamlinedTab.DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                                contentDescription = "Tổng quan"
                            )
                        },
                        label = {
                            Text(
                                "Tổng quan",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == StreamlinedTab.DASHBOARD) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryBlueLight
                        ),
                        modifier = Modifier.testTag("tab_dashboard")
                    )

                    NavigationBarItem(
                        selected = currentTab == StreamlinedTab.MAP_2D,
                        onClick = {
                            currentTab = StreamlinedTab.MAP_2D
                            mapInSetupMode = false
                        },
                        icon = {
                            Icon(
                                if (currentTab == StreamlinedTab.MAP_2D) Icons.Filled.Map else Icons.Outlined.Map,
                                contentDescription = "Bản đồ 2D"
                            )
                        },
                        label = {
                            Text(
                                "Bản đồ 2D",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == StreamlinedTab.MAP_2D) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryBlueLight
                        ),
                        modifier = Modifier.testTag("tab_map_2d")
                    )

                    NavigationBarItem(
                        selected = currentTab == StreamlinedTab.SETTINGS,
                        onClick = { currentTab = StreamlinedTab.SETTINGS },
                        icon = {
                            Icon(
                                if (currentTab == StreamlinedTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Cài đặt & Nhật ký"
                            )
                        },
                        label = {
                            Text(
                                "Cài đặt & Nhật ký",
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == StreamlinedTab.SETTINGS) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryBlueLight
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        },
        containerColor = LightBackground
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedDeviceId != null) {
                DeviceDetailScreen(
                    deviceId = selectedDeviceId!!,
                    repository = repository,
                    rooms = rooms,
                    onBackClick = { selectedDeviceId = null },
                    onReconfigureBleClick = {
                        selectedDeviceId = null
                        showAddDeviceDialog = true
                    }
                )
            } else {
                when (currentTab) {
                    StreamlinedTab.DASHBOARD -> DashboardScreen(
                        nodes = nodes,
                        currentHome = currentHome,
                        isDemoMode = isDemoMode,
                        gasThresholdAdc = gasThresholdAdc,
                        snapshots = recentSnapshots,
                        insights = aiInsights,
                        loggedInEmail = loggedInEmail,
                        loggedInRole = loggedInRole,
                        onNodeClick = { id -> selectedDeviceId = id },
                        onAddDeviceClick = { showAddDeviceDialog = true },
                        onAddQuickDemoDeviceClick = {
                            scope.launch {
                                val nextIdx = nodes.size + 1
                                repository.registerDevice(
                                    deviceId = "smartfire-demo-$nextIdx-${(100..999).random()}",
                                    displayName = "Báo cháy Lab #$nextIdx [DEMO]",
                                    homeId = "home_main",
                                    floorId = "floor_1",
                                    roomId = "room_main",
                                    ipAddress = "192.168.1.${110 + nextIdx}",
                                    token = SmartFireRepository.DEFAULT_TOKEN,
                                    xNormalized = (0.20f + (nextIdx % 4) * 0.18f).coerceIn(0.15f, 0.85f),
                                    yNormalized = (0.25f + (nextIdx % 3) * 0.22f).coerceIn(0.15f, 0.85f),
                                    isDemo = true
                                )
                            }
                        },
                        onDeleteDeviceClick = { id ->
                            scope.launch { repository.removeDevice(id) }
                        },
                        onRouteToNodeOnMapClick = { id ->
                            popupDismissedForCurrentAlarm = true
                            startNavigationWithLocalSilence(
                                MapNavigationRequest(
                                    targetType = MapNavigationTargetType.SPECIFIC_FIRE_SENSOR,
                                    targetDeviceId = id
                                ),
                                openMapDirectly = false
                            )
                        },
                        onOpenMapClick = {
                            currentTab = StreamlinedTab.MAP_2D
                            mapInSetupMode = false
                        },
                        onOpenSettingsClick = { currentTab = StreamlinedTab.SETTINGS },
                        onLogoutClick = { repository.logoutSimulated() },
                        onSilenceClick = { id ->
                            scope.launch { repository.sendCommand(id, "silence") }
                        },
                        onAckClick = { id ->
                            scope.launch { repository.sendCommand(id, "ack") }
                        },
                        onTestClick = { id ->
                            popupDismissedForCurrentAlarm = false
                            scope.launch { repository.sendCommand(id, "test") }
                        },
                        onAdjustMultiSensorParams = { id, tempC, ror, gas, co, hum, ir ->
                            popupDismissedForCurrentAlarm = false
                            scope.launch {
                                repository.simulateMultiSensorParameters(id, tempC, ror, gas, co, hum, ir)
                            }
                        }
                    )

                    StreamlinedTab.MAP_2D -> IndoorMap2DScreen(
                        floors = floors,
                        rooms = rooms,
                        nodes = nodes,
                        extinguishers = extinguishers,
                        mapObjects = mapObjects,
                        mapEdges = mapEdges,
                        userLocation = userLocation,
                        gasThresholdAdc = gasThresholdAdc,
                        stepStrideMeters = stepStrideMeters,
                        autoRouteOnAlarm = autoRouteOnAlarm,
                        initialInSetupMode = mapInSetupMode,
                        navigationRequest = mapNavigationRequest,
                        onConsumeNavigationRequest = {
                            repository.requestMapNavigation(null)
                        },
                        onSaveScannedSetup = { sensorPts, doors, stairs, exts, exits, mainDoors ->
                            scope.launch {
                                val flId = currentFloor?.id ?: "floor_1"
                                repository.clearObjectsOnlyForFloor(flId)
                                if ((currentFloor?.widthMeters ?: 0f) <= 0f) {
                                    repository.updateFloorDimensions(flId, 12f, 16f)
                                }
                                sensorPts.forEachIndexed { i, pt ->
                                    nodes.getOrNull(i)?.let { n ->
                                        repository.updateDeviceMapPosition(n.deviceId, pt.x, pt.y)
                                    }
                                }
                                mainDoors.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cửa Chính #${i + 1}", com.example.domain.model.MapObjectType.MAIN_DOOR, pt.x, pt.y)
                                }
                                doors.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cửa #${i + 1}", com.example.domain.model.MapObjectType.DOOR, pt.x, pt.y)
                                }
                                stairs.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cầu thang ${i + 1}", com.example.domain.model.MapObjectType.STAIRS, pt.x, pt.y)
                                }
                                exts.forEachIndexed { i, pt ->
                                    repository.addExtinguisher(flId, "room_main", "Bình chữa cháy ${i + 1}", ExtinguisherType.POWDER_ABC, pt.x, pt.y)
                                }
                                exits.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cửa Exit ${i + 1}", com.example.domain.model.MapObjectType.EXIT, pt.x, pt.y)
                                }
                                mapInSetupMode = false
                            }
                        },
                        onSaveBoundaryPolygon = { flId, poly, widthM, lengthM ->
                            scope.launch {
                                repository.saveBoundaryPolygon(flId, poly, widthM, lengthM)
                            }
                        },
                        onBindSensorPosition = { deviceId, x, y ->
                            scope.launch {
                                repository.updateDeviceMapPosition(deviceId, x, y)
                            }
                        },
                        onDeleteExtinguisher = { extId ->
                            scope.launch {
                                repository.deleteExtinguisher(extId)
                            }
                        },
                        onUpdateExtinguisherPosition = { extId, x, y ->
                            scope.launch {
                                repository.updateExtinguisherPosition(extId, x, y)
                            }
                        },
                        onDeleteMapObject = { objId ->
                            scope.launch {
                                repository.deleteMapObject(objId)
                            }
                        },
                        onUpdateMapObjectPosition = { objId, x, y ->
                            scope.launch {
                                repository.updateMapObjectPosition(objId, x, y)
                            }
                        },
                        onUpdateFloorDimensions = { flId, widthM, lengthM ->
                            scope.launch {
                                repository.updateFloorDimensions(flId, widthM, lengthM)
                            }
                        },
                        onClearAllMapConfig = { flId ->
                            scope.launch {
                                repository.clearMapConfiguration(flId)
                            }
                        },
                        onSetUserPosition = { flId, x, y ->
                            scope.launch {
                                repository.setUserLocation(currentHome?.id ?: "home_main", flId, null, x, y, "MANUAL_TAP")
                            }
                        },
                        onMuteLocalNotifications = {
                            repository.muteLocalNotificationsTemporarily()
                        },
                        sharedMovementTracker = sharedMovementTracker,
                        isLocalAlarmMuted = isLocalAlarmMuted,
                        onBackToDashboardWithNav = {
                            currentTab = StreamlinedTab.DASHBOARD
                        },
                        onStopGlobalNavigation = {
                            activeGuidanceRequest = null
                            repository.requestMapNavigation(null)
                        }
                    )

                    StreamlinedTab.SETTINGS -> SettingsScreen(
                        repository = repository,
                        isDemoMode = isDemoMode,
                        currentHome = currentHome,
                        nodes = nodes,
                        events = events,
                        onTriggerScanMap = {
                            mapInSetupMode = true
                            currentTab = StreamlinedTab.MAP_2D
                        }
                    )
                }
            }

            // Quick Add Device Dialog
            if (showAddDeviceDialog) {
                AddDeviceWizardDialog(
                    repository = repository,
                    onDismiss = { showAddDeviceDialog = false },
                    onSuccess = { showAddDeviceDialog = false }
                )
            }

            // Floating Google Maps-style Turn-by-Turn Navigation Popup Banner (shown on Dashboard/Settings; user can choose to open 2D Map)
            if (activeGuidanceRequest != null && currentTab != StreamlinedTab.MAP_2D) {
                val overlayGuidance = buildOverlayGuidance(activeGuidanceRequest!!)
                TurnByTurnNavigationBanner(
                    guidance = overlayGuidance,
                    isMapCurrentlyVisible = false,
                    isLocalAlarmMuted = isLocalAlarmMuted,
                    onToggleMapView = {
                        selectedDeviceId = null
                        mapInSetupMode = false
                        activeGuidanceRequest?.let { repository.requestMapNavigation(it) }
                        currentTab = StreamlinedTab.MAP_2D
                    },
                    onStopNavigation = {
                        activeGuidanceRequest = null
                        repository.requestMapNavigation(null)
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            // Full-Screen Flashing & Beeping Emergency Popup when Fire Alarm triggers
            if (alarmNodes.isNotEmpty() && nearestAlarmNode != null && !popupDismissedForCurrentAlarm) {
                FireEmergencyPopupDialog(
                    nearestAlarmNode = nearestAlarmNode,
                    totalAlarmCount = alarmNodes.size,
                    distanceMeters = nearestAlarmDistMeters,
                    soundEnabled = phoneAlarmSoundEnabled && !isLocalAlarmMuted,
                    onRouteToExtinguisher = {
                        popupDismissedForCurrentAlarm = true
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_EXTINGUISHER),
                            openMapDirectly = false
                        )
                    },
                    onRouteToNearestFireSensor = {
                        popupDismissedForCurrentAlarm = true
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_FIRE_SENSOR),
                            openMapDirectly = false
                        )
                    },
                    onRouteTwoStageExtinguisherToFire = {
                        popupDismissedForCurrentAlarm = true
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE),
                            openMapDirectly = false
                        )
                    },
                    onRouteToExit = {
                        popupDismissedForCurrentAlarm = true
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_EXIT),
                            openMapDirectly = false
                        )
                    },
                    onSilenceAllAlarms = {
                        popupDismissedForCurrentAlarm = true
                        repository.muteLocalNotificationsTemporarily()
                        scope.launch {
                            alarmNodes.forEach { n ->
                                repository.sendCommand(n.deviceId, "silence")
                            }
                        }
                    }
                )
            }
        }
    }
}
