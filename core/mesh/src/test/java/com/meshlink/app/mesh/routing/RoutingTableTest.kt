package com.meshlink.app.mesh.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutingTableTest {

    private lateinit var table: RoutingTable
    private val myDeviceId = "ME"

    @Before
    fun setUp() {
        table = RoutingTable(myDeviceId)
    }

    @Test
    fun `unknown destination returns null`() {
        assertNull(table.getNextHop("device-unknown"))
    }

    @Test
    fun `addLink then getNextHop returns the direct target`() {
        table.addLink(myDeviceId, "device-A")
        assertEquals("device-A", table.getNextHop("device-A"))
    }

    @Test
    fun `multi-hop getNextHop returns the correct first hop`() {
        // ME -> A -> B -> C
        table.addLink(myDeviceId, "device-A")
        table.updateLinks("device-A", listOf("device-B"), 100)
        table.updateLinks("device-B", listOf("device-C"), 100)
        
        assertEquals("device-A", table.getNextHop("device-C"))
        assertEquals("device-A", table.getNextHop("device-B"))
    }

    @Test
    fun `dijkstra selects shortest path based on hops`() {
        // ME -> A -> B -> D
        // ME -> C -> D
        table.addLink(myDeviceId, "device-A")
        table.addLink(myDeviceId, "device-C")
        
        table.updateLinks("device-A", listOf("device-B"), 100)
        table.updateLinks("device-B", listOf("device-D"), 100)
        table.updateLinks("device-C", listOf("device-D"), 100)
        
        // Path via C is shorter (2 hops vs 3 hops)
        assertEquals("device-C", table.getNextHop("device-D"))
    }

    @Test
    fun `removeRoutesFor removes links from ME`() {
        table.addLink(myDeviceId, "device-A")
        table.addLink(myDeviceId, "device-B")
        table.updateLinks("device-B", listOf("device-C"), 100)
        
        table.removeRoutesFor("device-B")

        assertEquals("device-A", table.getNextHop("device-A"))
        assertNull(table.getNextHop("device-B"))
        assertNull(table.getNextHop("device-C")) // Path to C was through B
    }

    @Test
    fun `knownDestinations returns all nodes in graph`() {
        table.addLink(myDeviceId, "device-A")
        table.updateLinks("device-A", listOf("device-B", "device-C"), 100)
        
        val known = table.knownDestinations()
        assertTrue(known.contains(myDeviceId))
        assertTrue(known.contains("device-A"))
        assertTrue(known.contains("device-B"))
        assertTrue(known.contains("device-C"))
    }
}
