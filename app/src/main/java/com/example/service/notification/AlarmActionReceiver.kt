package com.example.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SmartFireApplication
import com.example.domain.model.AlertMode
import com.example.service.audio.AlarmAudioPlayer
import com.example.service.overlay.EmergencyOverlayPopupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SILENCE_DEVICE = "com.example.smartfire.ACTION_SILENCE_DEVICE"
        const val ACTION_DISMISS_OVERLAY = "com.example.smartfire.ACTION_DISMISS_OVERLAY"
        const val EXTRA_DEVICE_ID = "extra_device_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val appContext = context.applicationContext ?: context
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)?.takeIf { it.isNotBlank() }

        when (intent.action) {
            ACTION_SILENCE_DEVICE -> {
                if (deviceId == null) return
                val app = appContext as? SmartFireApplication ?: return
                val repository = app.repository
                val notificationManager = SmartFireNotificationManager(appContext)

                repository.muteDeviceLocally(deviceId, 30_000L)
                EmergencyOverlayPopupManager.dismissOverlay(appContext)
                notificationManager.cancelFireAlarmNotification(deviceId)
                AlarmAudioPlayer.stopManagedAlert("bg_alarm_$deviceId")

                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val devices = repository.allDevices.firstOrNull().orEmpty()
                        val resolvedId = devices.find {
                            it.deviceId == deviceId || it.displayName == deviceId
                        }?.deviceId ?: deviceId

                        if (resolvedId != deviceId) {
                            repository.muteDeviceLocally(resolvedId, 30_000L)
                            notificationManager.cancelFireAlarmNotification(resolvedId)
                            AlarmAudioPlayer.stopManagedAlert("bg_alarm_$resolvedId")
                        }

                        val latched = repository.offlineFireLatchedDevices.value
                        val savedConfig = repository.savedEspConfig.value
                        val otherUnmutedAlarm = devices.firstOrNull { node ->
                            node.deviceId != resolvedId &&
                                node.deviceId != deviceId &&
                                node.isEffectivelyAlarming(savedConfig) &&
                                !node.suspectedBurnedOffline &&
                                node.deviceId !in latched &&
                                !repository.isDeviceMutedNow(node.deviceId)
                        }

                        if (otherUnmutedAlarm == null) {
                            AlarmAudioPlayer.stopManagedAlert(null)
                        } else if (
                            repository.phoneAlarmSoundEnabled.value &&
                            repository.alertMode.value != AlertMode.SILENT
                        ) {
                            AlarmAudioPlayer.startManagedAlert(
                                context = appContext,
                                sessionId = "bg_alarm_${otherUnmutedAlarm.deviceId}",
                                alertMode = repository.alertMode.value,
                                soundType = repository.alarmSoundType.value,
                                volumePercent = repository.alarmVolumePercent.value,
                                isLevel1Warning = false
                            )
                        }

                        repository.sendCommand(resolvedId, "silence")
                    } catch (_: Throwable) {
                        // Ignore command transport exceptions in background broadcast
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_DISMISS_OVERLAY -> {
                EmergencyOverlayPopupManager.dismissOverlay(appContext)
                if (deviceId != null) {
                    AlarmAudioPlayer.stopManagedAlert("bg_alarm_$deviceId")
                } else {
                    AlarmAudioPlayer.stopManagedAlert(null)
                }
            }
        }
    }
}
