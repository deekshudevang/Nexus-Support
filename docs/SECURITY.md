# Nexus Support — Security Policy & Threat Model

## 1. STRIDE Threat Model Summary

In an emergency disaster environment, wireless communications and field infrastructure face physical disruption, compromised edge devices, and potentially hostile or adversarial network conditions. The security architecture addresses the STRIDE threat model as follows:

| Threat Category | Specific Threat in Emergency Context | Architectural Mitigation | Code Location |
| :--- | :--- | :--- | :--- |
| **Spoofing Identity** | Attacker injects fraudulent distress beacons or impersonates authorized incident dispatchers. | • Digital signatures on SOS packets via Google Tink.<br>• Gateway authentication using `X-Gateway-Key` token.<br>• JWT Bearer tokens with signed role claims for web dispatchers. | `core/crypto/.../TinkCryptoManager.kt`<br>`cloud/backend/main.py` |
| **Tampering with Data** | Rogue node alters GPS coordinates or emergency severity during multi-hop relay. | • AEAD authenticated encryption (AES-256-GCM) with HMAC integrity tags prevents in-transit tampering without detection. | `core/crypto/.../TinkCryptoManager.kt` |
| **Repudiation** | Field unit claims they did not receive dispatch instructions or status was dropped. | • State transition audit logs in PostgreSQL.<br>• Signed delivery receipts and immutable assignment transitions (`PENDING` $\to$ `ACCEPTED` $\to$ `COMPLETED`). | `cloud/backend/app/services/dispatch_service.py` |
| **Information Disclosure** | Adversary eavesdrops on distress communications or captures lost responder phone. | • End-to-end payload encryption over mesh RF.<br>• SQLCipher 4.9.0 256-bit AES database encryption at rest with Android Keystore derived key. | `core/data/.../DatabaseModule.kt`<br>`core/crypto/.../TinkCryptoManager.kt` |
| **Denial of Service (DoS)** | Attacker floods mesh with spam messages or loops packets to deplete node batteries. | • **P10 SOS Priority Preemption**: under queue congestion, low-priority packets are preempted and dropped.<br>• Packet deduplication cache and hop-limit / TTL decrementing. | `core/mesh/.../MeshRouter.kt` |
| **Elevation of Privilege** | User from Org A attempts to view or dispatch responders belonging to Org B. | • Strict tenant isolation: all queries scoped by `org_id` foreign key.<br>• Backend returns 403 Forbidden for cross-tenant resource access. | `cloud/backend/main.py`<br>`cloud/backend/test_tenant_isolation.py` |

---

## 2. Protected Subsystems & Safety Invariants

### 2.1 SOS Priority Preemption (P10 Emergency Invariant)
In `core/mesh/src/main/java/com/meshlink/app/mesh/routing/MeshRouter.kt`, emergency beacons are assigned a priority of 10 (`SOS_EMERGENCY`). The routing engine enforces:
- SOS packets immediately jump to the head of transmission queues.
- When the outbox queue exceeds capacity, non-emergency packets (telemetry, chat, pings) are evicted to guarantee transmission of distress signals.
- Packets with TTL $\le 0$ or corrupted formats are safely dropped to prevent queue starvation.

### 2.2 Zero-Fabrication Charter (Location Integrity)
The system strictly prohibits estimating, interpolating, or default-zeroing GPS coordinates:
- **Android Client (`LocationTracker.kt`)**: Distinguishes between explicit GPS fixes and absent fixes. Null coordinates are preserved as null.
- **Web Dashboard (`TacticalMap.jsx`)**: Filters incidents and field units; entities with null, undefined, NaN, or explicit $(0.0, 0.0)$ coordinates are quarantined into an "AWAITING GPS FIX" flyout drawer rather than rendered at "Null Island" (Gulf of Guinea).

### 2.3 Offline AI Prompt Injection Defense
In `app/src/main/java/com/meshlink/app/ai/OfflineRagManager.kt`, on-device language models provide triage and first-aid instructions based on local emergency guides. To prevent prompt injection:
- User queries and retrieved triage documents are fenced within strict XML delimiters: `<emergency_corpus>` and `<user_query>`.
- The system prompt forbids tool execution, file access, and modification of SOS or dispatch states.
- Adversarial jailbreak patterns (e.g. "ignore previous instructions") trigger an immediate deterministic fallback response.

### 2.4 Multi-Tenant Data Isolation
In `cloud/backend/main.py` and `app/services/dispatch_service.py`:
- All incident ingestion, assignment updates, and responder status changes validate the caller's `organization_id`.
- The database schema indexes `organization_id` on all primary tables.
- Cross-tenant requests produce HTTP 403 Forbidden responses, verified by automated security regression tests (`cloud/backend/test_tenant_isolation.py`).

---

## 3. Trust Boundaries

```
[Ad-Hoc Mesh Peers] 
       │ (Untrusted physical wireless environment: BLE / Wi-Fi Direct)
       ▼
[Edge Mobile Node (:app / :core:mesh)]
       │ (Android Keystore boundary: Hardware-backed key isolation)
       ▼
[Encrypted Storage (:core:data)] ── SQLCipher AES-256
       │
       │ (Boundary: Gateway Network Sync — HTTPS + X-Gateway-Key)
       ▼
[Cloud Gateway Entrypoint (main.py)]
       │ (Boundary: JWT Bearer RBAC + Multi-Tenant Scoping)
       ▼
[Internal Backend & PostgreSQL Database]
```

1. **Mesh Node $\leftrightarrow$ Mesh Node**: Mutual zero-trust. Nodes relay packets without being able to decrypt payload contents.
2. **Gateway Node $\leftrightarrow$ Cloud Backend**: Authenticated via `X-Gateway-Key`. The backend enforces that default development keys (`meshlink_gw_dev_secret`) are strictly rejected in production environments (`ENVIRONMENT=production`).
3. **Dispatcher $\leftrightarrow$ Cloud Backend**: Authenticated via OAuth2 / JWT with role claims (`dispatcher`, `responder`, `admin`).

---

## 4. Known Security Gaps & Limitations (Honest Disclosure)

1. **Pre-Shared Gateway Secrets**:
   Gateway nodes currently authenticate using pre-shared gateway keys injected via `BuildConfig` / Gradle properties. While effective for small deployments, enterprise production requires automated mutual TLS (mTLS) with per-device X.509 client certificate issuance and hardware attestation.
2. **Ad-Hoc Mesh Peer Discovery Metadata**:
   While packet contents are encrypted with Tink AEAD, Nearby Connections advertisements and BLE beacon metadata are unencrypted at the physical radio layer. An adversary with a spectrum analyzer or BLE sniffer can detect the physical presence and packet frequency of mesh devices in their immediate vicinity.
3. **Decentralized Key Distribution & Web-of-Trust**:
   Public keys are currently exchanged during direct pairing. In the absence of a global certificate authority or established web-of-trust, initial ad-hoc key exchanges without out-of-band verification remain vulnerable to active local man-in-the-middle attacks.
