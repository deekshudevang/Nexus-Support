package com.meshlink.app.domain.model

/**
 * Truthful runtime telemetry for background backend synchronization.
 */
data class SyncTelemetry(
    val lastSyncAttemptMs: Long? = null,
    val lastSyncSuccessMs: Long? = null,
    val lastSyncedCount: Int = 0,
    val lastHttpStatus: Int? = null,
    val lastError: String? = null
)
