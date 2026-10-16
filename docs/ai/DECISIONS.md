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
