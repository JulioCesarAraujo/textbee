package com.textbee.gateway.di

import com.textbee.gateway.data.local.ServerPreferencesDataSource
import com.textbee.gateway.data.repository.ServerConfigRepositoryImpl
import com.textbee.gateway.domain.repository.ServerConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TextbeeRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindServerConfigRepository(
        impl: ServerConfigRepositoryImpl
    ): ServerConfigRepository
}

@Module
@InstallIn(SingletonComponent::class)
object TextbeeNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
