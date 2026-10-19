package com.meshlink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.meshlink.app.ai.AiAssistantScreen
import com.meshlink.app.ui.broadcast.BroadcastScreen
import com.meshlink.app.ui.chat.ChatScreen
import com.meshlink.app.ui.chat.ChatViewModel
import com.meshlink.app.ui.dashboard.NodeDashboardScreen
import com.meshlink.app.ui.debug.DebugDashboardScreen
import com.meshlink.app.ui.diagnostics.DiagnosticsScreen
import com.meshlink.app.ui.discovery.DiscoveryScreen
import com.meshlink.app.ui.guide.EmergencyGuideScreen
import com.meshlink.app.ui.home.HomeScreen
import com.meshlink.app.ui.map.MapDownloadScreen
import com.meshlink.app.ui.map.MeshMapScreen
import com.meshlink.app.ui.medical.MedicalProfileScreen
import com.meshlink.app.ui.settings.SettingsScreen
import com.meshlink.app.ui.sos.SosScreen
import com.meshlink.app.ui.sos.SosViewModel

@Composable
fun MeshLinkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home,
        modifier         = modifier
    ) {
        composable<Screen.Home> {
            HomeScreen(
                onConversationClick = { deviceId, deviceName ->
                    navController.navigate(Screen.Chat(deviceId, deviceName))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings)
                },
                onBroadcastClick = {
                    navController.navigate(Screen.Broadcast)
                }
            )
        }

        composable<Screen.Discovery> {
            DiscoveryScreen(
                onDeviceClick = { endpointId, deviceName ->
                    navController.navigate(Screen.Chat(endpointId, deviceName))
                },
                onSettingsClick = {
                    navController.navigate(Screen.MedicalProfile)
                }
            )
        }

        composable<Screen.Sos> {
            val viewModel: SosViewModel = hiltViewModel()
            SosScreen(
                viewModel = viewModel,
                onGuideClick = { navController.navigate(Screen.EmergencyGuide) },
                onDashboardClick = { navController.navigate(Screen.NodeDashboard) }
            )
        }

        composable<Screen.Map> {
            MeshMapScreen(
                onNavigateToDownloads = { navController.navigate(Screen.MapDownload) }
            )
        }

        composable<Screen.MapDownload> {
            MapDownloadScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.MedicalProfile> {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDiagnosticsClick = { navController.navigate(Screen.Diagnostics) },
                onDebugDashboardClick = { navController.navigate(Screen.DebugDashboard) }
            )
        }

        composable<Screen.Broadcast> {
            BroadcastScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Screen.Diagnostics> {
            DiagnosticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Screen.DebugDashboard> {
            DebugDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.EmergencyGuide> {
            EmergencyGuideScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAi = { navController.navigate(Screen.AiAssistant) }
            )
        }

        composable<Screen.NodeDashboard> {
            NodeDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.AiAssistant> {
            AiAssistantScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home) {
                            popUpTo<Screen.Home> { inclusive = true }
                        }
                    }
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings) }
            )
        }

        composable<Screen.Chat>(
            deepLinks = listOf(
                navDeepLink<Screen.Chat>(basePath = "meshlink://chat")
            )
        ) {
            val viewModel: ChatViewModel = hiltViewModel()
            ChatScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
