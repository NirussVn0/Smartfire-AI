package com.example.service.monitoring

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.SmartFireApplication
import com.example.domain.model.AlertMode
import com.example.domain.model.DeviceConnectionState
import com.example.domain.model.MapNavigationRequest
import com.example.domain.model.MapNavigationTargetType
import com.example.service.audio.AlarmAudioPlayer
import com.example.service.notification.SmartFireNotificationManager
import com.example.service.overlay.EmergencyOverlayPopupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SmartFireMonitoringService : Service() {

    private data class MonitoringSummary(
        val onlineCount: Int,
        val totalCount: Int,
        val activeAlarmNames: List<String>,
        val offlineBurnedCount: Int
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeBgAlarmDeviceIds = ConcurrentHashMap.newKeySet<String>()
    private val previouslyAlarmingIds = ConcurrentHashMap.newKeySet<String>()

    private var summaryJob: Job? = null
    private var alarmEventJob: Job? = null
    private var lastSummary: MonitoringSummary? = null

    private lateinit var notificationManager: SmartFireNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = SmartFireNotificationManager(applicationContext)

        val initialNotification = notificationManager.buildMonitoringForegroundNotification(
            onlineCount = 0,
            totalCount = 0,
            activeAlarmNames = emptyList(),
            offlineBurnedCount = 0
        )
        startForegroundCompat(initialNotification)

        val app = SmartFireApplication.instance
        app.monitoringCoordinator.start()
        startMonitoringCollectors(app)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SmartFireApplication.instance.monitoringCoordinator.start()
        val current = lastSummary
        if (current == null) {
            val notif = notificationManager.buildMonitoringForegroundNotification(
                onlineCount = 0,
                totalCount = 0,
                activeAlarmNames = emptyList(),
                offlineBurnedCount = 0
            )
            startForegroundCompat(notif)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeBgAlarmDeviceIds.forEach { deviceId ->
            AlarmAudioPlayer.stopManagedAlert("bg_alarm_$deviceId")
        }
        activeBgAlarmDeviceIds.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        val fgServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            SmartFireNotificationManager.FG_NOTIFICATION_ID,
            notification,
            fgServiceType
        )
    }

    private fun startMonitoringCollectors(app: SmartFireApplication) {
        val repository = app.repository

        if (summaryJob?.isActive != true) {
            summaryJob = serviceScope.launch {
                combine(
                    repository.allDevices,
                    repository.mutedUntilByDevice,
                    repository.offlineFireLatchedDevices,
                    repository.localAlarmMutedUntilMs,
                    repository.savedEspConfig
                ) { devices, mutedMap, latchedSet, localMutedUntil, savedConfig ->
                    val now = System.currentTimeMillis()
                    val totalCount = devices.size
                    val onlineCount = devices.count {
                        it.connectionState == DeviceConnectionState.ONLINE_FRESH ||
                            it.connectionState == DeviceConnectionState.STALE
                    }
                    val offlineBurnedCount = devices.count {
                        it.suspectedBurnedOffline || it.deviceId in latchedSet
                    }

                    val activeAlarmNodes = devices.filter { node ->
                        node.isEffectivelyAlarming(savedConfig) &&
                            !node.suspectedBurnedOffline &&
                            node.deviceId !in latchedSet
                    }

                    val unmutedActiveIds = activeAlarmNodes.filter { node ->
                        val deviceMutedUntil = mutedMap[node.deviceId] ?: 0L
                        now >= deviceMutedUntil && now >= localMutedUntil
                    }.map { it.deviceId }.toSet()

                    val activeIds = activeAlarmNodes.map { it.deviceId }.toSet()
                    val clearedIds = previouslyAlarmingIds.filter { it !in activeIds }
                    clearedIds.forEach { clearedId ->
                        previouslyAlarmingIds.remove(clearedId)
                        notificationManager.cancelFireAlarmNotification(clearedId)
                    }
                    previouslyAlarmingIds.addAll(activeIds)

                    // Stop background siren immediately for muted/cleared/offline-latched nodes
                    val staleBgIds = activeBgAlarmDeviceIds.filter { it !in unmutedActiveIds }
                    staleBgIds.forEach { id ->
                        activeBgAlarmDeviceIds.remove(id)
                        AlarmAudioPlayer.stopManagedAlert("bg_alarm_$id")
                    }

                    if (unmutedActiveIds.isEmpty()) {
                        if (!app.isAppInForeground.value) {
                            AlarmAudioPlayer.stopManagedAlert(null)
                            EmergencyOverlayPopupManager.dismissOverlay(applicationContext)
                        }
                    }

                    MonitoringSummary(
                        onlineCount = onlineCount,
                        totalCount = totalCount,
                        activeAlarmNames = activeAlarmNodes.map { it.displayName },
                        offlineBurnedCount = offlineBurnedCount
                    )
                }.distinctUntilChanged().collect { summary ->
                    lastSummary = summary
                    val updatedNotif = notificationManager.buildMonitoringForegroundNotification(
                        onlineCount = summary.onlineCount,
                        totalCount = summary.totalCount,
                        activeAlarmNames = summary.activeAlarmNames,
                        offlineBurnedCount = summary.offlineBurnedCount
                    )
                    try {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.notify(SmartFireNotificationManager.FG_NOTIFICATION_ID, updatedNotif)
                    } catch (_: Throwable) {}
                }
            }
        }

        if (alarmEventJob?.isActive != true) {
            alarmEventJob = serviceScope.launch {
                repository.newlyTriggeredAlarmEvent.collect { node ->
                    val isMuted = repository.isDeviceMutedNow(node.deviceId)
                    val isLatchedOffline = node.suspectedBurnedOffline ||
                        node.deviceId in repository.offlineFireLatchedDevices.value
                    if (isMuted || isLatchedOffline) return@collect

                    if (node.alarmEpisodeId > 0L) {
                        repository.markAlarmEpisodeAlerted(node.deviceId, node.alarmEpisodeId)
                    }

                    if (!app.isAppInForeground.value) {
                        val roomName = node.firmwareRoomId.ifBlank { "Khu vực cảm biến" }
                        // Remove legacy displayName-keyed notification if coordinator posted one
                        if (node.displayName.isNotBlank() && node.displayName != node.deviceId) {
                            notificationManager.cancelFireAlarmNotification(node.displayName)
                        }

                        notificationManager.showFireAlarmNotification(
                            deviceName = node.displayName,
                            roomName = roomName,
                            gasRaw = node.telemetry.gasRaw,
                            hasFlame = node.telemetry.flame,
                            alertMode = repository.alertMode.value,
                            deviceId = node.deviceId,
                            temperatureCelsius = node.telemetry.temperatureCelsius
                        )

                        if (EmergencyOverlayPopupManager.canDrawOverlay(this@SmartFireMonitoringService)) {
                            EmergencyOverlayPopupManager.showOverlay(
                                context = applicationContext,
                                deviceId = node.deviceId,
                                deviceName = node.displayName,
                                gasRaw = node.telemetry.gasRaw,
                                tempC = node.telemetry.temperatureCelsius,
                                hasFlame = node.telemetry.flame,
                                onEvacuate = {
                                    repository.muteDeviceLocally(node.deviceId, 180_000L)
                                    repository.muteLocalNotificationsTemporarily(180_000L)
                                    activeBgAlarmDeviceIds.remove(node.deviceId)
                                    AlarmAudioPlayer.stopManagedAlert(null)
                                    notificationManager.cancelFireAlarmNotification(node.deviceId)
                                    repository.requestMapNavigation(
                                        MapNavigationRequest(
                                            targetType = MapNavigationTargetType.NEAREST_EXIT,
                                            targetDeviceId = node.deviceId
                                        )
                                    )
                                    val evacuateIntent = Intent(this@SmartFireMonitoringService, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra(MainActivity.EXTRA_EVACUATE_DEVICE_ID, node.deviceId)
                                    }
                                    try {
                                        startActivity(evacuateIntent)
                                    } catch (_: Throwable) {}
                                },
                                onSilence = {
                                    repository.muteDeviceLocally(node.deviceId, 30_000L)
                                    activeBgAlarmDeviceIds.remove(node.deviceId)
                                    AlarmAudioPlayer.stopManagedAlert("bg_alarm_${node.deviceId}")
                                    notificationManager.cancelFireAlarmNotification(node.deviceId)
                                    serviceScope.launch(Dispatchers.IO) {
                                        val devices = repository.allDevices.firstOrNull().orEmpty()
                                        val latched = repository.offlineFireLatchedDevices.value
                                        val cfg = repository.savedEspConfig.value
                                        val hasOther = devices.any { other ->
                                            other.deviceId != node.deviceId &&
                                                other.isEffectivelyAlarming(cfg) &&
                                                !other.suspectedBurnedOffline &&
                                                other.deviceId !in latched &&
                                                !repository.isDeviceMutedNow(other.deviceId)
                                        }
                                        if (!hasOther) {
                                            AlarmAudioPlayer.stopManagedAlert(null)
                                        }
                                        repository.sendCommand(node.deviceId, "silence")
                                    }
                                }
                            )
                        }

                        if (repository.phoneAlarmSoundEnabled.value &&
                            repository.alertMode.value != AlertMode.SILENT
                        ) {
                            activeBgAlarmDeviceIds.add(node.deviceId)
                            AlarmAudioPlayer.startManagedAlert(
                                context = applicationContext,
                                sessionId = "bg_alarm_${node.deviceId}",
                                alertMode = repository.alertMode.value,
                                soundType = repository.alarmSoundType.value,
                                volumePercent = repository.alarmVolumePercent.value,
                                isLevel1Warning = false
                            )
                        }
                    }
                }
            }
        }
    }
}
