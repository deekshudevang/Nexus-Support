package com.meshlink.app.sync

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.local.entity.SosPacketEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewaySyncWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val sosDao: SosPacketDao = mockk(relaxed = true)

    @Before
    fun setup() {
        every { workerParams.inputData } returns Data.EMPTY
    }

    @Test
    fun `doWork returns success immediately when no packets in database`() = runTest {
        coEvery { sosDao.getAllPacketsBlocking() } returns emptyList()

        val worker = GatewaySyncWorker(context, workerParams, sosDao)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sosDao.updateStatus(any(), any()) }
    }

    @Test
    fun `doWork returns success immediately when all packets are already acknowledged`() = runTest {
        val acknowledgedPackets = listOf(
            SosPacketEntity(
                uuid = "sos-uuid-1",
                senderId = "sender-1",
                senderName = "Responder 1",
                emergencyType = "MEDICAL",
                severity = 4,
                latitude = 37.77,
                longitude = -122.41,
                message = "Evacuated",
                timestamp = 1700000000L,
                status = "ACKNOWLEDGED"
            ),
            SosPacketEntity(
                uuid = "sos-uuid-2",
                senderId = "sender-2",
                senderName = "Responder 2",
                emergencyType = "FIRE",
                severity = 5,
                latitude = 37.78,
                longitude = -122.42,
                message = "Contained",
                timestamp = 1700000010L,
                status = "DELIVERED"
            )
        )
        coEvery { sosDao.getAllPacketsBlocking() } returns acknowledgedPackets

        val worker = GatewaySyncWorker(context, workerParams, sosDao)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sosDao.updateStatus(any(), any()) }
    }

    @Test
    fun `doWork returns retry when server connection fails`() = runTest {
        val pendingPackets = listOf(
            SosPacketEntity(
                uuid = "sos-uuid-pending-1",
                senderId = "victim-1",
                senderName = "Citizen",
                emergencyType = "TRAPPED",
                severity = 5,
                latitude = 0.0,
                longitude = 0.0,
                message = "Trapped in stairwell",
                timestamp = 1700000020L,
                status = "PENDING"
            )
        )
        coEvery { sosDao.getAllPacketsBlocking() } returns pendingPackets

        // Point to invalid port/endpoint that fails connection
        val inputData = Data.Builder()
            .putString(GatewaySyncWorker.KEY_GATEWAY_URL, "http://127.0.0.1:54321/api/v1/sync/sos")
            .build()
        every { workerParams.inputData } returns inputData

        val worker = GatewaySyncWorker(context, workerParams, sosDao)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `buildWorkRequest configures connected network and exponential backoff`() {
        val request = GatewaySyncWorker.buildWorkRequest(
            gatewayUrl = "http://192.168.1.100:8000/api/v1/sync/sos",
            gatewayKey = "secret-key-123"
        )

        val spec = request.workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals("http://192.168.1.100:8000/api/v1/sync/sos", spec.input.getString(GatewaySyncWorker.KEY_GATEWAY_URL))
        assertEquals("secret-key-123", spec.input.getString(GatewaySyncWorker.KEY_GATEWAY_KEY))
        assertTrue(spec.backoffDelayDuration > 0)
    }
}

