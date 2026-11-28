package com.meshlink.app.data.remote

import com.meshlink.app.data.BuildConfig
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    internal const val PLACEHOLDER_HOST = "meshlink.local"
    private const val PLACEHOLDER_BASE_URL = "https://$PLACEHOLDER_HOST/"

    @Provides
    @Singleton
    fun provideOkHttpClient(settingsRepository: SettingsRepository): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val targetBaseStr = settingsRepository.getEffectiveBackendBaseUrl()
                    .ifBlank { BuildConfig.BACKEND_BASE_URL }
                @Suppress("DEPRECATION")
                val targetBase = HttpUrl.parse(targetBaseStr)

                val requestBuilder = original.newBuilder()
                @Suppress("DEPRECATION")
                val origUrl = original.url()

                @Suppress("DEPRECATION")
                if (targetBase != null && origUrl.host() == PLACEHOLDER_HOST) {
                    val basePathSegments = targetBase.encodedPathSegments().filter { it.isNotEmpty() }
                    val reqPathSegments = origUrl.encodedPathSegments().filter { it.isNotEmpty() }
                    val mergedPath = "/" + (basePathSegments + reqPathSegments).joinToString("/")

                    val newUrl = origUrl.newBuilder()
                        .scheme(targetBase.scheme())
                        .host(targetBase.host())
                        .port(targetBase.port())
                        .encodedPath(mergedPath)
                        .build()
                    requestBuilder.url(newUrl)
                }

                val effectiveKey = settingsRepository.getEffectiveGatewayKey()
                    .ifBlank { BuildConfig.GATEWAY_KEY }
                    .trim()
                if (effectiveKey.isNotEmpty()) {
                    if (original.header("X-Gateway-Key") == null) {
                        requestBuilder.header("X-Gateway-Key", effectiveKey)
                    }
                    if (original.header("Authorization") == null) {
                        requestBuilder.header("Authorization", "Bearer $effectiveKey")
                    }
                }
                requestBuilder.header("Accept", "application/json")

                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideMeshBackendService(okHttpClient: OkHttpClient): MeshBackendService {
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)
    }
}
