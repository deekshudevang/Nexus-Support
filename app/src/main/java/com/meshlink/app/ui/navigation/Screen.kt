package com.meshlink.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for MeshLink Navigation Compose (2.8+).
 * Each route is represented by a @Serializable object or data class.
 */
@Serializable
sealed interface Screen {
    /** Bottom-tab: Conversation list */
    @Serializable
    data object Home : Screen {
        val route: String = "home"
    }

    /** Bottom-tab: Emergency device discovery */
    @Serializable
    data object Discovery : Screen {
        val route: String = "discovery"
    }

    /** Bottom-tab: SOS / emergency broadcast */
    @Serializable
    data object Sos : Screen {
        val route: String = "sos"
    }

    /** Bottom-tab: Live Mesh Map */
    @Serializable
    data object Map : Screen {
        val route: String = "map"
    }

    /** Full-screen: Medical profile */
    @Serializable
    data object MedicalProfile : Screen {
        val route: String = "medical_profile"
    }

    /** Full-screen: App and mesh settings */
    @Serializable
    data object Settings : Screen {
        val route: String = "settings"
    }

    /** Full-screen: Broadcast message to all peers */
    @Serializable
    data object Broadcast : Screen {
        val route: String = "broadcast"
    }

    /** Full-screen: Diagnostics */
    @Serializable
    data object Diagnostics : Screen {
        val route: String = "diagnostics"
    }
    
    /** Full-screen: Debug Dashboard for QA testers */
    @Serializable
    data object DebugDashboard : Screen {
        val route: String = "debug_dashboard"
    }
    
    /** Full-screen: Map Download UI */
    @Serializable
    data object MapDownload : Screen {
        val route: String = "map_download"
    }

    /** Full-screen: Emergency Guide Knowledge Base */
    @Serializable
    data object EmergencyGuide : Screen {
        val route: String = "emergency_guide"
    }

    /** Full-screen: Mesh Node Dashboard */
    @Serializable
    data object NodeDashboard : Screen {
        val route: String = "node_dashboard"
    }

    /** Full-screen: AI Assistant (Voice + RAG) */
    @Serializable
    data object AiAssistant : Screen {
        val route: String = "ai_assistant"
    }

    /** Full-screen: Individual chat with type-safe parameters */
    @Serializable
    data class Chat(
        val deviceId: String,
        val deviceName: String
    ) : Screen {
        companion object {
            const val routePattern = "chat/{deviceId}/{deviceName}"
            fun createRoute(deviceId: String, deviceName: String): Chat =
                Chat(deviceId = deviceId, deviceName = deviceName)
        }
    }
}
