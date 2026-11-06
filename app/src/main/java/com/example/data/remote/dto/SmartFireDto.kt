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
    @Json(name = "horn_requested") val hornRequested: Boolean = false,
    @Json(name = "horn_enabled") val hornEnabled: Boolean = false,
    @Json(name = "gas_commissioned") val gasCommissioned: Boolean = false,
    @Json(name = "temperature_celsius") val temperatureCelsius: Float? = null,
    @Json(name = "humidity_percent") val humidityPercent: Float? = null,
    @Json(name = "co_ppm") val coPpm: Int? = null,
    @Json(name = "temp_rate_of_rise") val tempRateOfRise: Float? = null
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
