# Nexus Support — Viva Examination Q&A Guide

This guide prepares project presenters for 25 technical examination questions covering architecture, networking, cryptography, concurrency, and reliability. All answers cite real files and architectural patterns from this repository.

---

### Q1: Why did you choose Google Nearby Connections over raw Wi-Fi Direct or raw BLE?
**Answer**:  
Raw Wi-Fi Direct (`WifiP2pManager`) on Android exhibits extreme OEM fragmentation—Samsung, Xiaomi, and Google Pixel devices have documented bugs in P2P group owner negotiation, channel selection, and background socket stability. Google Nearby Connections (`core/mesh/.../NearbyConnectionsTransport.kt`) abstracts this by using low-power BLE for discovery and dynamically negotiating Wi-Fi Direct or local Wi-Fi hotspot channels when bandwidth is required, providing reliable multi-vendor interoperability.

---

### Q2: How does SOS priority preemption work in the mesh router?
**Answer**:  
In `core/mesh/src/main/java/com/meshlink/app/mesh/routing/MeshRouter.kt`, emergency packets are assigned a priority value of 10 (`SOS_EMERGENCY`), while telemetry and chat have lower priorities (1–5). The router maintains a bounded priority queue: when network bandwidth is constrained or the queue is saturated, incoming SOS packets jump to the head of the line, and low-priority non-emergency packets are evicted.

---

### Q3: What happens if an Android gateway device completely loses cellular and internet connectivity?
**Answer**:  
The system relies on the **Transactional Outbox Pattern** (`core/data/.../OutboxEntity.kt`). When distress beacons or responder status updates arrive over the mesh, they are written to the encrypted Room database in the `outbox` table. `GatewaySyncWorker.kt` (managed by Android `WorkManager`) polls this table with bounded exponential backoff (2s to 30s) and automatic network constraint listeners, guaranteeing at-least-once delivery once connectivity returns.

---

### Q4: How do you prevent routing loops and broadcast packet storms in the ad-hoc mesh?
**Answer**:  
In `MeshRouter.kt`, each packet contains a unique UUID and a Time-To-Live (TTL) hop count. Nodes maintain an in-memory deduplication cache of recently processed packet IDs. If an incoming packet's UUID already exists in the cache, it is immediately discarded. If the packet is new, its TTL is decremented by 1; if TTL reaches 0, forwarding halts.

---

### Q5: How is multi-tenant isolation enforced in the backend?
**Answer**:  
In `cloud/backend/main.py` and `dispatch_service.py`, all incident, assignment, responder, and user tables include an `organization_id` column. REST endpoints extract the caller's organization from their authenticated JWT token or gateway key registration and enforce tenant filtering on all SQLAlchemy queries. Cross-tenant access attempts return HTTP 403 Forbidden (`test_tenant_isolation.py`).

---

### Q6: What is the "Zero-Fabrication Charter", and how is it enforced?
**Answer**:  
Emergency teams must never be dispatched to fake or guessed locations. In `app/.../LocationTracker.kt`, coordinates are kept strictly `null` if a GPS fix is unavailable (no fallback to default values). On the tactical dashboard (`TacticalMap.jsx`), incidents with null, undefined, or explicit $(0,0)$ coordinates are quarantined into an "AWAITING GPS FIX" flyout drawer rather than rendered at $(0,0)$ in the Atlantic Ocean. Furthermore, when no responders are online, `DispatchPanel.jsx` renders an explicit empty state instead of fake demo assets.

---

### Q7: How does the offline AI assistant protect against prompt injection?
**Answer**:  
In `app/src/main/java/com/meshlink/app/ai/OfflineRagManager.kt`, on-device language models provide triage and first-aid instructions based on local emergency guides. User input and retrieved context documents are fenced within strict XML delimiters: `<emergency_corpus>` and `<user_query>`. The system prompt strictly prohibits the AI from executing shell commands, modifying database state, or altering SOS status, with an adversarial input scanner that rejects prompt injections deterministically.

---

