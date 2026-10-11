# Nexus-Support Threat Model

## 1. System Overview & Assets
Nexus-Support is an offline-first emergency mesh communication and disaster-response Android application. It functions in zero-connectivity environments without requiring cell towers, internet access, or cloud infrastructure.

### Critical Assets:
1. **Device Identity & Cryptographic Keys:** Long-term Curve25519 / ECDH private keys and hardware-backed keystores.
2. **Session Ephemeral Keys:** Ephemeral symmetric AES-256-GCM session keys negotiated between peers.
3. **Emergency SOS Beacons:** Life-safety distress signals carrying timestamps, coordinates, and emergency metadata.
4. **User Communications:** Private 1:1 direct chat, multi-hop routed messages, and community broadcast messages.
5. **Local Emergency Knowledge & AI Weights:** On-device medical/disaster RAG database and LiteRT/Gemma weights.

---

## 2. Trust Boundaries & Network Topology
- **Trust Boundary 1: Local Device Environment:** The Android application sandbox, Room database, EncryptedSharedPreferences/KeyStore, and isolated AI coroutine scope.
- **Trust Boundary 2: Direct Peer Link (Hop 0):** BLE and Wi-Fi Direct physical RF links with adjacent peers. Untrusted until authenticated via ECDH handshake.
- **Trust Boundary 3: Multi-Hop Relays (Hops 1..N):** Intermediate nodes relaying packets across the mesh. Intermediate nodes are untrusted and must never be able to read plaintext routed messages or forge identities.
- **Trust Boundary 4: AI Context & Retrieved Data:** User queries and retrieved RAG passages entering the LLM inference engine. Untrusted text must never hijack system directives.

---

## 3. Threat Matrix & Abuse Cases

| Threat ID | Threat / Attack Vector | Severity | Mitigation / Security Control | Verification Status |
|:---|:---|:---:|:---|:---:|
| **T-01** | **Frame Size Exhaustion DoS**<br>Attacker sends multi-megabyte payloads over BLE/Wi-Fi to crash device with OOM. | High | Strict `MAX_PACKET_BYTES = 65,536` limit in `MeshPacketSerializer`. Packets exceeding 64KB are dropped immediately before allocation. | **VERIFIED** (`MeshPacketSerializerSecurityTest`) |
| **T-02** | **SOS ID Collision / Deduplication Loss**<br>Repeated SOS activations collapse to the same ID due to 16-bit time delta clamping. | Critical | 19-byte v1 SOS beacon encoding with high-entropy per-activation nonces and CRC-16 integrity verification over bytes [0-15]. | **VERIFIED** (`SosMessageIdTest`) |
| **T-03** | **Stale Peer State Hijacking**<br>Peers disappearing without disconnect callbacks remain marked `CONNECTED` or `AUTHENTICATED`. | High | Periodic liveness verification (`checkStalePeers` > 90s), pruning expired entries, and immediate teardown on transport failure. | **VERIFIED** (`NearbyRepositoryImplEndpointLostTest`) |
| **T-04** | **Unacknowledged SOS Drop**<br>SOS packets sent over mesh are dropped without confirmation, leaving victims unaware. | Critical | Store-and-forward persistent queueing in Room, peer-level priority ACK generation, and clear UI delivery state (`SosDeliveryState`). | **VERIFIED** (`MeshRouterTest`, `SosViewModelTest`) |
| **T-05** | **Multi-Hop Hop Count Amplification**<br>Packets looped endlessly between compromised relays. | Medium | Enforced `maxHops <= 32` and `MAX_ROUTE_HOPS <= 32` bounding in packet serialization and routing logic. | **VERIFIED** (`MeshPacketSerializerSecurityTest`) |
| **T-06** | **Adversarial Prompt Injection & Role Hijacking**<br>Malicious instructions embedded in mesh messages to override safety rules. | High | Regex-based instruction hierarchy filter (`AiSafetyGuard.isPromptInjection`), isolated `<context>` markers, and dual-use policy. | **VERIFIED** (`AiSafetyGuardTest`) |
| **T-07** | **AI Rescue Dispatch Hallucination**<br>Model claims it dispatched 911 or real-world emergency teams. | High | Post-generation output validation (`AiSafetyGuard.validateOutput`) that appends mandatory local-only safety advisories. | **VERIFIED** (`AiSafetyGuardTest`) |
| **T-08** | **Offensive Cyberattack Exploit Generation**<br>Attacker requests exploit scripts or ransomware payloads. | High | Dual-use classification in `AiSafetyGuard.isCyberAttackRequest`: permits defensive security practices while blocking exploit code. | **VERIFIED** (`AiSafetyGuardTest`) |

---

## 4. Cryptographic Specifications
1. **Direct Link Encryption:** ECDH (secp256r1 / NIST P-256) key agreement with AES-256-GCM. 12-byte random nonces generated per packet using `java.security.SecureRandom`.
2. **Multi-Hop Routed Messages:** ECIES-style envelope: Ephemeral public key + 12-byte nonce + AES-256-GCM ciphertext + 16-byte authentication tag. Intermediate relays only inspect envelope headers (`originId`, `finalDestId`, `hopCount`).
3. **Integrity & Checksums:** CRC-16/CCITT for compact 19-byte SOS beacons; AES-GCM 128-bit authentication tags for encrypted payloads.

---

## 5. Residual Risks & Next Actions
1. **Physical RF Jamming:** Intentional 2.4 GHz RF jamming cannot be prevented by software; requires dual-band failover (Wi-Fi 5GHz / BLE / Meshtastic LoRa).
2. **On-Device Keystore Portability:** Devices without StrongBox Keymaster fallback to software-backed Android Keystore; document hardware requirements in user guide.
