# Nexus-Support End-to-End Connectivity & Production Readiness Audit

**Date:** 2026-11-28  
**Branch:** `m3-15-to-m3-24-production` (synchronized with `master` at `f649323`)  
**Scope:** Android (`app`, `:core:data`, `:core:mesh`, `:core:crypto`, `:core:domain`), Cloud Backend (`cloud/backend`), Web Command Console (`cloud/frontend`), Docker/Env packaging, and CI/test coverage.

---

## 1. Prime Problem Reproduction (Captured Before Any Code Edits)

### Environment Snapshot
- `adb devices -l` (`C:\Users\deeks\AppData\Local\Android\Sdk\platform-tools\adb.exe`): **0 physical devices attached**.
- Android Emulator AVDs (`%USERPROFILE%\.android\avd`, `%LOCALAPPDATA%\Android\Sdk\system-images`): **0 AVDs / 0 system images installed**.
- Local host network interfaces: Ethernet/LAN `192.168.1.4`, Tailscale `100.81.111.40`.
- Docker Desktop CLI (`29.8.0`): daemon stopped (`//./pipe/dockerDesktopLinuxEngine` unavailable).

### Empirical Reproduction Run (Live FastAPI Uvicorn on `0.0.0.0:18000`)
Running Uvicorn directly from `cloud/backend/main.py` and exercising the actual Android, Web, and Backend configuration paths produced:

```text
[BACKEND UP] {"status":"ok","version":"1.0.0","environment":"development","database_connected":true,"migration_head":"6f1a8b92c431","timestamp":1795841748,"service":"MeshLink Gateway Backend"}
[LAN_HTTP_HEALTH] HTTP 200: {"status":"ok","version":"1.0.0","environment":"development","database_connected":true,"migration_head":"6f1a8b92c431","timestamp":1795841748,"service":"MeshLink Gateway Backend"}
[EMULATOR_ONLY_10_0_2_2] FAILED: URLError: <urlopen error timed out>
[PLACEHOLDER_RETROFIT_BASE] FAILED: URLError: <urlopen error [Errno 11001] getaddrinfo failed>
[EMPTY_GATEWAY_KEY] HTTP 401: {"detail":"Gateway credentials required"}
[CONFIG_SPLIT] main.GATEWAY_API_KEYS={'meshlink_gw_dev_secret'} vs config.GATEWAY_API_KEYS=['meshlink_gateway_dev_secret', 'gw_key_valid_1']
[CONFIG_SPLIT] main.CORS_ORIGINS=['http://localhost:5173', 'http://localhost:3000', 'http://127.0.0.1:5173'] vs config.CORS_ORIGINS=['*']
[CONFIG_SPLIT] main.REVOKED_CREDENTIALS=set() vs config.REVOKED_CREDENTIALS={'revoked_gw_key_1', 'revoked_op_token_1'}
[ROUTE_CHECK GET /api/v1/dispatch/responders] HTTP 404: {"detail":"Not Found"}
[ROUTE_CHECK GET /api/v1/dispatch/teams] HTTP 404: {"detail":"Not Found"}
[ROUTE_CHECK POST /api/v1/dispatch/assignments] HTTP 404: {"detail":"Not Found"}
[ROUTE_CHECK GET /api/v1/responders] HTTP 200
[ROUTE_CHECK GET /api/v1/teams] HTTP 200
```

Baseline automated suites prior to edits:
- Backend pytest: `128 passed`
- Frontend Vitest: `17 passed` (`3 test files`)
- Frontend `vite build`: succeeded **with no `VITE_API_URL`/`VITE_WS_URL` set**, generating a 353.81 kB JS bundle embedding `http://localhost:8000` and only `17.59 kB` CSS (no Tailwind utility styles).
- Android Gradle `testDebugUnitTest`: `BUILD SUCCESSFUL` (`144 actionable tasks: 144 executed`).

---

## 2. Audit Findings Register

