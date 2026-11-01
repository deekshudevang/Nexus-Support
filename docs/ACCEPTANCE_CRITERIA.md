# Production Readiness Acceptance Criteria

**Scope:** `release/production-readiness`  
**Created:** 2026-10-19  
**Rule:** A status is only marked VERIFIED when a reproducible artifact, CI run, test output, or device/deployment record exists for the exact commit under review. Documentation-only updates do not imply code or device verification.

## Status definitions

- **VERIFIED** — reproducible evidence is linked and tied to a commit.
- **PASSING-CI** — GitHub Actions passed for the named SHA; this does not imply physical-device or production-deployment validation.
- **IMPLEMENTED-PENDING-DEVICE** — code and automated tests exist, but hardware validation is outstanding.
- **IMPLEMENTED-UNEXECUTED** — implementation exists, but the required deployment/run has not been performed.
- **BLOCKED** — a required input or artifact is missing.
- **NOT-STARTED** — work and evidence have not been provided.

## Frozen acceptance inventory

| ID | Area | Acceptance criterion | Current status | Evidence / next action |
|---|---|---|---|---|
| AR-01 | Repository hygiene | No debug dumps, stale dead implementation, or tracked runtime database artifacts | VERIFIED (prior audit) | Confirm again on release candidate; keep generated databases untracked |
| AR-02 | Android build | Debug build and Android lint pass on the release candidate | PASSING-CI | CI run [39304382472](https://github.com/deekshudevang/Nexus-Support/actions/runs/39304382472), SHA `aaec744ead6872dcd2686759f6d8dffbe708f707); re-run after code changes |
| AR-03 | Android unit tests | Test report lists total, passed, failed, skipped for the exact SHA | PARTIALLY VERIFIED | CI unit-test job passed; preserve and aggregate JUnit XMLs before claiming a cross-stack grand total |
| AR-04 | Room migrations | Every supported historical schema has a preserved source schema and migration test | BLOCKED | Schema versions 4–12 are absent. Recover from an authentic archive or explicitly define unsupported legacy versions and a user-safe upgrade policy; do not fabricate schema files |
| AR-05 | Database destructive fallback | Destructive migration fallback is impossible in release builds | VERIFIED (reported in audit) | Confirm with release variant test/configuration and keep this assertion covered |
| AR-06 | Settings persistence | DataStore state persists across process restart and drives scan controllers | VERIFIED (reported in audit) | Keep repository/ViewModel tests in CI |
| AR-07 | Meshtastic BLE transport | Official UUIDs, GATT lifecycle, send and receive behavior work with physical hardware | IMPLEMENTED-PENDING-DEVICE | Test physical Meshtastic node pairing, FromNum/FromRadio notifications, ToRadio writes, ACK/receive behavior and record device/firmware |
| AR-08 | Mesh multi-hop | Offline delivery and relay work across four or more physical devices beyond direct range | IMPLEMENTED-PENDING-DEVICE | Run field exercise; simulation is not physical RF evidence |
| AR-09 | Navigation | Typed routes and deep links resolve safely | VERIFIED (reported in audit) | Keep navigation tests in CI |
| AR-10 | Crypto storage | Encrypted database/key storage, migration and backup exclusions verified | PASSING-CI / DEVICE CHECK PENDING | CI run passed; verify existing production data upgrade and real-device behavior before release |
| AR-11 | Android 16 KB pages | APK ZIP alignment and native ELF load segment alignment meet 16 KB requirements | NOT VERIFIED | Do not use `zipalign -c -v 4` alone as proof. Run `zipalign -c -P 16 -v 4 <apk>` and inspect every bundled native `.so` ELF PT_LOAD alignment using current Android tooling; record exact artifact and outputs. |
| AR-12 | Backend security | Role separation, CORS allow-list, ticketed WebSockets, limits and DB concurrency tests pass | PASSING-CI | CI run [39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075); production configuration still requires review |
| AR-13 | Production TLS | Backend and dashboard connect using valid production HTTPS/WSS with strict certificate validation | IMPLEMENTED-UNEXECUTED | Deploy behind a real TLS terminator and verify HTTPS/WSS handshake, origin checks, expiry and reconnect behavior |
| AR-14 | Dashboard | Frontend tests and production bundle build pass; secrets are not embedded | PASSING-CI | CI run [39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075); repeat secret scan after any frontend configuration change |
| AR-15 | CI | Android, backend, frontend and dependency checks execute successfully on release branch | PASSING-CI | CI run [39300831075](https://github.com/deekshudevang/Nexus-Support/actions/runs/39300831075), exact SHA shown above |
| AR-16 | Static analysis | CodeQL scan passes on the exact release SHA | VERIFIED for run | Record specific successful CodeQL run and SHA in checkpoint; investigate any duplicate/in-progress run independently |
| AR-17 | Privacy diagnostics | Diagnostics default OFF; sensitive location/content is redacted; consent is respected | PASSING-CI / MANUAL REVIEW PENDING | Keep automated coverage and conduct a privacy review on the release candidate |
| AR-18 | Accessibility | TalkBack can use Home, SOS, Chat, Map and Settings without inaccessible controls | IMPLEMENTED-PENDING-DEVICE | Record Android version, app SHA/version, test cases and results |
| AR-19 | Release assembly | Release artifact assembles; artifact SHA-256 and signing state are documented | VERIFIED for prior local report only | Rebuild from exact release SHA in CI, upload artifact, record checksum; unsigned output is not a distributable signed release |
| AR-20 | Report integrity | Audit claims match evidence and do not overstate counts or device coverage | IN PROGRESS | Keep prior Gemma/device audit clearly scoped to its branch/SHA; separate current release branch evidence |

## Required evidence package

For each candidate release, attach or link:
1. Exact branch and full commit SHA.
2. GitHub Actions run URL and per-job result.
3. JUnit XML aggregation showing Android, backend and frontend counts separately, including skipped/ignored tests.
4. Release artifact filename, SHA-256 checksum and signing status.
5. 16 KB native ELF / ZIP alignment command output for the exact artifact.
6. Device-validation records for BLE/LoRa, multi-hop, TalkBack and 16 KB runtime.
7. Production HTTPS/WSS handshake evidence and deployment configuration review.

**Release gate:** Do not describe the application as fully production-ready while AR-04, AR-07, AR-08, AR-11, AR-13, or AR-18 remain blocked, unexecuted, or pending device validation.