# Policy: Cryptographic Compatibility & Key Continuity

## 1. Baseline Cryptographic Standards
- **Key Generation:** Hardware-backed Android KeyStore via StrongBox Keymaster with automatic, verified fallback to Trusted Execution Environment (TEE).
- **Asymmetric Primitives:** NIST P-256 (secp256r1) for ECDSA signatures and ECDH key agreement.
- **Symmetric Encryption:** AES-256-GCM / Google Tink AEAD for local SQLCipher and payload encryption.
- **Short Authentication String (SAS):** Multi-factor visual/verbal SAS numeric verification protocol for peer-to-peer pairing.

## 2. Compatibility Rules
1. **No Silent Algorithm Downgrades:** Never replace P-256 or hardware KeyStore with software-only primitives.
2. **Ed25519 / X25519 Staged Evaluation:** Any potential transition to Curve25519 requires an approved ADR demonstrating compatibility across all supported Android API levels (API 26+) and radio hardware.
3. **Meshtastic Trust Separation:** Application cryptographic signatures operate as an end-to-end payload layer on top of Meshtastic link-layer channel keys.

