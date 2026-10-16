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

## 4. Gate 0C — Migration Safety Gate

Gate 0C ensures that no migration steps compromise pre-existing code, protocols, or safety bounds:

| Criterion ID | Criterion Description | Observed Evidence | Status |
|---|---|---|---|
| **0C-1** | Baseline Working Tree & Git Hygiene | Working tree clean; zero commits lost or reset; base commit `bca47d2` preserved. | **VERIFIED** |
| **0C-2** | Out-of-Repository Safety Snapshot | Snapshot bundle (`gemma4_f776ceb.bundle`) and file tree created at `C:\nexus_snapshots\`. | **VERIFIED** |
| **0C-3** | Android Codebase Isolation | Zero lines changed in `app/`, `core/`, `*.kt`, `*.gradle*`, or manifests. | **VERIFIED** |
| **0C-4** | Hardware & Process Safety | RTX 4060 VRAM usage 1.27 GB (6.9 GB free); 0 training or download processes active. | **VERIFIED** |
| **0C-5** | Rollback & Migration Audit Documentation | `docs/ai/MIGRATION_AUDIT.md` and `docs/ai/ROLLBACK.md` published. | **VERIFIED** |
| **0C-6** | PII & Secret Isolation | Automated PII scanner verified 0 credentials or personal data in repo files. | **VERIFIED** |

**Gate 0C Overall Verdict:** **PASS** (All six isolation and safety prerequisites verified).

---

## 5. Gemma 4 Feasibility & Export Blocker (Gate 0 Status)

- **Official Model ID:** `google/gemma-4-E2B-it` (Hugging Face Hub metadata verified).
- **Architecture:** Per-Layer Embeddings (PLE) Dense Transformer (~5.1B total params, ~2.3B compute params).
- **Custom Weight Export Route:** **UNPROVEN / UNAVAILABLE**. Official Google AI Edge conversion documentation specifically covering the Gemma 4 PLE architecture and custom LoRA weight merging to `.litertlm` has not been empirically verified.
- **GATE 0 Decision:** In accordance with Precedence Rule 2 and Section 2 Rule 16, **Gate 0 is marked NOT VERIFIED (criterion b unproven)**. Full model training is withheld until an approved mobile export route is verified.
