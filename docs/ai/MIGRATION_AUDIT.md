# Nexus-Support Offline AI — Migration Audit (`docs/ai/MIGRATION_AUDIT.md`)

**Document Version:** 1.0.0
**Audit Date:** 2026-10-16
**Auditor:** Senior ML & Android Systems Engineer
**Scope:** Strict Read-Only Baseline Inspection of Android AI Architecture and Migration Feasibility

---

## 1. Executive Summary

This document establishes the verified baseline of the existing Android AI subsystem in Nexus-Support prior to any Gemma 4 model integration or fine-tuning. The repository currently contains a complete text-generation engine architecture built on Google MediaPipe Tasks GenAI (`0.10.35`), designed to consume quantized Gemma models imported at runtime via Android's Storage Access Framework (SAF). Zero model weights are bundled in the repository or APK.

---

## 2. Hypothesis Audit (H1 – H4)

All four hypotheses are audited from direct, read-only inspection of repository code, manifests, and Git history:

| Hypothesis | Description | Evidence & File References | Status |
|---|---|---|---|
| **H1** | Installed model metadata names `gemma-3-1b-it` | `ModelAssetManagerImpl.kt:132` hardcodes `modelId = "gemma-3-1b-it"`; `docs/OFFLINE_AI_COMPATIBILITY.md:24` documents `Gemma 2B / Gemma 3 1B IT`. No model file exists in `app/src/main/assets/` (only `emergency_packs/guides.json`). App acquires model at runtime via user SAF import (`ModelAssetManagerImpl.importModelFromUri`) staged into `files/ai_models/`. | **CONFIRMED** |
| **H2** | MediaPipe Tasks GenAI dependency is `0.10.14` | `app/build.gradle.kts:102` explicitly declares `implementation("com.google.mediapipe:tasks-genai:0.10.35")`. `docs/OFFLINE_AI_COMPATIBILITY.md:9,18` previously noted `0.10.14`, but the active build file uses `0.10.35`. | **REFUTED** (Active version is `0.10.35`) |
| **H3** | Branch existence before this run | Prior to this run, branch `feature/gemma4-offline` did not exist locally or remotely. `fix/sos-defect-n` had candidate base `3ebf998` on remote, plus 5 unpushed local commits (`21474eb`, `ca2f707`, `f0ac6eb`, `934a67f`, `bca47d2`). `feature/gemma4-offline` was branched from `bca47d2`. | **CONFIRMED** |
| **H4** | Current engine covers text generation only | `LocalAiEngine.kt` and `MediaPipeLocalAiEngine.kt:77-120` implement text prompt in, text response out (`generate`, `generateStream`). Vision is decoupled in `VisionManager.kt` using MLKit classification (`ImageLabeler`). Voice is decoupled in `SpeechManager.kt` using Android `TextToSpeech` and `SpeechRecognizer`. The LLM engine performs zero multimodal processing. | **CONFIRMED** |

---

## 3. Existing Android AI Architecture Audit

### 3.1 Model Asset Management (`ModelAssetManagerImpl.kt`)
- **Storage Path:** Private internal storage at `context.filesDir/ai_models/`.
- **Supported File Formats:** `.litertlm`, `.task`, `.bin`.
- **Import Pathway:** Models are never bundled in APK assets (preserving zero-bloat APK). Users import model binaries via Android Storage Access Framework (`importModelFromUri`) which stages, hashes, and confirms integrity against `confirmed_hash.txt`.

### 3.2 Execution Engine (`MediaPipeLocalAiEngine.kt`)
- **Runtime:** `com.google.mediapipe.tasks.genai.llminference.LlmInference`.
- **Backend:** Prefers GPU delegate (`LlmInference.Backend.GPU` via OpenCL/Vulkan), with CPU fallback.
- **Constraints:** Max tokens 2048, top-K 40.

