# 2. TOFU Pinning vs PKI

Date: 2024-04-05

## Status
Accepted

## Context
To prevent Man-in-the-Middle (MITM) attacks during initial handshakes or subsequent connections, we need a way to verify device identities. In a traditional system, we would rely on a Public Key Infrastructure (PKI) with a central Certificate Authority (CA) to sign device certificates. However, Meshlink operates entirely off-grid where a central CA cannot be reached.

## Decision
We rely on Trust On First Use (TOFU) pinning combined with an out-of-band Short Authentication String (SAS).

On the first handshake with a peer, we blindly accept their public key and pin it to their device ID in the local database. On all subsequent connections, if the presented public key does not match the pinned key, the connection is rejected.

To secure the *first* connection, we generate a 6-digit SAS derived from the peer's public key (via SHA-256) that users can verify out-of-band (e.g., visually inspecting screens or speaking the numbers).

## Consequences
**Pros:**
* **Fully Decentralized:** Zero reliance on an internet-reachable CA.
* **Frictionless:** Repeat connections are seamlessly authenticated without user intervention.

**Cons:**
* **First-Contact Vulnerability:** If the user ignores the SAS verification on first contact, a MITM attacker could intercept the initial pairing. 
* **Key Rotation:** If a user loses their device/key, there is no built-in revocation mechanism. They must be manually purged from the local contact list.
