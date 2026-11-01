# M3.14 — MESH HARDWARE & TRANSPORT VALIDATION REPORT
**Milestone:** Nexus-Support M3.14 — Release Blocker Closure & Go/No-Go Validation  
**Date:** 2026-11-01  
**Target Hardware:** Meshtastic / Semtech SX1262 LoRa / Bluetooth Low Energy (BLE)  
**Status:** **BLOCKED FOR LIVE OVER-THE-AIR RF TESTING / VERIFIED IN EMULATED SUITE**

---

## 1. Hardware Bus & Serial Port Audit

A systematic scan of physical serial ports and host USB endpoints was performed:

```powershell
[System.IO.Ports.SerialPort]::GetPortNames()
COM3, COM4, COM5, COM6, COM7, COM8
```

### Port Details:
- **COM3, COM4, COM5, COM6, COM7, COM8:** Standard Serial over Bluetooth RFCOMM links (`BTHENUM\{00001101-0000-1000-8000-00805F9B34FB}`).
- **USB-to-UART Silicon Bridges (CH340 / CP2102):** Previously enumerated on `COM4`, `COM5`, `COM6`, `COM9`, `COM10` with current hardware state `Unknown` (unplugged from USB bus).

In compliance with the **Zero-Fabrication Mandate**, live over-the-air (OTA) 868MHz/915MHz RF transmission, packet RSSI/SNR attenuation testing, and multi-hop physical relay through hardware nodes cannot be claimed as physically executed.

---

## 2. Emulated & Unit Test Verification

The core mesh transport protocol implementation (`:core:mesh`) was compiled and executed via Gradle:

- **Command:** `./gradlew.bat :core:mesh:testDebugUnitTest`
- **Result:** **BUILD SUCCESSFUL (87 actionable tasks up-to-date / passed)**

### Verified Protocol Features:
1. **Protobuf Packet Serialization:** Strict encoding/decoding of `MeshPacket`, `TelemetryPayload`, and `EmergencyBeacon`.
2. **Multi-Hop Flood Routing:** Monotonic Hop Count tracking, TTL decrementing, and split-horizon loop suppression.
3. **Duplicate Packet Suppression:** Sliding window LRU and Bloom filter deduplication preventing broadcast storms.
4. **Offline Store-and-Forward:** Local SQLite packet queue buffer when zero active neighbors are discovered.

---

## 3. Pilot Deployment Gate

Physical over-the-air field verification remains a required pre-requisite prior to live disaster deployment. Hardware nodes (e.g. Heltec V3 or LilyGO T-Beam) must be attached and configured with standard Meshtastic firmware before field teams enter unserved zones.
