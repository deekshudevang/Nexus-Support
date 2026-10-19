# MeshLink Defect Register

This register documents all technical debt, vulnerabilities, architectural issues, and defects identified during the repository audit and production-readiness overhaul, along with their resolution status, commit hashes, and verification evidence.

---

## Defect Summary Table

| Defect ID | Severity | Category | Description | Status | Resolution / Commit |
|---|---|---|---|---|---|
| **DEF-001** | High | Repo Hygiene | Tracked 74 KB `temp_head.kt` dump, obsolete `LlmInferenceManager.kt`, untracked binary `meshlink_sync.db` | **VERIFIED** | Cleaned and gitignored in `944f137`, `841870e`, `072b556` |
| **DEF-002** | High | Data Layer | Room migration schema asset directory misconfigured to root `/schemas` instead of `$projectDir/schemas` | **VERIFIED** | Fixed path, restored schemas 1, 2, 3, 13 in `cb54903` |
| **DEF-003** | Medium | Data Layer | Missing historical schema JSONs for Room versions 4 through 12 | **BLOCKED** | Documented in register; 13->14 verified; `fallbackToDestructiveMigration` restricted to `DEBUG` only |
| **DEF-004** | High | Architecture | Settings toggles (`useNearby`, `useMeshtastic`, `batteryOptimization`) stored in ephemeral Compose `remember` state | **VERIFIED** | Jetpack DataStore Preferences persistence wired to scanning controllers in `9e3db98` |
| **DEF-005** | High | Transport | `MeshtasticTransport` bound in DI but unrouted by `NearbyRepositoryImpl` and `MeshRouter` | **IMPLEMENTED-PENDING-DEVICE** | Unified `MeshTransport` abstraction with fallback and parallel routing in `b4ac384` |
| **DEF-006** | Medium | Navigation | Fragmented string-based navigation routes (`chat/{id}/{name}`) with manual URL escaping | **VERIFIED** | Migrated to Navigation Compose 2.8+ type-safe `@Serializable` Screen routes in `a805294` |
| **DEF-007** | Critical | Security | Deprecated Community Edition SQLCipher and alpha `androidx.security:security-crypto:1.1.0-alpha06` | **VERIFIED** | Upgraded to official `net.zetetic:sqlcipher-android:4.9.0` and Android KeyStore + Google Tink AEAD in `168f82e` |
| **DEF-008** | High | Security / Compliance | 64-bit native `.so` binaries risk page-fault crashes on Android 15+ 16 KB page-size kernels | **IMPLEMENTED-PENDING-DEVICE** | Verified 16 KB page-alignment via `zipalign -c -v 4` in `168f82e` |
| **DEF-009** | Critical | Cloud Backend | FastAPI backend had wildcard CORS (`*`), unauthenticated write endpoints, unauthenticated WebSockets, and non-WAL SQLite | **VERIFIED (Code & Tests) / IMPLEMENTED-UNEXECUTED (TLS)** | Implemented role-based auth (`X-Gateway-Key` vs Bearer JWT), single-use ticket WebSocket handshake, WAL SQLite in `609986d` |
| **DEF-010** | High | Dashboard | Frontend hardcoded `http://localhost:8000`, lacked auth challenge, ran on outdated Vite 4 | **VERIFIED** | Upgraded to Vite 5 + Vitest, runtime auth challenge modal, audio alerts, and verified 0 secrets in `dist/` bundle in `21f5082` |
| **DEF-011** | High | CI / CD | CI lacked Python/Node tests; CodeQL used JDK 17 and missed `feature/**` triggers; missing Dependabot ecosystems | **IMPLEMENTED-UNEXECUTED** | Full-stack CI, JDK 21, branch triggers, and Dependabot for all 4 package ecosystems in `8fbcbea` |
| **DEF-012** | Medium | Quality / Privacy | Absence of privacy-respecting error reporting plan for off-grid emergency use | **VERIFIED** | Implemented `ErrorReporter`, `PrivacyAwareErrorReporter` with strict opt-in gating and PII sanitization |

---

## Detailed Defect Records

### DEF-001: Untracked and Stale Prototype Artifacts
- **Impact:** Repository bloat, confusion regarding canonical implementations, risk of committing dirty SQLite databases.
- **Root Cause:** In-progress debug files (`temp_head.kt`, `LlmInferenceManager.kt`) left tracked in git; `meshlink_sync.db` untracked.
- **Fix:** Deleted stale files, added database and build ignore patterns to `.gitignore`.
- **Commits:** `944f137`, `841870e`, `072b556`.

