package com.meshlink.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meshlink_settings")

@Singleton
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.settingsDataStore)

    companion object {
        val KEY_USE_NEARBY = booleanPreferencesKey("use_nearby")
        val KEY_USE_MESHTASTIC = booleanPreferencesKey("use_meshtastic")
        val KEY_BATTERY_OPTIMIZATION = booleanPreferencesKey("battery_optimization")
    }

    override val useNearby: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_USE_NEARBY] ?: true
        }

    override val useMeshtastic: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_USE_MESHTASTIC] ?: false
        }

    override val batteryOptimization: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BATTERY_OPTIMIZATION] ?: true
        }

    override suspend fun setUseNearby(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_USE_NEARBY] = enabled
        }
    }

    override suspend fun setUseMeshtastic(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_USE_MESHTASTIC] = enabled
        }
    }

    override suspend fun setBatteryOptimization(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BATTERY_OPTIMIZATION] = enabled
        }
    }
}
