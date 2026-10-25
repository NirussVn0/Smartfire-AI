package com.example.data.remote.api

import com.example.data.remote.dto.CommandResponseDto
import com.example.data.remote.dto.EventsResponseDto
import com.example.data.remote.dto.StatusResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
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
}
