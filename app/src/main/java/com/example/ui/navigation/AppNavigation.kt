package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.hypot
import kotlin.math.roundToInt
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
    val savedEspConfig by repository.savedEspConfig.collectAsState()
    val alertMode by repository.alertMode.collectAsState()
    val alarmSoundType by repository.alarmSoundType.collectAsState()
    val alarmVolumePercent by repository.alarmVolumePercent.collectAsState()
    val level1PopupEnabled by repository.level1PopupEnabled.collectAsState()
    val localAlarmMutedUntilMs by repository.localAlarmMutedUntilMs.collectAsState()
    val mutedUntilByDevice by repository.mutedUntilByDevice.collectAsState()
    val dismissedByDevice by repository.dismissedAlarmEpisodeByDevice.collectAsState()
    val offlineFireLatched by repository.offlineFireLatchedDevices.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mutedUntilByDevice, localAlarmMutedUntilMs) {
        val current = System.currentTimeMillis()
        nowMs = current
        val nextExpiry = (mutedUntilByDevice.values + localAlarmMutedUntilMs)
            .filter { it > current }
            .minOrNull()
        if (nextExpiry != null) {
            delay((nextExpiry - current + 50L).coerceAtLeast(50L))
            nowMs = System.currentTimeMillis()
        }
    }
    val isAnyLocalMuteActive = mutedUntilByDevice.values.any { it > nowMs } || nowMs < localAlarmMutedUntilMs

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
                val fireN = nodes.filter { it.isEffectivelyAlarming(savedEspConfig) }.firstOrNull() ?: nodes.firstOrNull()
                Triple(
                    fireN?.let { Point2D(it.xNormalized, it.yNormalized) } ?: Point2D(0.45f, 0.35f),
                    "Bình CC ➔ ${fireN?.displayName ?: "Đám Cháy"}",
                    "TWO_STAGE"
                )
            }
            MapNavigationTargetType.NEAREST_FIRE_SENSOR -> {
                val fireN = nodes.filter { it.isEffectivelyAlarming(savedEspConfig) }
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
        // Silence only the relevant device(s) on THIS phone without muting unrelated rooms
        val targetId = req.targetDeviceId
        if (!targetId.isNullOrBlank()) {
            repository.muteDeviceLocally(targetId, 180_000L)
        } else {
            repository.muteLocalNotificationsTemporarily(180_000L)
        }
        com.example.service.audio.AlarmAudioPlayer.stopManagedAlert()
        activeGuidanceRequest = req
        repository.requestMapNavigation(req)
        if (openMapDirectly) {
            selectedDeviceId = null
            mapInSetupMode = false
            currentTab = StreamlinedTab.MAP_2D
        }
    }

    // Track active fire alarms (Level 2) and early warnings (Level 1) independently per device
    val alarmNodes = nodes.filter { it.isEffectivelyAlarming(savedEspConfig) }
    val warningLevel1Nodes = nodes.filter { it.isEffectivelyWarningLevel1(savedEspConfig) && it !in alarmNodes }

    val activePopupAlarmNodes = alarmNodes.filter { n ->
        !n.suspectedBurnedOffline &&
            n.deviceId !in offlineFireLatched &&
            dismissedByDevice[n.deviceId] != n.alarmEpisodeId
    }
    val shouldPlayAlarmSound = phoneAlarmSoundEnabled && activePopupAlarmNodes.any {
        (mutedUntilByDevice[it.deviceId] ?: 0L) <= nowMs
    }

    val userPos = userLocation?.position ?: Point2D(0.50f, 0.78f)
    val primaryPopupNode = activePopupAlarmNodes.minByOrNull { n ->
        hypot((n.xNormalized - userPos.x).toDouble(), (n.yNormalized - userPos.y).toDouble())
    } ?: activePopupAlarmNodes.firstOrNull()

    val primaryPopupDistMeters = primaryPopupNode?.let { n ->
        val wM = (currentFloor?.widthMeters ?: 12f).coerceAtLeast(5f)
        val lM = (currentFloor?.lengthMeters ?: 16f).coerceAtLeast(5f)
        val dx = (n.xNormalized - userPos.x) * wM
        val dy = (n.yNormalized - userPos.y) * lM
        hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.5f)
    } ?: 3.5f

    val notificationHelper = remember { com.example.service.notification.SmartFireNotificationManager(context.applicationContext) }
    var dismissedWarningByDevice by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastAlertedPopupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastAlertedWarningIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(activePopupAlarmNodes, autoRouteOnAlarm, mutedUntilByDevice, alertMode) {
        val currentKeys = activePopupAlarmNodes.map { "${it.deviceId}:${it.alarmEpisodeId}" }.toSet()
        val newNodes = activePopupAlarmNodes.filter { "${it.deviceId}:${it.alarmEpisodeId}" !in lastAlertedPopupKeys }
        lastAlertedPopupKeys = currentKeys
        if (newNodes.isNotEmpty()) {
            val unmutedNew = newNodes.firstOrNull { (mutedUntilByDevice[it.deviceId] ?: 0L) <= System.currentTimeMillis() }
            if (unmutedNew != null) {
                notificationHelper.showFireAlarmNotification(
                    deviceName = unmutedNew.displayName,
                    roomName = unmutedNew.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                    gasRaw = unmutedNew.telemetry.gasRaw,
                    hasFlame = unmutedNew.telemetry.flame,
                    alertMode = alertMode,
                    deviceId = unmutedNew.deviceId,
                    temperatureCelsius = unmutedNew.telemetry.temperatureCelsius
                )
            }
            if (autoRouteOnAlarm) {
                repository.requestMapNavigation(
                    MapNavigationRequest(targetType = MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE)
                )
            }
        }
    }

    LaunchedEffect(warningLevel1Nodes, activePopupAlarmNodes.size, level1PopupEnabled, mutedUntilByDevice, alertMode) {
        val activeWarnIds = warningLevel1Nodes.map { it.deviceId }.toSet()
        dismissedWarningByDevice = dismissedWarningByDevice intersect activeWarnIds
        if (activePopupAlarmNodes.isEmpty() && level1PopupEnabled) {
            val newWarn = warningLevel1Nodes.firstOrNull {
                it.deviceId !in lastAlertedWarningIds && (mutedUntilByDevice[it.deviceId] ?: 0L) <= System.currentTimeMillis()
            }
            if (newWarn != null) {
                notificationHelper.showLevel1WarningNotification(
                    deviceName = newWarn.displayName,
                    roomName = newWarn.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                    detail = "Gas=${newWarn.telemetry.gasRaw} ADC · Nhiệt=${newWarn.telemetry.temperatureCelsius?.let { String.format(java.util.Locale.US, "%.1f°C", it) } ?: "N/A"}",
                    alertMode = alertMode,
                    deviceId = newWarn.deviceId
                )
            }
        }
        lastAlertedWarningIds = activeWarnIds
    }

    val activeWarningNodes = warningLevel1Nodes.filter { it.deviceId !in dismissedWarningByDevice }

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
                            nodes.find { it.deviceId == id }?.let { n ->
                                repository.dismissAlarmPopupForDevice(id, n.alarmEpisodeId)
                            }
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
                            val nodeEpisode = nodes.find { it.deviceId == id }?.alarmEpisodeId
                                ?: repository.getAlarmEpisodeId(id)
                            repository.muteDeviceLocally(id, 30_000L)
                            repository.dismissAlarmPopupForDevice(id, nodeEpisode)
                            dismissedWarningByDevice = dismissedWarningByDevice + id
                            scope.launch { repository.sendCommand(id, "silence") }
                        },
                        onDisarmClick = { id ->
                            val nodeEpisode = nodes.find { it.deviceId == id }?.alarmEpisodeId
                                ?: repository.getAlarmEpisodeId(id)
                            repository.dismissAlarmPopupForDevice(id, nodeEpisode)
                            dismissedWarningByDevice = dismissedWarningByDevice + id
                            scope.launch { repository.sendCommand(id, "disarm") }
                        },
                        onArmClick = { id ->
                            scope.launch { repository.sendCommand(id, "arm") }
                        },
                        onAckClick = { id ->
                            scope.launch { repository.sendCommand(id, "ack") }
                        },
                        onTestClick = { id ->
                            repository.unmuteDeviceLocally(id)
                            scope.launch { repository.sendCommand(id, "test") }
                        },
                        onAdjustMultiSensorParams = { id, tempC, ror, gas, co, hum, ir ->
                            scope.launch {
                                repository.simulateMultiSensorParameters(id, tempC, ror, gas, co, hum, ir)
                            }
                        },
                        savedEspConfig = savedEspConfig,
                        onResetOfflineBurnedClick = { id ->
                            scope.launch { repository.clearOfflineBurnedAlarm(id) }
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
                        isLocalAlarmMuted = isAnyLocalMuteActive,
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
                    isLocalAlarmMuted = mutedUntilByDevice.values.any { it > nowMs } || nowMs < localAlarmMutedUntilMs,
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

            // Level 1 Early Warning Popup (Mức 1 Cảnh Báo Sớm)
            if (activeWarningNodes.isNotEmpty() && activePopupAlarmNodes.isEmpty() && level1PopupEnabled) {
                val warnNode = activeWarningNodes.first()
                val warnMuted = (mutedUntilByDevice[warnNode.deviceId] ?: 0L) > nowMs
                val warnFlamePct = if (warnNode.telemetry.flame) {
                    100
                } else {
                    ((warnNode.telemetry.irFlameIntensity ?: 0f) * 100f).roundToInt().coerceIn(0, 100)
                }
                LaunchedEffect(warnNode.deviceId, phoneAlarmSoundEnabled, warnMuted, alertMode, alarmSoundType, alarmVolumePercent) {
                    if (phoneAlarmSoundEnabled && !warnMuted && alertMode != com.example.domain.model.AlertMode.SILENT) {
                        com.example.service.audio.AlarmAudioPlayer.runAlertLoop(
                            context = context,
                            alertMode = alertMode,
                            soundType = alarmSoundType,
                            volumePercent = alarmVolumePercent,
                            isLevel1Warning = true
                        )
                    }
                }
                Dialog(
                    onDismissRequest = {
                        dismissedWarningByDevice = dismissedWarningByDevice + warnNode.deviceId
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .testTag("level1_warning_popup"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.5.dp, Color(0xFFD97706), RoundedCornerShape(22.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEF3C7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "MỨC 1 · CẢNH BÁO SỚM",
                                        color = Color(0xFFB45309),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = warnNode.displayName,
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // 3 Compact Metric Pills: Nhiệt | Khói | Lửa IR
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFFBEB))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Nhiệt Độ", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        text = warnNode.telemetry.temperatureCelsius?.let {
                                            String.format(java.util.Locale.US, "%.1f°C", it)
                                        } ?: "N/A",
                                        color = Color(0xFFB45309),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Khói MQ-2", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        text = "${warnNode.telemetry.gasRaw} ADC",
                                        color = Color(0xFFB45309),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Lửa IR", color = TextSecondary, fontSize = 10.sp)
                                    Text(
                                        text = if (warnNode.telemetry.flame) "CÓ" else "$warnFlamePct%",
                                        color = Color(0xFFB45309),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        dismissedWarningByDevice = dismissedWarningByDevice + warnNode.deviceId
                                        repository.muteDeviceLocally(warnNode.deviceId, 30_000L)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.NotificationsOff,
                                        contentDescription = null,
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Tạm Tắt",
                                        color = Color(0xFFB45309),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Button(
                                    onClick = {
                                        dismissedWarningByDevice = dismissedWarningByDevice + warnNode.deviceId
                                        startNavigationWithLocalSilence(
                                            MapNavigationRequest(
                                                targetType = MapNavigationTargetType.SPECIFIC_FIRE_SENSOR,
                                                targetDeviceId = warnNode.deviceId
                                            ),
                                            openMapDirectly = false
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(
                                        "Kiểm Tra Ngay",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Full-Screen Flashing & Beeping Emergency Popup when Fire Alarm (Level 2) triggers
            if (activePopupAlarmNodes.isNotEmpty() && primaryPopupNode != null) {
                val dismissAllActivePopups = {
                    activePopupAlarmNodes.forEach { n ->
                        repository.dismissAlarmPopupForDevice(n.deviceId, n.alarmEpisodeId)
                    }
                }
                FireEmergencyPopupDialog(
                    nearestAlarmNode = primaryPopupNode,
                    totalAlarmCount = activePopupAlarmNodes.size,
                    distanceMeters = primaryPopupDistMeters,
                    soundEnabled = shouldPlayAlarmSound,
                    alertMode = alertMode,
                    soundType = alarmSoundType,
                    volumePercent = alarmVolumePercent,
                    onRouteToExtinguisher = {
                        dismissAllActivePopups()
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_EXTINGUISHER),
                            openMapDirectly = false
                        )
                    },
                    onRouteToNearestFireSensor = {
                        dismissAllActivePopups()
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_FIRE_SENSOR),
                            openMapDirectly = false
                        )
                    },
                    onRouteTwoStageExtinguisherToFire = {
                        dismissAllActivePopups()
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.TWO_STAGE_EXTINGUISHER_TO_FIRE),
                            openMapDirectly = false
                        )
                    },
                    onRouteToExit = {
                        dismissAllActivePopups()
                        startNavigationWithLocalSilence(
                            MapNavigationRequest(targetType = MapNavigationTargetType.NEAREST_EXIT),
                            openMapDirectly = false
                        )
                    },
                    onSilenceAllAlarms = {
                        com.example.service.audio.AlarmAudioPlayer.stopManagedAlert()
                        activePopupAlarmNodes.forEach { n ->
                            repository.muteDeviceLocally(n.deviceId, 30_000L)
                            repository.dismissAlarmPopupForDevice(n.deviceId, n.alarmEpisodeId)
                            notificationHelper.cancelFireAlarmNotification(n.deviceId)
                        }
                        scope.launch {
                            activePopupAlarmNodes.forEach { n ->
                                repository.sendCommand(n.deviceId, "silence")
                            }
                        }
                    },
                    onDisarmAllAlarms = {
                        com.example.service.audio.AlarmAudioPlayer.stopManagedAlert()
                        dismissAllActivePopups()
                        activePopupAlarmNodes.forEach { n ->
                            notificationHelper.cancelFireAlarmNotification(n.deviceId)
                        }
                        dismissedWarningByDevice = dismissedWarningByDevice + activePopupAlarmNodes.map { it.deviceId }
                        scope.launch {
                            activePopupAlarmNodes.forEach { n ->
                                repository.sendCommand(n.deviceId, "disarm")
                            }
                        }
                    },
                    allAlarmNodes = activePopupAlarmNodes,
                    isOfflineBurned = primaryPopupNode.suspectedBurnedOffline
                )
            }
        }
    }
}
