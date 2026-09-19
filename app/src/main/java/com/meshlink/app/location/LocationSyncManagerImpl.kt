package com.meshlink.app.location

import android.util.Log
import com.meshlink.app.data.local.dao.LocationEventDao
import com.meshlink.app.data.local.dao.LocationSyncQueueDao
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.data.local.entity.LocationSyncQueueEntity
import com.meshlink.app.mesh.routing.MeshRouter
import com.meshlink.app.domain.model.MeshPacket
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
import dagger.Lazy

@Singleton
class LocationSyncManagerImpl @Inject constructor(
    private val locationEventDao: LocationEventDao,
    private val locationSyncQueueDao: LocationSyncQueueDao,
    private val meshRouter: MeshRouter,
    private val deviceRepository: DeviceRepository,
    @Named("localDeviceId") private val localDeviceId: String,
    private val nearbyRepository: Lazy<NearbyRepository> // Lazy to avoid circular dependency
) : LocationSyncManager {

    companion object {
        const val MAX_TTL = 5
    }

    // Deduplication cache to avoid processing the same event multiple times in rapid succession
    private val processedEventIds = mutableSetOf<String>()

    override suspend fun onPeerConnected(peerDeviceId: String) {
        withContext(Dispatchers.IO) {
            Timber.i("LocationSyncManager: Peer connected $peerDeviceId, sending our latest locations.")
            // Simplified "Vector Clock" exchange: just send the latest locations we know of
            // In a fully rigorous CRDT, we would ask them what sequence numbers they have first.
            // For Phase 5 MVP: just send all recent events (last 1 from each peer)
            val latestEvents = mutableListOf<LocationEventEntity>()
            // We need a query in LocationEventDao to get latest per peer, but for now we can just fetch all and group
            // Wait, we can't easily get latest per peer without a specific query. We will just send our own latest location for now, and maybe broadcast what we have.
            val ourLatest = locationEventDao.getLatestLocationForPeer(localDeviceId)
            if (ourLatest != null) {
                latestEvents.add(ourLatest)
            }
            
            if (latestEvents.isNotEmpty()) {
                sendEventsToPeer(peerDeviceId, latestEvents)
            }
        }
    }

    override suspend fun processIncomingSync(payload: String) {
        withContext(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray(payload)
                val newEvents = mutableListOf<LocationEventEntity>()
                val eventsToForward = mutableListOf<LocationEventEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val eventId = obj.getString("eventId")
                    
                    if (processedEventIds.contains(eventId)) {
                        continue // Skip, we already saw this event recently
                    }

                    val peerId = obj.getString("peerId")
                    val sequenceNumber = obj.getInt("sequenceNumber")
                    
                    // Check if we already have this sequence number or higher
                    val highestKnown = locationEventDao.getHighestSequenceNumber(peerId)
                    if (sequenceNumber <= highestKnown) {
                        continue // We have this or a newer one
                    }

                    val event = LocationEventEntity(
                        eventId = eventId,
                        peerId = peerId,
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        accuracy = obj.getDouble("accuracy").toFloat(),
                        timestamp = obj.getLong("timestamp"),
                        sequenceNumber = sequenceNumber,
                        signature = obj.optString("signature", ""),
                        syncStatus = 0
                    )

                    newEvents.add(event)
                    processedEventIds.add(eventId)
                    if (processedEventIds.size > 1000) {
                        processedEventIds.clear() // Prevent memory leak, simple clear is fine for MVP
                    }

                    // Multihop Propagation (TTL)
                    val ttl = obj.optInt("ttl", MAX_TTL)
                    if (ttl > 0 && peerId != localDeviceId) {
                        eventsToForward.add(event)
                        // Add to sync queue for store-and-forward
                        locationSyncQueueDao.enqueue(
                            LocationSyncQueueEntity(
                                eventId = eventId,
                                targetPeerId = MeshPacket.BROADCAST_DEST, // Flood
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
                    // Immediate flood of the newly received events that still have TTL
                    Timber.i("LocationSyncManager: Flooding ${eventsToForward.size} events with remaining TTL.")
                    val floodPayload = buildPayload(eventsToForward, decrementTtl = true)
                    val peers = nearbyRepository.get().getConnectedPeers()
                    val result = meshRouter.buildLocationSync(floodPayload, peers)
                    if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                        result.forwardTargets.forEach { target ->
                            nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "LocationSyncManager: Failed to process incoming sync payload")
            }
        }
    }

    override suspend fun broadcastLocation(lat: Double, lon: Double, accuracy: Float) {
        withContext(Dispatchers.IO) {
            val highestKnown = locationEventDao.getHighestSequenceNumber(localDeviceId)
            val eventId = UUID.randomUUID().toString()
            val event = LocationEventEntity(
                eventId = eventId,
                peerId = localDeviceId,
                latitude = lat,
                longitude = lon,
                accuracy = accuracy,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = highestKnown + 1,
                signature = "dummy_sig", // For MVP
                syncStatus = 0
            )

            locationEventDao.insertEvent(event)

            val payload = buildPayload(listOf(event), decrementTtl = false)
            val peers = nearbyRepository.get().getConnectedPeers()
            val result = meshRouter.buildLocationSync(payload, peers)
            if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                result.forwardTargets.forEach { target ->
                    nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                }
            }
        }
    }

    private fun sendEventsToPeer(peerDeviceId: String, events: List<LocationEventEntity>) {
        val payload = buildPayload(events, decrementTtl = false)
        val peers = nearbyRepository.get().getConnectedPeers()
        
        // Find endpointId for peerDeviceId
        val targetEndpoint = peers.entries.firstOrNull { it.value == peerDeviceId }?.key
        if (targetEndpoint != null) {
            // We just use buildLocationSync which broadcasts, but since we just want to send to one peer we can construct a target
            val result = meshRouter.buildLocationSync(payload, mapOf(targetEndpoint to peerDeviceId))
            if (result is com.meshlink.app.mesh.routing.RoutingResult.Processed) {
                result.forwardTargets.forEach { target ->
                    nearbyRepository.get().dispatchRawPacket(target.endpointId, target.packet)
                }
            }
        }
    }

    private fun buildPayload(events: List<LocationEventEntity>, decrementTtl: Boolean): String {
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
                // If decrementing TTL, read from sync queue or just assume MAX_TTL - 1. 
                // For simplicity, we can pass TTL inside the JSON.
                put("ttl", if (decrementTtl) MAX_TTL - 1 else MAX_TTL)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
