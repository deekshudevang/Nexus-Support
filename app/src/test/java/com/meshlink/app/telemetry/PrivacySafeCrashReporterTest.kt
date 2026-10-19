package com.meshlink.app.telemetry

import android.content.Context
import com.meshlink.app.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySafeCrashReporterTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val mockSettingsRepository: SettingsRepository = mockk(relaxed = true)
    private val optInFlow = MutableStateFlow(false)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var crashReporter: PrivacySafeCrashReporter

    @Before
    fun setUp() {
        every { mockSettingsRepository.crashReportingOptIn } returns optInFlow
        crashReporter = PrivacySafeCrashReporter(mockContext, mockSettingsRepository, testScope)
    }

    @Test
    fun defaultState_isNotOptedIn() {
        assertFalse("Telemetry reporting must be default off", crashReporter.isOptedIn)
    }

    @Test
    fun initialize_observesOptInChanges() = runTest(testDispatcher) {
        crashReporter.initialize()
        assertFalse(crashReporter.isOptedIn)

        optInFlow.value = true
        assertTrue("Telemetry reporting should be enabled when user opts in", crashReporter.isOptedIn)

        optInFlow.value = false
        assertFalse("Telemetry reporting should be disabled when user revokes opt-in", crashReporter.isOptedIn)
    }

    @Test
    fun whenNotOptedIn_customKeysAndExceptionsAreIgnored() = runTest(testDispatcher) {
        crashReporter.initialize()
        optInFlow.value = false

        crashReporter.setCustomKey("user_token", "secret_123")
        crashReporter.log("User at lat 37.7749, lon -122.4194 with key=abc12345")
        crashReporter.recordException(RuntimeException("Emergency test crash"))

        assertTrue("No custom keys should be recorded when not opted in", crashReporter.getCustomKeys().isEmpty())
    }

    @Test
    fun whenOptedIn_sanitizesSensitiveTokensAndCoordinates() = runTest(testDispatcher) {
        crashReporter.initialize()
        optInFlow.value = true

        crashReporter.setCustomKey("auth_header", "Bearer eyJhbGciOiJIUzI1NiJ9.test")
        crashReporter.setCustomKey("location", "37.774929, -122.419416")

        val keys = crashReporter.getCustomKeys()
        assertEquals("Bearer [REDACTED]", keys["auth_header"])
        assertEquals("[COORDINATES REDACTED]", keys["location"])
    }
}
