# Security Policy

Nexus Support is designed as an emergency, off-grid communication and location mesh network. Because it operates in zero-trust, broadcast, and hostile peer environments, security and cryptographic integrity are paramount.

---

## 🛡️ Supported Versions

| Version | Supported | Notes |
|---|---|---|
| `1.0.x` | :white_check_mark: | Active security maintenance |
| `< 1.0` | :x: | Deprecated prototypes |

---

## 🔒 Security & Threat Model

### 1. Identity & Hardware-Backed Key Storage
- Node identity is defined by an **EC P-256 (secp256r1)** key pair generated inside the **Android KeyStore**.
- Uses `setIsStrongBoxBacked(true)` when running on supported hardware (Google Pixel 3+, Samsung Knox, modern Snapdragon chips) with automatic fallback to standard hardware TEE KeyStore.
- Private keys never leave the secure enclave.

### 2. End-to-End Encryption (E2EE)
- Direct 1-to-1 packets are encrypted using **AES-256-GCM** with unique 96-bit nonces.
- Multi-hop routed messages use **ECIES** (Ephemeral-Static ECDH key agreement over NIST P-256 + HKDF SHA-256 + AES-256-GCM), ensuring intermediate routing nodes cannot inspect payload contents.

### 3. Replay & Injection Defense
- Every location and status broadcast is signed:
  $$\text{Signature} = \text{ECDSA}_{\text{priv}}(\text{SHA-256}(\text{eventId} \parallel \text{peerId} \parallel \text{lat} \parallel \text{lon} \parallel \text{accuracy} \parallel \text{timestamp} \parallel \text{seq}))$$
- Signatures are bound to unique `eventId` UUIDs.
- Stale packets ($>24\text{ hours}$ old) and timestamp spoofing ($>60\text{ seconds}$ in the future) are rejected immediately at the protocol layer.

### 4. Routing Attack Hardening
- **Sinkhole / Sybil Flood Protection**: Per-peer heartbeat rate limit allows max 1 heartbeat per 5 seconds.
- **Split-Horizon Forwarding**: Inbound packets on a link are never echoed back to the same link.
- **Deduplication**: Cryptographic hash sliding window (`SeenMessageCache`) prevents broadcast storms.

### 5. At-Rest Encryption
- Room database is encrypted with **SQLCipher AES-256-CBC** using a key derived from Android KeyStore.
- Cloud device backups are disabled (`allowBackup="false"`).

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in Nexus Support, please report it responsibly:

1. **Do NOT open a public GitHub issue.**
2. Email the vulnerability details to **`security@meshlink.app`** (or open a private GitHub Security Advisory).
3. Include:
   - Description of the vulnerability
   - Proof of Concept (PoC) or reproduction steps
   - Potential impact on offline mesh nodes
   - Suggested mitigation if available

We will acknowledge receipt within 48 hours and work with you on a patch and responsible disclosure timeline.
