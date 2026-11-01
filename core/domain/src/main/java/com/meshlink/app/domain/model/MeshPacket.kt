package com.meshlink.app.domain.model

import java.util.UUID

/**
 * Network-layer message exchanged between devices over Nearby Connections.
 *
 * Phase 3 / direct link fields:
 *   senderId   — current hop sender  (SHA-256(pubKey).take(16))
 *   receiverId — current hop receiver (may differ from finalDestId during forwarding)
 *   content    — HANDSHAKE    → Base64(publicKey)
 *                CHAT         → Base64(nonce[12] + AES-GCM ciphertext + tag[16])
 *                ROUTED_CHAT  → Base64(ephemeralPubKey[91] + nonce[12] + ECIES_ciphertext + tag[16])
 *                BROADCAST    → plaintext string
 *   type       — CHAT | HANDSHAKE | ACK | ROUTED_CHAT | BROADCAST
 *
 * Phase 4 / mesh routing fields:
 *   originId      — original author; never changes during forwarding
 *   finalDestId   — ultimate recipient; BROADCAST_DEST="*" means flood to all
 *   hopCount      — incremented by each relay before forwarding; drop when >= maxHops
 *   maxHops       — packet TTL (default 7 for ROUTED_CHAT, 10 for BROADCAST)
 *   priority      — priority level for QoS and queueing (default 0)
 *   routeHistory  — ordered relay deviceIds traversed so far (for diagnostics)
 */
data class MeshPacket(
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val type: PacketType = PacketType.CHAT,
    val messageId: String = UUID.randomUUID().toString(),

    /** Original author — immutable across all hops. Same as senderId for first hop. */
    val originId: String = senderId,
    /** Ultimate destination deviceId. BROADCAST_DEST means flood to all reachable nodes. */
    val finalDestId: String = receiverId,
    /** Number of relay hops taken so far. Relay increments before forwarding. */
    val hopCount: Int = 0,
    /** Maximum allowed hops. Packet dropped once hopCount >= maxHops. */
    val maxHops: Int = 7,
    /** Priority level (higher is more critical). Used for store-and-forward queueing. */
    val priority: Int = 0,
    /** Ordered list of relaying deviceIds (not including origin or final destination). */
    val routeHistory: List<String> = emptyList(),

    /** Human-readable display name of the original sender. Travels with the packet. */
    val senderName: String = ""
) {
    enum class PacketType {
        /** Direct link — AES-256-GCM with ECDH session key. */
        CHAT,
        /** ECDH public-key exchange — direct link only, never forwarded. */
        HANDSHAKE,
        /** Multi-hop — ECIES encrypted for finalDestId's public key. Relays are opaque. */
        ROUTED_CHAT,
        /** Delivery acknowledgment packet. Plaintext `messageId` of original packet. */
        ACK,
        /** Flood to all reachable nodes. finalDestId == BROADCAST_DEST. Plaintext. */
        BROADCAST,
        /** Emergency SOS flood. Plaintext, high priority. */
        SOS,
        /** Routing metadata. Plaintext JSON (battery, link quality). 1-2 hops max. */
        HEARTBEAT,
        /** Location synchronization payload. Contains JSON array of LocationEventEntity. */
        LOCATION_SYNC,
        /** Node role/resource status announcement. JSON NodeStatus payload. */
        STATUS_UPDATE
    }

    val isBroadcast: Boolean get() = finalDestId == BROADCAST_DEST

    companion object {
        const val BROADCAST_DEST = "*"
    }
}
