# Policy: Offline Identity Enrollment & Mesh Key Derivation

> [!CAUTION]
> **HUMAN APPROVAL GATE REQUIRED**
> This policy defines identity issuance, peer authorization, and credential caching in off-grid environments. Implementation and activation are strictly blocked until the relevant Architecture Decision Record (ADR) receives explicit human sign-off.

## 1. Objective
Establish verified cryptographic identities and mesh access credentials when devices cannot reach a central certification authority (CA) or backend server.

## 2. Proposed Architecture (Draft)
- **Pre-Provisioned Authority:** Devices receive a root public certificate during initial setup / staging.
- **Signed Enrollment Tickets:** Authorized local coordinators can sign short-lived offline enrollment tickets (ECDSA P-256).
- **Locally Verifiable Trust Chains:** Relays verify the signature of the issuing coordinator before routing traffic.
- **Expiration & Revocation Lists:** Offline nodes cache a compact Bloom filter / cryptographic delta of revoked keys broadcasted via gateway nodes.
- **Meshtastic Channel Alignment:** Reconcile application SAS verification with Meshtastic channel encryption keys (PSK/AES-256).

## 3. Human Approval Checklist
- [ ] Legal and compliance review of decentralized credential issuance.
- [ ] Threat model review for rogue coordinator injection.
- [ ] Explicit ADR approval.

