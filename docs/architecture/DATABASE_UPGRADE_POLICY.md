# Room Database Migration & Supported Upgrade Policy

**Document ID:** `docs/architecture/DATABASE_UPGRADE_POLICY.md`  
**Status:** Approved / Active  
**Database Name:** `meshlink.db`  
**Current Target Version:** 14  
**Encryption:** SQLCipher 4.9.0 with Google Tink Keystore AEAD passphrase derivation  

---

## 1. Context & Historical Schemas Investigation

A forensic inspection of the Git commit history from repository creation (`c08e7e4`) confirmed:
1. **Authentic Schemas Present:**
   - Version 1 (`1.json`): Original messaging baseline.
   - Version 2 (`2.json`): Added `pending_messages` table for store-and-forward queueing.
   - Version 3 (`3.json`): Added `senderName` to `messages` for clear peer identification.
   - Version 13 (`13.json`): Introduced structured `sos_packets`, `location_events`, and location sync tables.
   - Version 14 (`14.json`): Added `isVerified` flag to `known_devices` for Short Authentication String (SAS) identity verification.
2. **Historical Schemas 4 through 12:**
   - Schemas 4..12 were intermediate development schema iterations prior to the v13 emergency refactoring. They were never committed to any branch or tag in this repository.
   - Per non-negotiable architectural rules, **synthetic or plausible schema files must never be fabricated**.

---

## 2. Supported Upgrade Policy

To guarantee 100% data safety across all device histories:

### Supported Migration Paths
1. **Tier 1 (Authentic Schema-Verified via `MigrationTestHelper`):**
   - Migration `1 -> 2`: Fully validated against `1.json` and `2.json`.
   - Migration `2 -> 3`: Fully validated against `2.json` and `3.json`.
   - Migration `1 -> 3` (chained): Fully validated against `1.json` and `3.json`.
   - Migration `13 -> 14`: Fully validated against `13.json` and `14.json`.
2. **Tier 2 (Continuous DDL Intermediate Migration Chain 3..14):**
   - All intermediate migration objects (`3_4`, `4_5`, `5_6`, `6_7`, `7_8`, `8_9`, `9_10`, `10_11`, `11_12`, `12_13`) are explicitly declared in `AppDatabase.kt` and registered in `DatabaseModule.kt`.
   - Sequential schema evolution and data preservation are verified via `testFullV1ToV14SequentialDataIntegrityMigration()`, confirming that:
     - Old messages, cryptographic keys, and device identities remain intact.
     - New columns have default values ensuring non-null constraints are satisfied.
     - New tables are created with proper indices and foreign-key cascades.

---

## 3. Failure Recovery & Data Protection Policy (ADR 0007 Enforcement)

1. **Zero Silent Data Annihilation in Production:**
   - In production builds (`BuildConfig.DEBUG == false`), `fallbackToDestructiveMigration()` is strictly **disabled**.
   - If an unhandled migration path or schema corruption occurs, Room fails closed by throwing an `IllegalStateException`.
   - The user's on-disk database file and encrypted keystore credentials remain intact, allowing a software update or patch to resolve the schema without wiping critical emergency communications.
2. **Automated Verification:**
   - `testFailureRecoveryPolicy_missingMigrationThrowsWithoutDataDestruction()` continuously verifies in Robolectric that a missing migration throws `IllegalStateException` while preserving the database file and records on disk.
