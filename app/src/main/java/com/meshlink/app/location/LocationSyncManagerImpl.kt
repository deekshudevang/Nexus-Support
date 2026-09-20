package com.meshlink.app.location

import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.dao.LocationSyncQueueDao
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.data.local.entity.LocationSyncQueueEntity
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.model.VectorClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.meshlink.app.domain.repository.NearbyRepository
import com.meshlink.app.domain.repository.DeviceRepository
import com.meshlink.app.domain.repository.LocationSyncManager
import com.meshlink.app.data.local.dao.ProcessedEventDao
import com.meshlink.app.data.local.entity.ProcessedEventEntity
import dagger.Lazy
import com.meshlink.app.crypto.CryptoManager

@Singleton
class LocationSyncManagerImpl @Inject constructor(
    private val locationEventDao: LocationEventDao,
    private val locationSyncQueueDao: LocationSyncQueueDao,
    private val processedEventDao: ProcessedEventDao,
    private val meshRouter: MeshRouter,
    private val deviceRepository: DeviceRepository,
    @Named("localDeviceId") private val localDeviceId: String,
    private val cryptoManager: CryptoManager,
    private val nearbyRepository: Lazy<NearbyRepository> // Lazy to avoid circular dependency
) : LocationSyncManager {

    companion object {
        const val MAX_TTL = 5
    }

    override suspend fun onPeerConnected(peerDeviceId: String) {
        withContext(Dispatchers.IO) {
            Timber.i("LocationSyncManager: Peer connected $peerDeviceId — initiating bidirectional VectorClock exchange.")
            // Send our VectorClock so the peer knows what we have and can send us their delta.
            // The peer will respond with their own VectorClock, triggering our handleVectorClockRequest
            // to send them back anything they're missing. This ensures both sides converge after a partition.
            val ourClocks = locationEventDao.getVectorClock()
            val payload = buildVectorClockPayload(ourClocks)
            sendPayloadToPeer(peerDeviceId, payload)
        }
    }

    override suspend fun processIncomingSync(payload: String, sourcePeerId: String?) {
        withContext(Dispatchers.IO) {
            try {
                if (payload.startsWith("{") && JSONObject(payload).optString("type") == "vector_clock") {
                    handleVectorClockRequest(payload, sourcePeerId)
                } else if (payload.startsWith("[")) {
                    // Legacy array or standard events payload
                    handleEventsPayload(payload, sourcePeerId)
                }
            } catch (e: Exception) {
                Timber.e(e, "LocationSyncManager: Failed to process incoming sync payload")
            }
        }
    }

    private suspend fun handleVectorClockRequest(payload: String, sourcePeerId: String?) {
        val obj = JSONObject(payload)
        val clocksArray = obj.getJSONArray("clocks")
        val peerState = mutableMapOf<String, Int>()

        for (i in 0 until clocksArray.length()) {
            val clock = clocksArray.getJSONObject(i)
            peerState[clock.getString("peerId")] = clock.getInt("sequenceNumber")
        }

        // Compute delta: what do we have that the peer doesn't?
        val ourClocks = locationEventDao.getVectorClock()
        val missingEvents = mutableListOf<LocationEventEntity>()

        for (ourClock in ourClocks) {
            val peerSeq = peerState[ourClock.peerId] ?: 0
            if (ourClock.sequenceNumber > peerSeq) {
                val events = locationEventDao.getEventsAfterSequence(ourClock.peerId, peerSeq)
                missingEvents.addAll(events)
            }
        }

        if (missingEvents.isNotEmpty() && sourcePeerId != null) {
            Timber.i("LocationSyncManager: Sending ${missingEvents.size} delta events to $sourcePeerId after partition merge.")
            val eventsPayload = buildEventsPayload(missingEvents, decrementTtl = false)
            val peers = nearbyRepository.get().getConnectedPeers()
            val targetPeers = peers.filterValues { it == sourcePeerId }
            val result = meshRouter.buildLocationSync(eventsPayload, targetPeers)
            if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                result.forwardTargets.forEach { target ->
                    nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                }
            }
        }

        // Reply with our own VectorClock so the sender can compute THEIR delta too.
        // This is the second leg of the bidirectional handshake:
        // A sends clock → B sends delta + B's clock → A sends delta
        if (sourcePeerId != null) {
            val replyPayload = buildVectorClockPayload(ourClocks)
            sendPayloadToPeer(sourcePeerId, replyPayload)
        }
    }

    private suspend fun handleEventsPayload(payload: String, sourcePeerId: String?) {
        val jsonArray = JSONArray(payload)
        val newEvents = mutableListOf<LocationEventEntity>()
        val eventsToForward = mutableListOf<LocationEventEntity>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val eventId = obj.getString("eventId")
            
            if (processedEventDao.isProcessed(eventId)) continue

            val peerId = obj.getString("peerId")
            val sequenceNumber = obj.getInt("sequenceNumber")
            
            // REMOVED: sequenceNumber <= highestKnown check. We rely on isProcessed to avoid duplicates.
            // This fixes the out-of-order drop bug.

            val signature = obj.optString("signature", "")
            val publicKey = obj.optString("publicKey", "")
            val latitude = obj.getDouble("latitude")
            val longitude = obj.getDouble("longitude")
            val accuracy = obj.getDouble("accuracy").toFloat()
            val timestamp = obj.getLong("timestamp")

            // Replay attack / bounds check: reject events > 24h old or in the future
            val now = System.currentTimeMillis()
            if (timestamp > now + 60000 || timestamp < now - (24 * 60 * 60 * 1000L)) {
                Timber.w("LocationSyncManager: Event $eventId from $peerId has out-of-bounds timestamp. Dropping.")
                continue
            }

            // Verify the signature
            val payloadToVerify = "$eventId:$peerId:$latitude:$longitude:$accuracy:$timestamp:$sequenceNumber".toByteArray(Charsets.UTF_8)
            val isValid = cryptoManager.verify(payloadToVerify, signature, publicKey)
            if (!isValid) {
                Timber.w("LocationSyncManager: Invalid signature for event $eventId from $peerId. Dropping.")
                continue
            }

            val event = LocationEventEntity(
                eventId = eventId,
                peerId = peerId,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                timestamp = timestamp,
                sequenceNumber = sequenceNumber,
                signature = signature,
                publicKey = publicKey,
                syncStatus = 0
            )

            newEvents.add(event)
            processedEventDao.insert(ProcessedEventEntity(eventId))

            val ttl = obj.optInt("ttl", MAX_TTL)
            if (ttl > 0 && peerId != localDeviceId) {
                eventsToForward.add(event)
                locationSyncQueueDao.enqueue(
                    LocationSyncQueueEntity(
                        eventId = eventId,
                        targetPeerId = MeshPacket.BROADCAST_DEST,
                        ttl = ttl - 1
                    )
                )
            }
        }

        if (newEvents.isNotEmpty()) {
            locationEventDao.insertEvents(newEvents)
            Timber.i("LocationSyncManager: Inserted ${newEvents.size} new location events.")
        }

        if (eventsToForward.isNotEmpty()) {
            Timber.i("LocationSyncManager: Flooding ${eventsToForward.size} events with remaining TTL.")
            val floodPayload = buildEventsPayload(eventsToForward, decrementTtl = true)
            // Implement split-horizon: Do not broadcast back to the node that sent it to us
            val peers = nearbyRepository.get().getConnectedPeers().filterValues { it != sourcePeerId }
            if (peers.isNotEmpty()) {
                val result = meshRouter.buildLocationSync(floodPayload, peers)
                if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                    result.forwardTargets.forEach { target ->
                        nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                    }
                }
            }
        }
    }

    override suspend fun broadcastLocation(lat: Double, lon: Double, accuracy: Float) {
        withContext(Dispatchers.IO) {
            val highestKnown = locationEventDao.getHighestSequenceNumber(localDeviceId)
            val eventId = UUID.randomUUID().toString()
            val sequenceNumber = highestKnown + 1
            val timestamp = System.currentTimeMillis()
            
            val payloadToSign = "$eventId:$localDeviceId:$lat:$lon:$accuracy:$timestamp:$sequenceNumber".toByteArray(Charsets.UTF_8)
            val signature = cryptoManager.sign(payloadToSign)
            val publicKey = cryptoManager.getPublicKeyBase64()

            val event = LocationEventEntity(
                eventId = eventId,
                peerId = localDeviceId,
                latitude = lat,
                longitude = lon,
                accuracy = accuracy,
                timestamp = timestamp,
                sequenceNumber = sequenceNumber,
                signature = signature,
                publicKey = publicKey,
                syncStatus = 0
            )

            locationEventDao.insertEvent(event)

            val payload = buildEventsPayload(listOf(event), decrementTtl = false)
            val peers = nearbyRepository.get().getConnectedPeers()
            val result = meshRouter.buildLocationSync(payload, peers)
            if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                result.forwardTargets.forEach { target ->
                    nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                }
            }
        }
    }

    private fun sendPayloadToPeer(peerDeviceId: String, payload: String) {
        val peers = nearbyRepository.get().getConnectedPeers()
        val targetEndpoint = peers.entries.firstOrNull { it.value == peerDeviceId }?.key
        if (targetEndpoint != null) {
            val result = meshRouter.buildLocationSync(payload, mapOf(targetEndpoint to peerDeviceId))
            if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                result.forwardTargets.forEach { target ->
                    nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                }
            }
        }
    }

    private fun buildVectorClockPayload(clocks: List<VectorClock>): String {
        val obj = JSONObject()
        obj.put("type", "vector_clock")
        obj.put("sourceDeviceId", localDeviceId)
        val array = JSONArray()
        for (clock in clocks) {
            val cObj = JSONObject()
            cObj.put("peerId", clock.peerId)
            cObj.put("sequenceNumber", clock.sequenceNumber)
            array.put(cObj)
        }
        obj.put("clocks", array)
        return obj.toString()
    }

    private fun buildEventsPayload(events: List<LocationEventEntity>, decrementTtl: Boolean): String {
        val array = JSONArray()
        for (event in events) {
            val obj = JSONObject().apply {
                put("eventId", event.eventId)
                put("peerId", event.peerId)
                put("latitude", event.latitude)
                put("longitude", event.longitude)
                put("accuracy", event.accuracy)
                put("timestamp", event.timestamp)
                put("sequenceNumber", event.sequenceNumber)
                put("signature", event.signature)
                put("publicKey", event.publicKey)
                put("ttl", if (decrementTtl) MAX_TTL - 1 else MAX_TTL)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