### 3.3 Lifecycle & Safety Coordinator (`OfflineAiCoordinatorImpl.kt`)
- **Feature Flag Policy:** `_isAiEnabled` defaults to `false` in `init` unless explicitly toggled by user in preferences.
- **Emergency Kill Switch:** `triggerKillSwitch()` immediately unloads `LocalAiEngine`, cancels active inference jobs, and resets capability state to `NOT_INSTALLED`.
- **SOS Priority:** Background AI work yields immediately upon active emergency beaconing.

---

## 4. Existing Gemma 3 Model Archive Verification (`gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz`)

- **Archive Location:** `C:\Users\deeks\Downloads\gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz`
- **File Existence:** **CONFIRMED**
- **Exact File Size:** `786,501,209` bytes (~750.07 MiB)
- **Last Modified:** 2026-10-11 12:43:02
- **SHA-256 Checksum:** `E5DE8D8A482393D4E78F0429AA3896B90804BE4798E8357E82ADC80E2D2CDAFC`
- **Internal Archive Members (Read-Only Inspection via tar listing):**
  1. `gemma3-1B-it-int4.task` (Size: `554,661,243` bytes, mtime: `1741711890` [2025-03-11])
  2. `gemma3-1B-it-int4.tflite` (Size: `549,971,728` bytes, mtime: `1741711952` [2025-03-11])

### 4.1 Internal Format & Metadata Inspection
- **`gemma3-1B-it-int4.task`**:
  - Valid MediaPipe Tasks GenAI bundle starting at byte offset 4 with ZIP header (`PK\x03\x04`).
  - Contains three internal zip streams:
    - `TF_LITE_PREFILL_DECODE` (`549,971,728` bytes) — Quantized int4 TFLite graph.
    - `TOKENIZER_MODEL` (`4,689,074` bytes) — SentencePiece tokenizer binary for Gemma 3.
    - `METADATA` (`90` bytes) — Protobuf encoding Gemma turn tokens: `<bos>`, `<eos>`, `<start_of_turn>user\n`, `<end_of_turn>\n<start_of_turn>model\n`.
- **`gemma3-1B-it-int4.tflite`**:
  - Standalone TFLite FlatBuffer binary with magic bytes `TFL3` (`549,971,728` bytes).
- **Artifact Classification:**
  - Contains **Inference Artifacts Only**.
  - Contains **Zero Training Checkpoints** (no PyTorch weights, optimizer states, or LoRA training checkpoints).

### 4.2 Android Runtime Compatibility Analysis
- **Engine Compatibility (`MediaPipeLocalAiEngine.kt`):**
  - Line 42 permits `.task` and `.bin` extensions. `gemma3-1B-it-int4.task` matches this constraint.
  - The runtime initializes MediaPipe `LlmInference` via `setModelPath(...)`, consuming the bundled model, tokenizer, and metadata directly.
  - In contrast, standalone `gemma3-1B-it-int4.tflite` cannot be used directly with `MediaPipeLocalAiEngine` because it lacks the bundled SentencePiece tokenizer stream required by `LlmInference`.
