package com.meshlink.app.sync

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.AssignmentDao
import com.meshlink.app.data.local.dao.SosPacketDao
import com.meshlink.app.data.local.entity.AssignmentEntity
import com.meshlink.app.data.local.entity.SosPacketEntity
import com.meshlink.app.data.remote.MeshBackendService
import com.meshlink.app.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Provider

class GatewaySyncWorkerTest {

    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val sosDao: SosPacketDao = mockk(relaxed = true)
    private val assignmentDao: AssignmentDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private var mockWebServer: MockWebServer? = null

    @Before
    fun setup() {
        every { workerParams.inputData } returns Data.EMPTY
        every { settingsRepository.getEffectiveBackendBaseUrl() } returns "http://127.0.0.1:8000/"
        every { settingsRepository.getEffectiveGatewayKey() } returns "meshlink_gw_dev_secret"
        coEvery { assignmentDao.getUnsyncedAssignments() } returns emptyList()
    }

    @After
    fun teardown() {
        mockWebServer?.shutdown()
    }

    @Test
    fun `doWork returns success immediately when no packets in database`() = runTest {
        coEvery { sosDao.getAllPacketsBlocking() } returns emptyList()

        val worker = GatewaySyncWorker(context, workerParams, sosDao, assignmentDao, mockk(relaxed = true), settingsRepository)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sosDao.updateStatus(any(), any()) }
    }

    @Test
    fun `doWork returns success immediately when all packets are already cloud synced`() = runTest {
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
                status = "ACKNOWLEDGED",
                cloudSynced = true
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
                timestamp = 1700000100L,
                status = "DELIVERED",
                cloudSynced = true
            )
        )

        coEvery { sosDao.getAllPacketsBlocking() } returns acknowledgedPackets

        val worker = GatewaySyncWorker(context, workerParams, sosDao, assignmentDao, mockk(relaxed = true), settingsRepository)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sosDao.updateStatus(any(), any()) }
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

    @Test
    fun `doWork synchronizes unsynced assignment status via dedicated endpoint and marks synced on 200`() = runTest {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val backendService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","assignment_id":"asgn-route-1","assignment_status":"EN_ROUTE"}""")
        )

        coEvery { sosDao.getAllPacketsBlocking() } returns emptyList()
        val unsynced = listOf(
            AssignmentEntity(
                id = "asgn-route-1",
                incidentId = "inc-1",
                responderId = "resp-1",
                status = "EN_ROUTE",
                role = "LEAD",
                notes = "En route to site",
                description = "",
                severity = 5,
                priority = 3,
                offeredAt = 1000L,
                synced = false
            )
        )
        coEvery { assignmentDao.getUnsyncedAssignments() } returns unsynced

        val worker = GatewaySyncWorker(
            context = context,
            params = workerParams,
            sosDao = sosDao,
            assignmentDao = assignmentDao,
            backendService = backendService,
            settingsRepository = settingsRepository
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/sync/assignments/asgn-route-1/status", recorded.path)
        assertEquals("POST", recorded.method)
        coVerify(exactly = 1) { assignmentDao.markSynced("asgn-route-1") }
    }

    @Test
    fun `doWork leaves isSynced false and returns retry when dedicated status endpoint returns 500 error`() = runTest {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val backendService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)

        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"detail":"Internal server error"}""")
        )

        coEvery { sosDao.getAllPacketsBlocking() } returns emptyList()
        val unsynced = listOf(
            AssignmentEntity(
                id = "asgn-fail-1",
                incidentId = "inc-2",
                responderId = "resp-2",
                status = "ON_SCENE",
                role = "LEAD",
                notes = "On scene",
                description = "",
                severity = 5,
                priority = 3,
                offeredAt = 1000L,
                synced = false
            )
        )
        coEvery { assignmentDao.getUnsyncedAssignments() } returns unsynced

        val worker = GatewaySyncWorker(
            context = context,
            params = workerParams,
            sosDao = sosDao,
            assignmentDao = assignmentDao,
            backendService = backendService,
            settingsRepository = settingsRepository
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)

        // Verifies failed assignment was NOT marked as synced
        coVerify(exactly = 0) { assignmentDao.markSynced("asgn-fail-1") }
        coVerify(exactly = 1) { settingsRepository.recordSyncResult(any(), success = false, httpStatus = 500, error = any()) }
    }

    @Test
    fun `doWork uploads peer-acknowledged SOS that is not yet cloudSynced and marks cloudSynced on server receipt`() = runTest {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val backendService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","synced_count":1,"receipts":[{"uuid":"sos-peer-ack-1","status":"ACKNOWLEDGED","synced":true}],"downlinks":[]}""")
        )

        val peerAcked = SosPacketEntity(
            uuid = "sos-peer-ack-1",
            senderId = "node-1",
            senderName = "Field Node",
            emergencyType = "MEDICAL",
            severity = 8,
            latitude = 37.77,
            longitude = -122.41,
            message = "Trauma",
            timestamp = 1700000000L,
            status = "ACKNOWLEDGED",
            cloudSynced = false
        )
        coEvery { sosDao.getAllPacketsBlocking() } returns listOf(peerAcked)

        val worker = GatewaySyncWorker(
            context = context,
            params = workerParams,
            sosDao = sosDao,
            assignmentDao = assignmentDao,
            backendService = backendService,
            settingsRepository = settingsRepository
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/sync/sos", recorded.path)
        assertEquals("POST", recorded.method)
        coVerify(exactly = 1) { sosDao.markCloudSynced("sos-peer-ack-1") }
        coVerify(exactly = 1) { settingsRepository.recordSyncResult(any(), success = true, syncedCount = 1, httpStatus = 200) }
    }

    @Test
    fun `doWork returns failure without marking synced when SOS sync gets HTTP 401`() = runTest {
        val server = MockWebServer()
        server.start()
        mockWebServer = server

        val backendService = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail":"Invalid gateway credentials"}""")
        )

        val unsynced = SosPacketEntity(
            uuid = "sos-unauth-1",
            senderId = "node-1",
            emergencyType = "FIRE",
            severity = 9,
            message = "Fire",
            timestamp = 1700000000L,
            status = "PENDING",
            cloudSynced = false
        )
        coEvery { sosDao.getAllPacketsBlocking() } returns listOf(unsynced)

        val worker = GatewaySyncWorker(
            context = context,
            params = workerParams,
            sosDao = sosDao,
            assignmentDao = assignmentDao,
            backendService = backendService,
            settingsRepository = settingsRepository
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { sosDao.markCloudSynced(any()) }
        coVerify(exactly = 1) { settingsRepository.recordSyncResult(any(), success = false, httpStatus = 401, error = any()) }
    }
}
