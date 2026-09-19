# Cryptographic Security Specification

Nexus Support is architected with a zero-trust threat model. In an offline disaster scenario, malicious or compromised peers may participate in the mesh. This document describes the cryptographic primitives and guarantees protecting node communications.

---

## 🔑 Key Management & Hardware Security

### Node Identity KeyPair
- **Algorithm**: Elliptic Curve Diffie-Hellman / Digital Signature Algorithm (ECDH/ECDSA) over NIST curve **P-256 (secp256r1)**.
- **Enclave**: Generated via `KeyGenParameterSpec` inside the **Android KeyStore provider**.
- **StrongBox Backing**:
  ```kotlin
  if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
      builder.setIsStrongBoxBacked(true)
  }
  ```
  On devices with dedicated tamper-resistant hardware security modules (HSM) such as Pixel Titan M or Samsung Knox, keys are isolated from the main application processor.
- **Exportability**: Private keys are marked non-exportable (`PURPOSE_SIGN`, `PURPOSE_DECRYPT`).

---

## 🔒 Encryption Schemes

### 1. 1-to-1 Direct Messaging (Session Keys)
- During initial handshake, peers perform ECDH key exchange over their P-256 public keys.
- Derive a 256-bit symmetric session key using **HKDF-SHA-256**.
- Payloads are encrypted with **AES-256-GCM** with a fresh 12-byte initialization vector (IV / Nonce) and 128-bit authentication tag.

### 2. Multi-Hop Encrypted Routing (ECIES)
- For multi-hop routed messages where the intermediate nodes must relay without reading:
  1. Originating node generates an ephemeral keypair $(r, R = r \cdot G)$.
  2. Computes shared secret $S = r \cdot K_{\text{dest}}$ with destination public key.
  3. Derives encryption key $K_{\text{enc}} = \text{HKDF-SHA-256}(S)$.
  4. Encrypts payload via AES-256-GCM.
  5. Attaches $R$ (ephemeral public key) to packet header.
  6. Only the destination node with private key $k_{\text{dest}}$ can compute $S = k_{\text{dest}} \cdot R$.

---

## 🛡️ Tamper Proofing & Replay Protection

### 1. Payload-Bound Digital Signatures
To prevent man-in-the-middle payload alterations or signature theft, packet signatures are bound to the `eventId`:

$$\text{PayloadToSign} = \text{eventId} \parallel \text{sourceNodeId} \parallel \text{lat} \parallel \text{lon} \parallel \text{accuracy} \parallel \text{timestamp} \parallel \text{sequenceNumber}$$

Verified with `Signature.getInstance("SHA256withECDSA")`.

### 2. Temporal Window Enforcement
To mitigate packet replaying from prior days:
- Packet timestamps must satisfy:
  $$T_{\text{local}} - 24\,\text{hours} \le T_{\text{packet}} \le T_{\text{local}} + 60\,\text{seconds}$$
- Packets violating this threshold are dropped prior to database insertion or relay.

---

## 🗄️ Storage Security

- **Database**: Encrypted at rest via **SQLCipher 4.6.0** (256-bit AES in CBC mode with HMAC-SHA512 per page validation).
- **Passphrase**: Derived from a 256-bit AES master key stored in the hardware-backed Android KeyStore.
- **Backup Prevention**: Configured with `android:allowBackup="false"` in `AndroidManifest.xml` and restricted via `data_extraction_rules.xml`.
