package com.meshlink.app.domain.repository

interface LocationSyncManager {
    /**
     * Called when a new peer connects. Exits current location state and pushes to the new peer.
     */
    suspend fun onPeerConnected(peerDeviceId: String)

    /**
     * Processes an incoming LOCATION_SYNC JSON payload.
     */
    suspend fun processIncomingSync(payload: String, sourcePeerId: String? = null)

    /**
     * Broadcasts a new location event to all connected peers.
     */
    suspend fun broadcastLocation(lat: Double, lon: Double, accuracy: Float)
}
