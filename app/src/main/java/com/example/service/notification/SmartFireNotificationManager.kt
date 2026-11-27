package com.example.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AlertMode
import com.example.ui.alert.FireAlarmAlertActivity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SmartFireNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CRITICAL_ALARM_V2 = "smartfire_alarm_v2"
        const val CHANNEL_WARNING_L1_V2 = "smartfire_warning_l1_v2"
        const val CHANNEL_VIBRATE_ONLY_V2 = "smartfire_vibrate_only_v2"
        const val CHANNEL_SYSTEM = "smartfire_system_channel"
        const val CHANNEL_MONITORING_SERVICE = "smartfire_monitoring_service"
        const val FG_NOTIFICATION_ID = 1000
        private const val NOTIFICATION_ID_ALARM = 1001
        private const val NOTIFICATION_ID_WARNING_L1 = 1002

        private val activeAlarmIds = ConcurrentHashMap.newKeySet<Int>()
        private val activeWarningIds = ConcurrentHashMap.newKeySet<Int>()

        fun alarmNotificationId(deviceId: String): Int =
            2000 + (deviceId.hashCode() and 0x7FFFFFFF) % 1000

        fun warningNotificationId(deviceId: String): Int =
            4000 + (deviceId.hashCode() and 0x7FFFFFFF) % 1000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    fun alarmNotificationId(deviceId: String): Int = Companion.alarmNotificationId(deviceId)

    fun warningNotificationId(deviceId: String): Int = Companion.warningNotificationId(deviceId)

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val alarmAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALARM_V2,
                "Mức 2 · Báo cháy khẩn cấp (SmartFire)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nổi kèm còi báo động cháy khẩn cấp từ node ESP32"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmUri, alarmAudioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val warnUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val warnAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val warningChannel = NotificationChannel(
                CHANNEL_WARNING_L1_V2,
                "Mức 1 · Cảnh báo sớm (SmartFire)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nổi cảnh báo sớm khi vượt ngưỡng Mức 1"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(warnUri, warnAudioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val vibrateOnlyChannel = NotificationChannel(
                CHANNEL_VIBRATE_ONLY_V2,
                "Cảnh báo chỉ rung (SmartFire)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nổi chỉ rung không phát âm thanh"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Trạng thái hệ thống SmartFire",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo kết nối thiết bị và sự kiện hệ thống"
            }

            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING_SERVICE,
                "Giám sát nền SmartFire",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Trạng thái dịch vụ giám sát liên tục cảm biến báo cháy SmartFire"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(vibrateOnlyChannel)
            notificationManager.createNotificationChannel(systemChannel)
            notificationManager.createNotificationChannel(monitoringChannel)
        }
    }

    private fun buildMainPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildFullScreenAlertPendingIntent(
        deviceId: String,
        deviceName: String,
        roomName: String,
        gasRaw: Int,
        hasFlame: Boolean,
        temperatureCelsius: Float?
    ): PendingIntent {
        val intent = Intent(context, FireAlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FireAlarmAlertActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(FireAlarmAlertActivity.EXTRA_DEVICE_NAME, deviceName)
            putExtra(FireAlarmAlertActivity.EXTRA_ROOM_NAME, roomName)
            putExtra(FireAlarmAlertActivity.EXTRA_GAS_RAW, gasRaw)
            putExtra(FireAlarmAlertActivity.EXTRA_HAS_FLAME, hasFlame)
            if (temperatureCelsius != null) {
                putExtra(FireAlarmAlertActivity.EXTRA_TEMP_C, temperatureCelsius)
            }
        }
        val requestCode = 3000 + (deviceId.hashCode() and 0x7FFFFFFF) % 1000
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSilenceBroadcastPendingIntent(deviceId: String): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_SILENCE_DEVICE
            putExtra(AlarmActionReceiver.EXTRA_DEVICE_ID, deviceId)
        }
        val requestCode = 5000 + (deviceId.hashCode() and 0x7FFFFFFF) % 1000
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildEvacuateActivityPendingIntent(deviceId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_EVACUATE_DEVICE_ID, deviceId)
        }
        val requestCode = 6000 + (deviceId.hashCode() and 0x7FFFFFFF) % 1000
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showFireAlarmNotification(
        deviceName: String,
        roomName: String,
        gasRaw: Int,
        hasFlame: Boolean,
        alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATE,
        deviceId: String = deviceName,
        temperatureCelsius: Float? = null
    ) {
        val channelId = when (alertMode) {
            AlertMode.VIBRATE_ONLY -> CHANNEL_VIBRATE_ONLY_V2
            AlertMode.SILENT -> CHANNEL_SYSTEM
            AlertMode.SOUND_AND_VIBRATE -> CHANNEL_CRITICAL_ALARM_V2
        }
        val detail = buildString {
            if (hasFlame) append("Phát hiện ngọn lửa! ")
            if (temperatureCelsius != null) {
                append("Nhiệt: ${String.format(Locale.US, "%.1f°C", temperatureCelsius)} · ")
            }
            append("Khí MQ-2: $gasRaw ADC · Khu vực: $roomName.")
        }

        val notifId = alarmNotificationId(deviceId)
        val fullScreenPendingIntent = buildFullScreenAlertPendingIntent(
            deviceId = deviceId,
            deviceName = deviceName,
            roomName = roomName,
            gasRaw = gasRaw,
            hasFlame = hasFlame,
            temperatureCelsius = temperatureCelsius
        )
        val silencePendingIntent = buildSilenceBroadcastPendingIntent(deviceId)
        val evacuatePendingIntent = buildEvacuateActivityPendingIntent(deviceId)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MỨC 2 · BÁO CHÁY KHẨN CẤP: $deviceName")
            .setContentText(detail)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("BÁO ĐỘNG MỨC 2 TẠI $roomName:\n$detail\nNhấn để mở ứng dụng kiểm tra và dẫn đường thoát hiểm.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildMainPendingIntent(notifId))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Im 30s", silencePendingIntent)
            .addAction(0, "Thoát hiểm", evacuatePendingIntent)
            .setAutoCancel(true)

        if (alertMode == AlertMode.SILENT) {
            builder.setSilent(true)
        }

        try {
            activeAlarmIds.add(notifId)
            notificationManager.notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    fun showLevel1WarningNotification(
        deviceName: String,
        roomName: String,
        detail: String,
        alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATE,
        deviceId: String = deviceName
    ) {
        val channelId = when (alertMode) {
            AlertMode.VIBRATE_ONLY -> CHANNEL_VIBRATE_ONLY_V2
            AlertMode.SILENT -> CHANNEL_SYSTEM
            AlertMode.SOUND_AND_VIBRATE -> CHANNEL_WARNING_L1_V2
        }
        val notifId = warningNotificationId(deviceId)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MỨC 1 · CẢNH BÁO SỚM: $deviceName")
            .setContentText("$detail ($roomName)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("CẢNH BÁO MỨC 1 TẠI $roomName:\n$detail\nVui lòng kiểm tra cảm biến ngay.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildMainPendingIntent(notifId))
            .setAutoCancel(true)

        if (alertMode == AlertMode.SILENT) {
            builder.setSilent(true)
        }

        try {
            activeWarningIds.add(notifId)
            notificationManager.notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    fun buildMonitoringForegroundNotification(
        onlineCount: Int,
        totalCount: Int,
        activeAlarmNames: List<String>,
        offlineBurnedCount: Int
    ): Notification {
        val (title, text) = when {
            activeAlarmNames.isNotEmpty() -> {
                "SmartFire · ĐANG BÁO CHÁY (${activeAlarmNames.size})" to
                    "Khẩn cấp tại: ${activeAlarmNames.joinToString(", ")}"
            }
            offlineBurnedCount > 0 -> {
                "SmartFire · Cảnh báo mất kết nối khi cháy ($offlineBurnedCount)" to
                    "$onlineCount/$totalCount thiết bị đang kết nối · Kiểm tra ngay"
            }
            totalCount == 0 -> {
                "SmartFire · Sẵn sàng giám sát" to
                    "Chưa có node ESP32 nào được đăng ký"
            }
            else -> {
                "SmartFire · Đang giám sát ($onlineCount/$totalCount online)" to
                    "Hệ thống phòng cháy thông minh đang hoạt động liên tục"
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MONITORING_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(buildMainPendingIntent(FG_NOTIFICATION_ID))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    fun cancelFireAlarmNotification(deviceId: String? = null) {
        try {
            if (deviceId != null) {
                val id = alarmNotificationId(deviceId)
                activeAlarmIds.remove(id)
                notificationManager.cancel(id)
                notificationManager.cancel(NOTIFICATION_ID_ALARM)
            } else {
                notificationManager.cancel(NOTIFICATION_ID_ALARM)
                val ids = activeAlarmIds.toList()
                activeAlarmIds.clear()
                ids.forEach { notificationManager.cancel(it) }
            }
        } catch (_: Exception) {}
    }

    fun cancelWarningNotification(deviceId: String) {
        try {
            val id = warningNotificationId(deviceId)
            activeWarningIds.remove(id)
            notificationManager.cancel(id)
            notificationManager.cancel(NOTIFICATION_ID_WARNING_L1)
        } catch (_: Exception) {}
    }

    fun cancelAllAlertNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_ALARM)
            notificationManager.cancel(NOTIFICATION_ID_WARNING_L1)
            val alarmIds = activeAlarmIds.toList()
            activeAlarmIds.clear()
            alarmIds.forEach { notificationManager.cancel(it) }
            val warningIds = activeWarningIds.toList()
            activeWarningIds.clear()
            warningIds.forEach { notificationManager.cancel(it) }
        } catch (_: Exception) {}
    }
}
