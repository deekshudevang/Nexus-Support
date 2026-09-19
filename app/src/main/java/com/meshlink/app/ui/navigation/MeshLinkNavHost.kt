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
        // ── CHATS tab ─────────────────────────────────────────────────────────
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

        // ── DISCOVER tab ──────────────────────────────────────────────────────
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

        // ── SOS tab ───────────────────────────────────────────────────────────
        composable(Screen.Sos.route) {
            val viewModel: com.meshlink.app.ui.sos.SosViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            SosScreen(viewModel = viewModel)
        }

        // ── MAP tab ───────────────────────────────────────────────────────────
        composable(Screen.Map.route) {
            com.meshlink.app.ui.map.MeshMapScreen()
        }

        // ── Medical Profile (full-screen, no bottom bar) ──────────────────────
        composable(Screen.MedicalProfile.route) {
            MedicalProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Settings (full-screen, no bottom bar) ─────────────────────────────
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDiagnosticsClick = { navController.navigate(Screen.Diagnostics.route) }
            )
        }

        // ── Broadcast (full-screen, no bottom bar) ────────────────────────────
        composable(Screen.Broadcast.route) {
            BroadcastScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Diagnostics (full-screen, no bottom bar) ──────────────────────────
        composable(Screen.Diagnostics.route) {
            com.meshlink.app.ui.diagnostics.DiagnosticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Chat (full-screen, no bottom bar) ─────────────────────────────────
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
