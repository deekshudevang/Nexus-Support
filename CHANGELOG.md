# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- SAS (Short Authentication String) UI badge in the Chat Screen to support out-of-band TOFU verification.
- Room `MigrationTest` coverage for schema upgrades from version 5 through 13.
- Hardware-backed Android KeyStore (StrongBox/TEE) implementation for device identity keys (`ProductionKeyManager`).

### Fixed
- Fatal Room migration bug (destructive fallback) when upgrading from v6/v7 to v8+.
- Man-in-the-Middle vulnerability during initial handshake by enforcing TOFU (Trust On First Use) pinning of the peer's public key.
- Removed accidentally committed `node_modules` from the frontend web interface to fix repository hygiene.

### Changed
- Refactored `KeyManager` into a `KeyProvider` interface to decouple hardware cryptography from JVM testing.
- Clarified UI state by marking the Offline AI Assistant feature as Experimental.
