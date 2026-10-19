package com.meshlink.app.telemetry

import android.content.Context
import com.meshlink.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash and diagnostic reporting interface.
 *
 * PRIVACY GUARANTEE:
 * This is an offline emergency messaging application. Crash and diagnostic reporting
 * is STRICTLY OPT-IN and defaults to FALSE (OFF).
 *
 * When opt-in is false:
 * - No reports or breadcrumbs are transmitted or stored remotely.
 * - Sentry / Firebase Crashlytics DSNs are disabled.
 * - Sensitive message contents, node IDs, medical profiles, and location coordinates
 *   are NEVER logged or captured.
 */
interface CrashReporter {
    fun initialize()
    fun log(message: String)
    fun recordException(throwable: Throwable)
    fun setCustomKey(key: String, value: String)
    val isOptedIn: Boolean
}

@Singleton
class PrivacySafeCrashReporter(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : CrashReporter {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ) : this(context, settingsRepository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val optedIn = AtomicBoolean(false)
    private val customKeys = ConcurrentHashMap<String, String>()

    override val isOptedIn: Boolean
        get() = optedIn.get()

    override fun initialize() {
        scope.launch {
            settingsRepository.crashReportingOptIn.collectLatest { enabled ->
                optedIn.set(enabled)
                Timber.i("Crash reporting telemetry opt-in state updated: enabled=$enabled")
            }
        }
    }

    override fun log(message: String) {
        if (!optedIn.get()) return
        val sanitized = sanitize(message)
        Timber.d("[Telemetry Breadcrumb] $sanitized")
    }

    override fun recordException(throwable: Throwable) {
        if (!optedIn.get()) return
        // Ready for Sentry / Firebase Crashlytics DSN hook when configured by owner:
        // FirebaseCrashlytics.getInstance().recordException(throwable)
        Timber.w(throwable, "[Telemetry Crash Captured]")
    }

    override fun setCustomKey(key: String, value: String) {
        if (!optedIn.get()) return
        customKeys[key] = sanitize(value)
    }

    fun getCustomKeys(): Map<String, String> = customKeys.toMap()

    private fun sanitize(input: String): String {
        return input
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9\\-_.]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)key\\s*[:=]\\s*[a-zA-Z0-9\\-_.]+"), "key=[REDACTED]")
            .replace(Regex("-?\\d+\\.\\d{4,},\\s*-?\\d+\\.\\d{4,}"), "[COORDINATES REDACTED]")
    }
}
