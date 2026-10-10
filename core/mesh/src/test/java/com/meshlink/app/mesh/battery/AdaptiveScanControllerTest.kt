package com.meshlink.app.mesh.battery

import com.meshlink.app.domain.model.ScanStrategy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AdaptiveScanControllerTest {

    private lateinit var controller: AdaptiveScanController

    @Before
    fun setUp() {
        controller = AdaptiveScanController()
    }

    @Test
    fun `starts in FAST mode with CONTINUOUS strategy`() {
        assertEquals(ScanStrategy.CONTINUOUS, controller.scanStrategy.value)
        assertEquals(1500L, controller.nextRestartDelayMs)
    }

    @Test
    fun `manual pause sets PAUSED strategy`() {
        controller.setManualPause(true)
        assertEquals(ScanStrategy.PAUSED, controller.scanStrategy.value)
    }

    @Test
    fun `manual resume restores CONTINUOUS strategy when in FAST mode`() {
        controller.setManualPause(true)
        assertEquals(ScanStrategy.PAUSED, controller.scanStrategy.value)
        controller.setManualPause(false)
        assertEquals(ScanStrategy.CONTINUOUS, controller.scanStrategy.value)
    }

    @Test
    fun `critical battery with optimization enabled sets PAUSED strategy`() {
        controller.updateSettings(batteryLevel = 10, optimizationEnabled = true)
        assertEquals(ScanStrategy.PAUSED, controller.scanStrategy.value)
    }

    @Test
    fun `critical battery with optimization disabled keeps CONTINUOUS strategy`() {
        controller.updateSettings(batteryLevel = 10, optimizationEnabled = false)
        assertEquals(ScanStrategy.CONTINUOUS, controller.scanStrategy.value)
    }

    @Test
    fun `onDeviceFound resets to FAST mode`() {
        // Force an artificial old lastDeviceFoundAt to simulate entering slow mode
        controller.lastDeviceFoundAt = System.currentTimeMillis() - 100_000L
        controller.onRestartComplete()
        assertEquals(ScanStrategy.INTERMITTENT, controller.scanStrategy.value)

        // Then finding a device should reset it
        controller.onDeviceFound()
        assertEquals(ScanStrategy.CONTINUOUS, controller.scanStrategy.value)
        assertEquals(1500L, controller.nextRestartDelayMs)
    }

    @Test
    fun `onUserActive resets to FAST mode`() {
        controller.lastDeviceFoundAt = System.currentTimeMillis() - 100_000L
        controller.onRestartComplete()
        assertEquals(ScanStrategy.INTERMITTENT, controller.scanStrategy.value)

        controller.onUserActive()
        assertEquals(ScanStrategy.CONTINUOUS, controller.scanStrategy.value)
        assertEquals(1500L, controller.nextRestartDelayMs)
    }
}
