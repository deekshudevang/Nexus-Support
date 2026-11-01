# Nexus-Support Offline AI — Rollback Guide (`docs/ai/ROLLBACK.md`)

**Document Version:** 1.0.0
**Status:** VERIFIED
**Target:** Emergency Rollback Procedures for Gemma 4 Offline AI Exploration

---

## 1. Purpose & Guiding Principles

This document specifies fail-safe, deterministic rollback procedures to return the Nexus-Support repository, development environment, and Android project to any known-good baseline state.

**Guiding Principles:**
1. **Never lose user engineering work:** Commits `21474eb` through `bca47d2` on `fix/sos-defect-n` represent completed, passing SOS protocol fixes (192 unit tests passing). They must never be destroyed.
2. **Deterministic snapshots:** An out-of-tree Git bundle and worktree snapshot exist at `C:\nexus_snapshots\`.
3. **No destructive operations without authorization:** Rollback commands must be confirmed by the stakeholder before execution.

---

## 2. Baseline Checkpoints

- **Checkpoint A (Defect N Completed Baseline):** Commit `bca47d27571f9529010722fcb333a6434dfb6b9d` on branch `fix/sos-defect-n`.
  - Android source: 100% stable, SOS protocol hardened, 192 unit tests passing.
  - AI engine: MediaPipe Tasks GenAI 0.10.35 scaffold with SAF model import.
- **Checkpoint B (Pre-Migration Remote Head):** Commit `301e09c5ffe645e4b2f5f181a01d7eb29ba03ec7` on `origin/feature/offline-ai`.
- **Checkpoint C (Current Exploration State):** Commit `f776ceb` on `feature/gemma4-offline`.
  - Python venv and audit tooling established in `docs/ai/` and `scripts/`. Zero changes to Android source.

---

## 3. Rollback Scenarios & Concrete Commands

### Scenario 1: Immediate Discard of Uncommitted AI Changes
*Context: You are on `feature/gemma4-offline` and wish to discard uncommitted workspace edits without touching git history.*

```powershell
# Verify working tree status before discarding
git status

# Revert working tree changes to tracked files
git checkout -- docs/ai/ scripts/ requirements.txt task.md

# Remove untracked temporary data artifacts if any
Remove-Item -Path data\*.jsonl -ErrorAction SilentlyContinue
```

---

### Scenario 2: Switch Back to Completed Defect N Branch
*Context: You want to suspend AI work entirely and return to the verified Defect N codebase to run Android tests or prepare an SOS release.*

```powershell
# Switch to the verified Defect N branch
git checkout fix/sos-defect-n

# Verify branch commit
git log -1 --oneline
# Expected: bca47d2 fix(sos): N4 Hilt @Inject constructor separation for MeshRouter and SeenMessageCache

# Run verified test suite
.\gradlew.bat :core:mesh:testDebugUnitTest :core:domain:test
```

---

### Scenario 3: Complete Reset of `feature/gemma4-offline` to Base
*Context: You want to wipe commit `f776ceb` on `feature/gemma4-offline` and restart cleanly from base `bca47d2`.*

> [!CAUTION]
> This rewrites the local `feature/gemma4-offline` branch pointer. Verify that `C:\nexus_snapshots\gemma4_f776ceb.bundle` is intact before executing.

```powershell
# Reset branch to base commit
git checkout feature/gemma4-offline
git reset --hard bca47d27571f9529010722fcb333a6434dfb6b9d

# Clean untracked generated files
git clean -fd -e .venv
```

---

### Scenario 4: Recovery from Out-of-Repository Safety Bundle
*Context: Complete local corruption or accidental deletion. Restore entire repository state from the safety snapshot.*

```powershell
# Verify bundle integrity
git bundle verify C:\nexus_snapshots\gemma4_f776ceb.bundle

# Fetch missing commits directly from bundle
git fetch C:\nexus_snapshots\gemma4_f776ceb.bundle feature/gemma4-offline:restored_gemma4

