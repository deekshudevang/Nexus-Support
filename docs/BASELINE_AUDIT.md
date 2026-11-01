# Baseline Audit

**Last verified:** 2026-09-20 @ HEAD

This document records the exact state of the Nexus-Support repository against production engineering standards prior to the major upgrade phases. Every known limitation is listed here with its severity and the phase that will address it.

## 1. Documentation & Claims
| Limitation | Severity | Resolution Phase |
|---|---|---|
| **Marketing vs Reality:** README over-claims hardware capabilities ("StrongBox Keymaster HSM"), features ("Opportunistic cloud sync"), and maturity ("production-grade"). | Critical | Phase 2 (README Overhaul) |
| **No Architecture Validation:** Missing reproducible threat model and architecture documentation tying mitigations to specific tests. | High | Phase 2 (Threat Model) |
| **Simulator Claims:** Missing benchmarks and testing documentation; "offline AI" and "cloud sync" are currently unvalidated or mocked. | High | Phase 3 (Benchmarks & Metrics) |

## 2. Infrastructure & Hygiene
| Limitation | Severity | Resolution Phase |
|---|---|---|
| **Gitignore Leaks:** `.gitignore` does not adequately exclude `.idea/` (keeping only `codeStyles` and `runConfigurations`), `build/`, `*.log`, local DBs, and APKs. | Medium | Phase 1 (Hygiene + CI Green) |
| **Pull Request Standardization:** `.github/pull_request_template.md` lacks requirements for architecture impact, security impact, test citations, and device testing. | Medium | Phase 1 (Hygiene + CI Green) |
| **Local CI Environment:** The `core:data:compileDebugJavaWithJavac` task fails in the current local environment due to a missing `jlink` executable. | Low | Phase 4 (Explicitly marked UNVALIDATED) |

## 3. Cryptography & Security
| Limitation | Severity | Resolution Phase |
|---|---|---|
| **Fragmented Trust Root:** The repository historically maintained two distinct hardware keys (`ProductionKeyManager` for ECDH, `CryptoManager` for ECDSA), undermining TOFU pinning. | Resolved | Phase 0 (ADR 0006) |
| **Latent Hardware Crash:** `ProductionKeyManager` requested StrongBox generation without a fallback, which would crash devices returning `StrongBoxUnavailableException`. | Resolved | Phase 0 (Commit 9644b17) |
| **CodeQL False Positives:** Hardcoded string literals in `DatabaseModule.kt` and `EciesService.kt` trigger false-positive CodeQL credential alerts. | Resolved | Phase 0 (Inline `// lgtm` added) |

## 4. Testing & Verification
| Limitation | Severity | Resolution Phase |
|---|---|---|
| **Device Test Matrix Missing:** Lack of formalized grid scenarios (node counts vs features like SOS, TTL, partition reconnection) verified on physical hardware. | High | Phase 3 (Device Test Matrix) |
| **Unit Test Coverage:** Significant gaps in coverage, specifically around ViewModels and MeshRouter edge cases (TTL expiry, flooding). | High | Phase 3 (Test Matrix) |
