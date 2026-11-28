# STATE.md — Nexus Support System State

**Phase:** End-to-End Connectivity & Release Readiness Verification COMPLETE  
**Branch:** `m3-15-to-m3-24-production`  
**Classification:** `CONDITIONAL GO (Pending physical/emulator ADB device test, release keystore signing, and live Docker/PostgreSQL daemon verification)`

---

## 1. Verified Test Baselines (Executed 2026-11-28)

| Suite / Gate | Latest Result | Environment & Command | Evidence |
|---|---|---|---|
| Cloud Backend (`pytest`) | **129 / 129 PASSED** (44.55s) | Python 3.14, FastAPI + SQLAlchemy (`python -m pytest -q`) | `cloud/backend/` |
| Tactical Web UI (`Vitest`) | **19 / 19 PASSED** (2.32s, 3 files) | Node / Vite 8 / React 18 (`npm test`) | `cloud/frontend/src/` |
| Web Production Build & Guard | **PASSED** (negative guard exit 1 when unset; positive build exit 0, CSS 46,776 B Tailwind v4, JS 354,700 B with **0** `localhost` refs) | Vite 8 + `@tailwindcss/vite` (`npm run build`) | `cloud/frontend/dist/` |
| Android Unit Tests (`testDebugUnitTest`) | **BUILD SUCCESSFUL** (144 actionable tasks across `:app`, `:core:data`, `:core:mesh`, `:core:crypto`, `:core:domain`) | Java 17/Gradle 8.14.5 (`.\gradlew.bat testDebugUnitTest`) | `app/src/test/`, `core/*/src/test/` |
| Android APK & R8 Release Build + Lint | **BUILD SUCCESSFUL** (366 actionable tasks: `assembleDebug`, R8 `assembleRelease`, `lintDebug`) | `-PMESHLINK_GATEWAY_KEY=verify-release-key-2026` | `app/build/outputs/apk/` |
| Alembic Migration Round-Trip | **PASSED** (`upgrade head -> current (6f1a8b92c431) -> downgrade base -> upgrade head`) | Python / SQLAlchemy (`python -m alembic`) | `cloud/backend/alembic/` |
| Live LAN E2E & Negative Suite | **12 / 12 PASSED** (`http://192.168.1.4:18000` health/ready, SOS ingest, live WS `sos_alert`, `/api/v1/responders`, `/api/v1/teams`, 401/404/prod-fail-closed) | Live Uvicorn server (`0.0.0.0:18000`) | `docs/evidence/live_integration_2026-11-28.txt` |
| Dependency & Secret Audits | `npm audit`: **0** vulnerabilities; `pip check`: **0** broken requirements; tracked runtime log notice `logs/startup_logcat.txt` | `npm audit --json`, `pip check`, `git grep` | `docs/audit/CONNECTIVITY_AUDIT_2026-11-28.md` |

---

## 2. Connectivity & Security Hardening Summary
1. **Android Unified Networking:** Replaced placeholder `https://api.meshlink.app/v1/` and split `HttpURLConnection` vs Retrofit stacks with dynamic DataStore + `BuildConfig` URL routing (`debug` / `staging` / `release`), OkHttp timeouts (15s/20s), relative paths (`MeshBackendService`), and removed silent `Provider { null }` DI fallbacks.
2. **Release vs Debug Cleartext Policy:** `app/src/main/res/xml/network_security_config.xml` is strictly HTTPS-only; `app/src/debug/res/xml/network_security_config.xml` allows LAN (`192.168.x.x`), Tailscale (`100.x.y.z`), and emulator (`10.0.2.2`) HTTP testing in debug.
3. **SOS-to-Gateway Guarantee:** Room schema v15 -> v16 (`MIGRATION_15_16`) adds `cloudSynced` on `sos_packets` so peer mesh ACKs never suppress gateway cloud upload; `SosViewModel` enqueues `GatewaySyncWorker` immediately and `MeshLinkApp` schedules 15-minute periodic sync.
4. **In-App Connectivity Controls:** `SettingsScreen` -> **Cloud Gateway Connectivity** provides live `/health` + `/health/ready` `"Test Connection"`, Tink-encrypted `gatewayKey` storage, `"Sync Now"`, and truthful sync telemetry.
5. **Web & Backend Parity:** Fixed `/api/v1/responders`, `/api/v1/teams`, and `/api/v1/incidents/{id}/assignments` API parity, fallback login token persistence, live `sos_alert` WebSocket ingestion with exponential backoff, Tailwind v4 styling (`@tailwindcss/vite`), production `VITE_API_URL`/`VITE_WS_URL` build gate, single-source `app/core/config.py`, env-backed `JWT_SECRET`, and single root `docker-compose.yml`.

---

## 3. Honest Remaining Hardware / Environment Gaps
- **Physical Phone / Emulator Verification:** `adb devices -l` showed **0 attached devices** and **0 Android AVD system images** on this host, so physical phone Wi-Fi and emulator over-the-air UI runs must be executed once a device/AVD is attached.
- **Release Signing Keystore:** `app-release-unsigned.apk` passed R8 minification & resource shrinking; configure a release keystore in `signingConfigs` before Play Store / MDM distribution.
- **Docker Engine:** Docker Desktop daemon was stopped locally during verification; run `docker compose up --build` on a running Docker host to smoke-test full PostGIS container bring-up.
