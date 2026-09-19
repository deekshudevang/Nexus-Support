package com.meshlink.app.location

import com.meshlink.app.domain.model.RoutingResult

interface LocationSyncManager {
    /**
     * Called when a new peer connects. Exits current location state and pushes to the new peer.
     */
    suspend fun onPeerConnected(peerDeviceId: String)

    /**
     * Processes an incoming LOCATION_SYNC JSON payload.
     */
    suspend fun processIncomingSync(payload: String)

    /**
     * Broadcasts a new location event to all connected peers.
     */
    suspend fun broadcastLocation(lat: Double, lon: Double, accuracy: Float)
}
