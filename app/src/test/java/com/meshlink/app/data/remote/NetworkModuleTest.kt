package com.meshlink.app.data.remote

import com.meshlink.app.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NetworkModuleTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `dynamic interceptor routes requests to runtime backendBaseUrl including subpaths and injects gateway key`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val baseWithPrefix = mockWebServer.url("/mesh-proxy/").toString()
        every { settingsRepository.getEffectiveBackendBaseUrl() } returns baseWithPrefix
        every { settingsRepository.getEffectiveGatewayKey() } returns "runtime-gw-key-42"

        val client = NetworkModule.provideOkHttpClient(settingsRepository)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)

        val service = NetworkModule.provideMeshBackendService(client)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"ok","database_connected":true,"migration_head":"6f1a8b92c431"}""")
        )

        val health = service.checkHealth()
        assertEquals("ok", health.status)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/mesh-proxy/health", recorded.path)
        assertEquals("runtime-gw-key-42", recorded.getHeader("X-Gateway-Key"))
        assertEquals("Bearer runtime-gw-key-42", recorded.getHeader("Authorization"))
    }
}
