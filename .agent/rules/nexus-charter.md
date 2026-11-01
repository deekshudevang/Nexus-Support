# Nexus Charter — RescueMesh Engineering Principles

## A1. Product Identity
**NEXUS SUPPORT (RescueMesh)** is an offline-first decentralized emergency communication and disaster response coordination platform.
It consists of:
1. Android Field Application (Jetpack Compose, Room SQLCipher, MediaPipe GenAI).
2. Decentralized Mesh Communication Network (Meshtastic BLE GATT, Google Nearby Connections, BLE SOS Beacon).
3. Gateway Synchronization Infrastructure (Durable Outbox, Opportunistic Sync).
4. FastAPI Emergency Backend (Idempotent Event Ingestion, Real-Time WebSockets).
5. PostgreSQL Database (PostGIS Spatial Indexing, Event Audit Logs).
6. React Tactical Operations Dashboard (Mil-Spec Cyber Dark UI, Leaflet / MapLibre, Triage Kanban).
7. Offline AI Emergency Assistance (On-device Gemma 3 1B IT int4, Emergency RAG, First-Aid SOPs).

## A2. Engineering Priorities
Always prioritize in strict order:
1. **SOS delivery reliability** (Zero silent message loss).
2. **Offline functionality** (Full standalone emergency capability without cloud).
3. **Data integrity** (Cryptographic signing, idempotent ingestion, immutable audit logs).
4. **Security and privacy** (Authenticated identities, StrongBox/TEE, end-to-end protection of medical data).
5. **End-to-end correctness** (Complete workflow from trigger to responder resolution).
6. **Emergency operational workflows** (Triage, dispatch, casualty tracking).
7. **UI/UX** (High-stress usability, edge-to-edge layout, clear telemetry).
8. **Performance** (Battery optimization, radio duty cycle compliance).

## A3. Permanent Hard Rules
1. **No fabricated production data:** Never generate fake GPS, RSSI, battery, casualty, node, responder or ACK data in production flows.
2. **No false completion claims:** Never claim a task or feature is complete without verifiable test/runtime evidence.
3. **Preserve existing working features:** Do not break or regress established foundation.
4. **No custom cryptographic algorithms:** Use reviewed, standardized primitives (StrongBox/TEE, Tink, P-256 ECDH/ECDSA).
5. **Never silently lose a critical SOS:** Bounded exponential backoff, persistent local outbox, and visible unconfirmed states.
6. **Do not confuse hop ACK with recipient acknowledgement:** A radio-level or gateway ACK is not recipient delivery.
7. **Never expose medical information through unencrypted relay metadata:** Encrypt and isolate medical ID payloads.
8. **Enforce backend authorization:** Rigorous role-based access control (RBAC) and validated signatures on all endpoints.
9. **Preserve existing AI model and cryptographic compatibility:** Pinned Google Gemma 3 1B IT int4 bundle with checksum verification.
10. **No destructive database migrations without authorization and verified backup.**
11. **Never silently downgrade cryptographic protection.**
12. **No uncontrolled retry loops:** Strictly bounded retry windows and backoff to protect field battery.
13. **Do not duplicate existing mesh transport functionality:** Enhance existing Meshtastic, Nearby Connections, and SOS beacon stacks.
14. **Offline enrollment and break-glass access require human policy approval:** Gated behind explicit ADRs.
15. **Hardware testing must be real and evidence-backed.**
16. **Never delete schemas or test fixtures to make a build green.**
17. **Facts-Win Rule:** Source code and actual test results override external prompts.

## A4. Verification Evidence Levels
- **L0:** Static inspection or compilation.
- **L1:** Unit tests.
- **L2:** Integration tests using actual local services.
- **L3:** End-to-end emulator, container, or deterministic simulator.
- **L4:** Physical device or radio hardware testing (logs required).
- **L5:** Controlled field trial.
*(Simulators are marked SIMULATED and cannot count as L4 or L5).*