# Or clone from bundle to a clean directory
git clone C:\nexus_snapshots\gemma4_f776ceb.bundle C:\restored_nexus_repo
```

---

### Scenario 5: Emergency Android AI Runtime Kill Switch (Production App)
*Context: An imported model produces unstable outputs or device overheating on a physical phone.*

1. **User Action:** Navigate to Settings -> AI Assistant -> Toggle "Enable Offline AI" to OFF.
2. **Programmatic Trigger:** Call `offlineAiCoordinator.triggerKillSwitch()`.
   - Releases all GPU and native LiteRT memory.
   - Clears active coroutines.
   - Sets capability state to `NOT_INSTALLED`.
   - Reverts app to direct RAG excerpt cards (`guides.json`).

---

### Scenario 6: Reverting Model Artifact to Verified Gemma 3 Baseline
*Context: An experimental or unverified model artifact causes initialization failure or memory exhaustion on device, requiring restoration of the known-good Gemma 3 int4 task bundle.*

1. **Locate Verified Baseline Archive:** `C:\Users\deeks\Downloads\gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz` (SHA-256: `E5DE8D8A482393D4E78F0429AA3896B90804BE4798E8357E82ADC80E2D2CDAFC`).
2. **Extract Model Bundle to Isolated Staging Directory:**
   ```powershell
   tar -zxvf C:\Users\deeks\Downloads\gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz -C C:\nexus_snapshots\model_staging\
   ```
3. **Re-stage into Device via SAF:**
   - Import `gemma3-1B-it-int4.task` via `ModelAssetManagerImpl.importModelFromUri(...)`.
   - Purge corrupted or incompatible candidate files from `context.filesDir/ai_models/`.
   - Verify SHA-256 against manifest pinned hash.

---

### Scenario 7: In-App Dual-Slot Model Rollback Procedure (Phase 0D)
*Context: A newly staged or activated experimental model fails initialization, experiences high latency, or causes runtime degradation. Roll back to the retained previous known-good model directly within the app without deleting or re-downloading model files.*

#### 1. Architecture & Logical Roles
The persistent model registry (`context.filesDir/ai_models/model_registry.json`) manages model artifacts across three explicit states:
- **`ACTIVE`:** The currently operational, verified model loaded by `MediaPipeLocalAiEngine`.
- **`STAGED`:** A candidate model imported into staging (`context.filesDir/ai_models/staging/`), validated for format (`.task` / `.litertlm`), size (> 100 MB), and cryptographic SHA-256 checksum, pending activation.
- **`PREVIOUS`:** The immediately preceding known-good model retained in its dedicated slot (`baseline` or `slot_a`/`slot_b`) to guarantee instantaneous, non-destructive rollback.

#### 2. In-App User Rollback Flow
1. Navigate to **Settings** (`SettingsScreen.kt`) -> **Offline AI Assistant**.
2. Locate the **Active Model** section displaying the currently active slot, model name, and file size.
3. If a prior model is available, the **Rollback to Previous Model** button is automatically enabled (backed by `SettingsViewModel.canRollback`).
4. Click **Rollback to Previous Model**.
5. The UI issues `SettingsViewModel.rollbackModel()`, which invokes `OfflineAiCoordinator.rollbackToPreviousModel()`.
6. Upon successful swap, a confirmation snackbar confirms the restored model version, and the AI engine is re-initialized with the previous artifact.

#### 3. Programmatic Rollback Operation
```kotlin
// Via OfflineAiCoordinator (Hilt injected)
val rollbackResult = offlineAiCoordinator.rollbackToPreviousModel()
if (rollbackResult.isSuccess) {
    Log.i("AI_ROLLBACK", "Restored previous model: ${rollbackResult.getOrNull()?.modelName}")
} else {
    Log.e("AI_ROLLBACK", "Rollback failed: ${rollbackResult.exceptionOrNull()?.message}")
}
```

#### 4. Atomic Execution Mechanics
1. **Safety Pre-check:** `ModelAssetManagerImpl.rollbackToPrevious()` validates that `registry.previous` exists and the target file is present on disk with valid file size.
2. **Runtime Engine Drain:** Prior to switching registry pointers, `offlineAiCoordinator` safely unloads the native `MediaPipeLocalAiEngine` instance, releasing native C++ and Adreno GPU memory.
3. **Atomic State Swap:** The registry swaps `active` and `previous` slot definitions:
   - `active` $\leftarrow$ `old_previous`
   - `previous` $\leftarrow$ `old_active`
4. **Crash-Resilient Persistence:** The updated registry is written to `model_registry.json.tmp`, synced to disk via `FileOutputStream.flush() + fd.sync()`, and atomically renamed to `model_registry.json`.
5. **Re-Initialization:** Engine is re-initialized with the restored active slot file path. If re-initialization fails, the previous baseline is kept intact.

#### 5. Recovery from Corrupt or Missing Registry
If `model_registry.json` is missing or corrupted:
- `ModelAssetManagerImpl.loadRegistry()` automatically inspects physical storage.
- If the baseline model exists at `context.filesDir/models/gemma3-1B-it-int4.task`, the registry synthesizes a recovery manifest restoring `baseline` as `ACTIVE`.
- No model files are deleted during recovery.

#### 6. Emergency ADB CLI Rollback
If the UI is inaccessible, rollback can be commanded via ADB:
```powershell
# Verify current registry
adb shell run-as com.meshlink.app.debug cat files/ai_models/model_registry.json

# If manual registry reset to baseline is needed:
adb shell "run-as com.meshlink.app.debug sh -c 'cat <<EOF > files/ai_models/model_registry.json
{
  \"version\": 1,
  \"active\": {
    \"slotId\": \"baseline\",
    \"modelName\": \"gemma3-1B-it-int4.task\",
    \"relativePath\": \"../models/gemma3-1B-it-int4.task\",
    \"fileSizeBytes\": 554661243,
    \"sha256\": \"e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee\",
    \"installedAt\": 1730000000000
  },
  \"staged\": null,
  \"previous\": null
}
EOF'"

# Force stop and cold restart app to load baseline
adb shell am force-stop com.meshlink.app.debug
adb shell am start -n com.meshlink.app.debug/com.meshlink.app.ui.MainActivity
```
