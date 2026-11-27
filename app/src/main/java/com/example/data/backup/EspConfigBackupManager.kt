package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.domain.model.EspDeviceConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedEspDevice(
    val deviceId: String,
    val displayName: String,
    val ipAddress: String,
    val bearerToken: String? = null,
    val homeId: String = "home_main",
    val floorId: String = "floor_1",
    val roomId: String = "room_main",
    val firmwareRoomId: String = "room-101",
    val xNormalized: Float = 0.5f,
    val yNormalized: Float = 0.5f,
    val isDemoSensor: Boolean = false
)

data class ExportedAppSettings(
    val pollingIntervalMs: Long = 1500L,
    val httpTimeoutSeconds: Int = 5,
    val alertMode: String = "SOUND_AND_VIBRATE",
    val alarmSoundType: String = "FIRE_SIREN",
    val alarmVolumePercent: Int = 85,
    val level1PopupEnabled: Boolean = true,
    val autoRouteOnAlarm: Boolean = true,
    val stepStrideMeters: Float = 0.65f
)

data class SmartFireConfigBackup(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val includeTokens: Boolean = true,
    val devices: List<ExportedEspDevice>,
    val espConfig: EspDeviceConfig,
    val appSettings: ExportedAppSettings
)

object EspConfigBackupManager {

    private const val APP_NAME = "SmartFire AI"
    private const val CURRENT_SCHEMA_VERSION = 1

    fun defaultFileName(timestampMs: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        return "smartfire_esp32_config_${formatter.format(Date(timestampMs))}.json"
    }

    fun toJson(backup: SmartFireConfigBackup): String {
        val root = JSONObject().apply {
            put("app", APP_NAME)
            put("schema_version", backup.schemaVersion)
            put("exported_at_ms", backup.exportedAtMs)
            put("include_tokens", backup.includeTokens)
            put("devices", serializeDevices(backup.devices, backup.includeTokens))
            put("esp_config", serializeEspConfig(backup.espConfig))
            put("app_settings", serializeAppSettings(backup.appSettings))
        }
        return root.toString(2)
    }