### CONN-001 (P0) — Hardcoded Emulator-Only URL & No Runtime Backend Config
- **Location:** `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:58`, `app/src/main/java/com/meshlink/app/ui/assignments/AssignmentsViewModel.kt:47`
- **Root Cause:** `DEFAULT_GATEWAY_URL = "http://10.0.2.2:8000/api/v1/sync/sos"` only resolves inside an Android Emulator; physical devices on Wi-Fi (`192.168.1.4`) time out. `AssignmentsViewModel` calls `buildWorkRequest()` with no override, and Settings/DataStore has no backend URL field.
- **Fix:** Introduce `BuildConfig.BACKEND_BASE_URL` per build type (`debug` = `http://10.0.2.2:8000/`, `staging`, `release`), persist runtime `backendBaseUrl` in `SettingsRepository` (DataStore), read it across Retrofit/WorkManager, and add Settings UI + live `"Test connection"` against `/health` and `/health/ready`.
- **Proving Test:** `BackendConnectivityTest` + `GatewaySyncWorkerTest` verifying DataStore override supersedes BuildConfig default and reaches `MockWebServer` + live Uvicorn LAN IP.

### CONN-002 (P0) — Placeholder Retrofit Base URL, Path-Reset Bug & Split HTTP Stacks
- **Location:** `core/data/src/main/java/com/meshlink/app/data/remote/NetworkModule.kt:17`, `core/data/src/main/java/com/meshlink/app/data/remote/MeshBackendService.kt:13,19,25`, `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:156`
- **Root Cause:** `NetworkModule.BASE_URL` is hardcoded to non-existent `"https://api.meshlink.app/v1/"` without OkHttp connect/read/write timeouts; `MeshBackendService` uses root-leading `"/api/v1/..."` paths that discard any base path prefix; SOS sync bypasses Retrofit/OkHttp using raw `HttpURLConnection`.
- **Fix:** Replace placeholder URL with dynamic DataStore/BuildConfig interceptor-resolved base URL, add OkHttp timeouts (`15s` connect, `20s` read/write), convert `MeshBackendService` annotations to relative paths (`api/v1/...`, `health`, `health/ready`), add SOS sync (`syncSos`) & health endpoints to `MeshBackendService`, and route `GatewaySyncWorker` through the unified OkHttp/Retrofit client.
- **Proving Test:** `NetworkModuleTest` + `GatewaySyncWorkerTest` using `MockWebServer` on sub-path base URLs and custom runtime URL switching without rebuilding Dagger.

### CONN-003 (P0) — Cleartext Policy in Main Ships in Release & Blocks LAN/Tailscale in Debug
- **Location:** `app/src/main/res/xml/network_security_config.xml:20-23`, `app/src/main/AndroidManifest.xml:57`
- **Root Cause:** Single `src/main/res/xml/network_security_config.xml` permits cleartext for `localhost` and `10.0.2.2` in **all builds including release**, while blocking HTTP LAN (`192.168.x.x`, `10.x.x.x`) and Tailscale (`100.x.y.z`) hosts in debug builds on physical phones.
- **Fix:** Move HTTPS-only strict config (`cleartextTrafficPermitted="false"` with zero cleartext domain exceptions) to `app/src/main/res/xml/network_security_config.xml` and provide a debug/staging overlay in `app/src/debug/res/xml/network_security_config.xml` permitting local development/LAN cleartext. Enforce HTTPS validation in release runtime/URL parser.
- **Proving Test:** Release manifest/resource merge check + unit test rejecting `http://` URLs in non-debug validation.

