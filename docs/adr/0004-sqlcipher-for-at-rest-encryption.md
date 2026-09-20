# 4. SQLCipher for At-Rest Encryption

Date: 2024-06-02

## Status
Accepted

## Context
Meshlink stores highly sensitive data: location trajectories, private E2E messages, and contact lists. If a device is lost or seized in the field, this data must remain inaccessible even if the device's storage is dumped or the lock screen is bypassed (which is increasingly common on commodity Android devices with unpatched bootloaders).

## Decision
We encrypt the entire Room SQLite database at rest using SQLCipher (AES-256-CBC). The database passphrase is a 256-bit key derived and secured inside the Android KeyStore (`EncryptedSharedPreferences` master key).

## Consequences
**Pros:**
* **Transparent Encryption:** The entire database, including metadata and indices, is encrypted seamlessly without requiring manual column-level encryption in the DAOs.
* **Defense in Depth:** Even if the Android OS is compromised or storage is extracted via physical forensics, the database remains useless without the KeyStore-backed master key (which relies on the TEE/StrongBox).

**Cons:**
* **Performance Penalty:** Page-level encryption/decryption introduces a measurable overhead on read/write operations. 
* **Binary Size:** SQLCipher introduces native C++ libraries (libsqlite.so) across multiple ABIs (arm64, x86), inflating the APK size.
* **Tooling:** Developers cannot simply pull the `.db` file via `adb` and inspect it in standard DB Browsers without extracting the raw key from memory.

**Alternatives Rejected:**
* *Plaintext Room DB*: Unacceptable for a secure tactical application.
* *Application-level Column Encryption*: Tedious, prone to developer error (forgetting to encrypt a new column), and leaves metadata/indices in plaintext.
