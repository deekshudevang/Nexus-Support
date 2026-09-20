package com.meshlink.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.meshlink.app.ui.broadcast.BroadcastScreen
import com.meshlink.app.ui.chat.ChatScreen
import com.meshlink.app.ui.discovery.DiscoveryScreen
import com.meshlink.app.ui.home.HomeScreen
import com.meshlink.app.ui.medical.MedicalProfileScreen
import com.meshlink.app.ui.sos.SosScreen
import com.meshlink.app.ui.settings.SettingsScreen
import com.meshlink.app.ui.guide.EmergencyGuideScreen
import com.meshlink.app.ui.dashboard.NodeDashboardScreen

@Composable
fun MeshLinkNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
        modifier         = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onConversationClick = { deviceId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(deviceId, deviceName))
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onBroadcastClick = {
                    navController.navigate(Screen.Broadcast.route)
                }
            )
        }

        composable(Screen.Discovery.route) {
            DiscoveryScreen(
                onDeviceClick = { endpointId, deviceName ->
                    navController.navigate(Screen.Chat.createRoute(endpointId, deviceName))
                },
                onSettingsClick = {
                    navController.navigate(Screen.MedicalProfile.route)
                }
            )
        }

        composable(Screen.Sos.route) {
            val viewModel: com.meshlink.app.ui.sos.SosViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            SosScreen(
                viewModel = viewModel,
                onGuideClick = { navController.navigate(Screen.EmergencyGuide.route) },
                onDashboardClick = { navController.navigate(Screen.NodeDashboard.route) }
            )
        }

        composable(Screen.Map.route) {
            com.meshlink.app.ui.map.MeshMapScreen(
                onNavigateToDownloads = { navController.navigate(Screen.MapDownload.route) }
            )
        }

        composable(Screen.MapDownload.route) {
            com.meshlink.app.ui.map.MapDownloadScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MedicalProfile.route) {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDiagnosticsClick = { navController.navigate(Screen.Diagnostics.route) },
                onDebugDashboardClick = { navController.navigate(Screen.DebugDashboard.route) }
            )
        }

        composable(Screen.Broadcast.route) {
            BroadcastScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Diagnostics.route) {
            com.meshlink.app.ui.diagnostics.DiagnosticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.DebugDashboard.route) {
            com.meshlink.app.ui.debug.DebugDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.EmergencyGuide.route) {
            EmergencyGuideScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) }
            )
        }

        composable(Screen.NodeDashboard.route) {
            NodeDashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AiAssistant.route) {
            com.meshlink.app.ai.AiAssistantScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = Screen.Chat.route,
            arguments = listOf(
                navArgument("deviceId")   { type = NavType.StringType },
                navArgument("deviceName") { type = NavType.StringType }
            )
        ) {
            val viewModel: com.meshlink.app.ui.chat.ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            ChatScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
