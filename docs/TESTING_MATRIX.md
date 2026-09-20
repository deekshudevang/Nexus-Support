# Mesh Network Testing Matrix

To validate Nexus Support as a production-grade disaster communication system, the following real-device testing matrix must be executed to verify resilience under realistic field conditions.

## Topology & Scale Validation

| Test ID | Scenario | Devices Needed | Expected Outcome |
|---|---|---|---|
| T-101 | **1-Hop Direct** | 2 | Devices discover each other within 3s. Messages deliver instantly (<500ms). |
| T-102 | **2-Hop Relay** | 3 | Device A sends to Device C via Device B. Latency <1s. Device B cannot decrypt the payload. |
| T-103 | **4-Hop Chain** | 5 | Packet traverses a 4-hop chain. Delivery confirmed. |
| T-104 | **7-Hop Max** | 8 | Packet traverses the maximum 7 hops. Packets beyond 7 hops are correctly dropped (TTL exhausted). |
| T-105 | **20-Node Dense Mesh** | 20 | Broadcast storm mitigated. `SeenMessageCache` prevents infinite looping. All nodes reach consensus. |

## Environmental & OS Constraints

| Test ID | State | Description | Expected Outcome |
|---|---|---|---|
| E-201 | **Screen OFF** | Devices locked, screen off. | Background BLE advertising/scanning continues (at lower duty cycle). |
| E-202 | **Battery Saver Mode** | Android battery saver enabled. | Connections persist, though discovery may be delayed due to OS throttling. |
| E-203 | **App Process Death** | App force-killed / OS reclaimed memory. | Store-and-forward DB maintains pending packets. Re-syncs upon next app launch. |
| E-204 | **Radio Interruption** | Wi-Fi/Bluetooth toggled mid-transmission. | Network partitions gracefully. Packets queued locally until link is re-established. |

## Network Partitions & Sync

| Test ID | Scenario | Expected Outcome |
|---|---|---|
| P-301 | **Partition & Reconnect** | Subnet A (3 nodes) disconnects from Subnet B (3 nodes). Both subnets exchange local messages. When reconnected, Vector Clock CRDTs resolve all states without data loss. |
| P-302 | **Stale Routes** | Node physically moves out of range. | Routing table updates within 10s. Subsequent packets use alternative paths. |
| P-303 | **GPS Sync under Churn** | Nodes continuously drop and reconnect while transmitting location telemetry. | Eventually consistent. No node displays coordinates older than the last confirmed sync. |
