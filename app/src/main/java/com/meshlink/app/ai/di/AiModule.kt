package com.meshlink.app.ai.di

import com.meshlink.app.ai.coordinator.OfflineAiCoordinatorImpl
import com.meshlink.app.ai.data.ModelAssetManagerImpl
import com.meshlink.app.ai.domain.LocalAiEngine
import com.meshlink.app.ai.domain.ModelAssetManager
import com.meshlink.app.ai.domain.OfflineAiCoordinator
import com.meshlink.app.ai.domain.EmergencyRagService
import com.meshlink.app.ai.domain.LocalEmbeddingService
import com.meshlink.app.ai.inference.MediaPipeLocalAiEngine
import com.meshlink.app.ai.rag.EmergencyRagServiceImpl
import com.meshlink.app.ai.rag.LocalEmbeddingServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindLocalAiEngine(
        impl: MediaPipeLocalAiEngine
    ): LocalAiEngine

    @Binds
    @Singleton
    abstract fun bindModelAssetManager(
        impl: ModelAssetManagerImpl
    ): ModelAssetManager

    @Binds
    @Singleton
    abstract fun bindOfflineAiCoordinator(
        impl: OfflineAiCoordinatorImpl
    ): OfflineAiCoordinator

    @Binds
    @Singleton
    abstract fun bindEmergencyRagService(
        impl: EmergencyRagServiceImpl
    ): EmergencyRagService

    @Binds
    @Singleton
    abstract fun bindLocalEmbeddingService(
        impl: LocalEmbeddingServiceImpl
    ): LocalEmbeddingService

    @Binds
    @Singleton
    abstract fun bindMessageSummaryService(
        impl: com.meshlink.app.ai.data.MessageSummaryServiceImpl
    ): com.meshlink.app.ai.domain.MessageSummaryService
}
