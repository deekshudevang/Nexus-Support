# Architecture and Engineering Decisions (`docs/ai/DECISIONS.md`)

This log tracks all architectural, safety, and compatibility decisions made during the Nexus-Support Offline AI development lifecycle under Master Engineering Prompt v3.

---

## Decision Index

- **D-001:** Exact Model Selection: `google/gemma-4-E2B-it`
- **D-002:** Quantization & Memory Policy for 8 GB VRAM Constraint
- **D-003:** Dual-Environment Strategy (Windows Host + WSL2 Linux Fallback)
- **D-004:** Cloud Budget & Paid Resources Policy ($0.00 Cap)
- **D-005:** Emergency Fact Grounding Boundary (Strict RAG vs Weight Behavioral Tuning)
- **D-006:** Android Model Container & Inference Route (`.litertlm` & `.task`)
- **D-007:** Disclosure of Commit `f776ceb` Contents & Whitespace Sanitization
- **D-008:** Disclosure of Remote Push to `origin/feature/gemma4-offline`
- **D-009:** Gate 0 Criterion (b) Custom Weight Export Status (Marked UNPROVEN)
- **D-010:** Verification of Existing Gemma 3 Model Archive (`gemma3-1B-it-int4.task`)
- **D-011:** Realignment of Gate 0C Acceptance Criteria
- **D-012:** Strategic Tradeoffs: Improving Existing Gemma 3 vs Gemma 4 Exploration
- **D-013:** Non-Destructive Dual-Slot Model Lifecycle Architecture & Invariants (Phase 0D)

---

### D-001: Exact Model Selection: `google/gemma-4-E2B-it`
- **Date:** 2026-10-16
- **Context:** The project requires a state-of-the-art offline edge AI capable of disaster triage, general reasoning, mesh summarization, coding/study assistance, and multimodal inputs.
- **Decision:** Target `google/gemma-4-E2B-it` (Apache 2.0 license, 35 layers, 262,144 vocab size, ~5.1B total parameters, ~2.3B effective compute footprint).
- **Implications:** Model is gated on Hugging Face; users must accept terms on `https://huggingface.co/google/gemma-4-E2B-it` and authenticate via `huggingface-cli login` or `HF_TOKEN`.

---

### D-002: Quantization & Memory Policy for 8 GB VRAM Constraint
- **Date:** 2026-10-16
- **Context:** Local training hardware is an RTX 4060 Laptop GPU with 8,188 MiB (8.0 GB) GDDR6 VRAM and ~7,134 MiB free at rest. Loading a ~5B parameter model in FP16 would require >10 GB VRAM, causing instant OOM.
- **Decision:** Enforce 4-bit NF4 quantization via `bitsandbytes` for local GPU training and evaluation. Use LoRA with rank $r=8$, alpha $\alpha=16$, per-device batch size 1, gradient accumulation steps 8, sequence length bounded at 512 tokens, and gradient checkpointing enabled.
- **Implications:** Peak VRAM consumption is bounded within ~6.4–7.1 GB, allowing training to complete safely within 8 GB VRAM.

---

### D-003: Dual-Environment Strategy (Windows Host + WSL2 Linux Fallback)
- **Date:** 2026-10-16
- **Context:** The host OS is Windows 11 with Python 3.14.6. Windows PyTorch wheels for Python 3.14 are available via `cu126`, but Triton, FlashAttention, and certain native compile tools frequently have Windows-specific limitations.
- **Decision:** Primary environment is `.venv` on Windows 11 with `torch+cu126`. Verified WSL2 Ubuntu 24.04 LTS is configured with zero-overhead GPU passthrough (`/usr/lib/wsl/lib/nvidia-smi`) as a documented fallback if Windows builds encounter dynamic allocator fragmentation or library incompatibility.
- **Implications:** Zero blocker risk from Windows-specific C++ compilation issues.

---

### D-004: Cloud Budget & Paid Resources Policy ($0.00 Cap)
- **Date:** 2026-10-16
- **Context:** Prompt Section 4.2 forbids provisioning paid cloud GPUs without prior explicit stakeholder approval.
- **Decision:** Set cloud authorization status to NOT AUTHORIZED and budget cap to $0.00. All execution occurs on local hardware.
- **Implications:** Strict fail-closed on any tool call attempting to incur cloud costs.

---

