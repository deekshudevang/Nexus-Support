package com.meshlink.app.data.remote

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.meshlink.app/v1/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (BASE_URL == "https://api.meshlink.app/v1/") {
                    throw IOException("Backend is not yet deployed. Meshlink is operating in offline-only mode.")
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideMeshBackendService(okHttpClient: OkHttpClient): MeshBackendService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)
    }
}
