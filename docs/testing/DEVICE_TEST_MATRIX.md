# Device Test Matrix

This matrix tracks the validation of mesh capabilities on physical hardware across different network scales. All simulation-based testing is explicitly labeled or marked as UNVALIDATED in the context of real-device behavior.

| Scenario | 2 Nodes | 3 Nodes | 10 Nodes (Simulated) | 20 Nodes (Simulated) |
|---|---|---|---|---|
| **Discovery (BLE / Wi-Fi Direct)** | VALIDATED (Field Test) | UNVALIDATED | [SIMULATOR] N/A | [SIMULATOR] N/A |
| **Direct Messaging** | VALIDATED (Field Test) | UNVALIDATED | [SIMULATOR] N/A | [SIMULATOR] N/A |
| **Multi-Hop Messaging** | UNVALIDATED | VALIDATED (Field Test) | [SIMULATOR] `LargeScaleMeshSimTest.broadcast travels across 20-node linear chain` | [SIMULATOR] `LargeScaleMeshSimTest.broadcast travels across 20-node linear chain` |
| **Message Deduplication** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.cache evicts oldest entry` | [SIMULATOR] `LargeScaleMeshSimTest.cache evicts oldest entry` |
| **TTL Expiry (Max Hops)** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.packet dies before crossing a 4-hop chain` | [SIMULATOR] `LargeScaleMeshSimTest.packet dies before crossing a 4-hop chain` |
| **Store & Forward (48h Queue)** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.packet is stored as pending when next hop disappears` | [SIMULATOR] `LargeScaleMeshSimTest.packet is stored as pending when next hop disappears` |
| **Network Partitions** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.packet is stored-and-forwarded across a partition` | [SIMULATOR] `LargeScaleMeshSimTest.packet is stored-and-forwarded across a partition` |
| **Partition Reconnect & Sync** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.routing table converges after 10 rapid join-leave events` | [SIMULATOR] `LargeScaleMeshSimTest.routing table converges after 10 rapid join-leave events` |
| **SOS Propagation** | UNVALIDATED | UNVALIDATED | [SIMULATOR] N/A | [SIMULATOR] N/A |
| **Packet Flooding Resilience** | UNVALIDATED | UNVALIDATED | [SIMULATOR] `LargeScaleMeshSimTest.broadcast in 20-node fully-connected graph` | [SIMULATOR] `LargeScaleMeshSimTest.broadcast in 20-node fully-connected graph` |
| **Battery Impact / Hour** | UNVALIDATED | UNVALIDATED | [SIMULATOR] N/A | [SIMULATOR] N/A |
| **Long Duration Run (>24h)** | UNVALIDATED | UNVALIDATED | [SIMULATOR] N/A | [SIMULATOR] N/A |

---
**Field Test Specifications:**
* **2-Node Tests:** Google Pixel 7 (Android 14) & Samsung Galaxy S23 (Android 14) via Wi-Fi Direct + BLE fallback. Distance: 30m line-of-sight.
* **3-Node Multi-Hop Tests:** Google Pixel 7 (Node A), Samsung Galaxy S23 (Node B/Relay), and OnePlus 9 (Node C). Configured in A-B-C string topology (A and C separated by ~60m, out of direct range). ECIES encrypted payload successfully routed via Node B without interception capability.

---
**Last verified:** 2026-09-20 @ 4f6e61c5de8873ad6fa64f6398909c9347528b19
