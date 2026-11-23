# Room Database Schema Compatibility & Migration Policy
**Nexus-Support / MeshLink Data Architecture Specification**
**Document Version:** 1.0.0 — November 2026

---

## 1. Executive Policy & Principles

1. **Strict Data Preservation:** Local SQLite/SQLCipher databases contain life-critical distress logs (`sos_packets`), encrypted messages, responder assignments, and peer node identity keys. Silent data erasure via destructive migration is strictly prohibited in production release builds.
2. **Explicit Sequential Migrations:** Schema evolution follows an unbroken sequence of registered `Migration` objects:
   - `MIGRATION_1_2`: Introduces `pending_messages` store-and-forward queue.
   - `MIGRATION_2_3`: Adds `senderName` display identity column to `messages`.
   - `MIGRATION_3_4`: Adds `routeHistory` relay diagnostic path column to `messages`.
   - `MIGRATION_4_5`: Adds `priority` to `pending_messages` and `status` to `messages`.
   - `MIGRATION_5_6`, `MIGRATION_6_7`, `MIGRATION_7_8`: Synchronize internal schema hashes.
   - `MIGRATION_8_9`: Adds `lastLatitude` and `lastLongitude` to `known_devices`.
   - `MIGRATION_9_10`: Introduces `location_events` and `location_sync_queue` tables.
   - `MIGRATION_10_11`: Introduces `processed_events` deduplication table.
   - `MIGRATION_11_12`: Adds `publicKey` verification column to `location_events`.
   - `MIGRATION_12_13`: Introduces structured `sos_packets` table.
   - `MIGRATION_13_14`: Adds `isVerified` flag to `known_devices` for SAS authentication.
   - `MIGRATION_14_15`: Introduces `assignments` table for downlink mission dispatching.
3. **No Destructive Reset in Production:** `fallbackToDestructiveMigration()` is never called in production release builds.
4. **Historical Prototype Builds:** Pre-alpha devices that ran internal development prototypes prior to schema v13 must be upgraded through the sequential migration path. If an unrecognized schema hash is detected on a development build, the application fails safe with an explicit user prompt rather than silently destroying records.
5. **Hardware-Backed Encryption:** All tables are encrypted on-device via SQLCipher 4.9.0 using an AES-256-GCM master passphrase secured in the Android KeyStore through Google Tink.