### Q8: Why did you use SQLCipher for local storage instead of standard Android SQLite/Room?
**Answer**:  
If a first responder or civilian loses their phone or is captured in a disaster zone, standard unencrypted SQLite files allow anyone with physical or USB debugging access to extract SOS location history, responder callsigns, and network topologies. In `core/data/.../DatabaseModule.kt`, SQLCipher 4.9.0 provides 256-bit AES full-database encryption backed by passphrases derived from the hardware-backed Android Keystore.

---

### Q9: How does the backend validate assignment state transitions?
**Answer**:  
In `cloud/backend/app/services/dispatch_service.py`, `transition_assignment` validates transitions against a strict state machine (`ALLOWED_ASSIGNMENT_TRANSITIONS`):  
$\text{PENDING} \to \text{ACCEPTED} \to \text{EN\_ROUTE} \to \text{ON\_SCENE} \to \text{COMPLETED}$.  
Out-of-order transitions (e.g. `PENDING` directly to `COMPLETED`) immediately return HTTP 409 Conflict, non-existent assignments return HTTP 404, and cross-tenant attempts return HTTP 403.

---

### Q10: How are downlink assignments routed back to field responders?
**Answer**:  
When a dispatcher assigns a unit (`POST /api/v1/dispatch/assignments`), the backend stores the assignment and stages a downlink message in the downlink queue. When any online gateway polls `GET /api/v1/sync/downlinks`, the pending messages are retrieved, marked as delivered to gateway, and transmitted over the mesh network addressed to the target responder's device ID.

---

### Q11: What cryptographic libraries and algorithms are used?
**Answer**:  
We use Google Tink (`core/crypto/.../TinkCryptoManager.kt`). For symmetric payload encryption, we use AES-256-GCM (Authenticated Encryption with Associated Data). For asymmetric key exchange and public-key encryption, we use ECIES with HKDF-SHA256 and AES-128-GCM, avoiding legacy manual cipher construction risks like padding oracle attacks or nonce collisions.

---

### Q12: How does the system handle duplicate SOS distress beacons?
**Answer**:  
Deduplication occurs at two layers:
1. **Mesh Layer**: `MeshRouter.kt` maintains a rolling cache of recent packet UUIDs and drops duplicates before retransmission.
2. **Backend Layer**: In `cloud/backend/main.py:sync_sos`, the ingestion query uses idempotent SQL (`ON CONFLICT (id) DO UPDATE`), ensuring identical beacons sent via multiple gateways do not create duplicate incident records.

---

### Q13: How does the web command dashboard operate when completely disconnected from the internet?
**Answer**:  
The frontend (`TacticalMap.jsx`) reads an environment variable `VITE_LOCAL_TILE_URL` pointing to a local HTTP server serving pre-rendered OpenStreetMap XYZ tiles (`docs/OFFLINE_TILES_GUIDE.md`). The top HUD displays an indicator badge: `ONLINE TILES` when connected, `OFFLINE TILES` when using the local cache, or `NO MAP TILES` with a warning alert when disconnected without a local cache.

---

### Q14: How does the backend differentiate between gateway and dispatcher authentication?
**Answer**:  
- **Gateways** authenticate via a shared secret header `X-Gateway-Key` (`NetworkModule.kt` / `main.py`). In production environments, the server strictly rejects the default development key (`meshlink_gw_dev_secret`).
- **Dispatchers** authenticate via OAuth2 password grant receiving signed JWT Bearer tokens containing user ID, role (`dispatcher`), and `org_id` claims.

---

### Q15: What role does PostGIS play in tactical operations?
**Answer**:  
PostgreSQL with PostGIS stores geographic locations as `Geometry(Point, 4326)` spatial objects. When dispatchers search for available responders near an emergency, the query uses spatial functions like `ST_DWithin` and spatial R-Tree indexing (GIST) to locate units within a specified radius in sub-millisecond execution times.

---

### Q16: How are responder assignment notifications handled across modern Android versions?
**Answer**:  
In `app/.../AssignmentNotificationHelper.kt`:
- For Android 13+ (API level 33+), the helper explicitly checks for runtime permission `android.permission.POST_NOTIFICATIONS` before dispatching.
- It posts to a high-importance `NotificationChannel` with heads-up alerts and vibration, ensuring critical dispatches are noticed immediately.
- Validated via unit tests with Robolectric (`AssignmentNotificationHelperTest.kt`).

---

