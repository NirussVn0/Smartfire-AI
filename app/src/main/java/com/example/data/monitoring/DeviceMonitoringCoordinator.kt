package com.example.data.monitoring

import android.content.Context
import com.example.data.local.entity.DeviceEventEntity
import com.example.data.remote.api.SmartFireHttpClientFactory
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.CommandStatus
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.FirmwareState
import com.example.domain.model.SmartFireTelemetry
import com.example.service.notification.SmartFireNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DeviceMonitoringCoordinator(
    private val repository: SmartFireRepository,
    context: Context
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = SmartFireNotificationManager(context)
    private val pollingJobs = ConcurrentHashMap<String, Job>()
    private val lastSeenTimestamp = ConcurrentHashMap<String, Long>()
    private val lastSeenUptime = ConcurrentHashMap<String, Long>()
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()
    private val rssiFilters = ConcurrentHashMap<String, com.example.domain.model.RssiKalmanFilter>()

    private var masterJob: Job? = null

    fun start() {
        if (masterJob?.isActive == true) return

        masterJob = coordinatorScope.launch {
            // Monitor registered devices list from repository (real ESP32s always polled even in Demo Mode)
            repository.allDevices.collect { devices ->
                val realDevices = devices.filterNot { d ->
                    d.isDemoSensor ||
                        d.displayName.contains("DEMO", ignoreCase = true) ||
                        d.firmwareRoomId.startsWith("demo") ||
                        d.firmwareRoomId.startsWith("sov-")
                }
                val currentDeviceIds = realDevices.map { it.deviceId }.toSet()

                // Cancel jobs for removed or demo devices
                pollingJobs.keys.filter { it !in currentDeviceIds }.forEach { id ->
                    pollingJobs.remove(id)?.cancel()
                    consecutiveFailures.remove(id)
                    lastSeenTimestamp.remove(id)
                    lastSeenUptime.remove(id)
                    rssiFilters.remove(id)
                }

                // Launch poller only for real ESP32 devices
                realDevices.forEach { device ->
                    if (!pollingJobs.containsKey(device.deviceId)) {
                        val job = coordinatorScope.launch {
                            pollDeviceLoop(device.deviceId)
                        }
                        pollingJobs[device.deviceId] = job
                    }
                }
            }
        }
    }

    private suspend fun pollDeviceLoop(deviceId: String) {
        while (coordinatorScope.isActive) {
            try {
                val currentDevices = repository.allDevices.firstOrNull() ?: emptyList()
                val device = currentDevices.find { it.deviceId == deviceId }

                if (device == null || device.ipAddress.isBlank()) {
                    delay(3000L)
                    continue
                }

                if (device.isDemoSensor || device.displayName.contains("DEMO", ignoreCase = true) || device.firmwareRoomId.startsWith("demo")) {
                    repository.updateConnectionOnly(deviceId, DeviceConnectionState.ONLINE_FRESH)
                    delay(3000L)
                    continue
                }

                val token = repository.getDeviceToken(deviceId)?.ifBlank { null } ?: SmartFireRepository.DEFAULT_TOKEN
                val timeoutSec = repository.httpTimeoutSeconds.value
                val service = SmartFireHttpClientFactory.getService(device.ipAddress, token, timeoutSec)

                val response = SmartFireHttpClientFactory.withDeviceLock(device.ipAddress) {
                    var resp = try {
                        service.getStatus()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    // 1 fast retry on transient Wi-Fi packet loss to prevent false OFFLINE flickers
                    if (resp == null || !resp.isSuccessful) {
                        delay(150L)
                        resp = try {
                            service.getStatus()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                    resp
                }

                val now = System.currentTimeMillis()

                if (response != null && response.isSuccessful && response.body() != null) {
                    consecutiveFailures[deviceId] = 0

                    val body = response.body()!!
                    val status = body.status
                    val prevUptime = lastSeenUptime[deviceId] ?: -1L
                    val isUptimeFrozen = (prevUptime >= 0L && status.uptimeMs == prevUptime && status.uptimeMs > 0)

                    lastSeenTimestamp[deviceId] = now
                    lastSeenUptime[deviceId] = status.uptimeMs

                    val connState = if (isUptimeFrozen) DeviceConnectionState.STALE else DeviceConnectionState.ONLINE_FRESH

                    val prevAlarmStart = device.telemetry.alarmStartedAtMs
                    val alarmStartMs = if (status.alarm) {
                        if (prevAlarmStart > 0L) prevAlarmStart else now
                    } else {
                        0L
                    }

                    val rssiFilter = rssiFilters.getOrPut(deviceId) { com.example.domain.model.RssiKalmanFilter() }
                    val smoothedRssi = status.wifiRssi?.let { rssiFilter.update(it) } ?: device.telemetry.rssiDbm

                    val telemetry = SmartFireTelemetry(
                        uptimeMs = status.uptimeMs,
                        temperatureCelsius = status.temperatureCelsius,
                        humidityPercent = status.humidityPercent,
                        coPpm = status.coPpm,
                        tempRateOfRiseCPerMin = status.tempRateOfRise,
                        irFlameIntensity = if (status.flame) 0.88f else null,
                        rssiDbm = smoothedRssi,
                        wifiReconnects = status.wifiReconnects ?: 0L,
                        txPower1m = status.txPower1m ?: -45,
                        pathLossN = status.pathLossN ?: 2.4f,
                        mq2Enabled = status.mq2Enabled ?: true,
                        gasWarnThreshold = status.gasWarn ?: 1800,
                        gasOnThreshold = status.gasOn ?: 2400,
                        gasOffThreshold = status.gasOff ?: 1600,
                        tempEnabled = status.tempEnabled ?: true,
                        tempWarnC = status.tempWarnC ?: 45.0f,
                        tempAlarmC = status.tempAlarmC ?: 60.0f,
                        flameEnabled = status.flameEnabled ?: true,
                        flameWarnMs = status.flameWarnMs ?: 150,
                        flameAlarmMs = status.flameAlarmMs ?: 500,
                        firmwareVersion = status.firmwareVersion ?: "",
                        alarmStartedAtMs = alarmStartMs,
                        gasRaw = status.gasRaw,
                        flame = status.flame,
                        gasReady = status.gasReady,
                        gasFault = status.gasFault,
                        alarm = status.alarm,
                        muted = status.muted,
                        acknowledged = status.acknowledged,
                        testing = status.testing,
                        disarmed = status.disarmed,
                        hornRequested = status.hornRequested,
                        hornEnabled = status.hornEnabled,
                        gasCommissioned = status.gasCommissioned,
                        state = status.state.ifBlank { "NORMAL" },
                        receivedAt = now
                    )

                    val wasLatchedOfflineFire = deviceId in repository.offlineFireLatchedDevices.value

                    repository.updateTelemetryFromPoller(
                        deviceId = deviceId,
                        telemetry = telemetry,
                        connState = connState,
                        bootId = body.bootId,
                        latestSeq = body.latestSeq
                    )

                    val savedConfig = repository.savedEspConfig.value
                    val updatedEpisodeId = repository.getAlarmEpisodeId(deviceId)
                    val updatedNode = device.copy(
                        lastSeenAt = now,
                        bootId = body.bootId,
                        latestSeq = body.latestSeq,
                        firmwareState = FirmwareState.fromString(telemetry.state),
                        connectionState = connState,
                        lastKnownAlarm = telemetry.alarm,
                        telemetry = telemetry,
                        suspectedBurnedOffline = false,
                        isCachedOfflineData = false,
                        alarmEpisodeId = updatedEpisodeId
                    )

                    val isEffectivelyAlarm = updatedNode.isEffectivelyAlarming(savedConfig)
                    val isEffectivelyWarn1 = updatedNode.isEffectivelyWarningLevel1(savedConfig)
                    val alreadyAlertedEpisode = repository.alertedAlarmEpisodeByDevice.value[deviceId] ?: 0L
                    val isDeviceMuted = repository.isDeviceMutedNow(deviceId)
                    val alertMode = repository.alertMode.value

                    // Notify ONLY on a new alarm episode when not muted and not previously offline-fire-latched
                    if (isEffectivelyAlarm &&
                        updatedEpisodeId > 0L &&
                        updatedEpisodeId != alreadyAlertedEpisode &&
                        !wasLatchedOfflineFire &&
                        !isDeviceMuted
                    ) {
                        repository.markAlarmEpisodeAlerted(deviceId, updatedEpisodeId)
                        notificationManager.showFireAlarmNotification(
                            deviceName = device.displayName,
                            roomName = device.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                            gasRaw = status.gasRaw,
                            hasFlame = status.flame,
                            alertMode = alertMode,
                            deviceId = deviceId,
                            temperatureCelsius = status.temperatureCelsius
                        )
                        repository.emitNewlyTriggeredAlarm(updatedNode)
                    } else if (isEffectivelyWarn1 &&
                        device.firmwareState != FirmwareState.SUSPECTED &&
                        repository.level1PopupEnabled.value &&
                        !isDeviceMuted
                    ) {
                        notificationManager.showLevel1WarningNotification(
                            deviceName = device.displayName,
                            roomName = device.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                            detail = "Phát hiện vượt ngưỡng Mức 1 (Gas=${status.gasRaw} ADC, Lửa=${if (status.flame) "CÓ" else "Không"})",
                            alertMode = alertMode,
                            deviceId = deviceId
                        )
                    }

                    // If latest_seq changed or pending command exists, sync events
                    if (body.latestSeq > device.latestSeq || device.activeCommandStatus == CommandStatus.PENDING) {
                        syncDeviceEvents(
                            deviceId = deviceId,
                            ipAddress = device.ipAddress,
                            bootId = body.bootId,
                            currentSeq = device.latestSeq,
                            service = service,
                            pendingRequestId = device.lastCommandRequestId
                        )
                    }
                } else {
                    // Node unreachable or timeout: preserve cached telemetry and latch fire hazard if alarming
                    consecutiveFailures[deviceId] = (consecutiveFailures[deviceId] ?: 0) + 1
                    val lastSeen = lastSeenTimestamp[deviceId] ?: device.lastSeenAt
                    val elapsed = if (lastSeen > 0L) now - lastSeen else Long.MAX_VALUE
                    val connState = if (elapsed > 10_000L || lastSeen == 0L) {
                        DeviceConnectionState.OFFLINE
                    } else {
                        DeviceConnectionState.STALE
                    }
                    repository.updateConnectionOnly(deviceId, connState)
                    if (connState == DeviceConnectionState.OFFLINE &&
                        (device.lastKnownAlarm || device.firmwareState == FirmwareState.ALARM)
                    ) {
                        repository.markDeviceOfflineDuringFire(deviceId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                consecutiveFailures[deviceId] = (consecutiveFailures[deviceId] ?: 0) + 1
                repository.updateConnectionOnly(deviceId, DeviceConnectionState.OFFLINE)
                val current = repository.allDevices.firstOrNull()?.find { it.deviceId == deviceId }
                if (current != null && (current.lastKnownAlarm || current.firmwareState == FirmwareState.ALARM)) {
                    repository.markDeviceOfflineDuringFire(deviceId)
                }
            }

            // Dynamic polling interval with adaptive offline backoff
            val baseIntervalMs = repository.pollingIntervalMs.value.coerceIn(800L, 10000L)
            val fails = consecutiveFailures[deviceId] ?: 0
            val delayMs = when {
                fails <= 0 -> baseIntervalMs
                fails == 1 -> maxOf(baseIntervalMs, 1500L)
                fails == 2 -> maxOf(baseIntervalMs, 2500L)
                fails == 3 -> maxOf(baseIntervalMs, 4000L)
                else -> maxOf(baseIntervalMs, 6000L)
            }
            delay(delayMs)
        }
    }

    private suspend fun syncDeviceEvents(
        deviceId: String,
        ipAddress: String,
        bootId: String,
        currentSeq: Long,
        service: com.example.data.remote.api.SmartFireApiService,
        pendingRequestId: String
    ) {
        try {
            val response = SmartFireHttpClientFactory.withDeviceLock(ipAddress) {
                service.getEvents(since = currentSeq)
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val eventEntities = body.events.map { item ->
                    DeviceEventEntity(
                        deviceId = deviceId,
                        bootId = bootId,
                        seq = item.seq,
                        receivedAt = System.currentTimeMillis(),
                        kind = item.kind,
                        requestId = item.requestIdString,
                        result = item.result ?: "",
                        state = item.status?.state ?: "",
                        rawPayload = "flame=${item.status?.flame}, gas_raw=${item.status?.gasRaw}"
                    )
                }

                repository.syncEvents(
                    deviceId = deviceId,
                    bootId = bootId,
                    latestSeq = body.latestSeq,
                    oldestSeq = body.oldestSeq,
                    events = eventEntities
                )

                // Resolve pending command if matching requestId arrived
                if (pendingRequestId.isNotBlank()) {
                    val matching = body.events.find { it.requestIdString == pendingRequestId }
                    if (matching != null) {
                        val status = when (matching.result?.lowercase()) {
                            "applied" -> CommandStatus.APPLIED
                            "rejected" -> CommandStatus.REJECTED
                            "expired" -> CommandStatus.EXPIRED
                            else -> CommandStatus.APPLIED
                        }
                        repository.updateCommandResolution(deviceId, status)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    fun stop() {
        masterJob?.cancel()
        masterJob = null
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
        consecutiveFailures.clear()
    }
}
