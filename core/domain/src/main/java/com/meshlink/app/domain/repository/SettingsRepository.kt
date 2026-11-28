package com.meshlink.app.domain.repository

import com.meshlink.app.domain.model.SyncTelemetry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persisted application settings and backend connectivity config.
 */
interface SettingsRepository {
    val useNearby: Flow<Boolean>
    val useMeshtastic: Flow<Boolean>
    val batteryOptimization: Flow<Boolean>
    val crashReportingOptIn: Flow<Boolean>

    val backendBaseUrl: Flow<String>
    val gatewayKey: Flow<String>
    val syncTelemetry: Flow<SyncTelemetry>

    fun getEffectiveBackendBaseUrl(): String
    fun getEffectiveGatewayKey(): String

    suspend fun setUseNearby(enabled: Boolean)
    suspend fun setUseMeshtastic(enabled: Boolean)
    suspend fun setBatteryOptimization(enabled: Boolean)
    suspend fun setCrashReportingOptIn(enabled: Boolean)

    suspend fun setBackendBaseUrl(url: String)
    suspend fun setGatewayKey(key: String)
    suspend fun recordSyncResult(
        timestampMs: Long,
        success: Boolean,
        syncedCount: Int = 0,
        httpStatus: Int? = null,
        error: String? = null
    )
}
