package com.meshlink.app.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.data.remote.MeshBackendService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Provider

class CloudSyncWorkerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var backendService: MeshBackendService
    private val locationEventDao: LocationEventDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val okHttpClient = OkHttpClient.Builder().build()
        backendService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MeshBackendService::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `doWork uploads pending events and marks them uploaded on 200 response`() = runTest {
        val pendingEvents = listOf(
            LocationEventEntity(
                eventId = "evt-loc-01",
                peerId = "peer-01",
                latitude = 37.7749,
                longitude = -122.4194,
                accuracy = 4.0f,
                timestamp = 1700000000L,
                sequenceNumber = 1,
                signature = "sig-1",
                publicKey = "pk-1",
                syncStatus = 0
            )
        )
        coEvery { locationEventDao.getPendingCloudUploads() } returns pendingEvents

        // Enqueue successful upload response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "uploadedCount": 1}""")
                .addHeader("Content-Type", "application/json")
        )
        // Enqueue successful fetch response (empty list)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json")
        )

        val provider = Provider { backendService }
        val worker = CloudSyncWorker(context, workerParams, locationEventDao, provider)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { locationEventDao.markAsUploaded(listOf("evt-loc-01")) }

        // Verify request payload and gateway auth header
        val recordedUpload = mockWebServer.takeRequest()
        assertEquals("/api/v1/sync/location", recordedUpload.path)
        assertEquals("POST", recordedUpload.method)
        assertEquals("meshlink_gw_dev_secret", recordedUpload.getHeader("X-Gateway-Key"))
    }

    @Test
    fun `doWork downloads new events and inserts them into Room with syncStatus 1`() = runTest {
        coEvery { locationEventDao.getPendingCloudUploads() } returns emptyList()

        val jsonResponse = """
            [
                {
                    "eventId": "evt-cloud-02",
                    "peerId": "peer-02",
                    "latitude": 34.0522,
                    "longitude": -118.2437,
                    "accuracy": 3.5,
                    "timestamp": 1700001000,
                    "sequenceNumber": 5,
                    "signature": "sig-cloud",
                    "publicKey": "pk-cloud",
                    "syncStatus": 1
                }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json")
        )

        val provider = Provider { backendService }
        val worker = CloudSyncWorker(context, workerParams, locationEventDao, provider)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val fetchRequest = mockWebServer.takeRequest()
        assertEquals("GET", fetchRequest.method)
        assert(fetchRequest.path!!.startsWith("/api/v1/sync/location?sinceTimestamp="))
        assertEquals("meshlink_gw_dev_secret", fetchRequest.getHeader("X-Gateway-Key"))

        coVerify(exactly = 1) {
            locationEventDao.insertEvents(match { events ->
                events.size == 1 &&
                events[0].eventId == "evt-cloud-02" &&
                events[0].syncStatus == 1
            })
        }
    }

    @Test
    fun `doWork returns retry when backend returns 401 Unauthorized`() = runTest {
        val pendingEvents = listOf(
            LocationEventEntity(
                eventId = "evt-loc-03",
                peerId = "peer-03",
                latitude = 37.77,
                longitude = -122.41,
                accuracy = 5.0f,
                timestamp = 1700000000L,
                sequenceNumber = 1,
                signature = "",
                publicKey = "",
                syncStatus = 0
            )
        )
        coEvery { locationEventDao.getPendingCloudUploads() } returns pendingEvents

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail": "Gateway credentials required"}""")
                .addHeader("Content-Type", "application/json")
        )

        val provider = Provider { backendService }
        val worker = CloudSyncWorker(context, workerParams, locationEventDao, provider)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { locationEventDao.markAsUploaded(any()) }
    }

    @Test
    fun `doWork returns retry when backend returns 500 or 404 server error`() = runTest {
        val pendingEvents = listOf(
            LocationEventEntity(
                eventId = "evt-loc-04",
                peerId = "peer-04",
                latitude = 37.77,
                longitude = -122.41,
                accuracy = 5.0f,
                timestamp = 1700000000L,
                sequenceNumber = 1,
                signature = "",
                publicKey = "",
                syncStatus = 0
            )
        )
        coEvery { locationEventDao.getPendingCloudUploads() } returns pendingEvents

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"detail": "Internal server error"}""")
                .addHeader("Content-Type", "application/json")
        )

        val provider = Provider { backendService }
        val worker = CloudSyncWorker(context, workerParams, locationEventDao, provider)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { locationEventDao.markAsUploaded(any()) }
    }
}
