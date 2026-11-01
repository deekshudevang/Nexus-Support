# Evidence: Phase PG-P2 — Security & Role-Based Access Control (RBAC)

## Overview
Phase PG-P2 establishes the capability-based security, access control, and device cryptographic authentication model for Nexus Support / RescueMesh. It enforces authorization on privileged operations, eliminates hardcoded tokens, provides instant session revocation, and binds field mesh devices to their P-256 Elliptic Curve identities.

## Verification Evidence

### 1. Capability-Based Permission Matrix
- **File**: `cloud/backend/app/core/rbac.py`
- **Role Hierarchy & Capabilities**:
  - `ADMIN`: Full administrative control (`user:manage`, `audit:read`, incident lifecycle, dispatch, device management).
  - `COMMANDER`: Full operational control over incidents, assignments, teams, responders, and audit trail.
  - `DISPATCHER`: Operational dispatching, triage, incident creation, responder tracking, telemetry ingestion.
  - `RESPONDER`: Field responder access (view assigned incidents, accept/update own assignment status, submit telemetry).
  - `VIEWER`: Read-only situational awareness (view incidents, responders, active teams).
  - `DEVICE`: Mesh node operations (`device:sos`, `device:telemetry`, `device:enroll`).
- **Dependencies**: `get_current_actor`, `require_permission(permission)`.

### 2. Session Management & Instant Invalidation
- **Signed Tokens**: HS256 tamper-evident tokens carrying `sub`, `username`, `role`, `ver`, `iat`, `exp` without external dependencies.
- **Revocation Kill-Switch**: Bumping `UserModel.token_version` (triggered on `/api/v1/auth/logout` or security reset) immediately invalidates all active sessions issued under prior versions.
- **Backward Compatibility**: Seamless support for legacy bootstrap tokens (`OPERATOR_TOKENS`) mapped to Commander operational capabilities.

### 3. Device Keystore & ECDSA Request Authentication
- **File**: `cloud/backend/app/core/device_auth.py`
- **Cryptographic Standard**: NIST P-256 (SECP256R1) with SHA-256 ECDSA, directly compatible with Android's `ProductionKeyManager.kt` / `UnifiedIdentityKeyStore.kt`.
- **Request Headers**:
  - `X-Device-Id`: Enrolled hardware/mesh node identity.
  - `X-Device-Timestamp`: Epoch seconds timestamp.
  - `X-Device-Signature`: Base64-encoded ASN.1 DER or IEEE P1363 (64-byte r||s) ECDSA signature.
- **Replay Protection**: Strict $\pm 300\text{s}$ timestamp sliding window (`abs(now - ts) <= 300`).
- **Canonical Signature Message**:
  $$\text{SHA-256}\left(\text{METHOD} : \text{PATH} : \text{TIMESTAMP} : \text{SHA-256}(\text{BODY})\right)$$

### 4. IDOR Protection
- Field nodes authenticated via ECDSA signatures can only submit telemetry for their own registered `device_id` (`X-Device-Id == path.device_id`). Attempts to cross-submit telemetry for another node result in HTTP 403 Forbidden.
- Viewers are barred from dispatching, triaging, or modifying responders/devices.

### 5. Automated Test Pass
- Test command:
  ```powershell
  python -m pytest test_backend.py test_legacy_contract.py test_pg_p1_entities.py test_pg_p2_rbac.py
  ```
- Result: **47 passed** in 4.66s.
  - `test_pg_p2_rbac.py::test_token_lifecycle_and_tamper_detection`: PASSED
  - `test_pg_p2_rbac.py::test_user_login_and_me_endpoint`: PASSED
  - `test_pg_p2_rbac.py::test_logout_and_session_invalidation`: PASSED
  - `test_pg_p2_rbac.py::test_negative_authorization_and_rbac`: PASSED
  - `test_pg_p2_rbac.py::test_device_enrollment_and_ecdsa_signature_auth`: PASSED
  - `test_pg_p2_rbac.py::test_device_idor_protection`: PASSED
  - `test_pg_p2_rbac.py::test_revoked_device_cannot_authenticate`: PASSED
