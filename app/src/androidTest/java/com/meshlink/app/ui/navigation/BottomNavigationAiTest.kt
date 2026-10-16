package com.meshlink.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavigationAiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabCount_satisfiesSafetyGate_hasFiveOrFewerTabs() {
        assertTrue("Bottom navigation must have 5 or fewer tabs", bottomNavItems.size <= 5)
        assertEquals(5, bottomNavItems.size)
    }

    @Test
    fun canonicalAiRoute_existsExactlyOnce() {
        val aiTabs = bottomNavItems.filter { it.route == Screen.AiAssistant.route }
        assertEquals(1, aiTabs.size)
        assertEquals("ai_assistant", Screen.AiAssistant.route)
    }

    @Test
    fun tabOrder_preservesSosHighPriorityPosition() {
        val labels = bottomNavItems.map { it.label }
        assertEquals(listOf("CHATS", "DISCOVER", "MAP", "AI", "SOS"), labels)
        val lastItem = bottomNavItems.last()
        assertEquals("SOS", lastItem.label)
        assertTrue(lastItem.isSos)
    }

    @Test
    fun bottomBar_tabSelectionDispatchesExpectedRoutes() {
        var currentRoute by mutableStateOf(Screen.Home.route)
        val recordedNavigations = mutableListOf<String>()

        composeRule.setContent {
            Scaffold(
                bottomBar = {
                    MeshBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            recordedNavigations.add(route)
                            currentRoute = route
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding))
            }
        }

        // 1. Verify all 5 tabs are displayed
        composeRule.onNodeWithContentDescription("CHATS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("DISCOVER").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("MAP").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("AI").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("SOS").assertIsDisplayed()

        // 2. Select AI tab
        composeRule.onNodeWithContentDescription("AI").performClick()
        assertEquals(listOf(Screen.AiAssistant.route), recordedNavigations)
        assertEquals(Screen.AiAssistant.route, currentRoute)

        // 3. Select Chats/Home
        composeRule.onNodeWithContentDescription("CHATS").performClick()
        assertEquals(Screen.Home.route, recordedNavigations.last())

        // 4. Select Discover
        composeRule.onNodeWithContentDescription("DISCOVER").performClick()
        assertEquals(Screen.Discovery.route, recordedNavigations.last())

        // 5. Select Map
        composeRule.onNodeWithContentDescription("MAP").performClick()
        assertEquals(Screen.Map.route, recordedNavigations.last())

        // 6. Select SOS
        composeRule.onNodeWithContentDescription("SOS").performClick()
        assertEquals(Screen.Sos.route, recordedNavigations.last())
    }

    @Test
    fun aiTab_remainsVisible_andOpensDisabledBannerWhenFlagIsOff() {
        var currentRoute by mutableStateOf(Screen.AiAssistant.route)

        composeRule.setContent {
            Scaffold(
                bottomBar = {
                    MeshBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { currentRoute = it }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Simulate AiAssistantScreen loaded with isAiEnabled = false
                    com.meshlink.app.ai.OfflineAiDisabledBanner(
                        onNavigateToSettings = {}
                    )
                }
            }
        }

        // AI tab is visible
        composeRule.onNodeWithContentDescription("AI").assertIsDisplayed()

        // Disabled prompt is visible
        composeRule.onNodeWithText("Offline AI is currently disabled in Settings.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Enable Offline AI")
            .assertIsDisplayed()
    }
}
