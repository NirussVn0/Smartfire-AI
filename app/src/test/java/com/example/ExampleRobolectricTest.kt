package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.EspConfigBackupManager
import com.example.data.backup.ExportedAppSettings
import com.example.data.backup.ExportedEspDevice
import com.example.data.backup.SmartFireConfigBackup
import com.example.data.local.SmartFireDatabase
import com.example.data.local.entity.HomeEntity
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.EspDeviceConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testAppNameString() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SmartFire AI", appName)
    }

    @Test
    fun testDatabaseInitialization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = SmartFireDatabase.getInstance(context)
        val dao = db.smartFireDao()

        dao.insertHome(
            HomeEntity(
                id = "test_home_1",
                name = "Trường THPT 12D3",
                buildingType = "Trường học"
            )
        )

        val homes = dao.getAllHomes().first()
        assertNotNull(homes)
        assertEquals(1, homes.count { it.id == "test_home_1" })
    }

    @Test
    fun testDemoModeMerge_preservesRealEsp32DevicesAndAvoidsIdCollision() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SmartFireRepository(context)

        repo.registerDevice(
            deviceId = "smartfire-node-01",
            displayName = "ESP32 Phòng Khách",
            homeId = "home_main",
            floorId = "floor_1",
            roomId = "room_main",
            ipAddress = "192.168.1.150",
            token = SmartFireRepository.DEFAULT_TOKEN,
            isDemo = false
        )

        repo.toggleDemoMode(true)
        val mergedDevices = repo.allDevices.first()
        assertTrue("Real ESP32 node must stay present when Demo Mode is enabled", mergedDevices.any { it.deviceId == "smartfire-node-01" && !it.isDemoSensor })
        assertTrue("Demo nodes must use separate demo-sov-* IDs", mergedDevices.any { it.deviceId == "demo-sov-01" && it.isDemoSensor })

        // Attempting to register a demo sensor with colliding ID must not overwrite real node
        repo.registerDevice(
            deviceId = "smartfire-node-01",
            displayName = "Demo Collision Test",
            homeId = "home_main",
            floorId = "floor_1",
            roomId = "room_main",
            ipAddress = "192.168.1.199",
            token = SmartFireRepository.DEFAULT_TOKEN,
            isDemo = true
        )
        val afterDemoReg = repo.allDevices.first()
        val realNode = afterDemoReg.find { it.deviceId == "smartfire-node-01" }
        assertNotNull(realNode)
        assertFalse("Real ESP32 node must not be overwritten as demo", realNode!!.isDemoSensor)
        assertEquals("192.168.1.150", realNode.ipAddress)
    }

    @Test
    fun testMultiDeviceIndependence_separateRoomAlarmsAndPerDeviceMute() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SmartFireRepository(context)

        repo.muteDeviceLocally("esp-room-a", 30_000L)
        assertTrue("Room A must be muted", repo.isDeviceMutedNow("esp-room-a"))
        assertFalse("Muting Room A must NOT mute Room B", repo.isDeviceMutedNow("esp-room-b"))

        repo.sendCommand("esp-room-b", "test")
        assertFalse("Test Còi on Room B must keep Room B unmuted", repo.isDeviceMutedNow("esp-room-b"))
        assertTrue("Room A mute state must remain independent", repo.isDeviceMutedNow("esp-room-a"))
    }

    @Test
    fun testDisconnectDuringFire_preservesHazardWithoutRepeatAlarm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SmartFireRepository(context)

        repo.toggleDemoMode(true)
        repo.triggerDemoScenario("KITCHEN_ALARM")
        val alarmingNode = repo.allDevices.first().find { it.deviceId == "demo-sov-02" }
        assertNotNull(alarmingNode)
        assertTrue(alarmingNode!!.lastKnownAlarm)

        repo.triggerDemoScenario("DISCONNECT_DURING_ALARM")
        val burnedOfflineNode = repo.allDevices.first().find { it.deviceId == "demo-sov-02" }
        assertNotNull(burnedOfflineNode)
        assertTrue("Offline fire hazard must remain latched", burnedOfflineNode!!.lastKnownAlarm)
        assertTrue("suspectedBurnedOffline must be true so alarm stops repeating", burnedOfflineNode.suspectedBurnedOffline)
        assertTrue("demo-sov-02 must be in offlineFireLatchedDevices", "demo-sov-02" in repo.offlineFireLatchedDevices.value)

        repo.clearOfflineBurnedAlarm("demo-sov-02")
        val resetNode = repo.allDevices.first().find { it.deviceId == "demo-sov-02" }
        assertNotNull(resetNode)
        assertFalse("Manual reset must clear offline burned alarm", resetNode!!.lastKnownAlarm)
        assertFalse(resetNode.suspectedBurnedOffline)
    }

    @Test
    fun testEspConfigBackupManager_jsonExportImportRoundTrip() {
        val backup = SmartFireConfigBackup(
            includeTokens = true,
            devices = listOf(
                ExportedEspDevice(
                    deviceId = "esp-kitchen-01",
                    displayName = "Cảm biến Bếp",
                    ipAddress = "192.168.1.88",
                    bearerToken = "secret-token-1234567890123456",
                    xNormalized = 0.42f,
                    yNormalized = 0.65f,
                    isDemoSensor = false
                )
            ),
            espConfig = EspDeviceConfig(
                mq2Enabled = true,
                gasWarn = 1750,
                gasOn = 2350,
                gasOff = 1500,
                tempEnabled = true,
                tempWarnC = 44.0f,
                tempAlarmC = 58.0f,
                flameEnabled = true,
                flameWarnMs = 150,
                flameAlarmMs = 500,
                hornEnabled = true,
                gasCommissioned = true,
                txPower1m = -47,
                pathLossN = 2.5f
            ),
            appSettings = ExportedAppSettings(
                pollingIntervalMs = 1000L,
                httpTimeoutSeconds = 6,
                alarmVolumePercent = 90
            )
        )

        val json = EspConfigBackupManager.toJson(backup)
        val parsedResult = EspConfigBackupManager.fromJson(json)
        assertTrue(parsedResult.isSuccess)
        val parsed = parsedResult.getOrThrow()
        assertEquals(1, parsed.devices.size)
        assertEquals("esp-kitchen-01", parsed.devices.first().deviceId)
        assertEquals("192.168.1.88", parsed.devices.first().ipAddress)
        assertEquals("secret-token-1234567890123456", parsed.devices.first().bearerToken)
        assertEquals(2350, parsed.espConfig.gasOn)
        assertEquals(58.0f, parsed.espConfig.tempAlarmC, 0.1f)
        assertEquals(6, parsed.appSettings.httpTimeoutSeconds)

        // Verify token omission when includeTokens = false
        val redactedJson = EspConfigBackupManager.toJson(backup.copy(includeTokens = false))
        val redactedParsed = EspConfigBackupManager.fromJson(redactedJson).getOrThrow()
        assertNull(redactedParsed.devices.first().bearerToken)
    }
}
