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
- **Decision:** Pretrained Gemma 4 models will load via official `.litertlm` / `.task` packages. Fine-tuned weights will be merged via `peft.merge_and_unload()` and exported to `.litertlm` using the Google AI Edge `litert-lm` / `ai-edge-torch` export pipeline.
- **Implications:** Clean, native GPU acceleration on mobile (Adreno/Mali via OpenCL/Vulkan) with CPU fallback via XNNPack.
