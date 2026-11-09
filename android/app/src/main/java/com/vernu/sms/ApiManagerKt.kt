package com.vernu.sms

import com.vernu.sms.services.GatewayApiServiceKt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiManagerKt {
    @Volatile
    private var instance: GatewayApiServiceKt? = null

    @Volatile
    private var instanceBaseUrl: String? = null

    // Rebuilt when the base URL changes (offline mode switched, or the
    // WebSocket moved to the fallback server), see ApiManager.currentBaseUrl
    fun getApiService(): GatewayApiServiceKt {
        val baseUrl = ApiManager.currentBaseUrl()
        instance?.let { if (instanceBaseUrl == baseUrl) return it }
        return synchronized(this) {
            val current = instance
            if (current != null && instanceBaseUrl == baseUrl) {
                current
            } else {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    // Shares the client that names this app in every request.
                    .client(ApiManager.createHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GatewayApiServiceKt::class.java)
                    .also {
                        instance = it
                        instanceBaseUrl = baseUrl
                    }
            }
        }
    }
}
