package com.example

import com.example.data.local.entity.DeviceSnapshotEntity
import com.example.data.remote.dto.StatusPayloadDto
import com.example.data.remote.dto.StatusResponseDto
import com.example.domain.analytics.LocalAnalyticsEngine
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.ExtinguisherItem
import com.example.domain.model.ExtinguisherType
import com.example.domain.model.FirmwareState
import com.example.domain.model.MapEdgeItem
import com.example.domain.model.MapNodeItem
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import com.example.domain.model.SmartFireNode
import com.example.domain.pathfinding.GeoLatLng
import com.example.domain.pathfinding.GeoMapMath
import com.example.domain.pathfinding.IndoorPathfinder
import com.example.sensor.DeviceMovementTracker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ExampleUnitTest {

    @Test
    fun testStatusResponseDtoParsing() {
        val json = """
        {
          "api_version": 1,
          "device_id": "smartfire-node-01",
          "room_id": "room-101",
          "boot_id": "boot-test-99",
          "latest_seq": 15,
          "status": {
            "uptime_ms": 12345,
            "state": "WARMING",
            "gas_raw": 1024,
            "flame": false,
            "gas_ready": false,
            "gas_fault": false,
            "alarm": false,
            "muted": false,
            "acknowledged": false,
            "testing": false,
            "horn_requested": false,
            "horn_enabled": true,
            "gas_commissioned": true
          }
        }
        """.trimIndent()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(StatusResponseDto::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertEquals("smartfire-node-01", result!!.deviceId)
        assertEquals("room-101", result.roomId)
        assertEquals(15L, result.latestSeq)
        assertEquals(1024, result.status.gasRaw)
        assertFalse(result.status.gasReady)
        assertTrue(result.status.hornEnabled)
    }

    @Test
    fun testDualStateInvariant_alarmPreservedWhenOffline() {
        // Safety Invariant: An alarm node that goes offline must remain marked as ALARM
        val node = SmartFireNode(
            deviceId = "smartfire-node-02",
            displayName = "Cảm biến Nhà Bếp",
            homeId = "home_1",
            floorId = "floor_1",
            roomId = "room_kitchen",
            ipAddress = "192.168.1.151",
            firmwareState = FirmwareState.ALARM,
            connectionState = DeviceConnectionState.OFFLINE,
            lastKnownAlarm = true
        )

        assertEquals(DeviceConnectionState.OFFLINE, node.connectionState)
        assertEquals(FirmwareState.ALARM, node.firmwareState)
        assertTrue("Node phải duy trì trạng thái cảnh báo khi offline", node.lastKnownAlarm)
    }

    @Test
    fun testIndoorPathfinder_dijkstraNearestExtinguisher() {
        val start = Point2D(0.2f, 0.2f)
        val extinguishers = listOf(
            ExtinguisherItem(
                id = "ext_1",
                floorId = "floor_1",
                roomId = "room_corridor",
                name = "Bình Bột Hành Lang",
                type = ExtinguisherType.POWDER_ABC,
                position = Point2D(0.5f, 0.5f)
            )
        )

        val nodes = listOf(
            MapNodeItem("n1", "floor_1", "Cửa phòng", MapObjectType.DOOR, Point2D(0.25f, 0.25f)),
            MapNodeItem("n2", "floor_1", "Giao lộ", MapObjectType.CORRIDOR_NODE, Point2D(0.45f, 0.45f))
        )

        val edges = listOf(
            MapEdgeItem("e1", "floor_1", "n1", "n2", distanceMeters = 3.0f)
        )

        val route = IndoorPathfinder.findPathToNearestExtinguisher(
            startPoint = start,
            extinguishers = extinguishers,
            nodes = nodes,
            edges = edges,
            hazardRoomIds = emptySet(),
            scaleMeters = 10.0f
        )

        assertNotNull(route)
        assertEquals("EXTINGUISHER", route!!.targetType)
        assertFalse(route.crossesHazardRoom)
    }

    @Test
    fun testLocalAnalyticsEngine_warmupDetection() {
        val snapshots = listOf(
            DeviceSnapshotEntity(
                id = 1,
                deviceId = "smartfire-node-01",
                bootId = "boot-1",
                receivedAt = System.currentTimeMillis(),
                uptimeMs = 5000,
                state = "WARMING",
                gasRaw = 900,
                flame = false,
                gasReady = false,
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

        val insights = LocalAnalyticsEngine.analyzeSnapshots("smartfire-node-01", "Cảm biến 1", snapshots)
        val hasWarmup = insights.any { it.title.contains("WARMING", ignoreCase = true) }
        assertTrue("Hệ thống phải nhận diện cảm biến đang trong chu kỳ sấy nóng", hasWarmup)
    }

    @Test
    fun testCalculateFireProbability_respectsDynamicGasThreshold() {
        val telemetry = com.example.domain.model.SmartFireTelemetry(
            temperatureCelsius = 48.0f,
            tempRateOfRiseCPerMin = 4.5f,
            gasRaw = 1500,
            coPpm = 65,
            humidityPercent = 32f,
            irFlameIntensity = 0.35f
        )

        val probHighThreshold = telemetry.calculateFireProbability(gasThresholdAdc = 2400).totalProbabilityPercent
        val probLowThreshold = telemetry.calculateFireProbability(gasThresholdAdc = 1300).totalProbabilityPercent

        assertTrue(
            "Khi hạ ngưỡng cảnh báo MQ-2 xuống 1300 ADC, xác suất cháy tại 1500 ADC phải cao hơn so với ngưỡng 2400 ADC",
            probLowThreshold > probHighThreshold
        )
    }

    @Test
    fun testGeoMapMath_polygonDimensionsAndProjection() {
        val center = GeoLatLng(10.776889, 106.700806)
        val rect = GeoMapMath.createGeoTemplateRect(center, widthMeters = 10.0, lengthMeters = 20.0)
        val metrics = GeoMapMath.calculateGeoPolygonMetrics(rect)

        assertTrue("Chiều rộng thật phải xấp xỉ 10m", metrics.widthMeters in 9.2f..10.8f)
        assertTrue("Chiều dài thật phải xấp xỉ 20m", metrics.lengthMeters in 18.8f..21.2f)
        assertTrue("Diện tích phải xấp xỉ 200 m2", metrics.areaM2 in 185f..215f)

        val projected = GeoMapMath.projectGeoPolygonToNormalized(rect)
        assertEquals(4, projected.size)
        // North corners (0, 1) must have smaller screen Y than South corners (2, 3)
        assertTrue(projected[0].y < projected[3].y)
        assertTrue(projected[1].x > projected[0].x)
    }

    @Test
    fun testTriSensorFusion_zeroIndoorGpsAndRssiDriftCorrection() {
        val anchor = Point2D(0.50f, 0.25f)
        val startUser = Point2D(0.50f, 0.75f) // 10m away from anchor on a 10x20m floor

        // User walks 1 step North (0 deg) while ESP32 RSSI indicates 6.5m distance
        val result = DeviceMovementTracker.fuseIndoorPosition(
            currentPos = startUser,
            stepsDelta = 1,
            stepStrideMeters = 0.70f,
            compassHeadingDeg = 0f,
            houseWidthMeters = 10f,
            houseLengthMeters = 20f,
            anchorPos = anchor,
            rssiDistanceMeters = 6.5f,
            rssiDbm = -55,
            activePath = null
        )

        assertEquals("GPS weight indoors must remain 0%", 0, result.gpsWeightPct)
        assertTrue("Moved North toward anchor", result.fusedPosition.y < startUser.y)
        val distAfter = hypot(
            ((result.fusedPosition.x - anchor.x) * 10f).toDouble(),
            ((result.fusedPosition.y - anchor.y) * 20f).toDouble()
        ).toFloat()
        // Pure 1 step without RSSI pull would be 9.3m; RSSI (6.5m) pulls it closer than 9.2m
        assertTrue("ESP32 RSSI radial pull must damp cumulative drift", distAfter < 9.2f)

        // Verify circular smoothing across 355° -> 5° wraps cleanly near 0° rather than jumping to 180°
        val smoothed = DeviceMovementTracker.smoothHeadingDegrees(355f, 5f, alpha = 0.5f)
        assertTrue("Smoothed heading across North wrap must stay near 0/360", smoothed < 10f || smoothed > 350f)
    }

    @Test
    fun testOutdoorPresenceDetection_gpsPolygonAndRssi() {
        val housePoly = listOf(
            Point2D(0.15f, 0.15f),
            Point2D(0.85f, 0.15f),
            Point2D(0.85f, 0.85f),
            Point2D(0.15f, 0.85f)
        )

        // 1. Inside house with good Wi-Fi RSSI
        val inside = DeviceMovementTracker.evaluatePresenceZone(
            userPos = Point2D(0.50f, 0.50f),
            housePolygon = housePoly,
            houseWidthMeters = 10f,
            houseLengthMeters = 14f,
            gpsDistanceFromHouseCenterMeters = 3.0f,
            gpsAccuracyMeters = 8.0f,
            hasRealGpsFix = true,
            rssiDbm = -54,
            rssiDistanceMeters = 2.4f
        )
        assertEquals(com.example.sensor.PresenceZone.INSIDE_HOUSE, inside.zone)

        // 2. Walked outside polygon bounds (must NOT stay stuck inside)
        val outsidePoly = DeviceMovementTracker.evaluatePresenceZone(
            userPos = Point2D(0.50f, 1.08f),
            housePolygon = housePoly,
            houseWidthMeters = 10f,
            houseLengthMeters = 14f,
            gpsDistanceFromHouseCenterMeters = null,
            gpsAccuracyMeters = 20f,
            hasRealGpsFix = false,
            rssiDbm = -68,
            rssiDistanceMeters = 9.0f
        )
        assertEquals(com.example.sensor.PresenceZone.OUTSIDE_HOUSE, outsidePoly.zone)

        // 3. Clearly outside via GPS geo-fence (45m away)
        val outsideGps = DeviceMovementTracker.evaluatePresenceZone(
            userPos = Point2D(0.50f, 0.50f),
            housePolygon = housePoly,
            houseWidthMeters = 10f,
            houseLengthMeters = 14f,
            gpsDistanceFromHouseCenterMeters = 45.0f,
            gpsAccuracyMeters = 8.0f,
            hasRealGpsFix = true,
            rssiDbm = -75,
            rssiDistanceMeters = 15.0f
        )
        assertEquals(com.example.sensor.PresenceZone.OUTSIDE_HOUSE, outsideGps.zone)
        assertTrue(outsideGps.isGpsOutside)
        assertTrue(outsideGps.distanceOutsideMeters > 30f)

        // 4. All real ESP32 nodes offline -> outside Wi-Fi coverage
        val outsideOffline = DeviceMovementTracker.evaluatePresenceZone(
            userPos = Point2D(0.50f, 0.50f),
            housePolygon = housePoly,
            houseWidthMeters = 10f,
            houseLengthMeters = 14f,
            gpsDistanceFromHouseCenterMeters = null,
            gpsAccuracyMeters = 30f,
            hasRealGpsFix = false,
            rssiDbm = -88,
            rssiDistanceMeters = 35.0f,
            allRealNodesOffline = true
        )
        assertEquals(com.example.sensor.PresenceZone.OUTSIDE_HOUSE, outsideOffline.zone)
        assertTrue(outsideOffline.isRssiWeakOrOffline)
    }

    @Test
    fun testRssiKalmanFilter_suppressesSpikeAndDistanceCalibration() {
        val filter = com.example.domain.model.RssiKalmanFilter()
        filter.update(-55)
        filter.update(-56)
        filter.update(-54)
        // Sudden multipath outlier spike (-95 dBm) must be rejected by median+Kalman
        val afterSpike = filter.update(-95)
        assertTrue("Spike must be suppressed, got $afterSpike", afterSpike in -60..-52)

        val telemAt1m = com.example.domain.model.SmartFireTelemetry(
            rssiDbm = -45,
            txPower1m = -45,
            pathLossN = 2.4f
        )
        assertEquals(1.0f, telemAt1m.estimateDistanceFromRssiMeters(), 0.05f)

        val telemAt10m = com.example.domain.model.SmartFireTelemetry(
            rssiDbm = -69,
            txPower1m = -45,
            pathLossN = 2.4f
        )
        assertEquals(10.0f, telemAt10m.estimateDistanceFromRssiMeters(), 0.2f)
    }

    @Test
    fun testTurnByTurnNavigator_turnLeftNextStepAndOutdoorBanner() {
        // Route: Go North 10m, then Turn Left (West) 8m
        val route = com.example.domain.pathfinding.NavigationRoute(
            pathPoints = listOf(
                Point2D(0.5f, 0.9f),
                Point2D(0.5f, 0.4f),
                Point2D(0.1f, 0.4f)
            ),
            totalDistanceMeters = 18f,
            targetName = "Cửa Exit 1",
            targetType = "EXIT",
            crossesHazardRoom = false
        )

        val indoorGuidance = com.example.domain.pathfinding.TurnByTurnNavigator.buildGuidance(
            route = route,
            userHeadingDeg = 0f, // Facing North
            houseWidthMeters = 20f,
            houseLengthMeters = 20f,
            isOutsideHouse = false
        )
        assertEquals(com.example.domain.pathfinding.TurnDirection.TURN_LEFT, indoorGuidance.currentStep.direction)
        assertTrue(indoorGuidance.currentStep.primaryText.contains("rẽ trái", ignoreCase = true))
        assertNotNull(indoorGuidance.nextStep)
        assertTrue(indoorGuidance.nextStep!!.primaryText.contains("đi thẳng", ignoreCase = true))

        // Far outdoor distance (500m)
        val outdoorGuidance = com.example.domain.pathfinding.TurnByTurnNavigator.buildGuidance(
            route = route,
            userHeadingDeg = 0f,
            houseWidthMeters = 20f,
            houseLengthMeters = 20f,
            isOutsideHouse = true,
            outdoorOverrideDistanceMeters = 500f
        )
        assertTrue(outdoorGuidance.isOutsideHouse)
        assertTrue(outdoorGuidance.currentStep.primaryText.contains("500m"))
        assertTrue(outdoorGuidance.currentStep.primaryText.contains("cửa thoát hiểm", ignoreCase = true))
    }

    @Test
    fun testEspConfigAndStatusWifiDtoParsing() {
        val json = """
        {
          "api_version": 1,
          "device_id": "smartfire-node-01",
          "room_id": "kitchen",
          "boot_id": "abcd1234",
          "latest_seq": 9,
          "status": {
            "uptime_ms": 50000,
            "state": "NORMAL",
            "gas_raw": 820,
            "flame": false,
            "gas_ready": true,
            "gas_fault": false,
            "alarm": false,
            "muted": false,
            "acknowledged": false,
            "testing": false,
            "horn_requested": false,
            "horn_enabled": true,
            "gas_commissioned": true,
            "wifi_rssi": -58,
            "wifi_rssi_raw": -60,
            "wifi_reconnects": 2,
            "gas_on": 2200,
            "gas_off": 1700,
            "tx_power_1m": -45,
            "path_loss_n": 2.4,
            "firmware_version": "2.1.0-wifi-ota"
          }
        }
        """.trimIndent()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val parsed = moshi.adapter(StatusResponseDto::class.java).fromJson(json)
        assertNotNull(parsed)
        assertEquals(-58, parsed!!.status.wifiRssi)
        assertEquals(2L, parsed.status.wifiReconnects)
        assertEquals(2200, parsed.status.gasOn)
        assertEquals(1700, parsed.status.gasOff)
        assertEquals(-45, parsed.status.txPower1m)
        assertEquals("2.1.0-wifi-ota", parsed.status.firmwareVersion)
    }

    @Test
    fun testTurnByTurnAndOutdoorStepExit_respectsLiveHeadingAndUnclampedBounds() {
        val route = com.example.domain.pathfinding.NavigationRoute(
            pathPoints = listOf(
                Point2D(0.5f, 0.9f),
                Point2D(0.5f, 0.4f),
                Point2D(0.5f, 0.4f) // degenerate duplicate waypoint must be ignored
            ),
            totalDistanceMeters = 10f,
            targetName = "Cửa Chính",
            targetType = "EXIT",
            crossesHazardRoom = false
        )

        // User facing South (180 deg) while Exit is North (0 deg) -> must instruct U-turn
        val guidance = com.example.domain.pathfinding.TurnByTurnNavigator.buildGuidance(
            route = route,
            userHeadingDeg = 180f,
            houseWidthMeters = 12f,
            houseLengthMeters = 20f,
            isOutsideHouse = false
        )
        assertEquals(com.example.domain.pathfinding.TurnDirection.U_TURN, guidance.currentStep.direction)
        assertTrue(guidance.currentStep.primaryText.contains("quay đầu", ignoreCase = true))

        // Walking South past the bottom house wall (0.90) with allowOutsideBounds = true leaves the polygon
        val fused = DeviceMovementTracker.fuseIndoorPosition(
            currentPos = Point2D(0.50f, 0.90f),
            stepsDelta = 5,
            stepStrideMeters = 0.70f,
            compassHeadingDeg = 180f,
            houseWidthMeters = 10f,
            houseLengthMeters = 14f,
            anchorPos = null,
            rssiDistanceMeters = 8.0f,
            rssiDbm = -72,
            allowOutsideBounds = true
        )
        assertTrue("Walking past wall must exit > 1.0 normalized Y, got ${fused.fusedPosition.y}", fused.fusedPosition.y > 1.0f)
    }

    @Test
    fun testTwoLevelThresholds_independentWarningAndAlarmLevels() {
        val cfg = com.example.domain.model.EspDeviceConfig(
            mq2Enabled = true,
            gasWarn = 1800,
            gasOn = 2400,
            gasOff = 1500,
            tempEnabled = true,
            tempWarnC = 45.0f,
            tempAlarmC = 60.0f,
            flameEnabled = true
        )

        // MQ-2 only checks
        val mq2Normal = com.example.domain.model.SmartFireTelemetry(gasRaw = 1400, temperatureCelsius = 30.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, mq2Normal.evaluateTwoLevelStatus(cfg))

        val mq2Warn = com.example.domain.model.SmartFireTelemetry(gasRaw = 1950, temperatureCelsius = 30.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.WARNING_LEVEL_1, mq2Warn.evaluateTwoLevelStatus(cfg))
        val mq2WarnProb = mq2Warn.calculateFireProbability(config = cfg)
        assertTrue("Mức 1 MQ-2 phải nằm trong [45..74.9], thực tế ${mq2WarnProb.totalProbabilityPercent}", mq2WarnProb.totalProbabilityPercent in 45f..74.9f)
        assertTrue(mq2WarnProb.riskLevelLabel.contains("MỨC 1"))

        val mq2Alarm = com.example.domain.model.SmartFireTelemetry(gasRaw = 2500, temperatureCelsius = 30.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.ALARM_LEVEL_2, mq2Alarm.evaluateTwoLevelStatus(cfg))
        val mq2AlarmProb = mq2Alarm.calculateFireProbability(config = cfg)
        assertTrue("Mức 2 MQ-2 phải >= 78%, thực tế ${mq2AlarmProb.totalProbabilityPercent}", mq2AlarmProb.totalProbabilityPercent >= 78f)
        assertTrue(mq2AlarmProb.riskLevelLabel.contains("MỨC 2"))

        // Temperature only checks
        val tempNormal = com.example.domain.model.SmartFireTelemetry(gasRaw = 800, temperatureCelsius = 35.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, tempNormal.evaluateTwoLevelStatus(cfg))

        val tempWarn = com.example.domain.model.SmartFireTelemetry(gasRaw = 800, temperatureCelsius = 50.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.WARNING_LEVEL_1, tempWarn.evaluateTwoLevelStatus(cfg))
        val tempWarnProb = tempWarn.calculateFireProbability(config = cfg)
        assertTrue("Mức 1 Nhiệt độ phải nằm trong [45..74.9], thực tế ${tempWarnProb.totalProbabilityPercent}", tempWarnProb.totalProbabilityPercent in 45f..74.9f)

        val tempAlarm = com.example.domain.model.SmartFireTelemetry(gasRaw = 800, temperatureCelsius = 64.0f)
        assertEquals(com.example.domain.model.SensorAlertLevel.ALARM_LEVEL_2, tempAlarm.evaluateTwoLevelStatus(cfg))
        val tempAlarmProb = tempAlarm.calculateFireProbability(config = cfg)
        assertTrue("Mức 2 Nhiệt độ phải >= 78%, thực tế ${tempAlarmProb.totalProbabilityPercent}", tempAlarmProb.totalProbabilityPercent >= 78f)
    }

    @Test
    fun testSensorBypass_disabledSensorDoesNotTriggerWhileEnabledWorks() {
        // 1. MQ-2 disabled -> high gas does not alert
        val noMq2Cfg = com.example.domain.model.EspDeviceConfig(mq2Enabled = false, tempEnabled = true, flameEnabled = true)
        val highGas = com.example.domain.model.SmartFireTelemetry(gasRaw = 3600, temperatureCelsius = 32.0f, flame = false)
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, highGas.evaluateTwoLevelStatus(noMq2Cfg))
        assertTrue(highGas.calculateFireProbability(config = noMq2Cfg).totalProbabilityPercent < 45f)

        // 2. Temperature disabled -> high temp does not alert
        val noTempCfg = com.example.domain.model.EspDeviceConfig(mq2Enabled = true, tempEnabled = false, flameEnabled = true)
        val highTemp = com.example.domain.model.SmartFireTelemetry(gasRaw = 900, temperatureCelsius = 90.0f, flame = false)
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, highTemp.evaluateTwoLevelStatus(noTempCfg))
        assertTrue(highTemp.calculateFireProbability(config = noTempCfg).totalProbabilityPercent < 45f)

        // 3. Flame disabled -> flame=true suppressed, while enabled Temperature still triggers Level 2
        val noFlameCfg = com.example.domain.model.EspDeviceConfig(mq2Enabled = true, tempEnabled = true, flameEnabled = false)
        val flameOnly = com.example.domain.model.SmartFireTelemetry(gasRaw = 900, temperatureCelsius = 32.0f, flame = true, irFlameIntensity = 0.95f)
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, flameOnly.evaluateTwoLevelStatus(noFlameCfg))
        assertTrue(flameOnly.calculateFireProbability(config = noFlameCfg).totalProbabilityPercent < 45f)

        val flameBypassedPlusHighTemp = com.example.domain.model.SmartFireTelemetry(gasRaw = 900, temperatureCelsius = 65.0f, flame = true)
        assertEquals(com.example.domain.model.SensorAlertLevel.ALARM_LEVEL_2, flameBypassedPlusHighTemp.evaluateTwoLevelStatus(noFlameCfg))
    }

    @Test
    fun testGasFaultAndWarming_doNotTriggerFalseFireAlarm() {
        // Disconnected/rail-fault MQ-2 (ADC 4095, gasFault=true, state=FAULT) must NOT trigger fire alarm
        val faultTelem = com.example.domain.model.SmartFireTelemetry(
            gasRaw = 4095,
            gasReady = false,
            gasFault = true,
            flame = false,
            state = "FAULT"
        )
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, faultTelem.evaluateTwoLevelStatus())
        val faultProb = faultTelem.calculateFireProbability(gasThresholdAdc = 1800)
        assertTrue("MQ-2 FAULT 4095 must stay below 20%, got ${faultProb.totalProbabilityPercent}", faultProb.totalProbabilityPercent < 20f)
        assertTrue(faultProb.riskLevelLabel.contains("LỖI", ignoreCase = true))

        // Warming sensor reading 4095 must NOT trigger false alarm
        val warmingTelem = com.example.domain.model.SmartFireTelemetry(
            gasRaw = 4095,
            gasReady = false,
            gasFault = false,
            flame = false,
            state = "WARMING"
        )
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, warmingTelem.evaluateTwoLevelStatus())
        val warmProb = warmingTelem.calculateFireProbability(gasThresholdAdc = 1800)
        assertTrue("WARMING must stay below 20%, got ${warmProb.totalProbabilityPercent}", warmProb.totalProbabilityPercent < 20f)
    }

    @Test
    fun testDisarmedState_suppressesAlarmAndParsesFromDto() {
        assertEquals(FirmwareState.DISARMED, FirmwareState.fromString("DISARMED"))

        val json = """
        {
          "api_version": 1,
          "device_id": "smartfire-node-01",
          "room_id": "room-101",
          "boot_id": "boot-disarm",
          "latest_seq": 22,
          "status": {
            "uptime_ms": 15000,
            "state": "DISARMED",
            "gas_raw": 2900,
            "flame": false,
            "gas_ready": true,
            "gas_fault": false,
            "alarm": false,
            "muted": false,
            "acknowledged": false,
            "testing": false,
            "disarmed": true,
            "horn_requested": false,
            "horn_enabled": true,
            "gas_commissioned": true
          }
        }
        """.trimIndent()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val parsed = moshi.adapter(StatusResponseDto::class.java).fromJson(json)
        assertNotNull(parsed)
        assertTrue(parsed!!.status.disarmed)
        assertEquals("DISARMED", parsed.status.state)

        val disarmedTelem = com.example.domain.model.SmartFireTelemetry(
            gasRaw = 3500,
            flame = true,
            disarmed = true,
            state = "DISARMED"
        )
        assertEquals(com.example.domain.model.SensorAlertLevel.NORMAL, disarmedTelem.evaluateTwoLevelStatus())
        val prob = disarmedTelem.calculateFireProbability(gasThresholdAdc = 1800)
        assertEquals(0f, prob.totalProbabilityPercent, 0.01f)
        assertTrue(prob.riskLevelLabel.contains("TẮT HẲN", ignoreCase = true))
    }
}
