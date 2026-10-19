package com.meshlink.app.domain.monitoring

/**
 * Privacy-respecting crash and error reporting contract for MeshLink.
 * In accordance with offline-first and emergency application requirements, error reporting
 * is strictly opt-in (default OFF) and scrubs any sensitive context before capture.
 */
interface ErrorReporter {
    /**
     * Records an exception with optional metadata attributes.
     * Implementations must verify opt-in status and sanitize sensitive PII
     * (e.g. coordinates, user content, crypto keys).
     */
    fun recordException(throwable: Throwable, metadata: Map<String, String> = emptyMap())

    /**
     * Records a diagnostic breadcrumb if opt-in is enabled.
     */
    fun logBreadcrumb(message: String)
}
