# Security Policy & STRIDE Threat Model

MeshLink (Nexus Support) is designed as a secure, decentralized emergency communication and off-grid location mesh network. Because it operates in zero-trust, broadcast, and hostile peer environments, defense-in-depth security and cryptographic integrity are paramount.

---

## 🛡️ Supported Versions

| Version | Supported | Notes |
|---|---|---|
| `1.0.x` | :white_check_mark: | Production readiness release |
| `< 1.0` | :x: | Deprecated prototypes |

---

## 🔒 Cryptographic Architecture & Security Controls

### 1. Identity & Hardware-Backed Key Storage
- **Identity Keypair:** Node identity is anchored in an **EC P-256 (secp256r1)** keypair generated inside the **Android KeyStore**.
- **Hardware Enclave:** Uses `setIsStrongBoxBacked(true)` when running on supported hardware (Google Pixel, Samsung Knox, Qualcomm SPU) with automatic fallback to standard hardware TEE KeyStore.
- **Key Wrapping & Master Secrets:** Master passphrases for local database storage are wrapped using **Google Tink AEAD** (`Aead` AES-256-GCM) with KeyStore key encryption keys (`android-keystore://meshlink_master_key`).
- **No Hardcoded Secrets:** Zero API keys, private keys, keystores, or backend secrets are committed to the repository or packaged inside client binaries.

### 2. End-to-End Encryption (E2EE)
- **Direct 1-to-1 Messaging:** Encrypted using **AES-256-GCM** with unique 96-bit nonces.
- **Multi-Hop Routed Mesh:** Multi-hop routed messages use **ECIES** (Ephemeral-Static ECDH key agreement over NIST P-256 + HKDF SHA-256 + AES-256-GCM), guaranteeing intermediate mesh relays cannot inspect packet contents.
- **Short Authentication Strings (SAS):** Out-of-band numeric SAS verification prevents Man-in-the-Middle (MitM) attacks during initial peer pairing.

### 3. Database At-Rest Encryption & 16 KB Page Alignment
- **SQLCipher Integration:** Local Room database is encrypted with official maintained `net.zetetic:sqlcipher-android:4.9.0` utilizing 256-bit AES-CBC.
- **16 KB Page Alignment:** All native shared libraries (`libsqlcipher.so`) in release APKs and AAB bundles are verified 16 KB page-aligned (offset % 16384 == 0) for Android 15/16+ (targetSdk 35+).
- **Zero Cloud Leakage:** Cloud backups are strictly prohibited via `android:allowBackup="false"` and explicit `data_extraction_rules.xml`.

### 4. Cloud Backend & Dashboard Access Control
- **Role Separation:**
  - **Gateway Nodes (Write-Only):** Ingest node telemetry and emergency SOS packets via `X-Gateway-Key` token header verified with constant-time `secrets.compare_digest`. Gateway credentials cannot access read or admin endpoints.
  - **Operators (Read-Only/Admin):** Authenticate with `Authorization: Bearer <jwt_or_operator_token>` to inspect incidents and manage alerts.
- **Authenticated WebSocket Alerts (`/ws/alerts`):**
  - Rejects unauthenticated connections, wrong-origin requests, and connections exceeding capacity (cap of 50 active streams).
  - Authenticates either via short-lived single-use ticket handshake or strict time-bounded initial auth message (< 5.0 seconds).
  - Emits zero alert traffic prior to successful authentication.
  - Requires WSS/TLS in production deployments.
- **Zero Client Bundle Secrets:** Dashboard environment variables are limited to public endpoint URLs (`VITE_API_URL`, `VITE_WS_URL`). Secrets are never embedded in frontend code.

---

## 🎯 STRIDE Threat Model Analysis

| Threat Category | Potential Attack Vector | MeshLink Mitigation Strategy |
|---|---|---|
| **Spoofing** | Adversary impersonates an existing node or fabricates emergency SOS alerts | Every beacon and packet is bound to a cryptographic identity and signed via `ECDSA(SHA-256)`. Nonces and timestamp deltas prevent signature reuse. |
| **Tampering** | Rogue relay modifies coordinates or text in a routed emergency message | Authenticated encryption (AES-256-GCM) with 128-bit authentication tags ensures any bit flip causes instant payload rejection. |
| **Repudiation** | Node denies having broadcasted a specific emergency beacon | Digital signatures over the immutable event UUID and timestamp provide cryptographic non-repudiation. |
| **Information Disclosure** | Passive eavesdropper sniffs Wi-Fi / BLE radio traffic | All direct and multi-hop payloads are E2EE encrypted with ECIES. Database at rest is encrypted with SQLCipher. PII scrubbing active in opt-in telemetry. |
| **Denial of Service (DoS)** | Malicious peer floods mesh with replayed beacons or sinkhole traffic | `SeenMessageCache` deduplicates packets via SHA-256 bloom filter + LRU window. Adaptive scan backoff and rate-limiting reject flooding. Emergency packets preempt non-urgent AI workloads. |
| **Elevation of Privilege** | Gateway node attempts to read emergency communications or operator views | Strict role separation in FastAPI backend: gateway API keys grant write-only ingestion; operator JWT tokens grant read views. |

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in MeshLink, please report it responsibly:

1. **Do NOT open a public GitHub issue.**
2. Email vulnerability details to **`security@meshlink.app`** or open a private GitHub Security Advisory.
3. Include:
   - Vulnerability description and impact assessment
   - Reproducible proof of concept (PoC) or test script
   - Affected components (`app`, `core:crypto`, `cloud/backend`, etc.)
4. We will acknowledge receipt within 48 hours and coordinate a coordinated disclosure schedule.