- **Inference Verification Status:**
  - **VERIFIED ON PHYSICAL HARDWARE (Motorola edge 50 pro, `ZD222M3BNH`, Android 16 / API 36, arm64-v8a)**.
  - Model Path on Device: `/data/user/0/com.meshlink.app.debug/files/models/gemma3-1B-it-int4.task`
  - Model Size: `554,661,243` bytes
  - Internal Model SHA-256: `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee` (matches `confirmed_hash.txt` and verified archive member).
  - Runtime: Google MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai:0.10.35` / `0.10.14` binary ABI).
  - GPU Initialization Latency: 9,766ms (first load), 5,172ms (warm reload).
  - Test Prompt: `"What is 2 plus 2?"`
  - Generated Response: `"2 plus two equals four."`
  - Generation Latency: ~1,583ms tokens stream.
  - Offline Mode: Verified with airplane mode ENABLED (`settings get global airplane_mode_on` = 1, zero network usage).
  - Kill Switch: Physically triggered in Settings; logged `EMERGENCY KILL SWITCH TRIGGERED: Disabling AI and releasing all resources`; native C++ resources deallocated in 265ms (`LocalAiEngine native resources deallocated`); `offline_ai_prefs.xml` set to `ai_enabled=false`; state transitioned to `NOT_INSTALLED`; subsequent generation refused with user-facing banner.
  - Mesh/SOS Independence: Mesh scanning and SOS emergency beaconing remained 100% operational before, during, and after inference and kill switch.

---

## 5. Gate 0C — Migration Safety Gate Evaluation (Phase 0D Update)

Gate 0C ensures that pre-migration stability, baseline builds, feature flags, and safety mechanisms are empirically verified before any AI migration changes. Each criterion is evaluated individually below:

| Criterion ID | Criterion Description | Observed Evidence | Status |
|---|---|---|---|
| **0C-1** | Baseline Android Build Compilation | `.\gradlew.bat --no-daemon assembleDebug` passed with exit code 0; generated debug APK `app-debug.apk` in 41s. | **VERIFIED** |
| **0C-2** | Baseline Android Unit Test Suite | `.\gradlew.bat --no-daemon testDebugUnitTest` passed with exit code 0; 204 unit tests passed across all modules, 0 failures, 1 skipped. | **VERIFIED** |
| **0C-3** | Offline AI Feature Flag & Kill Switch | Verified on physical device: `_isAiEnabled` defaults to `false`; emergency kill switch immediately unloads GPU/C++ resources, resets state to `NOT_INSTALLED`, and blocks subsequent queries. | **VERIFIED** |
| **0C-4** | Verifiable Rollback Procedure | Non-destructive dual-slot architecture implemented and physically exercised on Motorola edge 50 pro using duplicate baseline slot (`candidate.task`). Activation promoted slot `slot_a` as ACTIVE and retained `baseline` as PREVIOUS; explicit rollback successfully restored `baseline` as ACTIVE. Baseline model was never deleted or overwritten. Formal Gate 0C signoff withheld pending final authorization. | **NOT VERIFIED** (In accordance with Phase 0D mission constraints, Gate 0C must remain NOT VERIFIED until formal gate review) |
| **0C-5** | Android Compatibility Evidence & Inference | Physical phone connected: Motorola edge 50 pro (Android 16, API 36, arm64-v8a); Gemma 3 1B INT4 inference executed on-device offline with ~1.58s generation latency ("2 plus two equals four.") across both slots. | **VERIFIED** |
| **0C-6** | Out-of-Repository Safety Snapshot | Safety bundle `C:\nexus_snapshots\gemma4_f776ceb.bundle` and file mirror created outside repo and OneDrive. | **VERIFIED** |

**Gate 0C Overall Verdict:** **NOT VERIFIED** (Maintained as NOT VERIFIED per Phase 0D strict requirements until formal stage migration signoff).

---

## 6. Gemma 4 Feasibility & Export Blocker (Gate 0 Status)

- **Official Model ID:** `google/gemma-4-E2B-it` (Hugging Face Hub metadata verified).
- **Architecture:** Per-Layer Embeddings (PLE) Dense Transformer (~5.1B total params, ~2.3B compute params).
- **Custom Weight Export Route:** **UNPROVEN / UNAVAILABLE**. Official Google AI Edge conversion documentation specifically covering the Gemma 4 PLE architecture and custom LoRA weight merging to `.litertlm` or MediaPipe `.task` has not been empirically verified.
- **GATE 0 Decision:** In accordance with Precedence Rule 2 and Section 2 Rule 16, **Gate 0 is marked NOT VERIFIED (criterion b unproven)**. Model training is withheld until an approved mobile export route is verified.

---

## 7. Phase 0D — Storage-Path Reconciliation & Physical Rollback Verification

### 7.1 Storage-Path Reconciliation (`files/models/` vs `files/ai_models/`)
- **Historical Discrepancy:** Prior Phase 0 documentation and legacy adb deployment pushed the baseline model to `context.filesDir/models/gemma3-1B-it-int4.task`. Concurrently, runtime SAF imports in `ModelAssetManagerImpl` defaulted to `context.filesDir/ai_models/`.
- **Reconciliation Resolution:** In Phase 0D, `ModelAssetManagerImpl` was redesigned to discover existing models in both `files/models/` and `files/ai_models/` without moving or modifying them. The baseline model is permanently registered in `model_registry.json` as slot `baseline` (`models/gemma3-1B-it-int4.task`), while new candidate imports are isolated in `ai_models/staging/` and promoted to `ai_models/slots/slot_a/` or `slot_b/`.

### 7.2 Archive Member Checksum vs On-Device Checksum Table
Stream-level cryptographic verification was conducted comparing the tarball archive member against the on-device physical file:

| Property | Archive Member (`gemma3-1B-it-int4.task`) | On-Device Model (`files/models/gemma3-1B-it-int4.task`) | Staged Candidate Copy (`files/ai_models/staging/candidate.task`) |
|---|---|---|---|
| **Archive Source** | `C:\Users\deeks\Downloads\gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz` | Physical phone internal storage | Physical phone internal storage |
| **Exact Size (bytes)** | `554,661,243` | `554,661,243` | `554,661,243` |
| **SHA-256 Checksum** | `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee` | `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee` | `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee` |
| **Integrity Status** | **VERIFIED (Bit-for-bit parity)** | **VERIFIED (Bit-for-bit parity)** | **VERIFIED (Bit-for-bit parity)** |

### 7.3 Physical Rollback Test Execution Evidence (`DualSlotRollbackDeviceTest.kt`)
On-device test execution was performed on physical Motorola edge 50 pro (`ZD222M3BNH`, Android 16 / API 36, `arm64-v8a`) under `HiltTestRunner` with logcat capture (`logs/phase0d_physical_rollback.log`):

1. **Preflight Verification:**
   - Baseline file confirmed at `/data/user/0/com.meshlink.app.debug/files/models/gemma3-1B-it-int4.task` (`554,661,243` bytes, SHA-256 `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`).
   - Staged duplicate candidate confirmed at `/data/user/0/com.meshlink.app.debug/files/ai_models/staging/candidate.task`.
   - Initial active slot: `baseline`.
2. **Step 5 — Slot Activation (`activateStagedModel`):**
   - Moved `candidate.task` into `/data/user/0/com.meshlink.app.debug/files/ai_models/slots/slot_a/model.task`.
   - Retained `baseline` as role `PREVIOUS` (`previousSlotId = "baseline"`).
   - Promoted `slot_a` as role `ACTIVE` (`activeSlotId = "slot_a"`).
   - Baseline file in `files/models/` was NOT deleted or overwritten.
   - Initialized `MediaPipeLocalAiEngine` with `slot_a` model on GPU.
   - Executed prompt `"What is 2 plus 2?"`; generated response: `"2 plus two equals four."`.
3. **Step 6 — Rollback Operation (`rollbackToPrevious`):**
   - Engine unloaded native resources.
   - Restored slot `baseline` as role `ACTIVE` (`activeSlotId = "baseline"`).
   - Moved slot `slot_a` to role `PREVIOUS` (`previousSlotId = "slot_a"`).
   - Updated `model_registry.json` atomically.
4. **Step 7 — Baseline Re-initialization & Verification:**
   - Re-initialized `MediaPipeLocalAiEngine` with restored baseline model (`gemma3-1B-it-int4.task`).
   - Executed prompt `"What is 2 plus 2?"`; generated response: `"2 plus two equals four."`.
5. **Step 8 — Kill Switch Verification:**
   - Called `engine.unload()`; verified `engine.isReady() == false`.
   - Subsequent prompt refused with `AiError.ModelMissing`.
6. **Step 9 — Final Baseline Integrity Audit:**
   - Baseline file `files/models/gemma3-1B-it-int4.task` confirmed intact.
   - File length: `554,661,243` bytes.
   - SHA-256: `e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee`.
   - Zero byte corruption, zero overwrites.
