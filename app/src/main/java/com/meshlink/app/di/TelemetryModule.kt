package com.meshlink.app.di

import com.meshlink.app.telemetry.CrashReporter
import com.meshlink.app.telemetry.PrivacySafeCrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {

    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: PrivacySafeCrashReporter): CrashReporter
}
