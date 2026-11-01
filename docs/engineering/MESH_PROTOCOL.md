# Nexus-Support Mesh Protocol Specification (v2.1)

## 1. Overview & Protocol Architecture
Nexus-Support implements an ad-hoc, multi-hop, decentralized mesh communication protocol designed for emergency environments without cellular or internet infrastructure.

The stack utilizes:
- **Transport Layer:** Google Nearby Connections (P2P_CLUSTER strategy over BLE and Wi-Fi Direct), with optional Meshtastic LoRa serial bridges.
- **Network Layer:** Ad-hoc distance-vector / hop-count bounded flood with store-and-forward queueing.
- **Security Layer:** Direct link AES-256-GCM authenticated encryption with ephemeral ECDH key agreement.

---

## 2. Frame Formats & Encodings

### A. Compact 19-Byte SOS Beacon (BLE Fast Broadcast)
For high-frequency, low-latency broadcast in severe RF interference, SOS beacons are encoded in a compact 19-byte frame:
- **Byte 0:** Protocol Version (`0x01`)
- **Bytes 1-2:** Sender ID Hash / Node ID (`Short`, 16-bit)
- **Bytes 3-4:** Seconds since midnight UTC (`Short`, 16-bit)
- **Bytes 5-8:** Latitude (`Float`, 32-bit IEEE 754)
- **Bytes 9-12:** Longitude (`Float`, 32-bit IEEE 754)
- **Byte 13:** Emergency Type (`0=GENERAL, 1=MEDICAL, 2=FIRE, 3=FLOOD`)
- **Byte 14:** Battery Percentage (`0-100%`)
- **Bytes 15-16:** Per-activation Cryptographic Nonce (`Short`, generated via `SecureRandom` to guarantee collision resistance)
- **Bytes 17-18:** CRC-16/CCITT checksum computed over bytes `[0..16]`

### B. Standard MeshPacket (JSON over P2P Frame)
For routed chat, acknowledgments, and community broadcasts:
- **`messageId`:** UUID string (unique identifier)
- **`senderId`:** Current hop transmitting node ID (SHA-256 truncated)
- **`receiverId`:** Current hop recipient node ID
- **`originId`:** Originating author node ID (immutable across relays)
- **`finalDestId`:** Target recipient node ID (`*` denotes flood broadcast)
- **`type`:** `CHAT`, `HANDSHAKE`, `ROUTED_CHAT`, `ACK`, `BROADCAST`, `SOS`, `HEARTBEAT`
- **`hopCount`:** Relays taken so far (incremented on each forward)
- **`maxHops`:** Maximum allowed TTL (clamped to `<= 32`)
- **`priority`:** QoS level (0=Standard, 10=Emergency SOS / ACK)
- **`content`:** Base64 encrypted payload or plaintext metadata
- **`routeHistory`:** List of relaying node IDs (clamped to `<= 32`)

**Frame Size Invariant:** Maximum frame size is strictly capped at `65,536` bytes (64 KB). Packets exceeding this size are dropped immediately before allocation.

---

## 3. Reliability & Lifecycle Management

### A. Stale Peer Eviction & Route Invalidation
Peers that disappear without sending a graceful disconnect callback are detected via:
1. `checkStalePeers(now, timeoutMs = 90_000L)`: Nodes without activity for >90s are transitioned to `DISCONNECTED`.
2. Immediate teardown on transport write failure: If `sendPayload` fails, `disconnect(endpointId)` is invoked, clearing all active routes associated with that peer.
3. Pruning expired entries: Dead connection records older than 300s are evicted from state memory.

### B. Store-and-Forward Persistence
- Outgoing messages and SOS beacons are persisted to the Room database (`messages` table) before transmission.
- Undelivered packets are enqueued in `pendingMessageRepository`. When a peer reconnects, pending packets are drained in priority order.

### C. SOS Acknowledgement & UI Status
- When a node receives an incoming `PacketType.SOS` packet from another peer, it emits a `PacketType.ACK` with priority 10 containing the original SOS UUID.
- The sender updates the local message status to `2` (DELIVERED/ACKNOWLEDGED) and transitions `SosViewModel.deliveryStatus` to `PEER_ACKNOWLEDGED`, providing visual confirmation to the user.
