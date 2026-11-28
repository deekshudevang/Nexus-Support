# STATE.md — Nexus Support System State

**Phase:** End-to-End Release Verification COMPLETE  
**Branch:** `m3-15-to-m3-24-production`  
**Classification:** `GO (All local, Docker PostGIS, Playwright E2E, physical phone & emulator gates verified; multi-phone mesh/LoRa RF and production TLS domain setup documented in runbook)`

---

## 1. Verified Test Baselines (Executed 2026-11-28)

| Suite / Gate | Latest Result | Environment & Command | Evidence |
|---|---|---|---|
| Docker + PostGIS Stack | **UP & HEALTHY** (4 containers: PostGIS `3.4`, Redis 7, Backend `:8000`, Frontend `:5173`) | `docker compose up -d --build` (`alembic current` = `8a2c4f91d3b0 (head)`, full PG `downgrade base` -> `upgrade head` PASSED) | `docs/evidence/docker_bringup_2026-11-28.txt` |
| PG Backup / Restore Drill | **PASSED** (`20` tables restored cleanly into scratch PostGIS DB & verified) | `pg_dump -Fc` -> `pg_restore --no-owner --no-privileges` | `docs/evidence/pg_backup_restore_drill_2026-11-28.txt` |
| Cloud Backend (`pytest`) | **131 / 131 PASSED** | Python 3.14, FastAPI + SQLAlchemy (`python -m pytest -q`) | `cloud/backend/` |
| Tactical Web UI (`Vitest`) | **19 / 19 PASSED** (3 files) | Node / Vite 8 / React 18 (`npx vitest run`) | `cloud/frontend/src/` |
| Web E2E (Playwright Chromium) | **PASSED** (401 reject, `WSS LIVE`, live SOS WS push, dispatch, `390x844` mobile map `w=390`, backend restart -> `WSS OFFLINE` -> `WSS LIVE`, `0` `pageerror`) | `python docs/evidence/playwright/e2e_verify.py` against live `:5173` / `:8000` | `docs/evidence/playwright/` |
| Physical Phone (`ZD222M3BNH`) | **PASSED** (Wi-Fi `192.168.1.4:8000` Test Connection, Hold SOS -> Postgres, downlink `asgn_93f80b636c6e`, offline queue, `401` key error, Wi-Fi restore flush without duplicates) | Motorola Edge 50 Pro (Android 16) via USB ADB | `docs/evidence/device_emulator_test_2026-11-28.txt` |
| Android Unit Tests & Lint | **383 / 383 PASSED** (`201` `:app`, `173` `:core:data/:mesh/:crypto`, `9` `:core:domain`) + `:app:lintDebug` PASSED | `.\gradlew.bat testDebugUnitTest :core:domain:test :app:lintDebug` | `app/build/reports/lint-results-debug.html` |
| Android Emulator (`emulator-5554`) | **PASSED** (`10.0.2.2:8000` Test Connection, Sync Now, Hold SOS `7e0b248a` reconciled in `112ms`) | AVD `meshlink_api34` (`pixel_6`, API 34 `x86_64`) | `docs/evidence/device_emulator_test_2026-11-28.txt` |
| Signed Release APK Build | **BUILD SUCCESSFUL** (`app-release.apk` signed with throwaway keystore, R8 `mapping.txt`; throwaway keystore deleted; unconfigured prod default = `https://unconfigured.meshlink.invalid/` & HTTPS-only config) | `.\gradlew.bat assembleRelease` | `app/build.gradle.kts`, `core/data/build.gradle.kts` |
| Security & Dependency Audits | `npm audit`: **0** vulnerabilities; `pip check`: **0** broken reqs; `685`-file secret scan: **0** prod secrets; historical `logs/*` & `*.db` noted for `git filter-repo` | `npm audit`, `pip check`, Python regex scan, `git log` | `docs/evidence/security_quality_gates_2026-11-28.txt` |

---

## 2. Release Engineering & Hardware Verification Fixes Applied
1. **PostgreSQL 64-bit Timestamps (`8a2c4f91d3b0`):** Widened `sos_packets.timestamp`, `node_status.timestamp`, `incidents.created_at/updated_at/resolved_at`, and `responders.last_location_at/created_at` to `BigInteger` (`with_variant(Integer, "sqlite")`), fixing `NumericValueOutOfRange` crashes on 13-digit Android millisecond timestamps.
2. **Mobile Blank `senderName` Compatibility:** Relaxed `SosPacket.senderName` `min_length=1` in `cloud/backend/main.py` (with `senderId` fallback) and normalized `GatewaySyncWorker`, eliminating HTTP 422 rejections of mobile SOS broadcasts.
3. **Zero-SOS Downlink Polling:** Added `KEY_PULL_DOWNLINKS` to `GatewaySyncWorker` (triggered on manual `Sync Now`, assignment updates, and 15-minute periodic sync) so responders receive dispatches even with zero pending outgoing SOS uploads.
4. **Mobile Map & Date Safety:** Guarded `MapFlyController` against zero-sized hidden mobile containers (`ResizeObserver` + `getSize()`), added a `< 768px` `QUEUE / RADAR MAP / ASSETS` switcher in `App.jsx`, and normalized seconds-vs-ms formatting in `IncidentQueue.jsx` and `EventTimeline.jsx`.
5. **Production Compose & Keystore Signing:** Added `docker-compose.prod.yml`, `cloud/Caddyfile`, `.env.production.example`, and `signingConfigs.release` in `app/build.gradle.kts`.

---

## 3. Remaining Hardware / Ops Notes (External to Local Host)
- **Multi-Phone Mesh / LoRa RF Test:** Requires 2+ physical handsets / Meshtastic radios (`docs/evidence/PHONE_TEST_CHECKLIST.md`).
- **Git History Purge & Repo Location:** Workspace resides inside Windows OneDrive (`c:\Users\deeks\OneDrive\Desktop\NEXUS SUPPORT`), which can lock Gradle build files; moving outside OneDrive and optionally running `git filter-repo` to purge historical `logs/` and `*.db` commits is documented in `docs/PR_DESCRIPTION.md`.