    fun fromJson(rawJson: String): Result<SmartFireConfigBackup> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Tệp sao lưu trống, không có dữ liệu JSON để nhập.")
            )
        }

        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            return Result.failure(
                IllegalArgumentException("Tệp JSON không hợp lệ hoặc bị lỗi cú pháp: ${e.message ?: "không xác định"}")
            )
        }

        val hasDevices = root.has("devices")
        val hasEspConfig = root.has("esp_config") || root.has("espConfig")
        if (!hasDevices && !hasEspConfig) {
            return Result.failure(
                IllegalArgumentException("Tệp sao lưu không hợp lệ: thiếu cả danh sách thiết bị (devices) lẫn cấu hình ESP32 (esp_config).")
            )
        }

        val devicesArray = if (hasDevices) {
            root.optJSONArray("devices")
                ?: return Result.failure(
                    IllegalArgumentException("Trường 'devices' trong tệp JSON không đúng định dạng mảng.")
                )
        } else {
            null
        }

        val espConfigKey = if (root.has("esp_config")) "esp_config" else "espConfig"
        val espConfigObj = if (hasEspConfig) {
            root.optJSONObject(espConfigKey)
                ?: return Result.failure(
                    IllegalArgumentException("Trường 'esp_config' trong tệp JSON không đúng định dạng đối tượng.")
                )
        } else {
            null
        }

        val schemaVersion = root.optIntCompat("schema_version", "schemaVersion", CURRENT_SCHEMA_VERSION)
            .coerceAtLeast(1)
        val exportedAtMs = root.optLongCompat("exported_at_ms", "exportedAtMs", System.currentTimeMillis())
            .coerceAtLeast(0L)
        val includeTokens = root.optBooleanCompat("include_tokens", "includeTokens", true)

        val devices = parseDevices(devicesArray, includeTokens)
        val espConfig = parseEspConfig(espConfigObj)
        val appSettingsObj = root.optJSONObject("app_settings") ?: root.optJSONObject("appSettings")
        val appSettings = parseAppSettings(appSettingsObj)

        return Result.success(
            SmartFireConfigBackup(
                schemaVersion = schemaVersion,
                exportedAtMs = exportedAtMs,
                includeTokens = includeTokens,
                devices = devices,
                espConfig = espConfig,
                appSettings = appSettings
            )
        )
    }

    fun writeToUri(context: Context, uri: Uri, json: String): Result<Unit> {
        return runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Không thể mở luồng ghi tới tệp đã chọn.")
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json)
                writer.flush()
            }
        }
    }

    fun readFromUri(context: Context, uri: Uri): Result<String> {
        return runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Không thể mở tệp cấu hình đã chọn để đọc.")
            val content = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            if (content.isBlank()) {
                throw IllegalArgumentException("Tệp cấu hình được chọn không có nội dung.")
            }
            content
        }
    }

    private fun serializeDevices(
        devices: List<ExportedEspDevice>,
        includeTokens: Boolean
    ): JSONArray {
        return JSONArray().apply {
            devices.forEach { device ->
                put(
                    JSONObject().apply {
                        put("device_id", device.deviceId)
                        put("display_name", device.displayName)
                        put("ip_address", device.ipAddress)
                        if (includeTokens && !device.bearerToken.isNullOrBlank()) {
                            put("bearer_token", device.bearerToken)
                        } else {
                            put("bearer_token", JSONObject.NULL)
                        }
                        put("home_id", device.homeId)
                        put("floor_id", device.floorId)
                        put("room_id", device.roomId)
                        put("firmware_room_id", device.firmwareRoomId)
                        put("x_normalized", device.xNormalized.toDouble())
                        put("y_normalized", device.yNormalized.toDouble())
                        put("is_demo_sensor", device.isDemoSensor)
                    }
                )
            }
        }
    }

    private fun serializeEspConfig(config: EspDeviceConfig): JSONObject {
        return JSONObject().apply {
            put("mq2_enabled", config.mq2Enabled)
            put("gas_warn", config.gasWarn)
            put("gas_on", config.gasOn)
            put("gas_off", config.gasOff)
            put("temp_enabled", config.tempEnabled)
            put("temp_warn_c", config.tempWarnC.toDouble())
            put("temp_alarm_c", config.tempAlarmC.toDouble())
            put("flame_enabled", config.flameEnabled)
            put("flame_warn_ms", config.flameWarnMs)
            put("flame_alarm_ms", config.flameAlarmMs)
            put("horn_enabled", config.hornEnabled)
            put("gas_commissioned", config.gasCommissioned)
            put("tx_power_1m", config.txPower1m)
            put("path_loss_n", config.pathLossN.toDouble())
            put("firmware_version", config.firmwareVersion)
        }
    }

    private fun serializeAppSettings(settings: ExportedAppSettings): JSONObject {
        return JSONObject().apply {
            put("polling_interval_ms", settings.pollingIntervalMs)
            put("http_timeout_seconds", settings.httpTimeoutSeconds)
            put("alert_mode", settings.alertMode)
            put("alarm_sound_type", settings.alarmSoundType)
            put("alarm_volume_percent", settings.alarmVolumePercent)
            put("level1_popup_enabled", settings.level1PopupEnabled)
            put("auto_route_on_alarm", settings.autoRouteOnAlarm)
            put("step_stride_meters", settings.stepStrideMeters.toDouble())
        }
    }

    private fun parseDevices(array: JSONArray?, includeTokens: Boolean): List<ExportedEspDevice> {
        if (array == null) return emptyList()
        val result = ArrayList<ExportedEspDevice>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val deviceId = obj.optStringCompat("device_id", "deviceId", "").trim()
            val ipAddress = obj.optStringCompat("ip_address", "ipAddress", "").trim()
            if (deviceId.isEmpty() && ipAddress.isEmpty()) continue

            val resolvedId = deviceId.ifEmpty { "esp_${i + 1}" }
            val displayName = obj.optStringCompat("display_name", "displayName", resolvedId)
                .trim()
                .ifEmpty { resolvedId }
            val rawToken = if (includeTokens) {
                obj.optNullableStringCompat("bearer_token", "bearerToken")
            } else {
                null
            }

            result.add(
                ExportedEspDevice(
                    deviceId = resolvedId,
                    displayName = displayName,
                    ipAddress = ipAddress,
                    bearerToken = rawToken,
                    homeId = obj.optStringCompat("home_id", "homeId", "home_main")
                        .trim()
                        .ifEmpty { "home_main" },
                    floorId = obj.optStringCompat("floor_id", "floorId", "floor_1")
                        .trim()
                        .ifEmpty { "floor_1" },
                    roomId = obj.optStringCompat("room_id", "roomId", "room_main")
                        .trim()
                        .ifEmpty { "room_main" },
                    firmwareRoomId = obj.optStringCompat("firmware_room_id", "firmwareRoomId", "room-101")
                        .trim()
                        .ifEmpty { "room-101" },
                    xNormalized = obj.optFloatClamped("x_normalized", "xNormalized", 0.5f, 0.0f, 1.0f),
                    yNormalized = obj.optFloatClamped("y_normalized", "yNormalized", 0.5f, 0.0f, 1.0f),
                    isDemoSensor = obj.optBooleanCompat("is_demo_sensor", "isDemoSensor", false)
                )
            )
        }
        return result
    }

    private fun parseEspConfig(obj: JSONObject?): EspDeviceConfig {
        val defaults = EspDeviceConfig()
        if (obj == null) return defaults

        val safeGasOn = obj.optIntCompat("gas_on", "gasOn", defaults.gasOn).coerceIn(800, 3900)
        val safeGasWarn = obj.optIntCompat("gas_warn", "gasWarn", defaults.gasWarn)
            .coerceIn(500, (safeGasOn - 150).coerceAtLeast(500))
        val safeGasOff = obj.optIntCompat("gas_off", "gasOff", defaults.gasOff)
            .coerceIn(400, (safeGasWarn - 100).coerceAtLeast(400))

        val safeTempAlarm = obj.optFloatClamped("temp_alarm_c", "tempAlarmC", defaults.tempAlarmC, 35.0f, 120.0f)
        val safeTempWarn = obj.optFloatClamped(
            "temp_warn_c",
            "tempWarnC",
            defaults.tempWarnC,
            25.0f,
            (safeTempAlarm - 4.0f).coerceAtLeast(25.0f)
        )

        val safeFlameAlarmMs = obj.optIntCompat("flame_alarm_ms", "flameAlarmMs", defaults.flameAlarmMs)
            .coerceIn(100, 5000)
        val safeFlameWarnMs = obj.optIntCompat("flame_warn_ms", "flameWarnMs", defaults.flameWarnMs)
            .coerceIn(0, safeFlameAlarmMs)

        val safeTxPower1m = obj.optIntCompat("tx_power_1m", "txPower1m", defaults.txPower1m)
            .coerceIn(-75, -20)
        val safePathLossN = obj.optFloatClamped("path_loss_n", "pathLossN", defaults.pathLossN, 1.6f, 4.5f)

        return EspDeviceConfig(
            mq2Enabled = obj.optBooleanCompat("mq2_enabled", "mq2Enabled", defaults.mq2Enabled),
            gasWarn = safeGasWarn,
            gasOn = safeGasOn,
            gasOff = safeGasOff,
            tempEnabled = obj.optBooleanCompat("temp_enabled", "tempEnabled", defaults.tempEnabled),
            tempWarnC = safeTempWarn,
            tempAlarmC = safeTempAlarm,
            flameEnabled = obj.optBooleanCompat("flame_enabled", "flameEnabled", defaults.flameEnabled),
            flameWarnMs = safeFlameWarnMs,
            flameAlarmMs = safeFlameAlarmMs,
            hornEnabled = obj.optBooleanCompat("horn_enabled", "hornEnabled", defaults.hornEnabled),
            gasCommissioned = obj.optBooleanCompat("gas_commissioned", "gasCommissioned", defaults.gasCommissioned),
            txPower1m = safeTxPower1m,
            pathLossN = safePathLossN,
            firmwareVersion = obj.optStringCompat("firmware_version", "firmwareVersion", defaults.firmwareVersion).trim()
        )
    }

    private fun parseAppSettings(obj: JSONObject?): ExportedAppSettings {
        val defaults = ExportedAppSettings()
        if (obj == null) return defaults

        return ExportedAppSettings(
            pollingIntervalMs = obj.optLongCompat("polling_interval_ms", "pollingIntervalMs", defaults.pollingIntervalMs)
                .coerceIn(800L, 10000L),
            httpTimeoutSeconds = obj.optIntCompat("http_timeout_seconds", "httpTimeoutSeconds", defaults.httpTimeoutSeconds)
                .coerceIn(1, 10),
            alertMode = obj.optStringCompat("alert_mode", "alertMode", defaults.alertMode)
                .trim()
                .ifEmpty { defaults.alertMode },
            alarmSoundType = obj.optStringCompat("alarm_sound_type", "alarmSoundType", defaults.alarmSoundType)
                .trim()
                .ifEmpty { defaults.alarmSoundType },
            alarmVolumePercent = obj.optIntCompat("alarm_volume_percent", "alarmVolumePercent", defaults.alarmVolumePercent)
                .coerceIn(10, 100),
            level1PopupEnabled = obj.optBooleanCompat("level1_popup_enabled", "level1PopupEnabled", defaults.level1PopupEnabled),
            autoRouteOnAlarm = obj.optBooleanCompat("auto_route_on_alarm", "autoRouteOnAlarm", defaults.autoRouteOnAlarm),
            stepStrideMeters = obj.optFloatClamped("step_stride_meters", "stepStrideMeters", defaults.stepStrideMeters, 0.35f, 1.2f)
        )
    }

    private fun JSONObject.optStringCompat(primary: String, fallback: String, default: String): String {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return default
        }
        return optString(key, default)
    }

    private fun JSONObject.optNullableStringCompat(primary: String, fallback: String): String? {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return null
        }
        return optString(key, "").trim().ifEmpty { null }
    }

    private fun JSONObject.optBooleanCompat(primary: String, fallback: String, default: Boolean): Boolean {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return default
        }
        return optBoolean(key, default)
    }

    private fun JSONObject.optIntCompat(primary: String, fallback: String, default: Int): Int {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return default
        }
        val raw = opt(key)
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun JSONObject.optLongCompat(primary: String, fallback: String, default: Long): Long {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return default
        }
        val raw = opt(key)
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: default
            else -> default
        }
    }

    private fun JSONObject.optFloatClamped(
        primary: String,
        fallback: String,
        default: Float,
        min: Float,
        max: Float
    ): Float {
        val key = when {
            has(primary) && !isNull(primary) -> primary
            has(fallback) && !isNull(fallback) -> fallback
            else -> return default.coerceIn(min, max)
        }
        val raw = opt(key)
        val parsed = when (raw) {
            is Number -> raw.toFloat()
            is String -> raw.trim().toFloatOrNull() ?: default
            else -> default
        }
        return if (parsed.isNaN() || parsed.isInfinite()) {
            default.coerceIn(min, max)
        } else {
            parsed.coerceIn(min, max)
        }
    }
}
