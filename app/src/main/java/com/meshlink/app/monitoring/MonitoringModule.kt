package com.meshlink.app.monitoring

import com.meshlink.app.domain.monitoring.ErrorReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringModule {

    @Binds
    @Singleton
    abstract fun bindErrorReporter(impl: PrivacyAwareErrorReporter): ErrorReporter
}
