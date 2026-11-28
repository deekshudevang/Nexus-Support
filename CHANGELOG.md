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
  - Root `.env.example` and expanded `cloud/backend/.env.example` / `cloud/frontend/.env.example` templates.
  - Audit report (`docs/audit/CONNECTIVITY_AUDIT_2026-11-28.md`) and live integration/negative test evidence (`docs/evidence/live_integration_2026-11-28.txt`).

### Fixed
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
