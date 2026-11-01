package com.meshlink.app.monitoring

import com.meshlink.app.domain.monitoring.ErrorReporter
import com.meshlink.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-respecting crash/error reporter scaffolding.
 *
 * In offline-first disaster response, telemetry MUST NOT broadcast PII, plaintext messages,
 * coordinates, or cryptographic material. Reporting is:
 * 1. Strictly OPT-IN (default OFF in Settings).
 * 2. Scaffolding-only: No real keys/tokens are committed or initialized.
 * 3. Sanitized: Sensitive keys (location, lat, lon, payload, token, secret, etc.) are stripped.
 */
@Singleton
class PrivacyAwareErrorReporter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : ErrorReporter {

    val isOptInEnabled: StateFlow<Boolean> = settingsRepository.crashReportingOptIn
        .stateIn(externalScope, SharingStarted.Eagerly, initialValue = false)

    override fun recordException(throwable: Throwable, metadata: Map<String, String>) {
        if (!isOptInEnabled.value) {
            // Opt-in is disabled (default): do not capture, persist, or transmit any error data
            return
        }

        val sanitizedMetadata = sanitize(metadata)
        // Scaffolding note: When a production crash reporting service (e.g. self-hosted Sentry
        // or Crashlytics) is configured with an owner-provided token via environment/secrets,
        // sanitized dispatch would occur here.
        Timber.i(
            "PrivacyAwareErrorReporter: Recorded sanitized exception [type=%s, keys=%s]",
            throwable.javaClass.simpleName,
            sanitizedMetadata.keys.joinToString()
        )
    }

    override fun logBreadcrumb(message: String) {
        if (!isOptInEnabled.value) {
            return
        }
        val sanitized = sanitizeMessage(message)
        Timber.i("PrivacyAwareErrorReporter breadcrumb: %s", sanitized)
    }

    companion object {
        private val SENSITIVE_KEY_PATTERNS = listOf(
            "lat", "lon", "gps", "location", "coord",
            "message", "payload", "text", "body", "content",
            "key", "secret", "token", "passphrase", "password", "auth",
            "peer", "device", "endpoint"
        )

        fun sanitize(metadata: Map<String, String>): Map<String, String> {
            return metadata.filterNot { (key, _) ->
                val lowerKey = key.lowercase()
                SENSITIVE_KEY_PATTERNS.any { pattern -> lowerKey.contains(pattern) }
            }
        }

        fun sanitizeMessage(message: String): String {
            // Redact GPS coordinate pairs like "37.7749, -122.4194"
            val coordRegex = Regex("""[-+]?\d{1,3}\.\d{3,},\s*[-+]?\d{1,3}\.\d{3,}""")
            return message.replace(coordRegex, "[REDACTED_COORDS]")
        }
    }
}
