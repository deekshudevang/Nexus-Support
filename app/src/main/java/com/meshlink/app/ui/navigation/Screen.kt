package com.meshlink.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    /** Bottom-tab: Conversation list */
    data object Home : Screen("home")

    /** Bottom-tab: Emergency device discovery */
    data object Discovery : Screen("discovery")

    /** Bottom-tab: SOS / emergency broadcast */
    data object Sos : Screen("sos")

    /** Bottom-tab: Live Mesh Map */
    data object Map : Screen("map")

    /** Full-screen: Medical profile */
    data object MedicalProfile : Screen("medical_profile")

    /** Full-screen: Broadcast message to all peers */
    data object Broadcast : Screen("broadcast")

    object Diagnostics : Screen("diagnostics")
    
    /** Full-screen: Debug Dashboard for QA testers */
    object DebugDashboard : Screen("debug_dashboard")
    
    /** Full-screen: Map Download UI */
    object MapDownload : Screen("map_download")

    /** Full-screen: Emergency Guide Knowledge Base */
    object EmergencyGuide : Screen("emergency_guide")

    /** Full-screen: Mesh Node Dashboard */
    object NodeDashboard : Screen("node_dashboard")

    /** Full-screen: AI Assistant (Voice + RAG) */
    object AiAssistant : Screen("ai_assistant")

    /** Full-screen: Individual chat */
    data object Chat : Screen("chat/{deviceId}/{deviceName}") {
        fun createRoute(deviceId: String, deviceName: String): String {
            val encodedId   = URLEncoder.encode(deviceId, "UTF-8")
            val encodedName = URLEncoder.encode(deviceName, "UTF-8")
            return "chat/$encodedId/$encodedName"
        }
    }
}
