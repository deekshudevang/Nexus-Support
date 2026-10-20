# MeshLink Defect Register

This register documents technical debt, security and reliability defects, their status, and available evidence. Statuses distinguish automated-code verification from physical-device and production-deployment validation.

---

## Defect Summary Table

| Defect ID | Severity | Category | Description | Status | Resolution / Evidence |
|---|---|---|---|---|---|
| **DEF-001** | High | Repo Hygiene | Tracked debug dump, obsolete inference implementation, untracked runtime database | **VERIFIED (historical audit)** | Cleanup recorded in commits `944f137`, `841870e`, `072b556`; recheck release candidate |
| **DEF-002** | High | Data Layer | Room migration schema asset directory was misconfigured | **VERIFIED (historical audit)** | Corrected path and schema restoration recorded in `cb54903` |
| **DEF-003** | High | Data Layer | Historical Room schema JSONs for versions 4 through 12 are unavailable | **BLOCKED** | Do not fabricate schemas; recover authentic versions or formally define supported legacy database policy and migration tests |
| **DEF-004** | High | Architecture | Settings toggles were ephemeral UI state | **VERIFIED (reported; retain CI coverage)** | DataStore-backed settings implementation recorded in `9e3db98` and `a06a187` |
| **DEF-005** | High | Transport | Meshtastic transport integration is not validated against a physical node | **IMPLEMENTED-PENDING-DEVICE** | Unified transport routing is recorded; complete real BLE pairing, packet TX/RX and RF acknowledgement checks |
| **DEF-006** | Medium | Navigation | String routes were fragile | **VERIFIED (historical audit)** | Typed Navigation Compose routes recorded in `a805294` |
| **DEF-007** | Critical | Security | Encrypted database and key-storage modernization | **IMPLEMENTED; release/device verification required** | SQLCipher and Tink implementation recorded in `168f82e` and `6bf25f1`; test upgrade of existing encrypted user data on target devices |
| **DEF-008** | High | Security / Compliance | Android 16 KB page-size compatibility of APK/native libraries | **NOT VERIFIED** | Earlier evidence cited `zipalign -c -v 4`, which checks standard 4-byte alignment and does not prove native ELF 16 KB segment alignment. Run `zipalign -c -P 16 -v 4 <apk>` and inspect all bundled native ELF PT_LOAD segment alignments with current Android tooling; record commands, outputs and artifact SHA-256. |
| **DEF-009** | Critical | Cloud Backend | Backend authentication, CORS, WebSocket authorization and database concurrency | **PASSING-CI; PRODUCTION TLS UNEXECUTED** | Security code/tests exist; full-stack CI passed on SHA `86ef3e23938d35e7c5210792ffcab58734148727`. Production WSS still requires deployment validation. |
| **DEF-010** | High | Dashboard | Dashboard configuration, authentication UX and build hygiene | **PASSING-CI** | Frontend tests/build pass in CI run [39304382472](https://github.com/deekshudevang/Nexus-Support/actions/runs/39304382472); repeat bundle secret scan for release artifact |
| **DEF-011** | High | CI / CD | Full-stack automated pipeline | **PASSING-CI** | CI run [39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075) completed successfully on SHA `86ef3e23938d35e7c5210792ffcab58734148727`; update this evidence per candidate SHA |
| **DEF-012** | Medium | Quality / Privacy | Privacy-gated diagnostic reporting | **PASSING-CI / MANUAL PRIVACY REVIEW PENDING** | Default-off/PII-redaction implementation is reported; verify opt-in behavior and redaction in release-candidate review |

---

## Required unresolved work

### DEF-003 — Historical Room migration safety
Schema JSONs 4–12 are unavailable in the audited branch. A green CI run does not prove all old database migration paths work. Never create plausible-looking JSONs to make the matrix appear complete. Recover authentic schema exports and migration logic, or explicitly document which old database versions are unsupported and implement a safe, user-visible upgrade/recovery policy that preserves data wherever possible. Keep release destructive fallback disabled.

### DEF-005 — Physical Meshtastic validation
Automated tests support transport logic but do not prove RF delivery. Pair with a supported physical Meshtastic node; test connection, FromNum notification, FromRadio parsing, ToRadio packet writes, acknowledgement/receive behavior, disconnect/reconnect and relevant band/device configuration. Save dated logs without personal identifiers or secret payloads.

### DEF-008 — 16 KB memory-page compatibility
The previous `zipalign -c -v 4` command is insufficient evidence for 16 KB compatibility. For the exact release APK, run current Android Build Tools using `zipalign -c -P 16 -v 4 <apk>`, then inspect every bundled native library's ELF PT_LOAD segment alignment with Android's current ELF alignment tooling. Preserve command output and artifact SHA-256. Where possible, install and execute on a 16 KB page-size Android 15+ emulator/device. Until then, status remains NOT VERIFIED.

### DEF-009 — Production TLS
Successful code/tests do not prove production TLS. Deploy behind a configured TLS terminator and verify HTTPS/WSS certificate validation, allowed origins, authentication, ticket expiry, reconnect and failure behavior using the actual deployment URLs. Do not store private keys or credentials in documentation.

### DEF-011 — CI evidence provenance
Link the latest passing workflow run and include the exact commit SHA. Do not mark a test count as a verified total unless XML results from the same commit were aggregated, including ignored/skipped tests. Update status on every new release candidate.

## Evidence references

- Full-stack CI: [run 39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075), SHA `aaec744ead6872dcd2686759f6d8dffbe708f707`.
- Meshtastic UUID implementation: [MeshtasticTransport.kt](../../core/mesh/src/main/java/com/meshlink/app/mesh/transport/MeshtasticTransport.kt).
- Frozen acceptance inventory: [ACCEPTANCE_CRITERIA.md](../ACCEPTANCE_CRITERIA.md).
