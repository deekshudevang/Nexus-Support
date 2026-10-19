package com.meshlink.app.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStoreFile: File
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        dataStoreFile = tmpFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { dataStoreFile }
        )
        repository = SettingsRepositoryImpl(dataStore)
    }

    @Test
    fun defaultValues_areExpected() = runTest(testDispatcher) {
        assertTrue("useNearby should default to true", repository.useNearby.first())
        assertFalse("useMeshtastic should default to false", repository.useMeshtastic.first())
        assertTrue("batteryOptimization should default to true", repository.batteryOptimization.first())
    }

    @Test
    fun updateSettings_emitsUpdatedValues() = runTest(testDispatcher) {
        repository.setUseNearby(false)
        assertFalse(repository.useNearby.first())

        repository.setUseMeshtastic(true)
        assertTrue(repository.useMeshtastic.first())

        repository.setBatteryOptimization(false)
        assertFalse(repository.batteryOptimization.first())
    }

    @Test
    fun updateSettings_persistsValuesAcrossReload() = runTest(testDispatcher) {
        val reloadFile = tmpFolder.newFile("reload_settings.preferences_pb")
        val scope1 = CoroutineScope(Dispatchers.Unconfined + Job())
        val store1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { reloadFile })
        val repo1 = SettingsRepositoryImpl(store1)

        repo1.setUseNearby(false)
        repo1.setUseMeshtastic(true)
        repo1.setBatteryOptimization(false)

        // Cancel scope1 to release DataStore lock before opening reloadFile in a new store
        scope1.cancel()

        val scope2 = CoroutineScope(Dispatchers.Unconfined + Job())
        val store2 = PreferenceDataStoreFactory.create(scope = scope2, produceFile = { reloadFile })
        val repo2 = SettingsRepositoryImpl(store2)

        assertFalse("Persisted useNearby should be false", repo2.useNearby.first())
        assertTrue("Persisted useMeshtastic should be true", repo2.useMeshtastic.first())
        assertFalse("Persisted batteryOptimization should be false", repo2.batteryOptimization.first())

        scope2.cancel()
    }
}
