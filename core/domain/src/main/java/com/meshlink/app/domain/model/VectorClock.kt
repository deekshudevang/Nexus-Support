package com.meshlink.app.domain.model

/**
 * Represents the sync state of a peer in the mesh network.
 * By knowing the highest sequence number we've seen from a peer,
 * we can ask them for all events they generated after this number (delta sync).
 */
data class VectorClock(
    val peerId: String,
    val sequenceNumber: Int
)
