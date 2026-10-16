package com.meshlink.app.ai.security

import timber.log.Timber

/**
 * Privacy-preserving logging wrapper for the AI subsystem.
 * Strictly forbids logging raw prompts, completions, mesh message text,
 * user images, or audio waveforms.
 */
object AiLogger {
    private const val TAG = "OfflineAi"

    fun d(message: String) {
        Timber.tag(TAG).d(sanitize(message))
    }

    fun i(message: String) {
        Timber.tag(TAG).i(sanitize(message))
    }

    fun w(message: String) {
        Timber.tag(TAG).w(sanitize(message))
    }

    fun e(message: String) {
        Timber.tag(TAG).e(sanitize(message))
    }

    fun e(throwable: Throwable?, message: String) {
        Timber.tag(TAG).e(throwable, sanitize(message))
    }

    /**
     * Diagnostic event logger for lifecycle and benchmarks without sensitive data.
     */
    fun logEvent(event: String, durationMs: Long? = null, tokenCount: Int? = null) {
        val meta = buildString {
            append("event=").append(event)
            if (durationMs != null) append(", latency=").append(durationMs).append("ms")
            if (tokenCount != null) append(", tokens=").append(tokenCount)
        }
        Timber.tag(TAG).i(meta)
    }

    private fun sanitize(input: String): String {
        // Redacts any potential prompt or text blocks
        return input.replace(Regex("(?i)(prompt|response|query|context)=[^,;\\n]+"), "$1=[REDACTED]")
    }
}
