package com.example.service.notification

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

class SmartFireNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CRITICAL_ALARM_V2 = "smartfire_alarm_v2"
        const val CHANNEL_WARNING_L1_V2 = "smartfire_warning_l1_v2"
        const val CHANNEL_VIBRATE_ONLY_V2 = "smartfire_vibrate_only_v2"
        const val CHANNEL_SYSTEM = "smartfire_system_channel"
        private const val NOTIFICATION_ID_ALARM = 1001
        private const val NOTIFICATION_ID_WARNING_L1 = 1002
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Trạng thái hệ thống SmartFire",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo kết nối thiết bị và sự kiện hệ thống"
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(vibrateOnlyChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    private fun buildMainPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
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
        alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATE
    ) {
        val channelId = when (alertMode) {
            AlertMode.VIBRATE_ONLY -> CHANNEL_VIBRATE_ONLY_V2
            AlertMode.SILENT -> CHANNEL_SYSTEM
            AlertMode.SOUND_AND_VIBRATE -> CHANNEL_CRITICAL_ALARM_V2
        }
        val detail = buildString {
            if (hasFlame) append("Phát hiện ngọn lửa! ")
            append("Khí MQ-2: $gasRaw ADC · Khu vực: $roomName.")
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MỨC 2 · BÁO CHÁY KHẨN CẤP: $deviceName")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("BÁO ĐỘNG MỨC 2 TẠI $roomName:\n$detail\nNhấn để mở ứng dụng kiểm tra và dẫn đường thoát hiểm."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(buildMainPendingIntent(101))
            .setAutoCancel(true)

        if (alertMode == AlertMode.SILENT) {
            builder.setSilent(true)
        }

        try {
            notificationManager.notify(NOTIFICATION_ID_ALARM, builder.build())
        } catch (_: Exception) {}
    }

    fun showLevel1WarningNotification(
        deviceName: String,
        roomName: String,
        detail: String,
        alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATE
    ) {
        val channelId = when (alertMode) {
            AlertMode.VIBRATE_ONLY -> CHANNEL_VIBRATE_ONLY_V2
            AlertMode.SILENT -> CHANNEL_SYSTEM
            AlertMode.SOUND_AND_VIBRATE -> CHANNEL_WARNING_L1_V2
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MỨC 1 · CẢNH BÁO SỚM: $deviceName")
            .setContentText("$detail ($roomName)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("CẢNH BÁO MỨC 1 TẠI $roomName:\n$detail\nVui lòng kiểm tra cảm biến ngay."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(buildMainPendingIntent(102))
            .setAutoCancel(true)

        if (alertMode == AlertMode.SILENT) {
            builder.setSilent(true)
        }

        try {
            notificationManager.notify(NOTIFICATION_ID_WARNING_L1, builder.build())
        } catch (_: Exception) {}
    }

    fun cancelFireAlarmNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_ALARM)
        } catch (_: Exception) {}
    }

    fun cancelAllAlertNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_ALARM)
            notificationManager.cancel(NOTIFICATION_ID_WARNING_L1)
        } catch (_: Exception) {}
    }
}
