# IMPLEMENTATION_PLAN.md

## Autonomous Implementation Plan: Nexus-Support 9.5/10 Quality Target

### Phase Roadmap & Execution Strategy

1. **Phase 0: Establish the Baseline**
   - Record environment, existing 346 passing unit tests, and defect inventory.
   - Synchronize `BASELINE_AUDIT.md`, `IMPLEMENTATION_PLAN.md`, `DEFECT_REGISTER.md`, and `task.md`.

2. **Phase 1: Eliminate Critical SOS Message-ID Collisions**
   - Verify complete chain: activation → nonce generation → 19-byte v1 beacon encoding → CRC-16 integrity → BLE advertisement & scanning → decoding → deduplication.
   - Add boundary regression tests for rollover, timezone/midnight shifts, and restart persistence.

3. **Phase 2: Fix Stale Peers and Mesh Connection Lifecycles**
   - Address Defect E and Defect J in `NearbyRepositoryImpl.kt`.
   - Implement peer liveness tracking (`lastSeenTimestamp` per endpoint/deviceId).
   - Implement bounded heartbeat timeout (e.g., 90s expiration for missed heartbeats).
   - Enforce clean cleanup on send failure and disconnect: clear handshake session, prune routing entries, clean up dead links, and trim `DISCONNECTED` map entries.
   - Add unit and regression tests in `NearbyRepositoryImplTest` covering peer drop without disconnect callback.

4. **Phase 3: Implement Reliable SOS Delivery and Acknowledgements**
   - Address Defect H in `MeshRouter.kt` and `NearbyRepositoryImpl.kt`.
   - Introduce peer-level and destination-level SOS acknowledgement mechanism (`PacketType.SOS_ACK` or prioritized peer receipt ACK).
   - Track delivery states in local persistence: `CREATED`, `QUEUED`, `TRANSMITTED`, `PEER_ACKNOWLEDGED`, `DELIVERED`.
   - Add bounded retry queue with exponential backoff and jitter for SOS packets.
   - Prevent duplicate alert creation upon retransmission.
   - Expose delivery state to UI while maintaining transparency (distinguish peer-received vs final recipient delivery).

5. **Phase 4: Harden Offline AI Implementation and Safety**
   - Reinforce prompt injection defense and output safety checks.
   - Post-generation validation: ensure model outputs never claim false physical rescue actions or fabricated SOS delivery confirmations.
   - Enforce bounded timeouts and graceful cancellation under low memory or SOS activation preemption.
   - Add regression tests covering adversarial prompt injection and output filtering.
   - Create `docs/ai/AI_SAFETY_AND_LIMITATIONS.md`.

6. **Phase 5: Strengthen Security Engineering**
   - Create `docs/security/THREAT_MODEL.md`.
   - Validate packet framing and size limits to prevent out-of-memory denial of service.
   - Replay protection and cryptographic nonce invariants verification.
   - Verify absence of sensitive data logging (private keys, plaintext messages, precise GPS coordinates).

7. **Phase 6: Build a Real Automated Testing and CI Pipeline**
   - Optimize GitHub Actions workflow (`.github/workflows/android.yml`).
   - Ensure clean compilation, unit testing, and release artifact assembly without hardcoded host paths.
   - Document testing commands in `docs/engineering/TESTING_GUIDE.md`.

8. **Phase 7: Verify Offline-First and Multi-Hop Mesh Behavior**
   - Update `docs/engineering/MESH_PROTOCOL.md` with multi-hop flooding, split-horizon, Dijkstra routing, and SOS ACK protocols.
   - Execute and verify deterministic multi-node simulation tests.

9. **Phase 8: Production Release Readiness**
   - Audit release build types, ProGuard/R8 shrinking rules, and signing config.
   - Verify release APK generation via `./gradlew :app:assembleRelease`.
   - Create `docs/engineering/RELEASE_GUIDE.md`.

10. **Phase 9: Documentation and Maintainability**
    - Audit all engineering and protocol documentation.
    - Synchronize `DEFECT_REGISTER.md` and `task.md`.

11. **Phase 10: Regression, Adversarial, and End-to-End Audit**
    - Run complete suite: unit tests, lint, release build, and device smoke verification.
    - Produce final evidence scorecard.
