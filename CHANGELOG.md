# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Unified Connectivity & Runtime Provisioning:**
  - Android `SettingsScreen` / `SettingsViewModel` "Cloud Gateway Connectivity" card with DataStore `backendBaseUrl`, Tink-encrypted `gatewayKey`, live `/health` + `/health/ready` `"Test Connection"`, `"Sync Now"`, and truthful sync telemetry.
  - Room schema v15 -> v16 (`MIGRATION_15_16`) adding `cloudSynced` to `sos_packets` so peer mesh ACKs never suppress gateway cloud upload.
  - Periodic + immediate SOS `GatewaySyncWorker` scheduling from `MeshLinkApp` and `SosViewModel`.
  - Root `.env.example`, strict `.env.production.example`, `docker-compose.prod.yml` (Caddy TLS proxy + internal DB/Redis), and `cloud/Caddyfile`.
  - Release keystore `signingConfigs.release` in `app/build.gradle.kts` (`MESHLINK_KEYSTORE_*` / `meshlink.keystore.*`) and fail-closed `unconfigured.meshlink.invalid` prod default in `core/data/build.gradle.kts`.
  - Responsive mobile workspace switcher (`QUEUE` / `RADAR MAP` / `ASSETS`) on the web command center (`cloud/frontend/src/App.jsx`).
  - Evidence logs (`docs/evidence/docker_bringup_2026-11-28.txt`, `pg_backup_restore_drill_2026-11-28.txt`, `device_emulator_test_2026-11-28.txt`, `PHONE_TEST_CHECKLIST.md`, `playwright/`) and expanded [docs/runbook.md](docs/runbook.md).

### Fixed
- **Real Hardware / PostGIS Sync Bugs Fixed Under Verification:**
  - Alembic migration `8a2c4f91d3b0` widens epoch timestamp columns (`sos_packets.timestamp`, `node_status.timestamp`, `incidents.created_at/updated_at/resolved_at`, `responders.last_location_at/created_at`) to `BigInteger` (`with_variant(Integer, "sqlite")`), fixing PostgreSQL 32-bit `NumericValueOutOfRange` overflows on 13-digit Android `System.currentTimeMillis()` payloads.
  - `cloud/backend/main.py`: relaxed `SosPacket.senderName` `min_length=1` (with `senderId` fallback) and normalized `GatewaySyncWorker` so blank display names on mobile SOS beacons no longer trigger HTTP 422 rejections.
  - `GatewaySyncWorker` / `SettingsViewModel` / `AssignmentsViewModel`: added `KEY_PULL_DOWNLINKS` so manual sync, assignment updates, and periodic sync fetch server-to-device dispatch downlinks even when 0 SOS uploads are pending.
  - `TacticalMap.jsx`: guarded `MapFlyController` against zero-sized hidden mobile containers (`ResizeObserver` + `getSize()` check), fixing Leaflet `Invalid LatLng object: (NaN, NaN)` crashes on mobile viewport toggle.
  - `_resolve_database_url()` in `cloud/backend/app/core/config.py`: normalized bare `postgresql://` URLs to `postgresql+psycopg2://` for SQLAlchemy 2.1.
- Unified Retrofit + `GatewaySyncWorker` over OkHttp with explicit timeouts, relative paths, and dynamic interceptor URL/key resolution; removed silent `Provider { null }` fallback and raw `HttpURLConnection` split.
- Split `network_security_config.xml` into HTTPS-only `src/main` (release/staging) and cleartext-capable `src/debug` for emulator, LAN, and Tailscale testing.
- Fixed Web Command Center Tailwind CSS v4 pipeline (`@tailwindcss/vite`), enforced required `VITE_API_URL` / `VITE_WS_URL` in production `vite build`, fixed `/api/v1/responders`, `/api/v1/teams`, and `/api/v1/incidents/{id}/assignments` API parity, stored returned bearer token instead of raw password on fallback login, ingested live `sos_alert` WebSocket pushes, and added bounded exponential WS backoff.
- Consolidated backend config into `cloud/backend/app/core/config.py`, moved `JWT_SECRET` out of ephemeral generation into env-backed config, added `DELETE`/`PUT`/`PATCH` to CORS methods, blocked unauthenticated key rotation on existing enrolled devices, and aligned `cli.py` roles with `rbac.py`.
- Removed duplicate `cloud/docker-compose.yml`, keeping root `docker-compose.yml` with frontend build-arg wiring.
- SAS (Short Authentication String) UI badge in the Chat Screen to support out-of-band TOFU verification.
- Room `MigrationTest` coverage for schema upgrades from version 5 through 13.
- Hardware-backed Android KeyStore (StrongBox/TEE) implementation for device identity keys (`ProductionKeyManager`).

### Fixed
- Fatal Room migration bug (destructive fallback) when upgrading from v6/v7 to v8+.
- Man-in-the-Middle vulnerability during initial handshake by enforcing TOFU (Trust On First Use) pinning of the peer's public key.
- Removed accidentally committed `node_modules` from the frontend web interface to fix repository hygiene.

### Changed
- Refactored `KeyManager` into a `KeyProvider` interface to decouple hardware cryptography from JVM testing.
- Clarified UI state by marking the Offline AI Assistant feature as Experimental.
- **Phase 0:** Unified identity keys (deleted `CryptoManager`) for both ECDH and ECDSA.
- **Phase 0:** Added CodeQL suppressions for false-positive credential alerts.
- **Phase 0:** Documented strict additive-only protobuf versioning rules.
- **Phase 1:** Cleaned up `.gitignore` and extended PR template with architecture/security mandates.

---
**Last verified:** 2026-09-20 @ HEAD
