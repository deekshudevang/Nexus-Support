package com.meshlink.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlink.app.data.BuildConfig
import com.meshlink.app.data.security.GatewaySecretStore
import com.meshlink.app.data.security.TinkGatewaySecretStore
import com.meshlink.app.domain.model.SyncTelemetry
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meshlink_settings")

@Singleton
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: GatewaySecretStore? = null
) : SettingsRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        dataStore = context.settingsDataStore,
        secretStore = TinkGatewaySecretStore(context)
    )

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedBackendBaseUrl: String = normalizeBackendBaseUrl(
        BuildConfig.BACKEND_BASE_URL,
        allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP
    )

    private val _gatewayKeyFlow = MutableStateFlow(
        secretStore?.getGatewayKey()?.takeIf { it.isNotBlank() } ?: BuildConfig.GATEWAY_KEY
    )

    init {
        syncScope.launch {
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) emit(emptyPreferences()) else throw exception
                }
                .collect { prefs ->
                    val storedUrl = prefs[KEY_BACKEND_BASE_URL]?.trim()
                    cachedBackendBaseUrl = if (!storedUrl.isNullOrBlank()) {
                        normalizeBackendBaseUrl(storedUrl, allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP)
                    } else {
                        normalizeBackendBaseUrl(BuildConfig.BACKEND_BASE_URL, allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP)
                    }
                }
        }
    }

    companion object {
        val KEY_USE_NEARBY = booleanPreferencesKey("use_nearby")
        val KEY_USE_MESHTASTIC = booleanPreferencesKey("use_meshtastic")
        val KEY_BATTERY_OPTIMIZATION = booleanPreferencesKey("battery_optimization")
        val KEY_CRASH_REPORTING_OPT_IN = booleanPreferencesKey("crash_reporting_opt_in")
        val KEY_BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val KEY_SYNC_LAST_ATTEMPT_MS = longPreferencesKey("sync_last_attempt_ms")
        val KEY_SYNC_LAST_SUCCESS_MS = longPreferencesKey("sync_last_success_ms")
        val KEY_SYNC_LAST_COUNT = intPreferencesKey("sync_last_count")
        val KEY_SYNC_LAST_HTTP_STATUS = intPreferencesKey("sync_last_http_status")
        val KEY_SYNC_LAST_ERROR = stringPreferencesKey("sync_last_error")

        fun normalizeBackendBaseUrl(rawUrl: String, allowCleartext: Boolean): String {
            val trimmed = rawUrl.trim()
            require(trimmed.isNotEmpty()) { "Backend URL cannot be empty" }
            val withTrailingSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            val uri = try {
                URI(withTrailingSlash)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid backend URL: $rawUrl", e)
            }
            val scheme = uri.scheme?.lowercase() ?: ""
            require(scheme == "http" || scheme == "https") { "Backend URL must use http:// or https://" }
            require(!uri.host.isNullOrBlank()) { "Invalid backend URL host: $rawUrl" }
            if (!allowCleartext && scheme == "http") {
                throw IllegalArgumentException("Cleartext HTTP backend URLs are disallowed in non-debug builds. Use HTTPS.")
            }
            return uri.toASCIIString()
        }
    }

    private val safeData: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) emit(emptyPreferences()) else throw exception
    }

    override val useNearby: Flow<Boolean> = safeData.map { it[KEY_USE_NEARBY] ?: true }
    override val useMeshtastic: Flow<Boolean> = safeData.map { it[KEY_USE_MESHTASTIC] ?: false }
    override val batteryOptimization: Flow<Boolean> = safeData.map { it[KEY_BATTERY_OPTIMIZATION] ?: true }
    override val crashReportingOptIn: Flow<Boolean> = safeData.map { it[KEY_CRASH_REPORTING_OPT_IN] ?: false }

    override val backendBaseUrl: Flow<String> = safeData.map { prefs ->
        val raw = prefs[KEY_BACKEND_BASE_URL]?.trim()
        val effective = if (!raw.isNullOrBlank()) raw else BuildConfig.BACKEND_BASE_URL
        normalizeBackendBaseUrl(effective, allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP)
    }

    override val gatewayKey: Flow<String> = _gatewayKeyFlow

    override val syncTelemetry: Flow<SyncTelemetry> = safeData.map { prefs ->
        SyncTelemetry(
            lastSyncAttemptMs = prefs[KEY_SYNC_LAST_ATTEMPT_MS],
            lastSyncSuccessMs = prefs[KEY_SYNC_LAST_SUCCESS_MS],
            lastSyncedCount = prefs[KEY_SYNC_LAST_COUNT] ?: 0,
            lastHttpStatus = prefs[KEY_SYNC_LAST_HTTP_STATUS],
            lastError = prefs[KEY_SYNC_LAST_ERROR]
        )
    }

    override fun getEffectiveBackendBaseUrl(): String = cachedBackendBaseUrl

    override fun getEffectiveGatewayKey(): String = _gatewayKeyFlow.value

    override suspend fun setUseNearby(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_NEARBY] = enabled }
    }

    override suspend fun setUseMeshtastic(enabled: Boolean) {
        dataStore.edit { it[KEY_USE_MESHTASTIC] = enabled }
    }

    override suspend fun setBatteryOptimization(enabled: Boolean) {
        dataStore.edit { it[KEY_BATTERY_OPTIMIZATION] = enabled }
    }

    override suspend fun setCrashReportingOptIn(enabled: Boolean) {
        dataStore.edit { it[KEY_CRASH_REPORTING_OPT_IN] = enabled }
    }

    override suspend fun setBackendBaseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            val defaultUrl = normalizeBackendBaseUrl(BuildConfig.BACKEND_BASE_URL, allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP)
            cachedBackendBaseUrl = defaultUrl
            dataStore.edit { it.remove(KEY_BACKEND_BASE_URL) }
            return
        }
        val normalized = normalizeBackendBaseUrl(trimmed, allowCleartext = BuildConfig.ALLOW_CLEARTEXT_HTTP)
        cachedBackendBaseUrl = normalized
        dataStore.edit { it[KEY_BACKEND_BASE_URL] = normalized }
    }

    override suspend fun setGatewayKey(key: String) {
        val trimmed = key.trim()
        secretStore?.setGatewayKey(trimmed)
        _gatewayKeyFlow.value = if (trimmed.isNotEmpty()) trimmed else BuildConfig.GATEWAY_KEY
    }

    override suspend fun recordSyncResult(
        timestampMs: Long,
        success: Boolean,
        syncedCount: Int,
        httpStatus: Int?,
        error: String?
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_LAST_ATTEMPT_MS] = timestampMs
            if (success) {
                prefs[KEY_SYNC_LAST_SUCCESS_MS] = timestampMs
                prefs[KEY_SYNC_LAST_COUNT] = syncedCount
                prefs.remove(KEY_SYNC_LAST_ERROR)
            } else if (!error.isNullOrBlank()) {
                prefs[KEY_SYNC_LAST_ERROR] = error.take(300)
            }
            if (httpStatus != null) {
                prefs[KEY_SYNC_LAST_HTTP_STATUS] = httpStatus
            } else {
                prefs.remove(KEY_SYNC_LAST_HTTP_STATUS)
            }
        }
    }
}
