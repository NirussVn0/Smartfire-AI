package com.example.ui.screens.map

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FireExtinguisher
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ExtinguisherItem
import com.example.domain.model.ExtinguisherType
import com.example.domain.model.FirmwareState
import com.example.domain.model.FloorPlan
import com.example.domain.model.MapEdgeItem
import com.example.domain.model.MapNodeItem
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import com.example.domain.model.RoomSpace
import com.example.domain.model.SmartFireNode
import com.example.domain.model.UserPosition
import com.example.domain.pathfinding.NavigationRoute
import com.example.sensor.DeviceMovementTracker
import com.example.ui.theme.CriticalAlarmBorder
import com.example.ui.theme.CriticalAlarmRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightCard
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.NormalGreen
import com.example.ui.theme.NormalGreenBackground
import com.example.ui.theme.NormalGreenBorder
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.sin

enum class EmergencyAutoPhase {
    IDLE,
    FETCH_EXTINGUISHER_FIRST, // Báo cháy: Dẫn tới bình chữa cháy trước
    ATTACK_FIRE_SOURCE         // Sau khi có bình: Dẫn tới điểm đang cháy để dập lửa
}

/**
 * 2D Architectural Map Screen with:
 * - Highly visible, large architectural icons (Doors with swing arc & posts, Fire Extinguishers, Stairs, Sensors, Exits, Walking User).
 * - Persistent Top Navigation HUD always displaying the destination, distance, steps, and turn instructions.
 * - Auto Emergency Routing on Fire Alarm: Step 1 (Grab Extinguisher) -> Step 2 (Attack Fire).
 * - Real Phone Sensor Integration: Step Detector, Accelerometer, Compass & Location tracking.
 */
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
    onSaveScannedSetup: (
        List<Point2D>, // sensors
        List<Point2D>, // doors
        List<Point2D>, // stairs
        List<Point2D>, // extinguishers
        List<Point2D>  // exits
    ) -> Unit,
    onSetUserPosition: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentFloor = floors.firstOrNull()
    val floorId = currentFloor?.id ?: "floor_1"

    // Hardware Movement & Dead-Reckoning Sensor Tracker
    val movementTracker = remember { DeviceMovementTracker(context, scope) }
    DisposableEffect(Unit) {
        movementTracker.startTracking()
        onDispose { movementTracker.stopTracking() }
    }

    val stepCount by movementTracker.stepCount.collectAsState()
    val compassHeading by movementTracker.compassHeading.collectAsState()
    val isWalking by movementTracker.isWalking.collectAsState()
    val gpsLocation by movementTracker.lastGpsLocation.collectAsState()

    // 0: Xem bản đồ 2D, 1: Thiết lập quét khu vực
    var activeModeTab by remember { mutableIntStateOf(if (initialInSetupMode) 1 else 0) }
    var scanStep by remember { mutableIntStateOf(1) } // 1: Sensor, 2: Door, 3: Stairs, 4: Extinguisher, 5: Exit

    // Setup Points (pre-loaded with accurate initial positions)
    val tempSensors = remember {
        mutableStateListOf<Point2D>().apply {
            if (nodes.isNotEmpty()) {
                nodes.forEach { n -> add(Point2D(n.xNormalized, n.yNormalized)) }
            } else {
                add(Point2D(0.28f, 0.26f))
            }
        }
    }
    val tempDoors = remember {
        mutableStateListOf<Point2D>().apply {
            val existing = mapObjects.filter { it.type == MapObjectType.DOOR }.map { it.position }
            if (existing.isNotEmpty()) {
                addAll(existing)
            } else {
                addAll(listOf(Point2D(0.48f, 0.26f), Point2D(0.28f, 0.48f), Point2D(0.72f, 0.48f)))
            }
        }
    }
    val tempStairs = remember {
        mutableStateListOf<Point2D>().apply {
            val existing = mapObjects.filter { it.type == MapObjectType.STAIRS }.map { it.position }
            if (existing.isNotEmpty()) addAll(existing) else add(Point2D(0.85f, 0.26f))
        }
    }
    val tempExtinguishers = remember {
        mutableStateListOf<Point2D>().apply {
            if (extinguishers.isNotEmpty()) {
                addAll(extinguishers.map { it.position })
            } else {
                addAll(listOf(Point2D(0.25f, 0.72f), Point2D(0.75f, 0.72f)))
            }
        }
    }
    val tempExits = remember {
        mutableStateListOf<Point2D>().apply {
            val existing = mapObjects.filter { it.type == MapObjectType.EXIT }.map { it.position }
            if (existing.isNotEmpty()) addAll(existing) else addAll(listOf(Point2D(0.50f, 0.88f)))
        }
    }

    // Dynamic User Position (advances via phone steps)
    var currentUserPos by remember {
        mutableStateOf(userLocation?.position ?: Point2D(0.28f, 0.26f))
    }

    // Map Transform (Pan & Zoom)
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Active Navigation Route
    var activeRoute by remember { mutableStateOf<NavigationRoute?>(null) }
    var emergencyPhase by remember { mutableStateOf(EmergencyAutoPhase.IDLE) }

    // Pulsing Animation for Fire Alarm Beacon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 44f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Restart),
        label = "alarm_pulse"
    )

    // Check for Fire Alarm Nodes
    val hazardNodes = nodes.filter { it.lastKnownAlarm || it.firmwareState == FirmwareState.ALARM }
    val isFireActive = hazardNodes.isNotEmpty()

    // AUTOMATIC EMERGENCY ROUTING LOGIC:
    // When fire alarm triggers, automatically route: User -> Nearest Extinguisher (Phase 1),
    // then to the fire location (Phase 2)!
    LaunchedEffect(isFireActive, hazardNodes.size) {
        if (isFireActive) {
            val fireNode = hazardNodes.firstOrNull()
            val firePoint = if (fireNode != null) Point2D(fireNode.xNormalized, fireNode.yNormalized) else Point2D(0.28f, 0.26f)
            val nearestExt = tempExtinguishers.firstOrNull() ?: Point2D(0.25f, 0.72f)
            val intermediateDoor = tempDoors.firstOrNull() ?: Point2D(0.28f, 0.48f)

            emergencyPhase = EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST
            activeRoute = NavigationRoute(
                pathPoints = listOf(currentUserPos, intermediateDoor, nearestExt),
                totalDistanceMeters = 5.8f,
                targetName = "Bình chữa cháy gần nhất (Phase 1)",
                targetType = "EXTINGUISHER",
                crossesHazardRoom = false,
                notes = "ĐANG BÁO CHÁY: Tự động chỉ đường đến bình chữa cháy trước để dập lửa!"
            )
        } else if (emergencyPhase != EmergencyAutoPhase.IDLE) {
            emergencyPhase = EmergencyAutoPhase.IDLE
            activeRoute = null
        }
    }

    // Advance position when phone step detector triggers
    LaunchedEffect(stepCount) {
        if (stepCount > 0) {
            activeRoute?.let { route ->
                val newPos = movementTracker.advancePositionAlongPath(currentUserPos, route.pathPoints, 0.035f)
                currentUserPos = newPos
                onSetUserPosition(floorId, newPos.x, newPos.y)

                // Check if reached extinguisher in Phase 1
                if (emergencyPhase == EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST) {
                    val targetExt = tempExtinguishers.firstOrNull() ?: Point2D(0.25f, 0.72f)
                    val dx = newPos.x - targetExt.x
                    val dy = newPos.y - targetExt.y
                    if (kotlin.math.hypot(dx, dy) < 0.07f) {
                        // Switch to Phase 2: Attack fire source!
                        emergencyPhase = EmergencyAutoPhase.ATTACK_FIRE_SOURCE
                        val fireNode = hazardNodes.firstOrNull()
                        val firePoint = if (fireNode != null) Point2D(fireNode.xNormalized, fireNode.yNormalized) else Point2D(0.28f, 0.26f)
                        val returnDoor = tempDoors.firstOrNull() ?: Point2D(0.28f, 0.48f)
                        activeRoute = NavigationRoute(
                            pathPoints = listOf(newPos, returnDoor, firePoint),
                            totalDistanceMeters = 6.2f,
                            targetName = "Điểm cháy ${fireNode?.displayName ?: "P.Lab"} (Phase 2)",
                            targetType = "FIRE_SOURCE",
                            crossesHazardRoom = true,
                            notes = "Đã lấy bình chữa cháy! Tiến ngay vào vị trí phát hiện khói để dập tắt!"
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
    ) {
        // 1. TOP HEADER BAR: Mode Switcher & Sensor Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface)
                .border(1.dp, LightBorder)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFireActive) CriticalAlarmRed.copy(alpha = 0.15f) else PrimaryBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isFireActive) Icons.Default.Warning else Icons.Default.Map,
                        contentDescription = null,
                        tint = if (isFireActive) CriticalAlarmRed else PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        if (activeModeTab == 1) "Thiết Lập Quét Khu Vực" else "Bản Đồ 2D Mặt Bằng",
                        color = if (isFireActive) CriticalAlarmRed else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isFireActive) "🚨 ĐANG CÓ CHÁY: TỰ ĐỘNG DẪN ĐƯỜNG LẤY BÌNH CC" else "STEM của SOV labs. · Biểu tượng Cửa to rõ & Cảm biến ĐT",
                        color = if (isFireActive) CriticalAlarmRed else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isFireActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Mode Toggle Button
            Button(
                onClick = {
                    activeModeTab = if (activeModeTab == 0) 1 else 0
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeModeTab == 0) PrimaryBlue else NormalGreen
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(34.dp).testTag("btn_toggle_map_mode")
            ) {
                Icon(
                    if (activeModeTab == 0) Icons.Default.Edit else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (activeModeTab == 0) "Bắt Đầu Setup" else "Xong Setup",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. ALWAYS-PINNED TOP HUD BANNER (LUÔN HIỆN TRÊN ĐẦU LÀ VỊ TRÍ TỚI CÁI ĐANG CHỈ ĐỊNH)
        activeRoute?.let { route ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .border(
                        1.5.dp,
                        if (isFireActive) CriticalAlarmRed else NormalGreen,
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFireActive) Color(0xFFFEF2F2) else NormalGreenBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isFireActive) CriticalAlarmRed else NormalGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (emergencyPhase) {
                                        EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST -> Icons.Default.FireExtinguisher
                                        EmergencyAutoPhase.ATTACK_FIRE_SOURCE -> Icons.Default.Warning
                                        else -> Icons.Default.Navigation
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = when (emergencyPhase) {
                                        EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST -> "🚨 BƯỚC 1/2: LẤY BÌNH CHỮA CHÁY TRƯỚC"
                                        EmergencyAutoPhase.ATTACK_FIRE_SOURCE -> "🔥 BƯỚC 2/2: ĐÃ CÓ BÌNH ➔ TIẾP CẬN ĐIỂM CHÁY"
                                        else -> "🎯 ĐANG CHỈ ĐƯỜNG ĐẾN: ${route.targetName.uppercase()}"
                                    },
                                    color = if (isFireActive) CriticalAlarmRed else NormalGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = when (emergencyPhase) {
                                        EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST -> "Đi qua Cửa ➔ Lấy bình chữa cháy đỏ ngay sảnh"
                                        EmergencyAutoPhase.ATTACK_FIRE_SOURCE -> "Đi qua Cửa ➔ Tiếp cận đầu báo khói Phòng Lab SOV dập lửa"
                                        else -> "Lộ trình dẫn qua các Cánh Cửa an toàn"
                                    },
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                activeRoute = null
                                emergencyPhase = EmergencyAutoPhase.IDLE
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng chỉ đường", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Distance, Steps & Phone Movement sensor feedback
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Còn ~${route.totalDistanceMeters}m (~${(route.totalDistanceMeters / 0.7f).toInt()} bước)",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "La bàn: ${compassHeading.toInt()}° · Đã đi: $stepCount bước",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }

                        // Simulate 1 step button for desktop/table test
                        Button(
                            onClick = { movementTracker.simulateManualStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueLight),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("+1 bước", color = PrimaryBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // In emergency Phase 1: Quick confirm button to jump to fire
                    if (emergencyPhase == EmergencyAutoPhase.FETCH_EXTINGUISHER_FIRST) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                emergencyPhase = EmergencyAutoPhase.ATTACK_FIRE_SOURCE
                                val fireNode = hazardNodes.firstOrNull()
                                val firePoint = if (fireNode != null) Point2D(fireNode.xNormalized, fireNode.yNormalized) else Point2D(0.28f, 0.26f)
                                val returnDoor = tempDoors.firstOrNull() ?: Point2D(0.28f, 0.48f)
                                activeRoute = NavigationRoute(
                                    pathPoints = listOf(currentUserPos, returnDoor, firePoint),
                                    totalDistanceMeters = 6.2f,
                                    targetName = "Điểm cháy ${fireNode?.displayName ?: "P.Lab"} (Phase 2)",
                                    targetType = "FIRE_SOURCE",
                                    crossesHazardRoom = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ĐÃ CẦM BÌNH ➔ CHỈ ĐƯỜNG DẬP LỬA NGAY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. SETUP WIZARD CARD (When activeModeTab == 1)
        if (activeModeTab == 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (scanStep) {
                                1 -> "BƯỚC 1/5: 🚨 GHIM CẢM BIẾN BÁO CHÁY"
                                2 -> "BƯỚC 2/5: 🚪 THÊM CỬA ĐI (DOOR)"
                                3 -> "BƯỚC 3/5: 🪜 THÊM CẦU THANG (STAIRS)"
                                4 -> "BƯỚC 4/5: 🧯 GHIM BÌNH CHỮA CHÁY"
                                else -> "BƯỚC 5/5: 🏃 GHIM CỬA THOÁT HIỂM"
                            },
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text("Chạm mặt bằng để định vị", color = TextSecondary, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when (scanStep) {
                            1 -> "Bước chân đến vị trí lắp đầu báo khói (hoặc chạm bản đồ bên dưới). Đã có: ${tempSensors.size} điểm."
                            2 -> "Mỗi khi đi qua cửa giữa các phòng/hành lang, bấm Ghim Cửa (hoặc chạm bản đồ). Lộ trình bắt buộc dẫn qua cửa! Đã có: ${tempDoors.size} cửa."
                            3 -> "Đến chân cầu thang nếu nhà nhiều tầng (hoặc bấm Bỏ qua). Đã có: ${tempStairs.size} cầu thang."
                            4 -> "Đến vị trí treo bình chữa cháy và bấm Ghim. Đã có: ${tempExtinguishers.size} bình."
                            else -> "Đến cửa thoát hiểm chính dẫn ra ngoài và bấm Hoàn tất. Đã có: ${tempExits.size} lối thoát."
                        },
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (scanStep) {
                            1 -> {
                                Button(
                                    onClick = { scanStep = 2 },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Xác Nhận & Qua Bước Cửa", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { tempSensors.add(Point2D(0.40f + (tempSensors.size * 0.1f), 0.28f)) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Thêm Báo Cháy", fontSize = 10.sp)
                                }
                            }
                            2 -> {
                                Button(
                                    onClick = { scanStep = 3 },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Ghim Cửa & Qua Cầu Thang", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { tempDoors.add(Point2D(0.48f, 0.40f + (tempDoors.size * 0.1f))) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Thêm Cửa Nữa", fontSize = 10.sp)
                                }
                            }
                            3 -> {
                                Button(
                                    onClick = { scanStep = 4 },
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Ghim Thang & Tiếp Tục", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { scanStep = 4 },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Bỏ qua", fontSize = 10.sp)
                                }
                            }
                            4 -> {
                                Button(
                                    onClick = { scanStep = 5 },
                                    colors = ButtonDefaults.buttonColors(containerColor = CriticalAlarmRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("Ghim Bình & Qua Lối Thoát", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { tempExtinguishers.add(Point2D(0.35f + (tempExtinguishers.size * 0.15f), 0.72f)) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Thêm Bình", fontSize = 10.sp)
                                }
                            }
                            5 -> {
                                Button(
                                    onClick = {
                                        onSaveScannedSetup(
                                            tempSensors.toList(),
                                            tempDoors.toList(),
                                            tempStairs.toList(),
                                            tempExtinguishers.toList(),
                                            tempExits.toList()
                                        )
                                        activeModeTab = 0
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NormalGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(32.dp)
                                ) {
                                    Text("💾 Lưu Bản Đồ & Xem 2D", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. ACTION BAR (When in View Mode): Quick Route selection
        if (activeModeTab == 0 && activeRoute == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route to Nearest Fire Extinguisher
                FilterChip(
                    selected = false,
                    onClick = {
                        val targetExt = tempExtinguishers.firstOrNull() ?: Point2D(0.25f, 0.72f)
                        val midDoor = tempDoors.firstOrNull() ?: Point2D(0.28f, 0.48f)
                        activeRoute = NavigationRoute(
                            pathPoints = listOf(currentUserPos, midDoor, targetExt),
                            totalDistanceMeters = 5.6f,
                            targetName = "Bình chữa cháy ABC",
                            targetType = "EXTINGUISHER",
                            crossesHazardRoom = false
                        )
                    },
                    label = { Text("Chỉ đường đến Bình Chữa Cháy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.FireExtinguisher, contentDescription = null, tint = CriticalAlarmRed, modifier = Modifier.size(15.dp)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = LightSurface)
                )

                // Route to Emergency Exit
                FilterChip(
                    selected = false,
                    onClick = {
                        val targetExit = tempExits.firstOrNull() ?: Point2D(0.50f, 0.88f)
                        val midDoor1 = tempDoors.firstOrNull() ?: Point2D(0.28f, 0.48f)
                        val midDoor2 = tempDoors.getOrNull(1) ?: Point2D(0.50f, 0.70f)
                        activeRoute = NavigationRoute(
                            pathPoints = listOf(currentUserPos, midDoor1, midDoor2, targetExit),
                            totalDistanceMeters = 11.4f,
                            targetName = "Lối thoát hiểm chính",
                            targetType = "EXIT",
                            crossesHazardRoom = false
                        )
                    },
                    label = { Text("Lối Thoát Hiểm", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(15.dp)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = LightSurface)
                )
            }
        }

        // 5. THE 2D ARCHITECTURAL CANVAS (SUPER SMOOTH, CRISP & LARGE ICONS)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFCFDFE))
                .border(1.5.dp, LightBorder, RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.75f, 3.5f)
                        panOffset += pan
                    }
                }
                .pointerInput(activeModeTab, scanStep) {
                    detectTapGestures { tapOffset ->
                        val w = size.width
                        val h = size.height
                        val normX = ((tapOffset.x - panOffset.x) / (w * scale)).coerceIn(0.05f, 0.95f)
                        val normY = ((tapOffset.y - panOffset.y) / (h * scale)).coerceIn(0.05f, 0.95f)

                        if (activeModeTab == 1) {
                            when (scanStep) {
                                1 -> tempSensors.add(Point2D(normX, normY))
                                2 -> tempDoors.add(Point2D(normX, normY))
                                3 -> tempStairs.add(Point2D(normX, normY))
                                4 -> tempExtinguishers.add(Point2D(normX, normY))
                                5 -> tempExits.add(Point2D(normX, normY))
                            }
                        } else {
                            currentUserPos = Point2D(normX, normY)
                            onSetUserPosition(floorId, normX, normY)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val effectiveW = canvasW * scale
                val effectiveH = canvasH * scale
                val originX = panOffset.x
                val originY = panOffset.y

                fun toCanvas(pt: Point2D): Offset {
                    return Offset(originX + pt.x * effectiveW, originY + pt.y * effectiveH)
                }

                // A. Background Grid Lines
                val gridStep = 48f * scale
                var gx = originX % gridStep
                while (gx < canvasW) {
                    drawLine(Color(0xFFF1F5F9), Offset(gx, 0f), Offset(gx, canvasH), 1f)
                    gx += gridStep
                }
                var gy = originY % gridStep
                while (gy < canvasH) {
                    drawLine(Color(0xFFF1F5F9), Offset(0f, gy), Offset(canvasW, gy), 1f)
                    gy += gridStep
                }

                // B. Architectural Rooms
                data class ArchRoom(val name: String, val l: Float, val t: Float, val r: Float, val b: Float, val bg: Color)
                val roomsPlan = listOf(
                    ArchRoom("PHÒNG THÍ NGHIỆM STEM SOV", 0.08f, 0.08f, 0.48f, 0.46f, Color(0xFFF8FAFC)),
                    ArchRoom("PHÒNG THIẾT BỊ IOT", 0.52f, 0.08f, 0.92f, 0.46f, Color(0xFFF8FAFC)),
                    ArchRoom("HÀNH LANG THOÁT NẠN TRUNG TÂM", 0.08f, 0.48f, 0.92f, 0.68f, Color(0xFFF1F5F9)),
                    ArchRoom("KHU BÌNH CHỮA CHÁY", 0.08f, 0.70f, 0.48f, 0.92f, Color(0xFFFEF2F2).copy(alpha = 0.45f)),
                    ArchRoom("CỬA THOÁT HIỂM NGOÀI TRỜI", 0.52f, 0.70f, 0.92f, 0.92f, Color(0xFFF0FDF4))
                )

                // Draw rooms and thick architectural walls
                roomsPlan.forEach { rm ->
                    val rL = originX + rm.l * effectiveW
                    val rT = originY + rm.t * effectiveH
                    val rW = (rm.r - rm.l) * effectiveW
                    val rH = (rm.b - rm.t) * effectiveH

                    // Room floor
                    drawRoundRect(
                        color = rm.bg,
                        topLeft = Offset(rL, rT),
                        size = Size(rW, rH),
                        cornerRadius = CornerRadius(8f * scale)
                    )
                    // Wall border (bold slate)
                    drawRoundRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(rL, rT),
                        size = Size(rW, rH),
                        cornerRadius = CornerRadius(8f * scale),
                        style = Stroke(width = 4.5f * scale)
                    )
                }

                // C. DRAW ESCAPE ROUTE PATH THROUGH DOORS (Large Glowing Ribbon)
                activeRoute?.let { r ->
                    if (r.pathPoints.size >= 2) {
                        val path = Path()
                        val start = toCanvas(r.pathPoints.first())
                        path.moveTo(start.x, start.y)

                        for (i in 1 until r.pathPoints.size) {
                            val next = toCanvas(r.pathPoints[i])
                            path.lineTo(next.x, next.y)
                        }

                        // Broad outer glow
                        drawPath(
                            path = path,
                            color = (if (isFireActive) CriticalAlarmRed else NormalGreen).copy(alpha = 0.35f),
                            style = Stroke(width = 16f * scale, cap = StrokeCap.Round)
                        )
                        // Bold inner neon core with directional dashes
                        drawPath(
                            path = path,
                            color = if (isFireActive) CriticalAlarmRed else NormalGreen,
                            style = Stroke(
                                width = 5.5f * scale,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f * scale, 10f * scale))
                            )
                        )
                    }
                }

                // D. DRAW DOORS (CÁI CỬA TO BẢN & RÕ RÀNG NHƯ BẢN VẼ KIẾN TRÚC)
                tempDoors.forEachIndexed { idx, dPt ->
                    val c = toCanvas(dPt)
                    val doorWidth = 36f * scale
                    val doorThickness = 8f * scale

                    // 1. Cut wall opening (white gap)
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(c.x - doorWidth / 2 - 4f * scale, c.y - doorThickness),
                        size = Size(doorWidth + 8f * scale, doorThickness * 2)
                    )

                    // 2. Door jambs (2 dark posts on left & right)
                    drawRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(c.x - doorWidth / 2 - 4f * scale, c.y - doorThickness),
                        size = Size(6f * scale, doorThickness * 2)
                    )
                    drawRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(c.x + doorWidth / 2 - 2f * scale, c.y - doorThickness),
                        size = Size(6f * scale, doorThickness * 2)
                    )

                    // 3. Open Door Leaf (cánh cửa mở nghiêng 90 độ, màu cam nổi bật)
                    drawLine(
                        color = Color(0xFFD97706),
                        start = Offset(c.x - doorWidth / 2, c.y),
                        end = Offset(c.x - doorWidth / 2, c.y - doorWidth),
                        strokeWidth = 4.5f * scale,
                        cap = StrokeCap.Round
                    )

                    // 4. Architectural Swing Arc (cung mở cửa nét đứt)
                    drawArc(
                        color = Color(0xFFF59E0B),
                        startAngle = 270f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(c.x - doorWidth / 2 - doorWidth, c.y - doorWidth),
                        size = Size(doorWidth * 2, doorWidth * 2),
                        style = Stroke(
                            width = 2f * scale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * scale, 4f * scale))
                        )
                    )

                    // 5. Door Label Badge
                    drawRoundRect(
                        color = Color(0xFFFEF3C7),
                        topLeft = Offset(c.x - 22f * scale, c.y + 10f * scale),
                        size = Size(44f * scale, 18f * scale),
                        cornerRadius = CornerRadius(4f * scale)
                    )
                    drawRoundRect(
                        color = Color(0xFFD97706),
                        topLeft = Offset(c.x - 22f * scale, c.y + 10f * scale),
                        size = Size(44f * scale, 18f * scale),
                        cornerRadius = CornerRadius(4f * scale),
                        style = Stroke(1.5f * scale)
                    )
                }

                // E. DRAW STAIRS (CẦU THANG TO RÕ VỚI TỪNG BẬC THANG)
                tempStairs.forEachIndexed { idx, stPt ->
                    val c = toCanvas(stPt)
                    val stW = 46f * scale
                    val stH = 34f * scale

                    // Stair footprint
                    drawRoundRect(
                        color = Color(0xFFEEF2FF),
                        topLeft = Offset(c.x - stW / 2, c.y - stH / 2),
                        size = Size(stW, stH),
                        cornerRadius = CornerRadius(4f * scale)
                    )
                    drawRoundRect(
                        color = IndigoAccent,
                        topLeft = Offset(c.x - stW / 2, c.y - stH / 2),
                        size = Size(stW, stH),
                        cornerRadius = CornerRadius(4f * scale),
                        style = Stroke(2.5f * scale)
                    )

                    // 5 Parallel step treads
                    for (step in 1..5) {
                        val sy = c.y - stH / 2 + (stH / 6) * step
                        drawLine(
                            color = IndigoAccent,
                            start = Offset(c.x - stW / 2 + 2f * scale, sy),
                            end = Offset(c.x + stW / 2 - 2f * scale, sy),
                            strokeWidth = 2.5f * scale
                        )
                    }

                    // Upward direction arrow
                    drawLine(
                        color = Color(0xFF4338CA),
                        start = Offset(c.x, c.y + stH / 3),
                        end = Offset(c.x, c.y - stH / 3),
                        strokeWidth = 3f * scale,
                        cap = StrokeCap.Round
                    )
                }

                // F. DRAW FIRE EXTINGUISHERS (BÌNH CHỮA CHÁY TO RÕ MÀU ĐỎ)
                tempExtinguishers.forEachIndexed { idx, extPt ->
                    val c = toCanvas(extPt)
                    val extW = 22f * scale
                    val extH = 36f * scale

                    // Extinguisher Red Cylinder Body
                    drawRoundRect(
                        color = CriticalAlarmRed,
                        topLeft = Offset(c.x - extW / 2, c.y - extH / 2 + 8f * scale),
                        size = Size(extW, extH - 8f * scale),
                        cornerRadius = CornerRadius(6f * scale)
                    )
                    // White Pressure band
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(c.x - extW / 2, c.y - 2f * scale),
                        size = Size(extW, 6f * scale)
                    )
                    // Black Handle / Lever on top
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(c.x, c.y - extH / 2 + 8f * scale),
                        end = Offset(c.x, c.y - extH / 2 - 2f * scale),
                        strokeWidth = 4f * scale
                    )
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(c.x - 8f * scale, c.y - extH / 2),
                        end = Offset(c.x + 8f * scale, c.y - extH / 2 - 4f * scale),
                        strokeWidth = 3f * scale,
                        cap = StrokeCap.Round
                    )

                    // Big Badge
                    drawRoundRect(
                        color = Color(0xFFFEE2E2),
                        topLeft = Offset(c.x - 30f * scale, c.y + extH / 2 + 2f * scale),
                        size = Size(60f * scale, 18f * scale),
                        cornerRadius = CornerRadius(4f * scale)
                    )
                    drawRoundRect(
                        color = CriticalAlarmRed,
                        topLeft = Offset(c.x - 30f * scale, c.y + extH / 2 + 2f * scale),
                        size = Size(60f * scale, 18f * scale),
                        cornerRadius = CornerRadius(4f * scale),
                        style = Stroke(1.5f * scale)
                    )
                }

                // G. DRAW EMERGENCY EXITS (LỐI THOÁT HIỂM TO XANH LÁ)
                tempExits.forEachIndexed { idx, exPt ->
                    val c = toCanvas(exPt)
                    val exW = 48f * scale
                    val exH = 28f * scale

                    drawRoundRect(
                        color = NormalGreen,
                        topLeft = Offset(c.x - exW / 2, c.y - exH / 2),
                        size = Size(exW, exH),
                        cornerRadius = CornerRadius(6f * scale)
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(c.x - exW / 2 + 2f * scale, c.y - exH / 2 + 2f * scale),
                        size = Size(exW - 4f * scale, exH - 4f * scale),
                        cornerRadius = CornerRadius(4f * scale),
                        style = Stroke(2f * scale)
                    )
                    // White exit door representation
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(c.x - 8f * scale, c.y - exH / 2 + 5f * scale),
                        size = Size(16f * scale, exH - 10f * scale)
                    )
                }

                // H. DRAW FIRE SENSORS (CẢM BIẾN BÁO CHÁY TO & TỎA SÓNG CẢNH BÁO)
                tempSensors.forEachIndexed { i, sPt ->
                    val c = toCanvas(sPt)
                    val matchingNode = nodes.getOrNull(i)
                    val isAlarmNode = matchingNode?.lastKnownAlarm == true || hazardNodes.isNotEmpty()

                    if (isAlarmNode) {
                        // Expanding shockwaves
                        drawCircle(
                            color = CriticalAlarmRed.copy(alpha = 0.35f),
                            radius = pulseRadius * scale,
                            center = c
                        )
                        drawCircle(
                            color = CriticalAlarmRed,
                            radius = 16f * scale,
                            center = c
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 8f * scale,
                            center = c
                        )
                        // Flame core
                        drawCircle(
                            color = Color(0xFFF59E0B),
                            radius = 4f * scale,
                            center = c
                        )
                    } else {
                        // Calm emerald sensor beacon
                        drawCircle(
                            color = NormalGreen.copy(alpha = 0.25f),
                            radius = 20f * scale,
                            center = c
                        )
                        drawCircle(
                            color = NormalGreen,
                            radius = 14f * scale,
                            center = c
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7f * scale,
                            center = c
                        )
                    }
                }

                // I. DRAW USER LOCATION WITH COMPASS DIRECTION CONE (BẠN Ở ĐÂY)
                val uOffset = toCanvas(currentUserPos)
                val coneRadius = 40f * scale
                val headingRad = Math.toRadians((compassHeading - 90).toDouble())

                // Vision cone (field of view based on compass orientation)
                val conePath = Path().apply {
                    moveTo(uOffset.x, uOffset.y)
                    val leftAngle = headingRad - 0.4
                    val rightAngle = headingRad + 0.4
                    lineTo(
                        (uOffset.x + coneRadius * cos(leftAngle)).toFloat(),
                        (uOffset.y + coneRadius * sin(leftAngle)).toFloat()
                    )
                    lineTo(
                        (uOffset.x + coneRadius * cos(rightAngle)).toFloat(),
                        (uOffset.y + coneRadius * sin(rightAngle)).toFloat()
                    )
                    close()
                }
                drawPath(conePath, PrimaryBlue.copy(alpha = 0.25f))

                // Pulsing blue user circle
                drawCircle(
                    color = PrimaryBlue.copy(alpha = 0.35f),
                    radius = 22f * scale,
                    center = uOffset
                )
                drawCircle(
                    color = PrimaryBlue,
                    radius = 12f * scale,
                    center = uOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.5f * scale,
                    center = uOffset
                )
            }

            // FLOATING BOTTOM-START LEGEND
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = LightSurface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFD97706)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cửa", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(CriticalAlarmRed))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bình CC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(NormalGreen))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Báo Cháy", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(IndigoAccent))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cầu Thang", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PrimaryBlue))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bạn", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            // FLOATING RESET ZOOM BUTTON
            IconButton(
                onClick = {
                    scale = 1.0f
                    panOffset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(LightSurface, CircleShape)
                    .border(1.dp, LightBorder, CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Đặt lại góc nhìn", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
