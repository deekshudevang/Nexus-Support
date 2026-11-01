# Nexus Support (RescueMesh) — Opportunistic Mesh-Gateway Sync Protocol

**Protocol Version:** 1.0  
**Status:** Approved  
**Author:** Distributed Systems & Mesh Networking Team  

---

## 1. Synchronization Architecture

In disaster zones without infrastructure, mesh devices buffer emergency telemetry locally in SQLite / SQLCipher databases. When any mobile mesh node establishes opportunistic internet connectivity (cellular recovery, satellite link, or Wi-Fi hotspot at incident command base), it acts as an **Edge Gateway Node**.

```
[ Field Node A ] (BLE / Nearby)
       │
       ▼
[ Field Node B ] (Meshtastic LoRa)
       │
       ▼
[ Gateway Node C ] (Field Tablet / Vehicle / Mobile Phone)
       │  (HTTPS / X-Gateway-Key)
       ▼
[ Operations Cloud Backend ] ──(WebSocket)──► [ Tactical Command Dashboard ]
```

---

## 2. Sync Execution Flow & Reconciliation

1. **Local Queue Query**:
   The `GatewaySyncWorker` queries the local `SosPacketDao` for records where `status NOT IN ('DELIVERED', 'ACKNOWLEDGED')`.
2. **Payload Serialization**:
   Packets are marshaled into the canonical JSON envelope, preserving truthful coordinate status (`gpsFixAvailable`).
3. **Authenticated Transmission**:
   Worker initiates an HTTPS POST to `/api/v1/sync/sos` carrying the `X-Gateway-Key` credential.
4. **Idempotent Ingestion**:
   Backend ingests records using `INSERT OR REPLACE` with conflict resolution based on timestamp and packet UUID.
5. **Receipt Verification**:
   Backend responds with HTTP 200 containing per-UUID receipts:
   ```json
   {
     "status": "success",
     "synced_count": N,
     "receipts": [{ "uuid": "...", "status": "ACKNOWLEDGED", "synced": true }]
   }
   ```
6. **Local State Transition**:
   For each confirmed UUID in the receipt array, `GatewaySyncWorker` updates the local Room database to `ACKNOWLEDGED`.
7. **Backoff and Failure Handling**:
   If the network connection drops mid-flight or the backend returns 5xx, the worker terminates with `Result.retry()` and relies on WorkManager's exponential backoff policy (10s initial delay, capped at 30m).
   If HTTP 401/403 is received, authentication has failed and the worker terminates with `Result.failure()` to prevent continuous battery drain.
