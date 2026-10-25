package com.example.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class SmartFireNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CRITICAL_ALARM = "smartfire_alarm_channel"
        const val CHANNEL_SYSTEM = "smartfire_system_channel"
        private const val NOTIFICATION_ID_ALARM = 1001
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                CHANNEL_CRITICAL_ALARM,
                "Cảnh báo cháy khẩn cấp (SmartFire)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo cảnh báo cháy hoặc phát hiện khói/khí nguy hiểm từ node ESP32"
                enableVibration(true)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Trạng thái hệ thống SmartFire",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo kết nối thiết bị và sự kiện hệ thống"
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    fun showFireAlarmNotification(deviceName: String, roomName: String, gasRaw: Int, hasFlame: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detail = buildString {
            if (hasFlame) append("Phát hiện ngọn lửa! ")
            append("Nồng độ khí MQ-2: $gasRaw ADC. Khu vực: $roomName.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CRITICAL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BÁO ĐỘNG CHÁY: $deviceName")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("CẢNH BÁO NGUY HIỂM TẠI $roomName:\n$detail\nVui lòng mở bản đồ kiểm tra vị trí bình chữa cháy và lối thoát hiểm."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ALARM, notification)
        } catch (_: Exception) {}
    }
}
