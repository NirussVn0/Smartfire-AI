package com.example.data.remote.api

import com.example.data.remote.dto.CommandResponseDto
import com.example.data.remote.dto.EspConfigApplyResponseDto
import com.example.data.remote.dto.EspConfigDto
import com.example.data.remote.dto.EventsResponseDto
import com.example.data.remote.dto.OtaUploadResponseDto
import com.example.data.remote.dto.StatusResponseDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface SmartFireApiService {

    @GET("v1/status")
    suspend fun getStatus(): Response<StatusResponseDto>

    @GET("v1/events")
    suspend fun getEvents(
        @Query("since") since: Long
    ): Response<EventsResponseDto>

    @POST("v1/commands/ack")
    suspend fun sendAcknowledge(): Response<CommandResponseDto>

    @POST("v1/commands/silence")
    suspend fun sendSilence(): Response<CommandResponseDto>

    @POST("v1/commands/test")
    suspend fun sendTest(): Response<CommandResponseDto>

    @POST("v1/commands/disarm")
    suspend fun sendDisarm(): Response<CommandResponseDto>

    @POST("v1/commands/arm")
    suspend fun sendArm(): Response<CommandResponseDto>

    @GET("v1/config")
    suspend fun getConfig(): Response<EspConfigDto>

    @POST("v1/config")
    suspend fun pushConfig(
        @Body config: EspConfigDto
    ): Response<EspConfigApplyResponseDto>

    @Multipart
    @POST("v1/ota")
    suspend fun uploadOtaFirmware(
        @Part firmware: MultipartBody.Part
    ): Response<OtaUploadResponseDto>
}