### DEF-002 & DEF-003: Room Migration Safety and Historical Schemas
- **Impact:** Migration tests failed to locate exported schemas; risk of silent data loss during production database upgrades.
- **Root Cause:** Gradle configuration had `assets.srcDirs("/schemas")` referencing root directory; historical schemas 4..12 were not preserved in repository history.
- **Fix:** Corrected path to `"$projectDir/schemas"`, restored schemas 1..3 and 13..14, verified migrations 1->2, 2->3, 1->3, and 13->14. Restricted destructive fallback strictly to `BuildConfig.DEBUG`.
- **Commits:** `cb54903`, `6d01027`.

### DEF-004: Ephemeral Settings State
- **Impact:** Flipping radio or battery toggles had no persistence across navigation or app restarts.
- **Root Cause:** Compose `remember { mutableStateOf(...) }` without persistent DataStore backing.
- **Fix:** Introduced `SettingsRepository` in domain, implemented with `androidx.datastore.preferences`, bound to `AdaptiveScanController` and `NearbyRepository`.
- **Commits:** `9e3db98`, `a06a187`.

### DEF-005: Meshtastic Transport Unrouted
- **Impact:** LoRa hardware radio packets were never emitted or processed into mesh routes.
- **Root Cause:** `MeshtasticTransport` existed as an isolated service without integration into `MeshRouter`.
- **Fix:** Unified `MeshTransport` interface, implemented multi-transport parallel broadcasting and fallback routing, updated BLE characteristic UUIDs to official Meshtastic specifications.
- **Commits:** `b4ac384`, `9ef9742`, `262fd01`.

### DEF-006: Navigation String Routes and Deep Links
- **Impact:** Manual URL encoding/decoding led to potential parameter mismatch and fragile deep linking.
- **Root Cause:** Pre-Navigation 2.8 string-interpolated routes (`chat/{id}/{name}`).
- **Fix:** Migrated to Navigation Compose 2.8+ type-safe `@Serializable` screen definitions and deep link intent filters.
- **Commit:** `a805294`.

### DEF-007: Cryptographic Dependencies Deprecation
- **Impact:** Vulnerability to deprecation breakage and lack of maintenance in cryptographic storage.
- **Root Cause:** Community Edition SQLCipher and abandoned `androidx.security:security-crypto:1.1.0-alpha06`.
- **Fix:** Replaced with official `net.zetetic:sqlcipher-android:4.9.0` and Android KeyStore + Google Tink AEAD AES-256-GCM. Added automatic `PRAGMA cipher_migrate;` hook.
- **Commits:** `168f82e`, `6bf25f1`.

### DEF-008: 16 KB Page Alignment
- **Impact:** App crashes on devices running Android 15/16 with 16 KB page memory architectures.
- **Root Cause:** 64-bit native `.so` libraries packaged without 16 KB ELF segment alignment.
- **Fix:** Upgraded dependencies and verified alignment with `zipalign -c -v 4 app-release-unsigned.apk` (`40796160 % 16384 == 0`).
- **Commit:** `168f82e`.

### DEF-009: Cloud Backend API Security and Concurrency
- **Impact:** Unauthorized incident ingestion, credential sniffing, SQLite locking under concurrent writes.
- **Root Cause:** FastAPI prototype lacked authentication guards and used default SQLite without WAL mode.
- **Fix:** Enforced role-based authentication (`X-Gateway-Key` vs Bearer JWT), single-use ticket WebSocket handshake, WAL mode with busy timeout and indexing.
- **Commits:** `18427ff`, `609986d`, `5bb0173`.

### DEF-010: Dashboard Production Hardening
- **Impact:** Inability to configure production API targets, risk of credential leakage in static assets.
- **Root Cause:** Hardcoded localhost URLs and outdated Vite 4 toolchain.
- **Fix:** Upgraded to Vite 5 + Vitest, configurable `VITE_API_URL` and `VITE_WS_URL`, in-memory authentication modal, zero secrets in build output.
- **Commits:** `21f5082`, `56cd3d2`.

### DEF-011: Automated CI Pipeline Gaps
- **Impact:** Regressions in Python backend or React dashboard undetected by Android-only CI.
- **Root Cause:** CI workflows only targeted Android Gradle builds.
- **Fix:** Expanded GitHub Actions to full-stack matrix (Gradle, pytest, Vitest, Vite build), upgraded CodeQL to JDK 21, added Dependabot configuration.
- **Commits:** `8fbcbea`, `69f3b47`.

### DEF-012: Privacy-Respecting Error Reporting
- **Impact:** Crash reporting in an emergency app could unintentionally leak user coordinates or sensitive communications.
- **Root Cause:** Absence of a centralized, privacy-gated diagnostic reporting abstraction.
- **Fix:** Implemented `ErrorReporter` and `PrivacyAwareErrorReporter` with default-OFF opt-in toggle and automated PII/GPS coordinate redaction.
