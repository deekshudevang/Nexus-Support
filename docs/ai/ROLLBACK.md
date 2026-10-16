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
