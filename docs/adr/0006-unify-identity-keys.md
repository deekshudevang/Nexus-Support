# 6. Unify Identity Keys for ECDH and ECDSA

Date: 2026-09-20

## Status
Accepted

## Context
Previously, the repository maintained two separate hardware identity keys:
1. `ProductionKeyManager` using alias `meshlink_identity_ec_p256` for ECDH (Key Agreement) and generating the `deviceId`.
2. `CryptoManager` using alias `meshlink_identity_key` for ECDSA (Signature generation and verification, used by `LocationSyncManager`).

Maintaining two independent keys undermines our trust model (TOFU Pinning). If an attacker compromises the initial exchange, or if the device rotates one key but not the other, peers cannot reliably cryptographically verify that the location events and the encrypted messages are originating from the exact same hardware enclave.

## Decision
We unify the identity trust root by deleting `CryptoManager` and moving `sign()` and `verify()` logic into the `KeyProvider` interface, backed by `ProductionKeyManager`. 

A single EC P-256 key pair, generated in AndroidKeyStore (with StrongBox enclave isolation where available), now handles both:
* `PURPOSE_AGREE_KEY` (ECIES ECDH session negotiation)
* `PURPOSE_SIGN` / `PURPOSE_VERIFY` (ECDSA for location events and packets)

## Consequences
**Pros:**
* **Single Root of Trust:** The `deviceId` derived from the public key definitively authenticates both the E2E encryption sessions and the broadcasted data.
* **Simplified Hardware Interactions:** Reduces the risk of `StrongBoxUnavailableException` edge cases by initializing the Keystore only once during startup.
* **Cleaner Architecture:** Removes circular dependency risks and redundant KeyStore boilerplate.

**Cons:**
* **Key Usage Conflict (Theoretical):** Some strict HSM configurations prefer physically separate keys for signing vs encryption (to prevent cross-protocol attacks). However, standard EC P-256 keys on Android KeyStore safely support `PURPOSE_AGREE_KEY or PURPOSE_SIGN`. Given our constraints, binding both to the same `deviceId` provides a superior defense against MITM.
