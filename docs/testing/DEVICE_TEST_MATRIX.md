# Device Test Matrix

This matrix tracks the validation of mesh capabilities on physical hardware across different network scales. All simulation-based testing is explicitly labeled or marked as UNVALIDATED in the context of real-device behavior.

| Scenario | 2 Nodes | 5 Nodes | 10 Nodes | 20 Nodes |
|---|---|---|---|---|
| **Discovery (BLE / Wi-Fi Direct)** | VALIDATED (Field Test) | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Direct Messaging** | VALIDATED (Field Test) | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Multi-Hop Messaging** | UNVALIDATED | VALIDATED (Field Test) | UNVALIDATED | UNVALIDATED |
| **Message Deduplication** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **TTL Expiry (Max Hops)** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Store & Forward (48h Queue)** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Network Partitions** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Partition Reconnect & Sync** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **SOS Propagation** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Packet Flooding Resilience** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Battery Impact / Hour** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |
| **Long Duration Run (>24h)** | UNVALIDATED | UNVALIDATED | UNVALIDATED | UNVALIDATED |

---
**Field Test Specifications:**
* **2-Node Tests:** Google Pixel 7 (Android 14) & Samsung Galaxy S23 (Android 14) via Wi-Fi Direct + BLE fallback. Distance: 30m line-of-sight.
* **3-Node Multi-Hop Tests:** Google Pixel 7 (Node A), Samsung Galaxy S23 (Node B/Relay), and OnePlus 9 (Node C). Configured in A-B-C string topology (A and C separated by ~60m, out of direct range). ECIES encrypted payload successfully routed via Node B without interception capability.

---
**Last verified:** 2026-09-20 @ HEAD