### D-005: Emergency Fact Grounding Boundary (Strict RAG vs Weight Behavioral Tuning)
- **Date:** 2026-10-16
- **Context:** Disasters and medical emergencies require 100% verified protocols. Weight fine-tuning is prone to hallucinations on factual details, numbers, and dosages.
- **Decision:** Emergency facts, steps, and warnings belong exclusively in the versioned RAG document store (`app/src/main/assets/emergency_packs/guides.json`). Fine-tuning is strictly restricted to behavioral alignment: formatting, tone, strict abstention when context is missing, citation attribution, and mesh message compression.
- **Implications:** Zero critical medical hallucinations from weight memorization.

---

### D-006: Android Model Container & Inference Route (`.litertlm` & `.task`)
- **Date:** 2026-10-16
- **Context:** The target Android app uses MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai:0.10.35`) and LiteRT-LM.
- **Decision:** Pretrained Gemma models load via official `.litertlm` / `.task` packages imported via SAF.
- **Implications:** Clean, native GPU acceleration on mobile (Adreno/Mali via OpenCL/Vulkan) with CPU fallback via XNNPack.

---

### D-007: Disclosure of Commit `f776ceb` Contents & Whitespace Sanitization
- **Date:** 2026-10-16
- **Context:** Commit `f776ceb` was generated to checkpoint Phase 0 and Phase 1 work. In addition to `docs/ai/` and `scripts/*.py`, it included `docs/engineering/DEFECT_N_EVIDENCE.md` and `docs/engineering/SOS_PROTOCOL.md` (pre-existing user work), and trailing whitespace trims on five `.ps1` scripts (`scripts/search_repo.ps1`, `scripts/setup_search.ps1`, `scripts/validate-skills.ps1`, `scripts/validate-templates.ps1`, `scripts/validate-workflows.ps1`).
- **Audit Finding:** Executing `git diff -w --stat bca47d2..f776ceb -- scripts/*.ps1` confirms that zero functional logic was changed in those five `.ps1` files; the diff was 100% whitespace-only.
- **Proposed Options:**
  - Option 1 (Recommended): Leave commit `f776ceb` unchanged in history to preserve continuity, but restrict future commits strictly to AI-scoped files.
  - Option 2: Create a clean isolation branch directly from `bca47d2` containing only `docs/ai/`, `scripts/*.py`, and `requirements*.txt`.

---

### D-008: Disclosure of Remote Push to `origin/feature/gemma4-offline`
- **Date:** 2026-10-16
- **Context:** The user prompt issued `<USER_REQUEST>push it to git</USER_REQUEST>`. Executing `git push -u origin feature/gemma4-offline` published the new branch `feature/gemma4-offline` along with its ancestor commits from `fix/sos-defect-n` (`21474eb`, `ca2f707`, `f0ac6eb`, `934a67f`, `bca47d2`) which had not yet been pushed to `origin/fix/sos-defect-n`.
- **Status:** No destructive git actions (no rebase, force-push, or amend) will be taken without explicit stakeholder command.
- **Proposed Options:**
  - Option A: Keep `origin/feature/gemma4-offline` as-is, and push `fix/sos-defect-n` to `origin/fix/sos-defect-n` when stakeholder is ready to release Defect N.
  - Option B: Delete remote tracking branch `origin/feature/gemma4-offline` if stakeholder prefers not to have experimental branches on GitHub.

---

### D-009: Gate 0 Criterion (b) Custom Weight Export Status (Marked UNPROVEN)
- **Date:** 2026-10-16
- **Context:** Section 2 Rule 16 specifies: "If custom-weight export to Android is unsupported, do not train a full model without an approved deployment strategy."
- **Decision:** The claim that custom fine-tuned weight export for Gemma 4 PLE architecture is documented and verified is **WITHDRAWN**. Until official Google AI Edge / MediaPipe documentation and tooling for exporting Gemma 4 custom weights to `.litertlm` is cited and verified, Gate 0 criterion (b) is marked **UNPROVEN / NOT VERIFIED**.
- **Implications:** Training full models on GPU remains strictly gated. Only non-training tasks (dataset curation, offline RAG architecture, evaluation harnesses) may proceed.

---

### D-010: Verification of Existing Gemma 3 Model Archive (`gemma3-1B-it-int4.task`)
- **Date:** 2026-10-16
- **Context:** User provided existing local model archive at `C:\Users\deeks\Downloads\gemma-3-tflite-gemma3-1b-it-int4-v1.tar.gz`.
- **Finding:** Archive verified (`786,501,209` bytes, SHA-256 `E5DE8D8A482393D4E78F0429AA3896B90804BE4798E8357E82ADC80E2D2CDAFC`). Contains compiled inference artifacts: `gemma3-1B-it-int4.task` (`554,661,243` bytes, MediaPipe bundle with TFLite model, SentencePiece tokenizer, and metadata) and `gemma3-1B-it-int4.tflite` (`549,971,728` bytes). Contains zero training checkpoints.
- **Decision:** `gemma3-1B-it-int4.task` is directly compatible with the app's `MediaPipeLocalAiEngine` runtime specification. No training or conversion is required to test baseline Gemma 3 on-device.

---

### D-011: Realignment of Gate 0C Acceptance Criteria
- **Date:** 2026-10-16
- **Context:** Recovery Checkpoint v2 requires Gate 0C to track the original Master Prompt v3.1 pre-migration baseline safety criteria.
- **Decision:** Gate 0C tracks six discrete criteria: (1) Baseline build, (2) Unit test suite, (3) Feature flag default-off & kill switch, (4) Verifiable rollback procedure, (5) Android compatibility evidence, (6) Out-of-repo safety snapshot.
- **Status:** Criteria 3, 4, and 6 are VERIFIED. Criteria 1 and 2 are NOT TESTED (no Gradle build/test run executed in read-only mode). Criterion 5 is PARTIAL. Gate 0C overall is marked **NOT VERIFIED**.

---

### D-012: Strategic Tradeoffs: Improving Existing Gemma 3 vs Gemma 4 Exploration
- **Date:** 2026-10-16
- **Analysis:**
  - **Option A (Safest & Fastest):** Operationalize existing `gemma3-1B-it-int4.task`. It already exists as a compiled, quantized MediaPipe bundle compatible with `MediaPipeLocalAiEngine.kt` and `ModelAssetManagerImpl.kt`. Improvement focus shifts to prompt engineering, FTS5/encrypted RAG emergency packs, and local evaluation harnesses. Zero risk of mobile export failure.
  - **Option B (Investigate Gemma 4):** Restrict Gemma 4 work strictly to desktop exploration and official export pipeline research. Do NOT train Gemma 4 weights until Google AI Edge publishes verified conversion tooling for the PLE architecture.

---

### D-013: Non-Destructive Dual-Slot Model Lifecycle Architecture & Invariants (Phase 0D)
- **Date:** 2026-10-16
- **Context:** Phase 0D mandates a persistent, safe model lifecycle supporting staging, validation, activation, and physical rollback without deleting or overwriting the active baseline Gemma 3 artifact.
- **Decision:** Implemented a slot-based persistent registry (`context.filesDir/ai_models/model_registry.json`) with three logical roles:
  1. `ACTIVE`: Currently loaded and operational model.
  2. `STAGED`: Validated candidate model residing in `ai_models/staging/`, awaiting explicit user or coordinator activation.
  3. `PREVIOUS`: Retained previous known-good model in its immutable slot, enabling immediate zero-loss rollback.
- **Enforced Safety Invariants (12/12):**
  1. *No Active Overwrite:* Candidates are imported to temporary staging (`ai_models/staging/`), never over the active model.
  2. *Staging Isolation:* Candidate files are validated in staging before any promotion.
  3. *Pre-Activation Validation:* Format (`.task` / `.litertlm`), minimum file size (> 100 MB), and cryptographic SHA-256 are verified prior to promotion.
  4. *Atomic Manifest Writes:* Manifest updates are written to `model_registry.json.tmp`, flushed to disk (`fd.sync()`), and atomically renamed.
  5. *Crash Resilience:* An interrupted or failed import leaves the active model and manifest fully operational.
  6. *Baseline Preservation:* Promoting a candidate to `ACTIVE` retains the prior active model in `PREVIOUS`.
  7. *Explicit Rollback:* Single-step rollback operation swaps `PREVIOUS` to `ACTIVE` and deallocates native runtime memory before re-initialization.
  8. *Inference Isolation:* Inference requests only access verified active slot files, never staging or partial files.
  9. *Concurrency Control:* All import, activation, rollback, and inference operations are serialized via coroutine Mutex lock.
  10. *Kill-Switch Integrity:* Kill switch forcibly unloads native LiteRT/MediaPipe engine instances and deallocates GPU/RAM regardless of active slot.
  11. *Fault-Tolerant Recovery:* Corrupted or missing manifests automatically synthesize a recovery state referencing the verified on-device baseline model.
  12. *Security & Flags:* Candidate models cannot bypass `ai_enabled` feature flags, permission checks, or emergency preemption controls.
- **Implications:** Verified on physical hardware (Motorola edge 50 pro). The baseline Gemma 3 1B INT4 model (`files/models/gemma3-1B-it-int4.task`) was preserved bit-for-bit while switching between slots and executing physical rollbacks.