### CONN-004 (P0) — SOS Packets Never Trigger GatewaySyncWorker & Peer ACK Blocks Cloud Sync
- **Location:** `app/src/main/java/com/meshlink/app/ui/sos/SosViewModel.kt:99-100,263-295`, `app/src/main/java/com/meshlink/app/MeshLinkApp.kt:58-59`, `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:116`
- **Root Cause:** `GatewaySyncWorker` is only enqueued from `AssignmentsViewModel`, never when an SOS is triggered (`SosViewModel`), periodically (`MeshLinkApp`), or when a relay device comes online. Additionally, when a mesh peer ACKs an SOS, `SosViewModel` marks `status = ACKNOWLEDGED`, causing `GatewaySyncWorker` (`it.status != "DELIVERED" && it.status != "ACKNOWLEDGED"`) to skip cloud upload completely! Also `SosRepositoryImpl` does not track whether cloud upload succeeded separately from mesh peer ACK.
- **Fix:** Add `cloudSynced: Boolean` / distinct tracking or safe status gating (`"RECEIVED_BY_COMMAND"` / `"DELIVERED"` after server receipt), keep peer-ACKed SOS eligible for cloud gateway ingestion until confirmed by server `receipts`, enqueue `GatewaySyncWorker` immediately on SOS enqueue (with `NetworkType.CONNECTED` constraint), and schedule periodic gateway sync alongside `CloudSyncWorker` in `MeshLinkApp`.
- **Proving Test:** `SosGatewayPipelineTest` proving peer-ACKed SOS is still uploaded to gateway and reconciled only upon valid server receipt.

### CONN-005 (P0) — Hidden DI Failures via `Provider { null }` & Silent Sync Swallow
- **Location:** `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:42-53,91,106-108`
- **Root Cause:** Secondary constructor supplies `Provider { null }`, `doWork()` silently ignores `backendServiceProvider.get()` errors as `null`, and assignment status failures return `Result.success()`.
- **Fix:** Remove `Provider { null }` fallback (inject required `MeshBackendService` / `SettingsRepository` cleanly; update tests to pass real/mocked instances), propagate HTTP 5xx/network failures to `Result.retry()` and `401`/`403` to `Result.failure()`, and persist truthful sync telemetry (`lastSyncAt`, `lastSyncError`, `lastHttpStatus`) in DataStore/telemetry repository.
- **Proving Test:** `GatewaySyncWorkerTest` verifying `500` assignment sync triggers `Result.retry()` without calling `markSynced`, and `401`/`403` triggers `Result.failure()` with surfaced auth error message.

### CONN-006 (P0) — Web Console API Path Mismatch, WS Event Name Mismatch & Login Token Bug
- **Location:** `cloud/frontend/src/api.js:143,164,181`, `cloud/frontend/src/components/DispatchPanel.jsx:59-64`, `cloud/frontend/src/App.jsx:46-54,136`, `cloud/backend/main.py:380,524`
- **Root Cause:**
  1. `api.js` calls `/api/v1/dispatch/responders`, `/api/v1/dispatch/teams`, and `/api/v1/dispatch/assignments` (all 404 on backend, which mounts `dispatch_v1_router` directly under `/api/v1`: `/api/v1/responders`, `/api/v1/teams`, `/api/v1/incidents/{incident_id}/assignments` accepting `{ responder_id, team_id }`).
  2. `App.jsx:136` checks `data.type === 'NEW_SOS'`, whereas backend `main.py:380` broadcasts `{"type": "sos_alert", "packet": packet}`, causing every live SOS WebSocket alert to be dropped from the queue UI.
  3. `App.jsx:52` saves `enteredToken` (raw password) to `sessionStorage` instead of `loginRes.access_token` when falling back to `loginOperator`.
- **Fix:** Fix `api.js` and `DispatchPanel.jsx` to call the exact OpenAPI routes and payloads (`/api/v1/responders`, `/api/v1/teams`, `/api/v1/incidents/${incidentId}/assignments` with `{ responder_id, team_id }`), update `App.jsx` WS handler to accept `sos_alert`, `NEW_SOS`, `event`, and `replay_event` messages, and store the real bearer token returned by `/api/auth/token`.
- **Proving Test:** `App.test.jsx` + `TacticalCommandCenter.test.jsx` + live end-to-end backend-to-frontend verification.

