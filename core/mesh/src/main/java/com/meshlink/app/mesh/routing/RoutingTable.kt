package com.meshlink.app.mesh.routing

import com.meshlink.app.domain.model.NodeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RoutingTable @Inject constructor(
    @Named("localDeviceId") private val myDeviceId: String
) {

    companion object {
        private const val LINK_TTL_MS = 120_000L // 2 minutes
    }

    data class Link(
        val source: String,
        val target: String,
        val battery: Int = 100,
        val timestamp: Long = System.currentTimeMillis()
    )

    // A map of source node to a map of target node -> Link
    private val graph = HashMap<String, HashMap<String, Link>>()

    private val _graphFlow = MutableStateFlow<Map<String, Map<String, Link>>>(emptyMap())
    val graphFlow: StateFlow<Map<String, Map<String, Link>>> = _graphFlow.asStateFlow()

    private fun publishState() {
        val snapshot = graph.mapValues { entry -> entry.value.toMap() }
        _graphFlow.value = snapshot
    }

    @Synchronized
    fun addLink(source: String, target: String, battery: Int = 100) {
        val nodeLinks = graph.getOrPut(source) { HashMap() }
        nodeLinks[target] = Link(source, target, battery, System.currentTimeMillis())
        publishState()
    }

    /**
     * Update links from a node to multiple targets (from a topology heartbeat)
     */
    @Synchronized
    fun updateLinks(source: String, neighbors: List<String>, battery: Int) {
        val now = System.currentTimeMillis()
        val nodeLinks = graph.getOrPut(source) { HashMap() }
        
        // Remove old links not in the new neighbors list
        val iterator = nodeLinks.iterator()
        while(iterator.hasNext()) {
            val entry = iterator.next()
            if (!neighbors.contains(entry.key)) {
                iterator.remove()
            }
        }

        // Add or update links
        for (neighbor in neighbors) {
            nodeLinks[neighbor] = Link(source, neighbor, battery, now)
        }
        publishState()
    }

    @Synchronized
    fun updatePeerMetrics(deviceId: String, batteryLevel: Int) {
        // Direct neighbor update
        val nodeLinks = graph[myDeviceId]
        if (nodeLinks?.containsKey(deviceId) == true) {
            nodeLinks[deviceId] = nodeLinks[deviceId]!!.copy(
                battery = batteryLevel, 
                timestamp = System.currentTimeMillis()
            )
            publishState()
        }
    }

    @Synchronized
    fun getNextHop(destinationDeviceId: String): String? {
        cleanStaleLinks()

        if (myDeviceId == destinationDeviceId) return null

        // Dijkstra's algorithm
        val distances = HashMap<String, Int>()
        val previous = HashMap<String, String>()
        val queue = PriorityQueue<Pair<String, Int>>(compareBy { it.second })

        // Initialize
        val allNodes = graph.keys.toMutableSet()
        graph.values.forEach { it.keys.forEach { node -> allNodes.add(node) } }
        
        for (node in allNodes) {
            distances[node] = Int.MAX_VALUE
        }
        distances[myDeviceId] = 0
        queue.add(Pair(myDeviceId, 0))

        while (queue.isNotEmpty()) {
            val (u, distU) = queue.poll()

            if (distU > (distances[u] ?: Int.MAX_VALUE)) continue
            if (u == destinationDeviceId) break // Found shortest path

            val neighbors = graph[u] ?: continue
            for ((v, link) in neighbors) {
                // Cost is inversely proportional to battery, plus a base cost per hop
                val baseCost = 10
                val batteryPenalty = (100 - link.battery) / 10
                val cost = baseCost + batteryPenalty

                val newDist = distU + cost
                val currentDistV = distances[v] ?: Int.MAX_VALUE
                if (newDist < currentDistV) {
                    distances[v] = newDist
                    previous[v] = u
                    queue.add(Pair(v, newDist))
                }
            }
        }

        // Backtrack to find the first hop
        var curr = destinationDeviceId
        if (!previous.containsKey(curr)) {
            return null // No path
        }

        while (previous[curr] != myDeviceId) {
            curr = previous[curr] ?: return null
        }

        return curr
    }

    @Synchronized
    fun removeRoutesFor(deviceId: String) { // Renamed param for clarity since it's deviceId now, wait, no, caller might pass endpointId!
        // The previous code passed endpointId, but if we changed the routing table to deal with deviceId, we should remove routes by deviceId.
        // I will change the caller to pass deviceId.
        graph[myDeviceId]?.remove(deviceId)
        
        // Also remove if deviceId is the source of any links
        graph.remove(deviceId)
        publishState()
    }

    @Synchronized
    private fun cleanStaleLinks() {
        val now = System.currentTimeMillis()
        var changed = false
        val nodeIter = graph.iterator()
        while (nodeIter.hasNext()) {
            val (node, links) = nodeIter.next()
            val linkIter = links.iterator()
            while (linkIter.hasNext()) {
                val link = linkIter.next()
                if (now - link.value.timestamp > LINK_TTL_MS) {
                    linkIter.remove()
                    changed = true
                }
            }
            if (links.isEmpty() && node != myDeviceId) {
                nodeIter.remove()
                changed = true
            }
        }
        if (changed) publishState()
    }

    @Synchronized
    fun knownDestinations(): Set<String> {
        val nodes = mutableSetOf<String>()
        graph.keys.forEach { nodes.add(it) }
        graph.values.forEach { it.keys.forEach { node -> nodes.add(node) } }
        return nodes
    }

    @Synchronized
    fun getDirectNeighbors(): List<String> {
        return graph[myDeviceId]?.keys?.toList() ?: emptyList()
    }

    @Synchronized
    fun clear() {
        graph.clear()
        publishState()
    }


    private val nodeStatusMap = HashMap<String, NodeStatus>()

    private val _nodeStatusFlow = MutableStateFlow<Map<String, NodeStatus>>(emptyMap())
    val nodeStatusFlow: StateFlow<Map<String, NodeStatus>> = _nodeStatusFlow

    @Synchronized
    fun updateNodeStatus(status: NodeStatus) {
        nodeStatusMap[status.deviceId] = status
        _nodeStatusFlow.value = nodeStatusMap.toMap()
    }

    @Synchronized
    fun getNodeStatus(deviceId: String): NodeStatus? = nodeStatusMap[deviceId]

    @Synchronized
    fun getAllNodeStatuses(): Map<String, NodeStatus> = nodeStatusMap.toMap()
}
