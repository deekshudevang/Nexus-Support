package com.meshlink.app.mesh.battery

import com.meshlink.app.domain.model.ScanStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Nearby scan activity and recommends a restart delay, implementing an
 * adaptive backoff strategy to reduce battery drain when no devices are nearby.
 */
@Singleton
class AdaptiveScanController @Inject constructor() {

    private enum class ScanMode { FAST, SLOW }

    companion object {
        private const val FAST_DELAY_MS          =  1_500L
        private const val SLOW_INITIAL_DELAY_MS  =  5_000L
        private const val SLOW_MAX_DELAY_MS      = 30_000L
        private const val NO_DEVICE_THRESHOLD_MS = 60_000L
        private const val CRITICAL_BATTERY_PCT   = 15
    }

    private var mode            = ScanMode.FAST
    private var currentDelayMs  = FAST_DELAY_MS
    @Volatile var lastDeviceFoundAt: Long = System.currentTimeMillis()

    private val _scanStrategy = MutableStateFlow(ScanStrategy.CONTINUOUS)
    val scanStrategy: StateFlow<ScanStrategy> = _scanStrategy.asStateFlow()

    private var currentBatteryLevel = 100
    private var isOptimizationEnabled = true
    private var isManualPause = false

    /** Returns the delay to wait before restarting advertising + discovery. */
    val nextRestartDelayMs: Long get() = currentDelayMs

    fun setManualPause(isPaused: Boolean) {
        isManualPause = isPaused
        updateStrategy()
    }

    fun updateSettings(batteryLevel: Int, optimizationEnabled: Boolean) {
        currentBatteryLevel = batteryLevel
        isOptimizationEnabled = optimizationEnabled
        updateStrategy()
    }

    private fun updateStrategy() {
        val newStrategy = when {
            isManualPause -> ScanStrategy.PAUSED
            isOptimizationEnabled && currentBatteryLevel <= CRITICAL_BATTERY_PCT -> ScanStrategy.PAUSED
            mode == ScanMode.SLOW -> ScanStrategy.INTERMITTENT
            else -> ScanStrategy.CONTINUOUS
        }
        if (_scanStrategy.value != newStrategy) {
            Timber.i("Scan strategy updated: ${_scanStrategy.value} -> $newStrategy")
            _scanStrategy.value = newStrategy
        }
    }

    /**
     * Call every time an endpoint is discovered.
     * Resets backoff to fast-scan mode.
     */
    fun onDeviceFound() {
        lastDeviceFoundAt = System.currentTimeMillis()
        if (mode != ScanMode.FAST) {
            mode = ScanMode.FAST
            currentDelayMs = FAST_DELAY_MS
            Timber.d("AdaptiveScan: device found → FAST mode (delay=${currentDelayMs}ms)")
            updateStrategy()
        }
    }

    /**
     * Call after each advertising+discovery restart cycle completes.
     * If no device has been seen for [NO_DEVICE_THRESHOLD_MS], ratchets up the delay.
     */
    fun onRestartComplete() {
        val idleMs = System.currentTimeMillis() - lastDeviceFoundAt
        if (idleMs >= NO_DEVICE_THRESHOLD_MS) {
            mode = ScanMode.SLOW
            currentDelayMs = when {
                currentDelayMs < SLOW_INITIAL_DELAY_MS -> SLOW_INITIAL_DELAY_MS
                currentDelayMs < SLOW_MAX_DELAY_MS     -> minOf(currentDelayMs * 2, SLOW_MAX_DELAY_MS)
                else                                   -> SLOW_MAX_DELAY_MS
            }
            Timber.d("AdaptiveScan: no devices for ${idleMs}ms → SLOW mode (delay=${currentDelayMs}ms)")
            updateStrategy()
        }
    }

    /**
     * Call when the screen turns ON or the user opens the app.
     * Immediately resets backoff so the next scan cycle is fast.
     */
    fun onUserActive() {
        lastDeviceFoundAt = System.currentTimeMillis()
        mode = ScanMode.FAST
        currentDelayMs = FAST_DELAY_MS
        Timber.d("AdaptiveScan: user active → reset to FAST mode")
        updateStrategy()
    }
}