### Q17: Why did you cancel the Protobuf serialization migration (IMP-HV-01)?
**Answer**:  
Protobuf serialization was evaluated to reduce packet byte size for narrowband LoRa. However, the existing JSON wire format was already integrated across Tink crypto envelopes, Android domain models, and FastAPI schemas. Cancelling Protobuf for the M3.24 milestone eliminated high architectural risk and prevented breaking verified cryptographic and routing invariants prior to final project submission.

---

### Q18: How do you verify database migrations between app versions?
**Answer**:  
- **Android**: `AppDatabaseMigrationTest.kt` uses Room's `MigrationTestHelper` to create schemas at versions 1, 2, 3, 13 and migrate them sequentially to version 14, asserting column integrity under SQLCipher encryption.
- **Backend**: Alembic manages versioned migrations (`alembic/versions/`). `GET /health` dynamically inspects the database to confirm the migration head matches `6f1a8b92c431`.

---

### Q19: What is the purpose of the TTL (Time-To-Live) field in mesh packets?
**Answer**:  
Without a TTL limit, a broadcast packet in an ad-hoc mesh containing cycles could circulate indefinitely, consuming battery and channel bandwidth. Each node decrements TTL by 1 before relaying; when TTL reaches 0, the packet is discarded.

---

### Q20: What happens if two dispatchers assign the same responder simultaneously?
**Answer**:  
In `dispatch_service.py`, assigning a responder performs a database check confirming the responder's status is `AVAILABLE` within a database transaction. The first commit succeeds and transitions the responder to `ASSIGNED`, while the second transaction fails validation and returns an error indicating the asset is no longer available.

---

### Q21: How are legacy SQLite databases migrated to PostgreSQL?
**Answer**:  
We built an automated CLI migration utility `scripts/import_sqlite_to_pg.py` (tested by `cloud/backend/test_import_sqlite_to_pg.py`). It reads legacy SQLite tables, validates foreign keys and organization IDs, converts coordinate pairs into PostGIS `Geometry(Point, 4326)` objects, and inserts them idempotently into PostgreSQL.

---

### Q22: What happens if an Android node runs out of memory or crashes during mesh forwarding?
**Answer**:  
Packets in transit that are intended for the gateway or local persistence are stored in the persistent SQLCipher database outbox. Android's `WorkManager` survives application process termination, device reboots, and low-memory kills, resuming synchronization as soon as resources permit.

---

### Q23: How are unmapped incidents displayed on the dashboard?
**Answer**:  
On `TacticalMap.jsx`, incidents lacking GPS coordinates are excluded from the Leaflet map layer to prevent plotting at $(0,0)$. Instead, a red warning badge indicates "AWAITING GPS FIX (N)". Clicking it opens a flyout drawer showing the incident text, sender node ID, and a dispatch button so coordinators can still act on the distress call.

---

### Q24: What are the primary security limitations of this working prototype?
**Answer**:  
1. Gateway nodes use pre-shared API keys rather than hardware-attested mutual TLS certificates.
2. Ad-hoc mesh peer discovery advertises device presence unencrypted at the physical radio layer.
3. Node public keys are exchanged during direct pairing without a centralized PKI or decentralized Web-of-Trust.

---

### Q25: How would you demonstrate the end-to-end incident lifecycle to an examiner?
**Answer**:  
Using our automated script `scripts/demo_flow.md` and integration test `test_pg_p33_demo_flow_integration.py`:
1. Check backend health: `GET /health` confirms `version: "1.0.0"` and `database_connected: True`.
2. Seed test organization and assets: `python scripts/seed_demo.py`.
3. Ingest SOS from mesh gateway: `POST /api/v1/sync/sos` with `X-Gateway-Key`.
4. Observe auto-created incident on Tactical Dashboard via WebSocket.
5. Dispatch responder unit: `POST /api/v1/dispatch/assignments`.
6. Gateway receives downlink: `GET /api/v1/sync/downlinks`.
7. Responder syncs status: `POST /api/v1/sync/assignments/{id}/status` through `ACCEPTED` $\to$ `EN_ROUTE` $\to$ `ON_SCENE` $\to$ `COMPLETED`, releasing the responder back to `AVAILABLE`.
