# Phase Mapping: Workstreams A-M to Phases 0-23

## Workstream A: Initialization & Baseline
- **Phase 0**: BASELINE AND RESTART VERIFICATION
  - **Acceptance Criteria**: Repository inspected. Baseline assessment recorded in `docs/engineering/BASELINE_AUDIT.md`.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream B: Core Foundation & Architecture
- **Phase 1**: GRADLE, JAVA AND DEPENDENCY REPAIR
  - **Acceptance Criteria**: Investigate missing ktx references and gradle daemon crashes.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 2**: ARCHITECTURE, DOMAIN MODELS AND DATABASE
  - **Acceptance Criteria**: Inspect models, verify Hilt bindings, Room entities, test migrations.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream C: Connectivity & Mesh Routing
- **Phase 3**: BLE AND PEER DISCOVERY
  - **Acceptance Criteria**: Peer discovery, adaptive scanning, tie-breaker logic, SOS passive scanning.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 4**: MESH ROUTING
  - **Acceptance Criteria**: Multi-hop routing using Dijkstra's algorithm, tracking battery metrics for routes, robust store-and-forward architecture.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream D: Offline Data & Queue Management
- **Phase 5**: OFFLINE QUEUES AND MESSAGE DELIVERY
  - **Acceptance Criteria**: Pending message database, periodic cleanup of expired messages, automatic queue flush upon peer connection.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream E: Cryptography & Identity
- **Phase 6**: CRYPTOGRAPHY AND IDENTITY
  - **Acceptance Criteria**: ECDH/HKDF handshake logic, ECIES end-to-end encryption, Session keys not persisted to disk.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream F: Emergency Protocols
- **Phase 7**: SOS BEACON PROTOCOL
  - **Acceptance Criteria**: Implement SosBeaconCodec for BLE advertising.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 8**: COMPLETE SOS WORKFLOW
  - **Acceptance Criteria**: Implement sos beacon advertising in NearbyRepository.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream G: AI Integrations
- **Phase 9**: REAL AI IMPLEMENTATION
  - **Acceptance Criteria**: MediaPipe GenAI library integrated for RAG responses, without canned fallbacks masking real inference.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream H: Remote Sync & API
- **Phase 10**: BACKEND AND API INTEGRATION
  - **Acceptance Criteria**: Retrofit integrated, CloudSyncWorker functional with a production-ready endpoint.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream I: App Navigation & State
- **Phase 11**: APP STATE AND NAVIGATION
  - **Acceptance Criteria**: NavHost covers all screens, state successfully hoisted to MainActivity.
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream J: Optimization, Security & CI
- **Phase 12**: PERFORMANCE
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 13**: SECURITY AND FAILURE TESTING
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 14**: CI AND PORTABLE BUILDS
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream K: Verification & Audits
- **Phase 15**: PHYSICAL DEVICE VERIFICATION
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 16**: ENGINEERING AUDIT
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 17**: FULL UX AUDIT
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream L: UI/UX Redesign & Implementation
- **Phase 18**: NEXUS DESIGN SYSTEM
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 19**: COMPLETE UI/UX IMPLEMENTATION
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 20**: INTERACTION AND ACCESSIBILITY
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)

## Workstream M: Final QA & Release Readiness
- **Phase 21**: UI QA
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 22**: CROSS-PROJECT REGRESSION
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)
- **Phase 23**: FINAL RELEASE READINESS
  - **Acceptance Criteria**: None
  - **Required Evidence Level**: L3 (Automated tests passing / Log output)


## Test classification (L2 only)
- SIMULATED (deterministic simulators/fault injection): LargeScaleMeshSimTest, DeterministicMeshSimulator
- L2 unit tests with mocked dependencies (not physical evidence): MeshRouterTest, RoutingTableTest, SeenMessageCacheTest, NearbyRepositoryImplEndpointLostTest, AdaptiveScanControllerTest, SosBeaconCodecTest, ThreatModelValidationTest (isolated crypto/cache logic only, not the real router)