### CONN-007 (P0) — Frontend Tailwind Styling Missing & Unsafe Production Localhost Fallback
- **Location:** `cloud/frontend/package.json`, `cloud/frontend/vite.config.js`, `cloud/frontend/src/App.css`, `cloud/frontend/src/api.js:9`, `cloud/frontend/Dockerfile`
- **Root Cause:** Components use Tailwind utility classes (`h-screen`, `bg-[#0b0f19]`, `max-w-md`), but neither Tailwind v3 nor `@tailwindcss/vite` v4 is installed or imported, leaving LoginTerminal and Dispatch HUD unstyled (`dist/assets/*.css` is only 17.59 kB of Leaflet/custom rules). Additionally, `vite build` succeeds in production with no `VITE_API_URL`/`VITE_WS_URL`, defaulting bundles to `http://localhost:8000`.
- **Fix:** Install & wire `@tailwindcss/vite` + `tailwindcss` (v4 `@import "tailwindcss";` in `index.css`/`App.css`), enforce a Vite production build plugin check requiring `VITE_API_URL` and `VITE_WS_URL` when `mode === 'production'` (or non-dev), accept `ARG VITE_API_URL` / `ARG VITE_WS_URL` in `cloud/frontend/Dockerfile`, and show explicit runtime WS reconnect backoff state in `TopHUD`.
- **Proving Test:** `npm run build` fails cleanly when `VITE_API_URL`/`VITE_WS_URL` are omitted and succeeds when supplied, emitting real Tailwind utility rules in `dist/assets/*.css`.

### CONN-008 (P0) — Backend Config Split, Ephemeral JWT Secret & Unauthenticated Device Overwrite
- **Location:** `cloud/backend/main.py:36-76` vs `cloud/backend/app/core/config.py:15-57`, `cloud/backend/app/core/rbac.py:146`, `cloud/backend/app/api/v1/devices.py:46-94`
- **Root Cause:**
  1. `main.py` and `app/core/config.py` parse env vars independently with conflicting defaults (`meshlink_gw_dev_secret` vs `meshlink_gateway_dev_secret`, localhost CORS vs `["*"]`, empty vs populated `REVOKED_CREDENTIALS`).
  2. `rbac.py:146` generates a random `JWT_SECRET` in memory on every process start instead of reading `JWT_SECRET` from `config.py` (breaking tokens on restart or multi-worker setups).
  3. `devices.py:46` `POST /api/v1/devices/enroll` has no authentication or proof-of-possession check before overwriting an existing active device's `public_key_pem`, allowing device key hijacking.
- **Fix:** Consolidate all environment parsing and production validation (`HTTPS` CORS, no dev keys, explicit `JWT_SECRET`) into `app/core/config.py` and import exclusively from it across `main.py`, `security.py`, and `rbac.py`; make `enroll_device` fail-closed when updating an existing device (require `ADMIN`/`RESPONDER_MANAGE` or valid device signature for key rotation, while keeping first-time enrollment operational for tests/onboarding unless admin-only configured).
- **Proving Test:** New negative & parity tests in `test_pg_p2_rbac.py` and `test_backend.py` confirming single config source, persistent `JWT_SECRET`, and rejection of unauthorized existing-device key overwrite (`401`/`403`).

### REL-001 (P1) — Hardcoded Dev Key & Unchecked Release `GATEWAY_KEY`
- **Location:** `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:59`, `core/data/build.gradle.kts:29`
- **Root Cause:** Hardcoded `"meshlink_gw_dev_secret"` lives in `GatewaySyncWorker.kt`; release builds default `GATEWAY_KEY` to `""`.
- **Fix:** Remove hardcoded dev secret from Kotlin `src/main` (`BuildConfig.GATEWAY_KEY` from `core/data` + Tink-encrypted `SecureGatewayConfigStore` override for field provisioning), and detect missing key cleanly before wasting requests.

