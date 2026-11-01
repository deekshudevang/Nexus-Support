# Mesh Routing & Protocol Specification

This document details the packet structure, multi-hop routing mechanics, loop prevention, and conflict resolution mechanisms used in **Nexus Support**.

---

## 📡 Packet Structure

All wire packets adhere to the Protocol Buffers wire format (`mesh.proto`).

```protobuf
message MeshPacket {
    string id = 1;                     // Unique UUID packet identifier
    string source_node_id = 2;         // Originating peer's public key hash
    string destination_node_id = 3;    // Target peer (or empty for flood broadcast)
    int32 hop_count = 4;               // Incremented at each intermediate hop
    int32 max_hops = 5;                // TTL boundary (default 7)
    int64 timestamp = 6;               // Epoch millisecond creation time
    PacketType type = 7;               // CHAT, SOS, LOCATION, ROUTE_ADVERT, HEARTBEAT
    bytes payload = 8;                 // ECIES / AES-GCM encrypted content
    bytes signature = 9;               // ECDSA SHA-256 signature by source node
    string sender_endpoint_id = 10;    // Radio link level transmitter ID
}
```

---

## 🔀 Routing Mechanics

### 1. Dijkstra Shortest-Path Forwarding
Each node maintains a dynamic topology graph based on periodically exchanged `ROUTE_ADVERT` packets. Link weights are computed dynamically:

$$\text{Weight}(u, v) = \text{BaseCost} + (1.0 - \text{RSSI}_{\text{normalized}}) \times 10 + \text{BatteryPenalty}$$

- Direct high-signal links receive priority.
- Low battery nodes advertise higher cost to preserve energy for critical relays.

### 2. Split-Horizon Loop Prevention
When node $B$ receives a packet from node $A$, $B$ will never relay that packet back onto the link connecting to $A$:

```
[Node A] ──────► [Node B] ──────► [Node C]
               (Will NOT echo
                back to A)
```

### 3. SeenMessageCache Deduplication
To prevent broadcast storms during flooding (e.g., `SOS` broadcasts or network-wide routing updates), every node maintains a time-decaying LRU cryptographic cache of seen `packet.id` hashes.
- If `packet.id` exists in cache $\rightarrow$ packet is dropped silently.
- If new $\rightarrow$ cached for 10 minutes and forwarded to adjacent links.

### 4. Hop Limit & TTL Enforcement
- Every packet begins with `hop_count = 0` and `max_hops = 7`.
- Intermediate relay nodes verify:
  $$\text{hop\_count} + 1 \le \text{max\_hops}$$
- If the condition fails, the packet is discarded at the TTL boundary.

---

## ⏱️ Vector Clocks & CRDT Location Synchronization

Geospatial location events may arrive out-of-order due to variable multi-hop relay latencies. Nexus Support uses causal **Vector Clocks** to achieve eventual consistency:

```
Node A Clock: { A: 4, B: 2 }
Incoming Location Event from A with Sequence: 5
Result: Applied immediately; Clock updated to { A: 5, B: 2 }

Incoming Out-of-Order Event from A with Sequence: 3
Result: Merged into history without overwriting current coordinate state.
```

- Each peer's latest verified coordinate is updated strictly when $Seq_{\text{new}} > Seq_{\text{stored}}$.
- Resolves conflicts deterministically without requiring a centralized clock server.


---
**Last verified:** 2026-09-20 @ HEAD
