package com.meshlink.app.mesh.transport

import com.meshlink.app.domain.model.MeshPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Supported hardware and network transport types for MeshLink.
 */
enum class TransportType {
    NEARBY,
    MESHTASTIC
}

/**
 * Unified abstraction representing an interchangeable or parallel mesh transport.
 */
interface MeshTransport {
    /** The specific transport type. */
    val transportType: TransportType

    /** Current availability or connection state of the transport. */
    val isAvailable: StateFlow<Boolean>

    /** Stream of incoming domain packets delivered by this transport. */
    val incomingPackets: SharedFlow<MeshPacket>

    /** Start scanning, advertising, or background listening for this transport. */
    fun start()

    /** Stop or disconnect this transport. */
    fun stop()

    /**
     * Send a packet across this transport.
     *
     * @param packet Domain mesh packet to transmit.
     * @param targetAddress Optional transport-specific hardware address or node identifier.
     * @return true if successfully queued/written to transport, false otherwise.
     */
    suspend fun send(packet: MeshPacket, targetAddress: String? = null): Boolean
}
