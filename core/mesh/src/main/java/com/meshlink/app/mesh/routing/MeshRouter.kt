package com.meshlink.app.mesh.routing

import android.util.Base64
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.session.SessionKeyStore
import com.meshlink.app.domain.model.Message
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.MeshPacket.PacketType
import com.meshlink.app.domain.model.NodeStatus
import com.meshlink.app.domain.model.PendingMessage
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.MessageRepository
import com.meshlink.app.domain.repository.PendingMessageRepository
import com.meshlink.app.domain.repository.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Core mesh routing engine — pure logic, no Nearby transport calls.
 *
 * [NearbyRepositoryImpl] calls [route] for every incoming packet and [buildAndRoute] for
 * every outgoing message, then acts on the returned [RoutingResult].
 *
 * Routing algorithm:
 *   1. Deduplication   — [SeenMessageCache] drops packets we've already forwarded.
 *   2. TTL check       — drop if hopCount >= maxHops.
 *   3. Local delivery  — if finalDestId == myDeviceId (or broadcast), decrypt and deliver.
 *   4. Forwarding      — if route exists in [RoutingTable], unicast to next hop;
 *                        otherwise flood to all connected peers except the sender.
 *   5. Store-and-fwd   — if no peers available at all, enqueue in [PendingMessageRepository].
 *
 * Encryption model:
 *   CHAT          → AES-256-GCM session key (direct link only)
 *   ROUTED_CHAT   → ECIES (recipient's public key; relay nodes are opaque)
 *   BROADCAST     → no encryption (public announcement)
 */
@Singleton
class MeshRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("localDeviceId") private val myDeviceId: String,
    private val routingTable: RoutingTable,
    private val seenMessageCache: SeenMessageCache,
    private val encryptionService: EncryptionService,
    private val eciesService: EciesService,
    private val sessionKeyStore: SessionKeyStore,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val pendingMessageRepository: PendingMessageRepository,
    private val userProfileManager: UserProfileManager
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel("sos_channel", "Emergency SOS", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Emergency SOS alerts from nearby peers"
            notificationManager.createNotificationChannel(channel)
        }
    }
    companion object {
        private const val PENDING_TTL_MS = 48 * 60 * 60 * 1_000L  // 48 hours
        /** Minimum interval between accepted heartbeats from any single peer (anti-flood). */
        private const val HEARTBEAT_MIN_INTERVAL_MS = 5_000L
    }

    /** Rate-limit table: peerDeviceId → timestamp of last accepted heartbeat. */
    private val heartbeatLastSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()


    /**
     * Process an incoming [packet] received from [fromEndpointId].
     *
     * @param fromEndpointId  Nearby endpointId of the immediate sender (one hop back).
     * @param packet          The deserialized [MeshPacket].
     * @param connectedPeers  Current snapshot of endpointId → peerDeviceId for all CONNECTED peers.
     *
     * @return [RoutingResult] describing what [NearbyRepositoryImpl] should do next.
     *         The caller is responsible for all Nearby transport calls.
     */
    suspend fun route(
        fromEndpointId: String,
        packet: MeshPacket,
        connectedPeers: Map<String, String>    // endpointId → peerDeviceId
    ): RoutingResult = withContext(Dispatchers.IO) {

        // 1. Deduplication — drop packets we've already seen (loop prevention)
        // NOTE: Relying strictly on messageId for dedup. A malicious node could pre-flood fake messageIds to suppress delivery of real messages later.
        if (seenMessageCache.isAlreadySeen(packet.messageId)) {
            Timber.d("MeshRouter: DROP duplicate messageId=${packet.messageId}")
            return@withContext RoutingResult.Drop
        }
        seenMessageCache.markSeen(packet.messageId)

        if (packet.type == PacketType.HEARTBEAT) {
            val now = System.currentTimeMillis()
            val lastSeen = heartbeatLastSeen[packet.originId] ?: 0L
            if (now - lastSeen < HEARTBEAT_MIN_INTERVAL_MS) {
                Timber.w("MeshRouter: Rate-limiting HEARTBEAT flood from ${packet.originId}")
                return@withContext RoutingResult.Drop
            }
            heartbeatLastSeen[packet.originId] = now
        }

        // 2. TTL check
        if (packet.hopCount >= packet.maxHops) {
            Timber.d("MeshRouter: DROP TTL exceeded hopCount=${packet.hopCount} maxHops=${packet.maxHops}")
            return@withContext RoutingResult.Drop
        }

        val isForMe       = packet.finalDestId == myDeviceId
        val isBroadcast   = packet.isBroadcast
        val needForward   = !isForMe  // broadcasts are always forwarded AND delivered

        // 3. Local delivery
        var localMessage: Message? = null
        if (isForMe || isBroadcast) {
            localMessage = decryptAndPersist(packet)
        }

        // 4. Forward / flood
        val forwardTargets = mutableListOf<ForwardTarget>()
        if (needForward || isBroadcast) {
            forwardTargets.addAll(buildForwardTargets(
                packet          = packet,
                fromEndpointId  = fromEndpointId,
                connectedPeers  = connectedPeers,
                isForMe         = isForMe
            ))
        }

        // 5. Generate ACK if we received a direct/routed message meant for us
        if (isForMe && packet.type != PacketType.ACK && packet.type != PacketType.BROADCAST) {
            val ackPacket = MeshPacket(
                senderId = myDeviceId,
                receiverId = packet.originId,
                content = packet.messageId,
                timestamp = System.currentTimeMillis(),
                type = PacketType.ACK,
                originId = myDeviceId,
                finalDestId = packet.originId,
                hopCount = 0,
                maxHops = 10,
                priority = 1
            )
            val nextHop = findNextHop(packet.originId, connectedPeers)
            if (nextHop != null) {
                forwardTargets.add(ForwardTarget(nextHop, ackPacket))
            } else {
                enqueuePending(ackPacket, packet.originId, System.currentTimeMillis())
            }
        }

        RoutingResult.Processed(
            localMessage   = localMessage,
            forwardTargets = forwardTargets
        )
    }


    /**
     * Encrypt and route an outgoing message from the local device.
     *
     * Decision tree:
     *   • [finalDestDeviceId] is directly connected (session key in [SessionKeyStore])
     *     → PacketType.CHAT  (AES-256-GCM, existing Phase 3 path)
     *   • [finalDestDeviceId] is known (public key in [DeviceRepository]) but not connected
     *     → PacketType.ROUTED_CHAT (ECIES; relayed by intermediate peers)
     *   • Completely unknown
     *     → Enqueue in [PendingMessageRepository] and return [RoutingResult.Queued]
     */
    suspend fun buildAndRoute(
        finalDestDeviceId: String,
        plaintext: String,
        connectedPeers: Map<String, String>,   // endpointId → peerDeviceId
        timestamp: Long = System.currentTimeMillis()
    ): RoutingResult = withContext(Dispatchers.IO) {

        val plaintextBytes    = plaintext.toByteArray(Charsets.UTF_8)
        val messageId         = UUID.randomUUID().toString()
        val localDisplayName  = userProfileManager.getDisplayName()

        val directEndpoint = connectedPeers.entries
            .firstOrNull { it.value == finalDestDeviceId }?.key

        if (directEndpoint != null) {
            val sessionKey = sessionKeyStore.getSessionKey(directEndpoint)
            if (sessionKey != null) {
                val encryptedBytes   = encryptionService.encrypt(plaintextBytes, sessionKey)
                val encryptedContent = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                val packet = MeshPacket(
                    senderId     = myDeviceId,
                    receiverId   = finalDestDeviceId,
                    content      = encryptedContent,
                    timestamp    = timestamp,
                    type         = PacketType.CHAT,
                    messageId    = messageId,
                    originId     = myDeviceId,
                    finalDestId  = finalDestDeviceId,
                    hopCount     = 0,
                    maxHops      = 7,
                    senderName   = localDisplayName
                )

                // Persist plaintext (sender side)
                messageRepository.insertMessage(
                    Message(
                        id         = messageId,
                        senderId   = myDeviceId,
                        receiverId = finalDestDeviceId,
                        ciphertext = plaintextBytes,
                        timestamp  = timestamp,
                        delivered  = false,
                        senderName = localDisplayName,
                        status     = 1 // 1 = SENT to network
                    )
                )

                seenMessageCache.markSeen(messageId)
                return@withContext RoutingResult.Processed(
                    localMessage   = null,
                    forwardTargets = listOf(ForwardTarget(directEndpoint, packet))
                )
            }
        }

        val destDevice = deviceRepository.getDeviceById(finalDestDeviceId)

        if (destDevice != null) {
            val eciesContent = eciesService.encryptToBase64(plaintextBytes, destDevice.publicKey)

            val packet = MeshPacket(
                senderId     = myDeviceId,
                receiverId   = finalDestDeviceId,  // will be updated per-hop by forwarder
                content      = eciesContent,
                timestamp    = timestamp,
                type         = PacketType.ROUTED_CHAT,
                messageId    = messageId,
                originId     = myDeviceId,
                finalDestId  = finalDestDeviceId,
                hopCount     = 0,
                maxHops      = 7,
                senderName   = localDisplayName
            )

            val forwardTargets = buildForwardTargets(
                packet         = packet,
                fromEndpointId = null,       // no "from" for self-originated
                connectedPeers = connectedPeers,
                isForMe        = false
            )

            val initialStatus = if (forwardTargets.isEmpty()) 0 else 1

            // Persist plaintext on sender side (we know what we sent)
            messageRepository.insertMessage(
                Message(
                    id         = messageId,
                    senderId   = myDeviceId,
                    receiverId = finalDestDeviceId,
                    ciphertext = plaintextBytes,
                    timestamp  = timestamp,
                    delivered  = false,
                    senderName = localDisplayName,
                    routeHistory = packet.routeHistory,
                    status     = initialStatus
                )
            )

            seenMessageCache.markSeen(messageId)

            if (forwardTargets.isEmpty()) {
                // No connected peers — queue it
                enqueuePending(packet, finalDestDeviceId, timestamp)
                return@withContext RoutingResult.Queued(messageId)
            }

            return@withContext RoutingResult.Processed(
                localMessage   = null,
                forwardTargets = forwardTargets
            )
        }

        Timber.w("MeshRouter: unknown destination $finalDestDeviceId — no public key stored")
        RoutingResult.UnknownDestination(finalDestDeviceId)
    }


    /**
     * Build a [PacketType.BROADCAST] packet originating from this device and flood it.
     * The broadcast is also delivered locally (creates a system message in Room).
     */
    fun buildBroadcast(
        content: String,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = content,
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.BROADCAST,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 10,
            senderName  = userProfileManager.getDisplayName()
        )

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }

    fun buildSos(
        content: String,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = content,
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.SOS,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 10,
            priority    = 10,
            senderName  = userProfileManager.getDisplayName()
        )

        // Local persist
        val msg = com.meshlink.app.domain.model.Message(
            id         = messageId,
            senderId   = myDeviceId,
            receiverId = MeshPacket.BROADCAST_DEST,
            ciphertext = content.toByteArray(Charsets.UTF_8),
            timestamp  = packet.timestamp,
            delivered  = false,
            senderName = packet.senderName,
            status     = 1,
            isSos      = true
        )
        kotlinx.coroutines.runBlocking { messageRepository.insertMessage(msg) }

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }

    fun buildHeartbeat(
        battery: Int,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        val neighbors = routingTable.getDirectNeighbors()
        val json = org.json.JSONObject()
        json.put("battery", battery)
        val arr = org.json.JSONArray()
        neighbors.forEach { arr.put(it) }
        json.put("neighbors", arr)
        val content = json.toString()

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = content,
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.HEARTBEAT,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 2, // Only propagate 1-2 hops to save battery
            priority    = 0,
            senderName  = userProfileManager.getDisplayName()
        )

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }

    fun buildLocationSync(
        content: String,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = content,
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.LOCATION_SYNC,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 5, // Location events have lower TTL to prevent stale flooding
            priority    = 2,
            senderName  = userProfileManager.getDisplayName()
        )

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }

    /**
     * Build a [PacketType.STATUS_UPDATE] packet to announce this node's role and resources.
     * Inspired by RescueMesh-1's community dashboard resource coordination.
     */
    fun buildStatusUpdate(
        nodeStatus: NodeStatus,
        connectedPeers: Map<String, String>
    ): RoutingResult {
        val messageId = UUID.randomUUID().toString()
        seenMessageCache.markSeen(messageId)

        // Also update our own routing table
        routingTable.updateNodeStatus(nodeStatus)

        val packet = MeshPacket(
            senderId    = myDeviceId,
            receiverId  = MeshPacket.BROADCAST_DEST,
            content     = nodeStatus.toJson(),
            timestamp   = System.currentTimeMillis(),
            type        = PacketType.STATUS_UPDATE,
            messageId   = messageId,
            originId    = myDeviceId,
            finalDestId = MeshPacket.BROADCAST_DEST,
            hopCount    = 0,
            maxHops     = 5,
            priority    = 1,
            senderName  = userProfileManager.getDisplayName()
        )

        val targets = connectedPeers.keys.map { ep -> ForwardTarget(ep, packet) }
        return RoutingResult.Processed(localMessage = null, forwardTargets = targets)
    }


    /**
     * Called when a new peer connects. Returns [ForwardTarget]s for any queued messages
     * that can now be delivered via the newly connected peers.
     *
     * [NearbyRepositoryImpl] calls this after every successful handshake.
     */
    suspend fun flushPendingQueue(
        connectedPeers: Map<String, String>
    ): List<ForwardTarget> = withContext(Dispatchers.IO) {
        val allPending = pendingMessageRepository.getAllPending()
        if (allPending.isEmpty()) return@withContext emptyList()

        val targets = mutableListOf<ForwardTarget>()
        for (pending in allPending) {
            val packet = pending.packetJson.toMeshPacketOrNull() ?: continue

            // Can any connected peer forward this to its destination?
            val nextHop = findNextHop(packet.finalDestId, connectedPeers)
            if (nextHop != null) {
                val refreshed = packet.copy(hopCount = 0)  // reset TTL on re-send
                targets.add(ForwardTarget(nextHop, refreshed))
                pendingMessageRepository.remove(pending.id)
                // Update local DB status to 1 (SENT) now that it's leaving the queue
                messageRepository.updateMessageStatus(packet.messageId, 1)
                Timber.i("MeshRouter: flushing pending ${packet.messageId} → $nextHop")
            } else if (packet.priority >= 10 || packet.type == PacketType.SOS) {
                // Epidemic Store-and-Forward (Data Mule):
                // For critical packets (e.g. SOS), opportunistically forward to all connected peers
                // even if we don't have a definitive route, hoping they will carry it closer.
                val refreshed = packet.copy(hopCount = 0)
                connectedPeers.keys.forEach { ep ->
                    targets.add(ForwardTarget(ep, refreshed))
                }
                // Do not remove from pendingQueue; let it expire via TTL so we can hand it off to future peers too.
                Timber.i("MeshRouter: epidemic forwarding for pending SOS ${packet.messageId}")
            }
        }
        targets
    }


    private suspend fun decryptAndPersist(packet: MeshPacket): Message? {
        return when (packet.type) {
            PacketType.CHAT -> {
                // AES-256-GCM via session key with origin sender
                val originEndpoint = findEndpointForDevice(packet.originId)
                val sessionKey     = originEndpoint?.let { sessionKeyStore.getSessionKey(it) }
                if (sessionKey == null) {
                    Timber.w("MeshRouter: no session key for CHAT from ${packet.originId}")
                    return null
                }
                val encryptedBytes = Base64.decode(packet.content, Base64.NO_WRAP)
                val plaintext      = encryptionService.decrypt(encryptedBytes, sessionKey) ?: run {
                    Timber.w("MeshRouter: AES-GCM decrypt failed for msg=${packet.messageId}")
                    return null
                }
                persistAndReturn(packet, plaintext)
            }

            PacketType.ROUTED_CHAT -> {
                // ECIES — decrypt with our private key
                val plaintext = eciesService.decryptFromBase64(packet.content) ?: run {
                    Timber.w("MeshRouter: ECIES decrypt failed for msg=${packet.messageId}")
                    return null
                }
                persistAndReturn(packet, plaintext)
            }

            PacketType.BROADCAST,
            PacketType.SOS -> {
                // Plaintext — no decryption needed
                persistAndReturn(packet, packet.content.toByteArray(Charsets.UTF_8))
            }

            PacketType.ACK -> {
                // The content is the original messageId in plaintext
                messageRepository.updateMessageStatus(packet.content, 2)
                return null
            }

            PacketType.HEARTBEAT -> {
                try {
                    val j = org.json.JSONObject(packet.content)
                    val battery = j.optInt("battery", 100)
                    val nArr = j.optJSONArray("neighbors")
                    val neighbors = buildList {
                        if (nArr != null) {
                            for (i in 0 until nArr.length()) add(nArr.getString(i))
                        }
                    }
                    routingTable.updateLinks(packet.originId, neighbors, battery)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse heartbeat payload")
                }
                return null
            }

            PacketType.LOCATION_SYNC -> {
                Timber.d("MeshRouter: Received LOCATION_SYNC payload size=${packet.content.length}")
                return null
            }

            PacketType.STATUS_UPDATE -> {
                try {
                    val status = packet.content.toNodeStatusOrNull()
                    if (status != null) {
                        routingTable.updateNodeStatus(status)
                        Timber.i("MeshRouter: Updated status for ${status.deviceId} role=${status.role}")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse STATUS_UPDATE payload")
                }
                return null
            }

            else -> null  // HANDSHAKE not routed through MeshRouter
        }
    }

    private suspend fun persistAndReturn(packet: MeshPacket, plaintext: ByteArray): Message {
        val msg = Message(
            id         = packet.messageId,
            senderId   = packet.originId,
            receiverId = myDeviceId,
            ciphertext = plaintext,     // stored as plaintext for UI display (at-rest = disk encryption)
            timestamp  = packet.timestamp,
            delivered  = true,
            senderName = packet.senderName,
            routeHistory = packet.routeHistory,
            isSos = packet.type == PacketType.SOS
        )
        messageRepository.insertMessage(msg)

        // Attempt to extract GPS coordinates from payload
        val contentStr = String(plaintext, Charsets.UTF_8)
        val mapsUrlPrefix = "https://maps.google.com/?q="
        val urlIndex = contentStr.indexOf(mapsUrlPrefix)
        if (urlIndex != -1) {
            try {
                val coords = contentStr.substring(urlIndex + mapsUrlPrefix.length).split(" ")[0].split(",")
                if (coords.size >= 2) {
                    val lat = coords[0].trim().toDoubleOrNull()
                    val lon = coords[1].trim().toDoubleOrNull()
                    if (lat != null && lon != null) {
                        deviceRepository.updateLocation(packet.originId, lat, lon)
                        Timber.i("MeshRouter: extracted location for ${packet.originId} -> $lat, $lon")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "MeshRouter: failed to parse location coordinates")
            }
        }

        // Update the sender's known display name if provided
        if (packet.senderName.isNotEmpty()) {
            val existingDevice = deviceRepository.getDeviceById(packet.originId)
            if (existingDevice != null && existingDevice.displayName != packet.senderName) {
                deviceRepository.updateDisplayName(packet.originId, packet.senderName)
            }
        }

        if (msg.isSos) {
            val sender = packet.senderName.ifEmpty { "Unknown Peer" }
            val text = String(plaintext, Charsets.UTF_8)
            val notification = android.app.Notification.Builder(context, "sos_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("EMERGENCY SOS: $sender")
                .setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(packet.originId.hashCode(), notification)
        }

        return msg
    }

    private fun buildForwardTargets(
        packet: MeshPacket,
        fromEndpointId: String?,
        connectedPeers: Map<String, String>,
        isForMe: Boolean
    ): List<ForwardTarget> {
        // Build forwarded packet — increment hopCount, append self to routeHistory
        val forwardedPacket = packet.copy(
            hopCount     = packet.hopCount + 1,
            routeHistory = packet.routeHistory + myDeviceId,
            senderId     = myDeviceId   // we are the current hop sender
        )

        return if (packet.isBroadcast) {
            // Flood to all connected peers except the one we received from
            connectedPeers.keys
                .filter { it != fromEndpointId }
                .map { ep -> ForwardTarget(ep, forwardedPacket) }
        } else {
            val nextHop = findNextHop(packet.finalDestId, connectedPeers)
            if (nextHop != null && nextHop != fromEndpointId) {
                listOf(ForwardTarget(nextHop, forwardedPacket))
            } else {
                // No known route — flood (excluding sender)
                connectedPeers.keys
                    .filter { it != fromEndpointId }
                    .also { eps ->
                        if (eps.isEmpty() && !isForMe) {
                            Timber.d("MeshRouter: no peers to forward to — packet will be lost")
                        }
                    }
                    .map { ep -> ForwardTarget(ep, forwardedPacket) }
            }
        }
    }

    private fun findNextHop(
        finalDestDeviceId: String,
        connectedPeers: Map<String, String>
    ): String? {
        // 1. Direct connection — best possible route
        val directEp = connectedPeers.entries.firstOrNull { it.value == finalDestDeviceId }?.key
        if (directEp != null) return directEp

        // 2. Routing table lookup (learned from previous packets)
        val tableHopDeviceId = routingTable.getNextHop(finalDestDeviceId)
        if (tableHopDeviceId != null) {
            val tableHopEndpoint = connectedPeers.entries.firstOrNull { it.value == tableHopDeviceId }?.key
            if (tableHopEndpoint != null) return tableHopEndpoint
        }

        return null
    }

    private fun findEndpointForDevice(deviceId: String): String? {
        // This requires NearbyRepositoryImpl to supply the connectedPeers map at call time.
        // MeshRouter.route() receives connectedPeers directly — used only for outgoing buildAndRoute.
        // For incoming CHAT packets, the session key is keyed by endpointId of the immediate sender.
        // Since CHAT is only used for direct connections (hopCount == 0), fromEndpointId == origin endpoint.
        return null  // Handled by the caller who has fromEndpointId context
    }

    private suspend fun enqueuePending(packet: MeshPacket, targetDeviceId: String, now: Long) {
        // HACK: Hardcoding TTL to 48 hours for now. Should really be a node-configurable policy based on battery/storage limits.
        pendingMessageRepository.enqueue(
            PendingMessage(
                id             = UUID.randomUUID().toString(),
                packetJson     = packet.toJson(),
                targetDeviceId = targetDeviceId,
                enqueuedAt     = now,
                expiresAt      = now + PENDING_TTL_MS
            )
        )
        Timber.i("MeshRouter: queued pending message for $targetDeviceId (expires in 48h)")
    }
}


/** Represents what [NearbyRepositoryImpl] should do after [MeshRouter] processes a packet. */
sealed class RoutingResult {
    /** Silently discard — duplicate or TTL exceeded. */
    object Drop : RoutingResult()

    /** Normal outcome: optional local delivery + zero or more forward targets. */
    data class Processed(
        val localMessage: Message?,              // non-null = emit to UI
        val forwardTargets: List<ForwardTarget>  // list of Nearby sends to execute
    ) : RoutingResult()

    /** Enqueued for store-and-forward delivery. */
    data class Queued(val messageId: String) : RoutingResult()

    /** Destination unknown and no public key available — cannot encrypt or queue. */
    data class UnknownDestination(val deviceId: String) : RoutingResult()
}

data class ForwardTarget(
    val endpointId: String,
    val packet: MeshPacket
)


private fun MeshPacket.toJson(): String {
    val sb = StringBuilder()
    sb.append("""{"messageId":"$messageId","senderId":"$senderId","receiverId":"$receiverId",""")
    sb.append(""""content":"${content.replace("\\", "\\\\").replace("\"", "\\\"")}",""")
    sb.append(""""timestamp":$timestamp,"type":"${type.name}",""")
    sb.append(""""originId":"$originId","finalDestId":"$finalDestId",""")
    sb.append(""""hopCount":$hopCount,"maxHops":$maxHops,""")
    val hist = routeHistory.joinToString(",") { "\"$it\"" }
    sb.append(""""routeHistory":[$hist],""")
    sb.append(""""senderName":"${senderName.replace("\\", "\\\\").replace("\"", "\\\"")}"}""")
    return sb.toString()
}

private fun String.toMeshPacketOrNull(): MeshPacket? = try {
    val j = org.json.JSONObject(this)
    val histArray = j.optJSONArray("routeHistory")
    val history   = buildList {
        if (histArray != null) for (i in 0 until histArray.length()) add(histArray.getString(i))
    }
    MeshPacket(
        messageId    = j.optString("messageId", java.util.UUID.randomUUID().toString()),
        senderId     = j.getString("senderId"),
        receiverId   = j.getString("receiverId"),
        content      = j.getString("content"),
        timestamp    = j.getLong("timestamp"),
        type         = MeshPacket.PacketType.valueOf(j.optString("type", "CHAT")),
        originId     = j.optString("originId", j.getString("senderId")),
        finalDestId  = j.optString("finalDestId", j.getString("receiverId")),
        hopCount     = j.optInt("hopCount", 0),
        maxHops      = j.optInt("maxHops", 7),
        routeHistory = history,
        senderName   = j.optString("senderName", "")
    )
} catch (e: Exception) {
    null
}

private fun String.toNodeStatusOrNull(): NodeStatus? = try {
    val j = org.json.JSONObject(this)
    NodeStatus(
        deviceId = j.getString("deviceId"),
        displayName = j.optString("displayName", ""),
        role = try {
            com.meshlink.app.domain.model.NodeRole.valueOf(j.optString("role", "SURVIVOR"))
        } catch (_: Exception) { com.meshlink.app.domain.model.NodeRole.SURVIVOR },
        batteryLevel = j.optInt("batteryLevel", 100),
        hasWater = j.optBoolean("hasWater", false),
        hasFood = j.optBoolean("hasFood", false),
        hasMedKit = j.optBoolean("hasMedKit", false),
        needsHelp = j.optBoolean("needsHelp", false),
        personCount = j.optInt("personCount", 1),
        latitude = j.optDouble("latitude", 0.0),
        longitude = j.optDouble("longitude", 0.0),
        timestamp = j.optLong("timestamp", System.currentTimeMillis())
    )
} catch (e: Exception) {
    null
}
