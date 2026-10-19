# Privacy Policy

MeshLink (Nexus Support) is built for off-grid emergency response, humanitarian missions, and decentralized community communication. Because users rely on MeshLink during critical disaster scenarios and communications blackouts, user privacy and data sovereignty are fundamental design requirements.

---

## 1. Core Principle: Zero Telemetry by Default

- **No Tracking:** MeshLink contains zero tracking pixels, third-party analytics trackers, or advertising SDKs.
- **Offline-First:** All primary features—messaging, map rendering, peer discovery, and emergency guidance—operate without requiring internet access or central user accounts.
- **Data Minimization:** No user accounts, phone numbers, email addresses, or personal identity documents are collected.

---

## 2. On-Device Storage & Encryption

- **Local Storage Only:** Message archives, cached emergency guides, peer lists, and location history are stored strictly on the local device.
- **At-Rest Encryption:** Local SQLite databases are encrypted at rest with **SQLCipher 4.9.0** (AES-256-CBC).
- **Key Security:** Database encryption passphrases are generated via cryptographically secure random sources, encrypted using **Google Tink AEAD (AES-256-GCM)**, and bound to the hardware-backed **Android KeyStore** (with StrongBox Keymaster support where available).
- **Backup Exclusion:** Cloud backups are strictly disabled (`android:allowBackup="false"`), and sensitive database/keystore files are explicitly excluded from device migration transfers via `data_extraction_rules.xml` and `backup_rules.xml`.

---

## 3. On-Device AI & Emergency Guidance

- **100% Local Inference:** Offline AI emergency guidance and RAG retrieval execute entirely on the device CPU/GPU/NPU using Google MediaPipe GenAI and local model weights (e.g. Gemma 3 1B INT4).
- **Zero Cloud Round-Trips:** Prompts, health queries, triage assessments, and emergency guides are processed locally and never transmitted across the internet.

---

## 4. Peer-to-Peer & Mesh Transports

- **Transport Mediums:** Packet transmission occurs over local radio interfaces: Bluetooth Low Energy (BLE), Wi-Fi Direct / Google Nearby Connections, and external LoRa radios (Meshtastic).
- **End-to-End Encryption (E2EE):** Direct communications are end-to-end encrypted using ephemeral-static ECDH (secp256r1) key agreement, HKDF SHA-256 derivation, and AES-256-GCM authenticated encryption.
- **Relay Privacy:** Intermediate mesh relay nodes process only envelope routing headers (packet ID, hop count, TTL). Relay nodes cannot decrypt or inspect message bodies or private payloads.

---

## 5. Crash & Error Reporting (Strictly Opt-In)

- **Default State:** Disabled. MeshLink will never send crash or diagnostic reports without explicit, affirmative user action.
- **User Control:** Users may enable anonymous diagnostic reporting under `Settings -> Privacy & Diagnostics`.
- **Context Sanitization:** When opt-in reporting is active, all error context is scrubbed before processing:
  - GPS coordinates and latitude/longitude values are redacted (`[REDACTED_COORDS]`).
  - Message contents, payloads, and communication transcripts are stripped.
  - Cryptographic keys, secrets, tokens, and passphrases are permanently excluded.
- **Scaffolding Only:** In standard distribution, error reporting is local scaffolding with no third-party collector keys bundled in the release APK/AAB.

---

## 6. Optional Cloud Sync

- **Gateway Sync:** When an operator device with cellular/satellite uplink is configured as an emergency gateway, it may forward emergency SOS alerts to a central dashboard (`POST /api/sync/sos`).
- **Emergency Data Scope:** Forwarded SOS signals contain only designated emergency telemetry (sender node ID, timestamp, coordinates, severity, and emergency type) for coordinator dispatch. Regular private peer-to-peer chats are never synchronized to cloud endpoints.

---

## 7. Contact

For privacy inquiries or technical verification details:
- **Email:** `privacy@meshlink.app`
- **Security & Vulnerability Reports:** `security@meshlink.app`
