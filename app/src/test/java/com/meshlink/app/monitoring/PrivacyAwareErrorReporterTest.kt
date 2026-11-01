package com.meshlink.app.monitoring

import com.meshlink.app.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyAwareErrorReporterTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockSettingsRepository: SettingsRepository = mockk(relaxed = true)
    private val optInFlow = MutableStateFlow(false)

    private lateinit var reporter: PrivacyAwareErrorReporter

    @Before
    fun setUp() {
        every { mockSettingsRepository.crashReportingOptIn } returns optInFlow
        reporter = PrivacyAwareErrorReporter(mockSettingsRepository, testScope)
    }

    @Test
    fun defaultState_isOptOut() {
        assertFalse("Crash reporting must default to disabled for privacy", reporter.isOptInEnabled.value)
    }

    @Test
    fun whenOptInToggled_stateUpdates() {
        optInFlow.value = true
        assertTrue("When user opts in, isOptInEnabled becomes true", reporter.isOptInEnabled.value)

        optInFlow.value = false
        assertFalse("When user opts out, isOptInEnabled becomes false", reporter.isOptInEnabled.value)
    }

    @Test
    fun sanitize_stripsSensitiveKeys() {
        val rawMetadata = mapOf(
            "app_version" to "1.0.0",
            "device_model" to "Pixel 8",
            "gps_latitude" to "37.7749",
            "user_location" to "San Francisco",
            "message_body" to "Help I am trapped",
            "auth_token" to "bearer-secret-123",
            "session_key" to "0xdeadbeef",
            "screen" to "SosScreen",
            "battery_level" to "85"
        )

        val sanitized = PrivacyAwareErrorReporter.sanitize(rawMetadata)

        // Non-sensitive diagnostic keys should be preserved
        assertEquals("1.0.0", sanitized["app_version"])
        assertEquals("SosScreen", sanitized["screen"])
        assertEquals("85", sanitized["battery_level"])

        // Sensitive keys must be scrubbed
        assertFalse(sanitized.containsKey("device_model"))
        assertFalse(sanitized.containsKey("gps_latitude"))
        assertFalse(sanitized.containsKey("user_location"))
        assertFalse(sanitized.containsKey("message_body"))
        assertFalse(sanitized.containsKey("auth_token"))
        assertFalse(sanitized.containsKey("session_key"))
    }

    @Test
    fun sanitizeMessage_redactsGpsCoordinates() {
        val rawMessage = "Failed to load tile near 40.7128, -74.0060 in cache"
        val redacted = PrivacyAwareErrorReporter.sanitizeMessage(rawMessage)

        assertEquals("Failed to load tile near [REDACTED_COORDS] in cache", redacted)
    }
}
