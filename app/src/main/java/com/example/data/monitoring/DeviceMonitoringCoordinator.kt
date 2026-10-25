package com.example.data.monitoring

import android.content.Context
import com.example.data.local.entity.DeviceEventEntity
import com.example.data.remote.api.SmartFireHttpClientFactory
import com.example.data.repository.SmartFireRepository
import com.example.domain.model.CommandStatus
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.SmartFireTelemetry
import com.example.service.notification.SmartFireNotificationManager
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

    private var masterJob: Job? = null

    fun start() {
        if (masterJob?.isActive == true) return

        masterJob = coordinatorScope.launch {
            // Monitor registered devices list from repository
            repository.allDevices.collect { devices ->
                if (repository.isDemoMode.value) {
                    // Demo mode manages its own simulated ticks
                    return@collect
                }

                val currentDeviceIds = devices.map { it.deviceId }.toSet()

                // Cancel jobs for removed devices
                pollingJobs.keys.filter { it !in currentDeviceIds }.forEach { id ->
                    pollingJobs.remove(id)?.cancel()
                }

                // Launch poller for each active device
                devices.forEach { device ->
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
                if (repository.isDemoMode.value) {
                    delay(2000L)
                    continue
                }

                val currentDevices = repository.allDevices.firstOrNull() ?: emptyList()
                val device = currentDevices.find { it.deviceId == deviceId }

                if (device == null || device.ipAddress.isBlank()) {
                    delay(3000L)
                    continue
                }

                val token = repository.getDeviceToken(deviceId) ?: ""
                val service = SmartFireHttpClientFactory.getService(device.ipAddress, token)

                val response = try {
                    service.getStatus()
                } catch (_: Exception) {
                    null
                }

                val now = System.currentTimeMillis()

                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val status = body.status
                    val prevUptime = lastSeenUptime[deviceId] ?: -1L
                    val isUptimeFrozen = (prevUptime >= 0L && status.uptimeMs == prevUptime && status.uptimeMs > 0)

                    lastSeenTimestamp[deviceId] = now
                    lastSeenUptime[deviceId] = status.uptimeMs

                    val connState = if (isUptimeFrozen) DeviceConnectionState.STALE else DeviceConnectionState.ONLINE_FRESH

                    val telemetry = SmartFireTelemetry(
                        uptimeMs = status.uptimeMs,
                        gasRaw = status.gasRaw,
                        flame = status.flame,
                        gasReady = status.gasReady,
                        gasFault = status.gasFault,
                        alarm = status.alarm,
                        muted = status.muted,
                        acknowledged = status.acknowledged,
                        testing = status.testing,
                        hornRequested = status.hornRequested,
                        hornEnabled = status.hornEnabled,
                        gasCommissioned = status.gasCommissioned,
                        receivedAt = now
                    )

                    // Notify if ALARM state newly detected
                    if (status.alarm && !device.lastKnownAlarm) {
                        notificationManager.showFireAlarmNotification(
                            deviceName = device.displayName,
                            roomName = device.firmwareRoomId.ifBlank { "Khu vực cảm biến" },
                            gasRaw = status.gasRaw,
                            hasFlame = status.flame
                        )
                    }

                    repository.updateTelemetryFromPoller(
                        deviceId = deviceId,
                        telemetry = telemetry,
                        connState = connState,
                        bootId = body.bootId,
                        latestSeq = body.latestSeq
                    )

                    // If latest_seq changed or pending command exists, sync events
                    if (body.latestSeq > device.latestSeq || device.activeCommandStatus == CommandStatus.PENDING) {
                        syncDeviceEvents(deviceId, body.bootId, device.latestSeq, service, device.lastCommandRequestId)
                    }
                } else {
                    // Node unreachable or timeout
                    val lastSeen = lastSeenTimestamp[deviceId] ?: 0L
                    val elapsed = now - lastSeen
                    val connState = if (elapsed > 10_000L || lastSeen == 0L) {
                        DeviceConnectionState.OFFLINE
                    } else {
                        DeviceConnectionState.STALE
                    }
                    repository.updateConnectionOnly(deviceId, connState)
                }
            } catch (_: Exception) {
                repository.updateConnectionOnly(deviceId, DeviceConnectionState.OFFLINE)
            }

            // Normal foreground polling interval ~1.2s
            delay(1200L)
        }
    }

    private suspend fun syncDeviceEvents(
        deviceId: String,
        bootId: String,
        currentSeq: Long,
        service: com.example.data.remote.api.SmartFireApiService,
        pendingRequestId: String
    ) {
        try {
            val response = service.getEvents(since = currentSeq)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val eventEntities = body.events.map { item ->
                    DeviceEventEntity(
                        deviceId = deviceId,
                        bootId = bootId,
                        seq = item.seq,
                        receivedAt = System.currentTimeMillis(),
                        kind = item.kind,
                        requestId = item.requestId ?: "",
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
                    val matching = body.events.find { it.requestId == pendingRequestId }
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
        } catch (_: Exception) {}
    }

    fun stop() {
        masterJob?.cancel()
        masterJob = null
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
    }
}
