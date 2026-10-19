package com.meshlink.app.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.crypto.session.HandshakeManager
import com.meshlink.app.mesh.battery.BatteryMonitor
import com.meshlink.app.ui.chat.ChatViewModel
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTypeSafetyTest {

    @Test
    fun testScreenSerialization_chatRouteEncodesAndDecodesCorrectly() {
        val original = Screen.Chat(
            deviceId = "node_sha256_xyz789",
            deviceName = "Camp Alpha / Medical #1"
        )

        val jsonStr = Json.encodeToString(original)
        val decoded = Json.decodeFromString<Screen.Chat>(jsonStr)

        assertEquals(original.deviceId, decoded.deviceId)
        assertEquals(original.deviceName, decoded.deviceName)
        assertEquals(original, decoded)
    }

    @Test
    fun testScreenObjects_allBottomNavItemsAreMappedProperly() {
        assertEquals(5, bottomNavItems.size)
        val screens = bottomNavItems.map { it.screen }

        assertEquals(
            listOf(
                Screen.Home,
                Screen.Discovery,
                Screen.Map,
                Screen.AiAssistant,
                Screen.Sos
            ),
            screens
        )

        // Verify SOS tab is flagged as emergency SOS
        val sosItem = bottomNavItems.first { it.screen == Screen.Sos }
        assertTrue(sosItem.isSos)
        assertEquals("SOS", sosItem.label)
    }

    @Test
    fun testChatViewModel_extractsArgumentsFromTypeSafeRoute() {
        val testChat = Screen.Chat(
            deviceId = "dev_12345",
            deviceName = "Rescue Base Station"
        )

        // Populate SavedStateHandle using the route parameter keys created by Navigation Compose
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "deviceId" to testChat.deviceId,
                "deviceName" to testChat.deviceName
            )
        )

        val messageRepo: MessageRepository = mockk(relaxed = true)
        val nearbyRepo: NearbyRepository = mockk(relaxed = true)
        val deviceRepo: DeviceRepository = mockk(relaxed = true)
        val handshakeManager: HandshakeManager = mockk(relaxed = true)
        val batteryMonitor: BatteryMonitor = mockk(relaxed = true)

        val viewModel = ChatViewModel(
            savedStateHandle = savedStateHandle,
            messageRepository = messageRepo,
            nearbyRepository = nearbyRepo,
            deviceRepository = deviceRepo,
            handshakeManager = handshakeManager,
            batteryMonitor = batteryMonitor,
            localDeviceId = "local_phone_001"
        )

        assertEquals("dev_12345", viewModel.deviceId)
        assertEquals("Rescue Base Station", viewModel.deviceName)
    }

    @Test
    fun testScreenChat_createRouteHelperReturnsTypeSafeInstance() {
        val route = Screen.Chat.createRoute("node_42", "Relay Station")
        assertEquals("node_42", route.deviceId)
        assertEquals("Relay Station", route.deviceName)
        assertTrue(route is Screen)
    }

    @Test
    fun testAllScreens_haveNonEmptyDistinctRouteStrings() {
        val allScreens: List<Screen> = listOf(
            Screen.Home,
            Screen.Discovery,
            Screen.Sos,
            Screen.Map,
            Screen.MedicalProfile,
            Screen.Settings,
            Screen.Broadcast,
            Screen.Diagnostics,
            Screen.DebugDashboard,
            Screen.MapDownload,
            Screen.EmergencyGuide,
            Screen.NodeDashboard,
            Screen.AiAssistant
        )

        val routes = allScreens.map { screen ->
            when (screen) {
                is Screen.Home -> screen.route
                is Screen.Discovery -> screen.route
                is Screen.Sos -> screen.route
                is Screen.Map -> screen.route
                is Screen.MedicalProfile -> screen.route
                is Screen.Settings -> screen.route
                is Screen.Broadcast -> screen.route
                is Screen.Diagnostics -> screen.route
                is Screen.DebugDashboard -> screen.route
                is Screen.MapDownload -> screen.route
                is Screen.EmergencyGuide -> screen.route
                is Screen.NodeDashboard -> screen.route
                is Screen.AiAssistant -> screen.route
                is Screen.Chat -> "chat"
            }
        }

        assertEquals(allScreens.size, routes.distinct().size)
    }
}