### REL-002 (P1) — ProGuard/R8 Risk on Non-`dto` Network Models
- **Location:** `app/proguard-rules.pro:86`, `core/data/src/main/java/com/meshlink/app/data/remote/MeshBackendService.kt:35-49`
- **Root Cause:** `proguard-rules.pro` keeps `com.meshlink.app.data.remote.dto.**`, missing `LocationUploadResponse`, `AssignmentStatusUpdateRequest`, `AssignmentStatusUpdateResponse`, and new SOS/Health DTOs in `com.meshlink.app.data.remote`.
- **Fix:** Add `-keep class com.meshlink.app.data.remote.** { *; }` (and verify `assembleRelease` R8 output).

### REL-003 (P1) — Duplicate Docker Compose Files & Incomplete `.env.example`
- **Location:** `./docker-compose.yml`, `./cloud/docker-compose.yml`, `cloud/backend/.env.example`, `cloud/frontend/.env.example`
- **Root Cause:** Duplicate compose definitions diverge in build context and lack frontend `build.args`, DB volume health/non-root or hardened defaults, and root `.env.example`.
- **Fix:** Make root `docker-compose.yml` the single canonical compose definition (removing duplicate `cloud/docker-compose.yml` or replacing with explicit README note), wire `VITE_API_URL`/`VITE_WS_URL`/`JWT_SECRET`/`GATEWAY_API_KEYS`/`OPERATOR_TOKENS`, and align all `.env.example` templates.

### REL-004 (P1) — CORS Methods Missing `DELETE`/`PUT`/`PATCH` & CLI Role Mismatch
- **Location:** `cloud/backend/main.py:155`, `cloud/backend/app/cli.py:73`
- **Root Cause:** CORS `allow_methods` omits `DELETE`/`PUT`/`PATCH` required by `DELETE /api/v1/devices/{device_id}`; `cli.py` writes invalid RBAC role names (`super_admin` instead of `admin`).
- **Fix:** Allow standard REST methods in CORS (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`) and align `cli.py` choices to `Role` enum values (`admin`, `commander`, `dispatcher`, `responder`, `viewer`).

### REL-005 (P1) — Unstructured Sync Errors & Unvalidated Receipt Fallback
- **Location:** `app/src/main/java/com/meshlink/app/sync/GatewaySyncWorker.kt:252-275`, `app/src/main/java/com/meshlink/app/worker/CloudSyncWorker.kt:64-70`
- **Root Cause:** Fallback marks all packets synced if response parsing fails; `CloudSyncWorker` treats 401/403 same as transient error (`Result.retry()`).
- **Fix:** Classify `401`/`403` -> `Result.failure()`, `429`/`5xx`/socket/TLS -> `Result.retry()`, record explicit user-safe telemetry in DataStore/sync tracker, and only reconcile receipts explicitly confirmed in response JSON.

### UX-001 (P2) — No Android Backend Connectivity Settings & Sync Telemetry UI
- **Location:** `app/src/main/java/com/meshlink/app/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`
- **Fix:** Add a "Cloud Gateway Connectivity" section in `SettingsScreen` (URL input, masked Tink-encrypted gateway key input, `"Test connection"` calling `/health` + `/health/ready`, `"Sync now"` button, and live telemetry showing last sync timestamp, pending count, and last error).

### UX-002 (P2) — Fixed WebSocket Reconnect Interval Without Auth Stop or Backoff
- **Location:** `cloud/frontend/src/App.jsx:168-184`, `cloud/frontend/src/components/TopHUD.jsx:69-95`
- **Fix:** Implement bounded exponential backoff with jitter (`1s -> 2s -> 4s ... max 30s`), display explicit retry attempt/backoff status in `TopHUD`, and stop looping cleanly if token is invalidated (`401`/`403`).

### HYG-001 (P3) — Tracked/Root SQLite DB Clutter & Stale Operational Docs
- **Location:** `./meshlink_emergency.db`, `./meshlink_sync.db`, `./sos_mesh.db`, `README.md`, `STATE.md`, `CHANGELOG.md`, `docs/RUNBOOK.md`
- **Fix:** Remove stray root DB test artifacts if untracked/generated and document runtime DB placement cleanly; update operational runbook, changelog, README, and `STATE.md` after verification completes.
