# Field Verification Checklist (Physical Phone & Multi-Node Mesh)

Use this checklist when validating on additional physical Android devices, restricted corporate Wi-Fi networks, or LoRa RF hardware outside automated CI.

## 1. Host LAN & Firewall Readiness
- [ ] Run `docker compose up -d --build` from repository root and confirm `GET http://127.0.0.1:8000/health/ready` returns `{"status":"READY","database":"OK","database_engine":"postgresql"}`.
- [ ] If phones on the same Wi-Fi/hotspot cannot reach `http://<HOST_LAN_IP>:8000/health` (connection timeout), open TCP port `8000` on the Private Windows Firewall profile from an **Administrator PowerShell**:
  ```powershell
  netsh advfirewall firewall add rule name="MeshLink Backend 8000" dir=in action=allow protocol=TCP localport=8000 profile=private
  ```
- [ ] For USB-only debugging without LAN inbound rules, run `adb reverse tcp:8000 tcp:8000` and configure `http://127.0.0.1:8000/` in **App Settings -> Cloud Gateway Connectivity**.

## 2. Single-Device Gateway Verification
- [ ] Install debug build (`./gradlew :app:installDebug`) or signed HTTPS release build (`-PMESHLINK_PROD_URL=https://... -PMESHLINK_GATEWAY_KEY=...`).
- [ ] Open **Settings -> Cloud Gateway Connectivity**, enter Gateway URL + key, tap **Test Connection** -> expect `Connected (...) • DB OK`.
- [ ] Trigger **Hold SOS**, confirm `Pending SOS uploads` drops to `0` within seconds and incident appears live on the Web Command Center map/queue.
- [ ] Enable Airplane Mode, trigger SOS (`Pending SOS uploads: 1`), restore Wi-Fi/cellular, confirm automatic flush with zero duplicate UUID rows in PostgreSQL.

## 3. Multi-Device Peer Mesh & Hardware Radio Tests (Requires 2+ Phones / Radios)
- [ ] Put **Phone A** (victim) in Airplane Mode with Bluetooth/Location enabled; keep **Phone B** (gateway) online on Wi-Fi/LTE.
- [ ] Trigger **Hold SOS** on Phone A -> verify Phone B receives packet over Google Nearby Connections (BLE/Wi-Fi Direct) with `hopCount = 1`.
- [ ] Verify Phone B's `GatewaySyncWorker` uploads Phone A's packet (`cloudSynced = true`) while Phone A remains offline.
- [ ] If using Meshtastic hardware: pair BLE LoRa radio in **App Settings -> LoRa Mesh (Meshtastic)** and repeat hop range test.
