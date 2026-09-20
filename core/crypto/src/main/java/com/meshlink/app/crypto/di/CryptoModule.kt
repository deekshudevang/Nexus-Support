package com.meshlink.app.crypto.di

import com.meshlink.app.crypto.identity.KeyProvider
import com.meshlink.app.crypto.identity.ProductionKeyManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindKeyProvider(
        keyManager: ProductionKeyManager
    ): KeyProvider
}
