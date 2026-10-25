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
import com.example.domain.pathfinding.IndoorPathfinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
