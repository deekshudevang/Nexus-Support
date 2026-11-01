# Policy: Mesh Transport Capabilities & Hardware Boundaries

## 1. Supported Mesh Transports
The system acknowledges three distinct physical and logical transport layers:

1. **Google Nearby Connections (P2P Cluster):**
   - High bandwidth, short-to-medium range (BLE discovery + Wi-Fi Hotspot/Direct data upgrade).
   - Used for Android-to-Android rich messaging, audio notes, and bulk synchronization.
2. **Meshtastic BLE GATT (External Radio Hardware):**
   - Long-range (LoRa 868/915MHz) telemetry, text, and emergency beaconing via external Meshtastic transceivers.
   - Low bandwidth (< 256 bytes per packet), high resilience across kilometers.
3. **Dedicated BLE SOS Beacon (Autonomous Advertisement):**
   - Autonomous, connectionless BLE advertising packet for immediate survivor beaconing without pairing.
   - Decodable by any listening Nexus Support scanner node.

## 2. Prohibited Assumptions
- **Do not assume raw Wi-Fi Direct or raw BLE GATT mesh exists outside these tested implementations.** Any new transport requires an ADR proving necessity.
- **Do not assume radio ACK equals end-to-end delivery.** Radio ACK only confirms hop receipt by adjacent node.
- **Regional Compliance:** All LoRa transmissions must adhere to regional spectrum allocation (e.g., India WPC guidelines for 865-867 MHz license-exempt band, max 1W EIRP).

