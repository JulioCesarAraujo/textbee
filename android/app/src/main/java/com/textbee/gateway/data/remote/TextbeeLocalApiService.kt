package com.textbee.gateway.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

data class ReadyStatusResponse(
    val status: String,
    val engine: String,
    val message: String
)

interface TextbeeLocalApiService {
    @GET("api/v1/gateway/offline/status")
    suspend fun pingReady(
        @Header("x-api-key") apiKey: String?
    ): Response<ReadyStatusResponse>
}
