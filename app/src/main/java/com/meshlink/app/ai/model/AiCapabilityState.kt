package com.meshlink.app.ai.model

/**
 * Explicit capability and lifecycle state machine for Offline AI.
 */
enum class AiCapabilityState {
    NOT_INSTALLED,
    VERIFYING,
    READY,
    LOADING,
    RUNNING,
    UNSUPPORTED,
    LOW_MEMORY,
    ERROR
}
