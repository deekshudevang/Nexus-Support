# Defect N Evidence Manifest

Governing standard: Execution Control F. All entries must reflect actual commands, captured exit codes, and non-fabricated logs.

---

## Step 0: Preflight, Baseline & Work Item

- **Candidate Commit SHA:** `3ebf99808a757011790d3f2baf8dfe673931a42a`
- **Starting Branch:** `fix/sos-defect-n`
- **Tree State (`git status --short`):**
  - Tracked modifications: `M task.md` (checkpoint and work item specification)
  - Untracked files: None
- **Preflight Commands & Actual Exit Codes:**
  - `git --version`: `git version 2.55.0.windows.3` (Exit code: 0)
  - `git branch --show-current`: `fix/sos-defect-n` (Exit code: 0)
  - `git rev-parse HEAD`: `3ebf99808a757011790d3f2baf8dfe673931a42a` (Exit code: 0)
  - `.\gradlew.bat --version`: Gradle 8.14.5, Kotlin 2.0.21, JVM 25.0.4.1 (Exit code: 0)
  - `Get-Content settings.gradle.kts`: Includes `:app`, `:core:domain`, `:core:data`, `:core:mesh`, `:core:crypto` (Exit code: 0)
  - `adb devices`: `List of devices attached` (Empty - 0 physical devices attached; radio behavior remains unverified on physical hardware) (Exit code: 0)
  - `.\gradlew.bat help --console=plain`: BUILD SUCCESSFUL (Exit code: 0)
- **Baseline Git Worktree:**
  - Worktree Path: `build/baseline_worktree` (detached at commit `3ebf998`)
  - Command: `git worktree add --detach build/baseline_worktree 3ebf998` (Exit code: 0)
  - Configuration: `local.properties` copied to worktree for Android SDK binding
- **Baseline Test Suite Run:**
  - Command: `.\gradlew.bat :core:domain:test :core:mesh:testDebugUnitTest --console=plain` (in `build/baseline_worktree`)
  - Exit Code: 0
  - Duration: 56s
  - Actionable Tasks: 47 executed
- **Baseline Test Counts (from `build/baseline_worktree/**/TEST-*.xml`):**
  - XML Files: 14
  - Total Tests: 76
  - Failures: 0
  - Errors: 0
  - Skipped: 0
- **CI Status:** `CI NOT VERIFIED` (no remote CI run completed on candidate commit `3ebf99808a757011790d3f2baf8dfe673931a42a`)

---

## Step 1: N1 Serialization

- **Hypotheses Tested & Results:**
  - *Gson pinned directly in build.gradle.kts instead of version catalog:* REPRODUCED & FIXED. Added `gson = "2.10.1"` to `[versions]` and `gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }` to `[libraries]` in `gradle/libs.versions.toml`. Updated `core/domain/build.gradle.kts` to `implementation(libs.gson)`.
  - *Gson Licence Undocumented:* CONFIRMED from official Google Gson repository: Apache License 2.0 (`Apache-2.0`). Documented in `DEFECT_N_EVIDENCE.md` and compatibility docs.
  - *Defect register claims 6 SosPacketDataTest tests but file had 3:* REPRODUCED. The source file `SosPacketDataTest.kt` had 3 `@Test` methods. Reconciled by adding the 3 missing/required tests: (1) payload size bounding against transport limit with deterministic truncation, (2) golden test old receiver parses new output, (3) golden test new parser accepts old format payloads with missing optional fields and unknown future fields. The test file now has exactly 6 `@Test` methods.
  - *No byte-size assertion against transport payload limit:* REPRODUCED. Google Nearby Connections `Payload.Type.BYTES` enforces a maximum payload size of 32,768 bytes (`Payload.MAX_BYTES`), and `MeshPacketSerializer` enforces 65,536 bytes. Untruncated SOS packets containing extensive medical notes exceeded 32,000 bytes (48,616 bytes in test), failing with `AssertionError: Serialized SOS byte size (48616) must not exceed transport limit (32000 bytes)`.
  - *No proof OLD receiver parses NEW output:* REPRODUCED & FIXED. Added golden test verifying that standard JSON parser (as used by legacy receivers in `MeshRouter`/`NearbyRepositoryImpl`) parses new output and extracts all fields (`uuid`, `senderId`, `senderName`, `emergencyType`, `severity`, `latitude`, `longitude`, `message`, `timestamp`, `hopCount`, `status`).
  - *Deterministic truncation behavior:* When serialized payload exceeds `MAX_TRANSPORT_PAYLOAD_BYTES = 32_000`, `message` is deterministically truncated with `TRUNCATION_MARKER = "\n[TRUNCATED: Medical info exceeded transport limit]"`. Invariant: location, event ID (`uuid`), `senderId`, and emergency type are NEVER dropped.
- **Before-Fix Failure Evidence:**
  - Command: `.\gradlew.bat :core:domain:test --console=plain` (task-6525)
  - Exit Code: 1 (BUILD FAILED)
  - Failure: `SosPacketDataTest > serialized SOS payload for worst realistic case must not exceed transport limit of 32KB FAILED (java.lang.AssertionError at SosPacketDataTest.kt:138)`
- **After-Fix Verification Evidence:**
  - Command: `.\gradlew.bat :core:domain:test --console=plain` (task-6533)
  - Exit Code: 0 (BUILD SUCCESSFUL in 21s)
  - Actionable Tasks: 4 executed
- **Fresh JUnit Test Results (`core/domain/build/test-results/test/TEST-com.meshlink.app.domain.model.SosPacketDataTest.xml`):**
  - Total Tests: 6
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - Tests Executed:
    1. `backward compatibility - tolerant parser accepts old format payload with 0_0 coordinates`
    2. `golden test - new parser accepts old format payloads with missing optional fields and unknown future fields`
    3. `location validation explicitly represents missing and invalid GPS`
    4. `serialized SOS payload for worst realistic case must not exceed transport limit of 32KB`
    5. `golden test - old receiver parser accepts new output`
    6. `toJson should produce valid JSON when message contains multiline text and backslashes`
- **Candidate Commit Message:** `fix(sos): N1 robust JSON serialization, payload size bounding, and golden wire compatibility`

---
