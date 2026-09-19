package com.meshlink.app.mesh.routing

import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory routing table: maps a known [destinationDeviceId] to the live [endpointId]
 * of the best next-hop Nearby peer that can reach it.
 *
 * Phase 4 uses a simple **direct-neighbor** routing model:
 *   • Every directly connected peer is a 1-hop route to itself.
 *   • Multi-hop routes are NOT stored here — unknown destinations use flooding.
 *
 * Routes have a TTL ([ROUTE_TTL_MS], default 60 s).  A route is considered stale once
 * [getNextHop] is called and the entry's age exceeds the TTL.  Routes are also explicitly
 * removed when a peer disconnects via [removeRoutesFor].
 *
 * Thread safety: all public methods are @Synchronized.
 */
@Singleton
class RoutingTable @Inject constructor() {

    companion object {
        /** A route entry older than this is treated as expired. */
        private const val ROUTE_TTL_MS = 60_000L
    }

    data class RouteEntry(
        val nextHopEndpointId: String,
        val batteryLevel: Int = 100,
        val hopCount: Int = 1,
        val addedAt: Long = System.currentTimeMillis()
    )

    // destinationDeviceId → list of possible next-hop entries
    private val table = HashMap<String, MutableList<RouteEntry>>()
    
    private val _routesFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<RouteEntry>>>(emptyMap())
    val routesFlow: kotlinx.coroutines.flow.StateFlow<Map<String, List<RouteEntry>>> = _routesFlow.asStateFlow()

    private fun publishState() {
        // Deep copy the map
        _routesFlow.value = table.mapValues { it.value.toList() }
    }

    @Synchronized
    fun addRoute(destinationDeviceId: String, nextHopEndpointId: String, batteryLevel: Int = 100, hopCount: Int = 1) {
        val entries = table.getOrPut(destinationDeviceId) { mutableListOf() }
        entries.removeAll { it.nextHopEndpointId == nextHopEndpointId }
        entries.add(RouteEntry(nextHopEndpointId, batteryLevel, hopCount))
        Timber.d("RoutingTable: added route $destinationDeviceId → endpointId=$nextHopEndpointId (batt=$batteryLevel, hops=$hopCount)")
        publishState()
    }

    @Synchronized
    fun updatePeerMetrics(deviceId: String, batteryLevel: Int) {
        val entries = table[deviceId] ?: return
        var changed = false
        for (i in entries.indices) {
            if (entries[i].hopCount == 1) { // Only update direct neighbors' battery
                entries[i] = entries[i].copy(batteryLevel = batteryLevel, addedAt = System.currentTimeMillis())
                changed = true
            }
        }
        if (changed) publishState()
    }

    @Synchronized
    fun getNextHop(destinationDeviceId: String): String? {
        val entries = table[destinationDeviceId] ?: return null
        
        // Remove stale routes
        val now = System.currentTimeMillis()
        entries.removeAll { now - it.addedAt > ROUTE_TTL_MS }
        
        if (entries.isEmpty()) {
            table.remove(destinationDeviceId)
            return null
        }
        
        // Dijkstra / scoring: Maximize battery, minimize hops
        // Score = battery - (hopCount * 10). Higher is better.
        val bestRoute = entries.maxByOrNull { it.batteryLevel - (it.hopCount * 10) }
        
        return bestRoute?.nextHopEndpointId
    }

    /**
     * Remove all routes whose next-hop is [endpointId].
     * Called when a Nearby endpoint disconnects so we don't forward into a dead link.
     */
    @Synchronized
    fun removeRoutesFor(endpointId: String) {
        var removedAny = false
        val it = table.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val removed = entry.value.removeAll { it.nextHopEndpointId == endpointId }
            if (removed) removedAny = true
            if (entry.value.isEmpty()) {
                it.remove()
            }
        }
        if (removedAny) {
            Timber.d("RoutingTable: removed routes via endpointId=$endpointId")
            publishState()
        }
    }

    /** Returns a snapshot of all known destinations (for debug/logging). */
    @Synchronized
    fun knownDestinations(): Set<String> = table.keys.toSet()

    @Synchronized
    fun clear() {
        table.clear()
        publishState()
    }
}
