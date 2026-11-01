# M0 Baseline System Audit Evidence

**Audit Date:** 2026-11-01  
**Auditor:** Principal Software Architect / Antigravity Agent  
**Branch:** `m0-baseline-audit`  
**Git Commit Reference:** `a1c92c5`  

---

## 1. Automated Test Verifications

### 1.1 Android Module Tests
- **`:core:data:testDebugUnitTest`**: **PASSED**
  - Room migration 1 -> 2, 2 -> 3, 3 -> 13, 13 -> 14 passed.
  - SQLCipher encryption with Google Tink AEAD key management verified.
  - Schema configuration verified at `$projectDir/schemas`.
- **`:core:crypto:testDebugUnitTest`**: **PASSED**
  - SAS (Short Authentication String) identity verification verified.
  - StrongBox key generation with TEE fallback verified.
- **`:core:mesh:testDebugUnitTest`**: **PASSED**
  - Google Nearby Connections protocol verified.
  - BLE SOS beacon advertising and scanning verified.
  - Meshtastic transport packet serialization verified.

### 1.2 Cloud Backend Tests
- **`pytest test_backend.py` (Python 3.14)**: **28 PASSED / 0 FAILED**
  - `test_production_configuration_accepts_explicit_secure_values`: PASSED
  - `test_production_configuration_rejects_unsafe_values` (6 cases): PASSED
  - `test_development_configuration_keeps_local_defaults_available`: PASSED
  - `test_health_endpoint`: PASSED
  - `test_sync_sos_unauthenticated`: PASSED
  - `test_sync_sos_role_separation_operator_cannot_write`: PASSED
  - `test_sync_sos_revoked_credentials_rejected`: PASSED
  - `test_sync_sos_validation_constraints`: PASSED
  - `test_sync_sos_success`: PASSED
  - `test_get_sos_unauthenticated`: PASSED
  - `test_get_sos_role_separation_gateway_cannot_read`: PASSED
  - `test_get_sos_revoked_operator_rejected`: PASSED
  - `test_get_sos_success_and_filtering`: PASSED
  - `test_sync_and_get_nodes`: PASSED
  - `test_ws_ticket_issuance_and_consumption`: PASSED
  - `test_ws_bad_origin_rejected`: PASSED
  - `test_ws_expired_ticket_rejected`: PASSED
  - `test_ws_initial_auth_message_success`: PASSED
  - `test_ws_realtime_broadcast_delivery`: PASSED
  - `test_ws_initial_auth_message_invalid_token_rejected`: PASSED
  - `test_ws_no_alerts_before_authentication`: PASSED
  - `test_production_non_tls_ws_rejected`: PASSED
  - `test_operator_token_login`: PASSED

### 1.3 Cloud Frontend Tests
- **`vitest run` (Node.js 22)**: **6 PASSED / 0 FAILED**
  - `renders tactical command dashboard header`: PASSED
  - `allows switching between tactical views`: PASSED
  - `renders live incidents on the map view`: PASSED
  - `filters incidents by status and type`: PASSED
  - `displays honest GPS status without fake coordinates`: PASSED
  - `renders audio alert mute control`: PASSED

---

## 2. Identified Engineering Deficiencies (Audit Baseline)

1. **Gateway Sync (`GatewaySyncWorker.kt`)**:
   - Hardcoded emulator URL `http://10.0.2.2:8000/api/v1/sync/sos` breaks production field nodes.
   - Missing gateway authentication header `X-Gateway-Key`.
   - Lacks transactional receipt reconciliation against local Room database.
2. **Meshtastic GATT Transport (`MeshtasticTransport.kt`)**:
   - Asynchronous GATT write reports success before callback confirmation.
   - Node identifiers generated via `String.hashCode()`, creating 32-bit hash collision risk.
3. **Backend Schema Nullable GPS**:
   - Pydantic schema rejects SOS payloads without GPS fixes.
4. **Backend Architecture**:
   - Flat monolithic file (`main.py`) using SQLite; lacks PostgreSQL/PostGIS, Alembic migrations, and transactional outbox.
5. **Human Approval Gates**:
   - Offline enrollment and medical break-glass policies require documented human signoff before code implementation.

---

## 3. Audit Verdict
Baseline reality is aligned with repository state. Proceed to Milestone M1 (Contracts & Architecture) and immediate critical defect fixes (E1, E3, E4).
