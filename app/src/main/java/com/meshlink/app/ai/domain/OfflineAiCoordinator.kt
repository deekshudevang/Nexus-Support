package com.meshlink.app.ai.domain

import com.meshlink.app.ai.model.AiCapabilityState
import com.meshlink.app.ai.model.AiError
import com.meshlink.app.ai.model.AiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Central orchestrator for the Offline AI subsystem.
 * Coordinates capability state, feature flags, emergency kill switch,
 * and preemption when SOS emergencies occur.
 */
interface OfflineAiCoordinator {
    /**
     * Observable state of the AI subsystem.
     */
    val state: StateFlow<AiCapabilityState>

    /**
     * Whether AI features are enabled (via compile default + user toggle + kill switch).
     */
    val isAiEnabled: StateFlow<Boolean>

    /**
     * Initializes the subsystem, detecting model availability on disk.
     */
    suspend fun initialize(): AiCapabilityState

    /**
     * Triggers the emergency kill switch: immediately cancels any active inference,
     * deallocates resident native model weights, and disables AI features.
     */
    suspend fun triggerKillSwitch()

    /**
     * Resets the kill switch and restores normal operation if model is installed.
     */
    suspend fun enableAi()

    /**
     * Notification callback when SOS beacon transmission or reception starts.
     * Must pre-empt active inference within 50ms to protect emergency battery & CPU.
     */
    fun onSosActivated()

    /**
     * Notification callback when SOS emergency has cleared.
     */
    fun onSosDeactivated()

    /**
     * Notification callback on system memory pressure (`ComponentCallbacks2.onTrimMemory`).
     */
    suspend fun onTrimMemory(level: Int)

    /**
     * Schedules and executes an inference block with single-flight mutex,
     * timeout bounding, and SOS preemption checking.
     */
    suspend fun <T> executeInference(
        timeoutMs: Long = 30000L,
        block: suspend () -> AiResult<T>
    ): AiResult<T>
}
