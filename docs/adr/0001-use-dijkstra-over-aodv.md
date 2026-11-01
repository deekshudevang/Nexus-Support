# 1. Use Dijkstra over AODV for Mesh Routing

Date: 2024-03-12

## Status
Accepted

## Context
We need a routing protocol to discover paths across an ad-hoc graph of mobile nodes (Android devices). Typical MANET (Mobile Ad-hoc Network) deployments use reactive protocols like AODV (Ad hoc On-Demand Distance Vector) or proactive ones like OLSR. Our network is relatively small (max 7 hops, <50 concurrent nodes in a local area), highly dynamic, and heavily constrained by battery. 

## Decision
We chose a simplified link-state protocol using Dijkstra's shortest path algorithm over a reactive protocol like AODV. 

Nodes periodically broadcast their local 1-hop neighbor lists. Every node independently builds a full view of the local graph and computes the shortest path to any known destination using Dijkstra.

## Consequences
**Pros:**
* **Simplicity:** No need to implement complex RREQ/RREP (Route Request/Reply) state machines.
* **Immediate Routing:** We know the entire local topology. If a route exists, we can dispatch immediately without waiting for a route discovery phase.
* **Debugging:** It's trivial to dump the adjacency matrix to the UI for field debugging.

**Cons:**
* **Overhead:** Broadcasts of the link state consume airtime. We mitigate this by aggressively tuning the broadcast interval based on battery state and movement.
* **Stale routes:** The graph view can become stale. If a link drops, we rely on the `SeenMessageCache` and store-and-forward mechanisms to eventually deliver the payload when topology updates.
