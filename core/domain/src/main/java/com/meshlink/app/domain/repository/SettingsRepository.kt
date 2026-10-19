package com.meshlink.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persisted application settings.
 */
interface SettingsRepository {
    val useNearby: Flow<Boolean>
    val useMeshtastic: Flow<Boolean>
    val batteryOptimization: Flow<Boolean>
    val crashReportingOptIn: Flow<Boolean>

    suspend fun setUseNearby(enabled: Boolean)
    suspend fun setUseMeshtastic(enabled: Boolean)
    suspend fun setBatteryOptimization(enabled: Boolean)
    suspend fun setCrashReportingOptIn(enabled: Boolean)
}
