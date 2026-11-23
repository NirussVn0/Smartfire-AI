package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusResponseDto(
    @Json(name = "api_version") val apiVersion: Int? = 1,
    @Json(name = "device_id") val deviceId: String = "",
    @Json(name = "room_id") val roomId: String? = "",
    @Json(name = "boot_id") val bootId: String = "",
    @Json(name = "latest_seq") val latestSeq: Long = 0L,
    @Json(name = "status") val status: StatusPayloadDto = StatusPayloadDto()
)

@JsonClass(generateAdapter = true)
data class StatusPayloadDto(
    @Json(name = "uptime_ms") val uptimeMs: Long = 0L,
    @Json(name = "state") val state: String = "UNKNOWN",
    @Json(name = "gas_raw") val gasRaw: Int = 0,
    @Json(name = "flame") val flame: Boolean = false,
    @Json(name = "gas_ready") val gasReady: Boolean = false,
    @Json(name = "gas_fault") val gasFault: Boolean = false,
    @Json(name = "alarm") val alarm: Boolean = false,
    @Json(name = "muted") val muted: Boolean = false,
    @Json(name = "acknowledged") val acknowledged: Boolean = false,
    @Json(name = "testing") val testing: Boolean = false,
    @Json(name = "disarmed") val disarmed: Boolean = false,
    @Json(name = "horn_requested") val hornRequested: Boolean = false,
    @Json(name = "horn_enabled") val hornEnabled: Boolean = false,
    @Json(name = "gas_commissioned") val gasCommissioned: Boolean = false,
    @Json(name = "temperature_celsius") val temperatureCelsius: Float? = null,
    @Json(name = "humidity_percent") val humidityPercent: Float? = null,
    @Json(name = "co_ppm") val coPpm: Int? = null,
    @Json(name = "temp_rate_of_rise") val tempRateOfRise: Float? = null,
    @Json(name = "wifi_rssi") val wifiRssi: Int? = null,
    @Json(name = "wifi_rssi_raw") val wifiRssiRaw: Int? = null,
    @Json(name = "wifi_reconnects") val wifiReconnects: Long? = null,
    @Json(name = "gas_warn") val gasWarn: Int? = null,
    @Json(name = "gas_on") val gasOn: Int? = null,
    @Json(name = "gas_off") val gasOff: Int? = null,
    @Json(name = "mq2_enabled") val mq2Enabled: Boolean? = null,
    @Json(name = "flame_enabled") val flameEnabled: Boolean? = null,
    @Json(name = "temp_enabled") val tempEnabled: Boolean? = null,
    @Json(name = "temp_warn_c") val tempWarnC: Float? = null,
    @Json(name = "temp_alarm_c") val tempAlarmC: Float? = null,
    @Json(name = "flame_warn_ms") val flameWarnMs: Int? = null,
    @Json(name = "flame_alarm_ms") val flameAlarmMs: Int? = null,
    @Json(name = "tx_power_1m") val txPower1m: Int? = null,
    @Json(name = "path_loss_n") val pathLossN: Float? = null,
    @Json(name = "firmware_version") val firmwareVersion: String? = null
)

@JsonClass(generateAdapter = true)
data class EventsResponseDto(
    @Json(name = "api_version") val apiVersion: Int? = 1,
    @Json(name = "device_id") val deviceId: String = "",
    @Json(name = "room_id") val roomId: String? = "",
    @Json(name = "boot_id") val bootId: String = "",
    @Json(name = "latest_seq") val latestSeq: Long = 0L,
    @Json(name = "oldest_seq") val oldestSeq: Long = 0L,
    @Json(name = "events") val events: List<EventItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EventItemDto(
    @Json(name = "seq") val seq: Long = 0L,
    @Json(name = "kind") val kind: String = "state", // state, button, ack, silence, test
    @Json(name = "request_id") val requestId: Long? = null,
    @Json(name = "result") val result: String? = null, // none, applied, rejected, expired
    @Json(name = "status") val status: StatusPayloadDto? = null
) {
    val requestIdString: String get() = requestId?.toString() ?: ""
}

@JsonClass(generateAdapter = true)
data class CommandResponseDto(
    @Json(name = "queued") val queued: Boolean? = true,
    @Json(name = "request_id") val requestId: Long? = null,
    @Json(name = "boot_id") val bootId: String? = null,
    @Json(name = "message") val message: String? = null
) {
    val requestIdString: String get() = requestId?.toString() ?: ""
}

@JsonClass(generateAdapter = true)
data class EspConfigDto(
    @Json(name = "mq2_enabled") val mq2Enabled: Boolean = true,
    @Json(name = "gas_warn") val gasWarn: Int = 1800,
    @Json(name = "gas_on") val gasOn: Int = 2400,
    @Json(name = "gas_off") val gasOff: Int = 1600,
    @Json(name = "temp_enabled") val tempEnabled: Boolean = true,
    @Json(name = "temp_warn_c") val tempWarnC: Float = 45.0f,
    @Json(name = "temp_alarm_c") val tempAlarmC: Float = 60.0f,
    @Json(name = "flame_enabled") val flameEnabled: Boolean = true,
    @Json(name = "flame_warn_ms") val flameWarnMs: Int = 150,
    @Json(name = "flame_alarm_ms") val flameAlarmMs: Int = 500,
    @Json(name = "horn_enabled") val hornEnabled: Boolean = false,
    @Json(name = "gas_commissioned") val gasCommissioned: Boolean = false,
    @Json(name = "tx_power_1m") val txPower1m: Int = -45,
    @Json(name = "path_loss_n") val pathLossN: Float = 2.4f,
    @Json(name = "firmware_version") val firmwareVersion: String? = null,
    @Json(name = "revision") val revision: Long? = null
)

@JsonClass(generateAdapter = true)
data class EspConfigApplyResponseDto(
    @Json(name = "applied") val applied: Boolean = false,
    @Json(name = "config") val config: EspConfigDto? = null,
    @Json(name = "error") val error: String? = null
)

@JsonClass(generateAdapter = true)
data class OtaUploadResponseDto(
    @Json(name = "ota") val ota: String = "",
    @Json(name = "rebooting") val rebooting: Boolean = false,
    @Json(name = "previous_version") val previousVersion: String? = null,
    @Json(name = "error") val error: String? = null
)
