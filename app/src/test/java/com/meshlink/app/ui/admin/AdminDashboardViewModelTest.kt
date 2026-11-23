package com.meshlink.app.ui.admin

import android.os.SystemClock
import com.meshlink.app.data.local.dao.AssignmentDao
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.local.entity.AssignmentEntity
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.SosPacketEntity
import com.meshlink.app.domain.model.ConnectionState
import com.meshlink.app.domain.model.DiscoveredDevice
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.SettingsRepository
import com.meshlink.app.mesh.routing.RoutingTable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var routingTable: RoutingTable
    private lateinit var nearbyRepo: NearbyRepository
    private lateinit var assignmentDao: AssignmentDao
    private lateinit var deviceDao: DeviceDao
    private lateinit var sosPacketDao: SosPacketDao
    private lateinit var settingsRepo: SettingsRepository

    private val localDeviceId = "local-device-admin-123"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 120_000L

        routingTable = mockk(relaxed = true)
        nearbyRepo = mockk(relaxed = true)
        assignmentDao = mockk(relaxed = true)
        deviceDao = mockk(relaxed = true)
        sosPacketDao = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)

        every { routingTable.graphFlow } returns MutableStateFlow(emptyMap())
        every { nearbyRepo.discoveredDevices } returns MutableStateFlow(emptyList())
        every { nearbyRepo.connectionStates } returns MutableStateFlow(emptyMap())
        every { deviceDao.getAll() } returns flowOf(emptyList())
        every { assignmentDao.getAll() } returns flowOf(emptyList())
        every { sosPacketDao.getAll() } returns flowOf(emptyList())
        every { settingsRepo.useNearby } returns flowOf(true)
        every { settingsRepo.useMeshtastic } returns flowOf(false)
        every { settingsRepo.batteryOptimization } returns flowOf(true)
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AdminDashboardViewModel(
        routingTable = routingTable,
        nearbyRepository = nearbyRepo,
        assignmentDao = assignmentDao,
        deviceDao = deviceDao,
        sosPacketDao = sosPacketDao,
        settingsRepository = settingsRepo,
        localDeviceId = localDeviceId
    )

    @Test
    fun initialState_overviewStatsReflectRealCountsAndNoFabricatedData() = runTest(testDispatcher) {
        val testSos = listOf(
            SosPacketEntity(
                uuid = "sos-1",
                senderId = "peer-alpha",
                emergencyType = "MEDICAL",
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )
        )
        val testAssignments = listOf(
            AssignmentEntity(
                id = "task-1",
                incidentId = "inc-101",
                status = "DISPATCHED",
                priority = 1
            )
        )
        every { sosPacketDao.getAll() } returns flowOf(testSos)
        every { assignmentDao.getAll() } returns flowOf(testAssignments)

        val vm = createViewModel()
        advanceUntilIdle()

        val stats = vm.overviewStats.value
        assertEquals(1, stats.activeSosCount)
        assertEquals(1, stats.totalAssignments)
        assertEquals(1, stats.pendingAssignments)
        assertEquals("LOCAL_ONLY", stats.meshSyncState)
    }

    @Test
    fun nodesFlow_unreportedBatteryRemainsNullInsteadOfFabricating75Percent() = runTest(testDispatcher) {
        val known = listOf(
            KnownDeviceEntity(
                deviceId = "peer-beta",
                displayName = "Alpha Responder",
                publicKey = byteArrayOf(1, 2, 3),
                lastSeen = 1_000L,
                isVerified = true
            )
        )
        every { deviceDao.getAll() } returns flowOf(known)

        val vm = createViewModel()
        advanceUntilIdle()

        val nodes = vm.nodes.value
        assertEquals(1, nodes.size)
        assertEquals("peer-beta", nodes.first().deviceId)
        // Invariant: Unreported battery must be null, never hardcoded to 75%
        assertNull("Unreported battery must remain null", nodes.first().batteryLevel)
        assertTrue(nodes.first().isVerified)
    }

    @Test
    fun updateAssignmentStatus_callsRoomAndLogsAuditEntry() = runTest(testDispatcher) {
        coEvery { assignmentDao.updateStatus(any(), any(), any(), any()) } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateAssignmentStatus("task-42", "ACCEPTED")
        advanceUntilIdle()

        coVerify { assignmentDao.updateStatus("task-42", "ACCEPTED", any(), false) }
        val auditEntries = vm.auditLog.value
        assertTrue(auditEntries.any { it.action == "ASSIGNMENT_UPDATE" && it.detail.contains("task-42") })
    }

    @Test
    fun configToggles_callSettingsRepositoryMutators() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.setUseNearby(false)
        advanceUntilIdle()
        coVerify { settingsRepo.setUseNearby(false) }

        vm.setUseMeshtastic(true)
        advanceUntilIdle()
        coVerify { settingsRepo.setUseMeshtastic(true) }

        vm.setBatteryOptimization(false)
        advanceUntilIdle()
        coVerify { settingsRepo.setBatteryOptimization(false) }
    }

    @Test
    fun test_adminAuthorization_lockedByDefault_and_requiresMasterPin() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // 1. Initial state must be locked (fail-closed security)
        assertFalse("Admin panel must be locked by default", vm.isAuthorized.value)
        assertNull(vm.authError.value)

        // 2. Reject incorrect PIN
        val wrongAttempt = vm.unlockAdmin("1234")
        assertFalse(wrongAttempt)
        assertFalse(vm.isAuthorized.value)
        assertNotNull(vm.authError.value)
        assertTrue(vm.auditLog.value.any { it.action == "ADMIN_AUTH_FAILED" })

        // 3. Grant access with valid Tactical Master PIN
        val successAttempt = vm.unlockAdmin("8492")
        assertTrue(successAttempt)
        assertTrue(vm.isAuthorized.value)
        assertNull(vm.authError.value)
        assertTrue(vm.auditLog.value.any { it.action == "ADMIN_UNLOCKED" })

        // 4. Lock admin session
        vm.lockAdmin()
        assertFalse(vm.isAuthorized.value)
        assertTrue(vm.auditLog.value.any { it.action == "ADMIN_LOCKED" })
    }
}
