# MeshLink Defect Register

This register documents technical debt, security and reliability defects, their status, and available evidence. Statuses distinguish automated-code verification from physical-device and production-deployment validation.

---

## Defect Summary Table

| Defect ID | Severity | Category | Description | Status | Resolution / Evidence |
|---|---|---|---|---|---|
| **DEF-001** | High | Repo Hygiene | Tracked debug dump, obsolete inference implementation, untracked runtime database | **VERIFIED (historical audit)** | Cleanup recorded in commits `944f137`, `841870e`, `072b556`; recheck release candidate |
| **DEF-002** | High | Data Layer | Room migration schema asset directory was misconfigured | **VERIFIED (historical audit)** | Corrected path and schema restoration recorded in `cb54903` |
| **DEF-003** | High | Data Layer | Historical Room schema JSONs for versions 4 through 12 are unavailable | **RESOLVED (Documented Policy & Tests)** | Recovered authentic schema state; documented legacy DB policy in `docs/architecture/DATABASE_UPGRADE_POLICY.md`; added sequential migration tests (v1->v2, v2->v3, v3->v13, v13->v14) and fail-closed release corruption recovery tests in `MeshDatabaseMigrationTest` and `DatabaseCorruptionRecoveryTest` (commit `eb9d03c`). |
| **DEF-004** | High | Architecture | Settings toggles were ephemeral UI state | **VERIFIED (reported; retain CI coverage)** | DataStore-backed settings implementation recorded in `9e3db98` and `a06a187` |
| **DEF-005** | High | Transport | Meshtastic transport integration is not validated against a physical node | **IMPLEMENTED-PENDING-DEVICE** | Unified transport routing is recorded; complete real BLE pairing, packet TX/RX and RF acknowledgement checks |
| **DEF-006** | Medium | Navigation | String routes were fragile | **VERIFIED (historical audit)** | Typed Navigation Compose routes recorded in `a805294` |
| **DEF-007** | Critical | Security | Encrypted database and key-storage modernization | **IMPLEMENTED; release/device verification required** | SQLCipher and Tink implementation recorded in `168f82e` and `6bf25f1`; test upgrade of existing encrypted user data on target devices |
| **DEF-008** | High | Security / Compliance | Android 16 KB page-size compatibility of APK/native libraries | **RESOLVED (Zip & ELF 16KB Verified; Pending Physical Device)** | `zipalign -c -P 16 -v 4` passed. Upgraded `mlkit:image-labeling:17.0.9` (commit `87f2d1d`). Native ELF inspection with `scripts/inspect_elf_alignment.py` confirmed ALL 64-bit `.so` libraries across `arm64-v8a` and `x86_64` have 16 KB `PT_LOAD` segment alignment (`p_align = 16384`), including `libmlkitcommonpipeline.so`. Physical 16 KB device execution remains `IMPLEMENTED-PENDING-DEVICE`. |
| **DEF-009** | Critical | Cloud Backend | Backend authentication, CORS, WebSocket authorization and database concurrency | **PASSING-CI; PRODUCTION TLS UNEXECUTED** | Security code/tests exist; full-stack CI passed on SHA `86ef3e23938d35e7c5210792ffcab58734148727`. Production WSS still requires deployment validation. |
| **DEF-010** | High | Dashboard | Dashboard configuration, authentication UX and build hygiene | **PASSING-CI** | Frontend tests/build pass in CI run [39304382472](https://github.com/deekshudevang/Nexus-Support/actions/runs/39304382472); zero secrets detected in built `dist/` bundle. |
| **DEF-011** | High | CI / CD | Full-stack automated pipeline | **PASSING-CI** | CI run [39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075) completed successfully on SHA `86ef3e23938d35e7c5210792ffcab58734148727`; update this evidence per candidate SHA |
| **DEF-012** | Medium | Quality / Privacy | Privacy-gated diagnostic reporting | **PASSING-CI / MANUAL PRIVACY REVIEW PENDING** | Default-off/PII-redaction implementation is reported; verify opt-in behavior and redaction in release-candidate review |
| **DEF-013** | Critical / P1 | Safety / Reliability | Fabricated satellite lock, missing durable queue and retry backoff | **RESOLVED** | Fixed in commits `a781648` and `a2da5ef`: eliminated fake satellite lock, fake SNR, and fake orbital countdown; connected truthful `gpsFixAvailable` via `LocationTracker` and real transports; implemented durable 50-item Room queue, bounded exponential backoff (2s..30s), process recreation recovery, and peer ACK resolution. |
| **DEF-014** | High / P1 | UX / Truthfulness | Fabricated RF distance in DiscoveryScreen | **RESOLVED** | Fixed in commit `757c538`: replaced hash-based distance estimation with truthful connection state badges (`AVAILABLE`, `CONNECTING`, `CONNECTED`) and `Nearby Direct Link (BLE / Wi-Fi)`. |
| **DEF-015** | Medium / P2 | Architecture / Hygiene | Dead simulator service in production source set | **RESOLVED** | Fixed in commit `897297e`: removed unreferenced `MockMeshBackendService.kt` from `core/data/src/main/` production source set. |
| **DEF-016** | Medium / P2 | QA / Test Coverage | Missing unit tests for secondary UI ViewModels | **RESOLVED** | Fixed in commit `7e8b65f`: added comprehensive unit test suites for `DiscoveryViewModel`, `ChatViewModel`, `DiagnosticsViewModel`, `EmergencyGuideViewModel`, and `BroadcastViewModel`. |
| **DEF-017** | High / P1 | SOS Reliability | Concurrency race conditions, unhandled job cancellations, and duplicate ACK resolution | **RESOLVED** | Fixed in commit `bb20d89`: added `broadcastJob` lifecycle tracking, cancellation guards before enqueuing, and duplicate ACK deduplication in `SosViewModel`; verified in `SosViewModelTest`. |
| **DEF-018** | High / P1 | Mesh & AI Safety | Missing NearbyRepository disconnection/tie-breaker tests, TTL expiration tests, adversarial AI prompt injection tests | **RESOLVED** | Fixed in commit `9f5a351`: populated `NearbyRepositoryImplTest` (disconnection cleanup, tie-breaker logic, stale peer cleanup), added TTL expiration tests in `SeenMessageCacheTest`, and expanded adversarial prompt injection & hallucination tests in `AiSafetyGuardTest`. |

---

## Required unresolved work

### DEF-003 — Historical Room migration safety
Documented in `docs/architecture/DATABASE_UPGRADE_POLICY.md`. Authentic schema recovery showed versions 4–12 were never distinct release schemas in git history. Supported sequential migrations (v1->v2, v2->v3, v3->v13, v13->v14) have been tested without data loss. For damaged or unrecoverable databases, release builds enforce a strict fail-closed recovery policy without unmonitored silent data loss.

### DEF-005 — Physical Meshtastic validation
Automated tests support transport logic but do not prove RF delivery. Pair with a supported physical Meshtastic node; test connection, FromNum notification, FromRadio parsing, ToRadio packet writes, acknowledgement/receive behavior, disconnect/reconnect and relevant band/device configuration. Save dated logs without personal identifiers or secret payloads.

### DEF-008 — 16 KB memory-page compatibility
`zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk` was executed using Android SDK Build-Tools 36.0.0 and reported `Verification successful`, confirming 16 KB zip alignment. In commit `87f2d1d`, `com.google.mlkit:image-labeling` was upgraded to `17.0.9` (pulling `vision-internal-vkp:19.0.1`). Deep ELF segment inspection with `scripts/inspect_elf_alignment.py` confirmed that ALL 64-bit shared libraries in `arm64-v8a` and `x86_64` (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libllm_inference_engine_jni.so`, `libmlkitcommonpipeline.so`, `libsqlcipher.so`) satisfy `p_align = 16384` (16 KB). Physical runtime execution on a physical 16 KB page-size Android 15+ device or emulator remains `IMPLEMENTED-PENDING-DEVICE`.

### DEF-009 — Production TLS
Successful code/tests do not prove production TLS. Deploy behind a configured TLS terminator and verify HTTPS/WSS certificate validation, allowed origins, authentication, ticket expiry, reconnect and failure behavior using the actual deployment URLs. Do not store private keys or credentials in documentation.

### DEF-011 — CI evidence provenance
Link the latest passing workflow run and include the exact commit SHA. Do not mark a test count as a verified total unless XML results from the same commit were aggregated, including ignored/skipped tests. Update status on every new release candidate.

## Evidence references

- Full-stack CI: [run 39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075), SHA `aaec744ead6872dcd2686759f6d8dffbe708f707`.
- Meshtastic UUID implementation: [MeshtasticTransport.kt](../../core/mesh/src/main/java/com/meshlink/app/mesh/transport/MeshtasticTransport.kt).
- Frozen acceptance inventory: [ACCEPTANCE_CRITERIA.md](../ACCEPTANCE_CRITERIA.md).
