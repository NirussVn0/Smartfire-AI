package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.SmartFireApplication
import com.example.domain.model.ExtinguisherType
import com.example.domain.model.Point2D
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.devices.AddDeviceWizardDialog
import com.example.ui.screens.devices.DeviceDetailScreen
import com.example.ui.screens.map.IndoorMap2DScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class StreamlinedTab {
    DASHBOARD, // Tổng quan: Xem toàn bộ cảm biến, add cảm biến, điều khiển còi/tắt báo cháy, biểu đồ
    MAP_2D,    // Bản đồ 2D: Quét khu vực bước đi & xem mặt bằng 2D siêu mượt
    SETTINGS   // Cài đặt: Nhật ký sự kiện & Setting hệ thống
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val repository = SmartFireApplication.instance.repository
    val scope = rememberCoroutineScope()

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
                        onNodeClick = { id -> selectedDeviceId = id },
                        onAddDeviceClick = { showAddDeviceDialog = true },
                        onOpenMapClick = {
                            currentTab = StreamlinedTab.MAP_2D
                            mapInSetupMode = false
                        },
                        onOpenSettingsClick = { currentTab = StreamlinedTab.SETTINGS },
                        onSilenceClick = { id ->
                            scope.launch { repository.sendCommand(id, "silence") }
                        },
                        onAckClick = { id ->
                            scope.launch { repository.sendCommand(id, "ack") }
                        },
                        onTestClick = { id ->
                            scope.launch { repository.sendCommand(id, "test") }
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
                        initialInSetupMode = mapInSetupMode,
                        onSaveScannedSetup = { sensors, doors, stairs, exts, exits ->
                            scope.launch {
                                val flId = currentFloor?.id ?: "floor_1"
                                // Save doors
                                doors.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cửa ${i + 1}", com.example.domain.model.MapObjectType.DOOR, pt.x, pt.y)
                                }
                                // Save stairs
                                stairs.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Cầu thang ${i + 1}", com.example.domain.model.MapObjectType.STAIRS, pt.x, pt.y)
                                }
                                // Save extinguishers
                                exts.forEachIndexed { i, pt ->
                                    repository.addExtinguisher(flId, "room_main", "Bình chữa cháy ${i + 1}", ExtinguisherType.POWDER_ABC, pt.x, pt.y)
                                }
                                // Save exits
                                exits.forEachIndexed { i, pt ->
                                    repository.addMapObject(flId, "Lối thoát hiểm ${i + 1}", com.example.domain.model.MapObjectType.EXIT, pt.x, pt.y)
                                }
                                mapInSetupMode = false
                            }
                        },
                        onSetUserPosition = { flId, x, y ->
                            scope.launch {
                                repository.setUserLocation(currentHome?.id ?: "home_main", flId, null, x, y, "MANUAL_TAP")
                            }
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
        }
    }
}
