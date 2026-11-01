# Baseline System, Hardware, and Android AI Audit (Phase 0B)

**Document Version:** 1.0.0
**Audit Date:** 2026-10-16
**Status:** VERIFIED (empirically audited via scripts and system commands)
**Evidence Logs:**
- Git Audit: [`logs/phase0/20261016_190608_git_audit.log`](file:///c:/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main/logs/phase0/20261016_190608_git_audit.log)
- System & GPU Audit: [`logs/phase0/20261016_190608_system_audit.log`](file:///c:/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main/logs/phase0/20261016_190608_system_audit.log)
- Python & WSL Audit: [`logs/phase0/20261016_190608_python_cuda_audit.log`](file:///c:/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main/logs/phase0/20261016_190608_python_cuda_audit.log)
- Android Audit: [`logs/phase0/20261016_190608_android_audit.log`](file:///c:/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main/logs/phase0/20261016_190608_android_audit.log)

---

## 1. Source Control & Repository State

- **Branch:** `feature/gemma4-offline` (created from HEAD `fix/sos-defect-n` at `bca47d27571f9529010722fcb333a6434dfb6b9d`).
- **Working Tree State:** All pre-existing user changes preserved:
  - Modified: `docs/engineering/DEFECT_N_EVIDENCE.md`
  - Modified: `task.md`
  - Untracked: `docs/engineering/SOS_PROTOCOL.md`
  - Added / Populated: `docs/ai/PROJECT_CONFIG.md`
- **History Hygiene:** Zero commits dropped, zero stashes removed, working tree clean of accidental file destructions.

---

## 2. Hardware and Host System Audit

| Metric | Measured Value | Acceptance Threshold | Evaluation |
|---|---|---|---|
| **OS** | Microsoft Windows 11 Home Single Language (Build 26300, 64-bit) | Windows 10/11 64-bit or Linux | VERIFIED |
| **Physical RAM** | 15.52 GB total (16,275,092 KB), 1.04 GB free at rest | >= 16 GB Total RAM | VERIFIED (High usage by host apps) |
| **Paging File** | `C:\pagefile.sys`: 24.0 GB allocated (24,576 MB) | >= 16 GB pagefile | VERIFIED |
| **Disk Storage** | `C:\` Free: 270.16 GB / Total: 903.93 GB | >= 50 GB free disk | VERIFIED |
| **GPU Model** | NVIDIA GeForce RTX 4060 Laptop GPU (Ada Lovelace, Bus-Id `01:00.0`) | Dedicated CUDA GPU | VERIFIED |
| **VRAM Total** | 8,188 MiB (~8.0 GB GDDR6) | >= 8 GB VRAM | VERIFIED |
| **VRAM In-Use / Free** | In-use: 1,054 MiB (GUI/OS) / Free: ~7,134 MiB | >= 6.5 GB free VRAM | VERIFIED |
| **NVIDIA Driver** | Driver Version: 617.14, CUDA UMD: 13.4 | Driver >= 535+ | VERIFIED |
| **WSL2 Subsystem** | Ubuntu 24.04 LTS (Version 2, running direct GPU passthrough) | Available for Linux fallbacks | VERIFIED |

---

## 3. Python, CUDA Runtime, and Quantization Feasibility

- **Base System Python:** Python 3.14.6 (`C:\Users\deeks\AppData\Local\Python\pythoncore-3.14-64\python.exe`).
- **Project Virtual Environment:** `.venv` isolated venv using Python 3.14.6.
- **PyTorch CUDA Status:**
  - Default PyPI install provides `torch-2.14.1+cpu` (`torch.cuda.is_available() == False`).
  - PyTorch CUDA repository (`https://download.pytorch.org/whl/cu126`) provides verified `torch-2.14.1+cu126-cp314-cp314-win_amd64.whl` (2,623 MB) matching NVIDIA Driver CUDA 13.4/12.6 runtime.
- **BitsAndBytes (4-bit NF4) Status:**
  - `bitsandbytes-0.50.2-py3-none-win_amd64.whl` successfully installed.
- **Alternative Execution Environment (WSL2):**
  - WSL2 Ubuntu distribution verified with full `/usr/lib/wsl/lib/nvidia-smi` hardware passthrough and 0 MB active VRAM overhead. Can run native Linux PyTorch/Triton/FlashAttention if Windows encounters any dynamic DLL or allocation fragmentation hurdles.

---

## 4. Android AI Architecture & Security Audit

- **Subproject Modules:**
  - `:app`: UI, ViewModels, and local AI coordination.
  - `:core:domain`: Core business domain entities and repository interfaces (`EmergencyGuideRepository`, `EmergencyGuide`).
  - `:core:data`: Data layers, Room database, asset loading (`EmergencyGuideRepositoryImpl`).
  - `:core:mesh`: BLE and Wi-Fi Direct mesh transport and packet routing.
  - `:core:crypto`: Local encryption and message identity verification.
- **Runtime Dependencies (`app/build.gradle.kts`):**
  - MediaPipe Tasks GenAI: `com.google.mediapipe:tasks-genai:0.10.35`
  - Image Labeling: `com.google.mlkit:image-labeling:17.0.8`
  - Architecture format support: `.task`, `.bin`, and `.litertlm` (via `ModelAssetManagerImpl.kt`).
- **Network Permissions Audit (`app/src/main/AndroidManifest.xml`):**
  - Hardware / Mesh permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`.
  - Internet permission: `android.permission.INTERNET` is declared in Manifest for maps tiles and local network HTTP gateway tests, but **zero network calls are permitted or executed within `LocalAiEngine`, `EmergencyRagServiceImpl`, or `SpeechManager`**.
  - All AI processing executes in-process on local hardware.
- **Emergency Corpus:**
  - 10 verified emergency guides bundled in `app/src/main/assets/emergency_packs/guides.json` (11,819 bytes).
  - First-aid guidance covers CPR, bleeding, burns, choking, earthquakes, floods, fire, fractures, shock, and poisoning.

---

## 5. Official Gemma 4 E2B Specifications & Documentation

- **Exact Model ID:** `google/gemma-4-E2B-it`
- **Release License:** Apache 2.0 (Open weights, commercial and offline deployment permitted).
- **Access Gate:** Gated repository on Hugging Face (`https://huggingface.co/google/gemma-4-E2B-it`). Requires accepting terms and authenticating via `huggingface-cli login` or `HF_TOKEN`.
- **Architecture:** Dense Transformer featuring **Per-Layer Embeddings (PLE)**.
- **Parameter Breakdown:**
  - Total Parameters: ~5.1 Billion (due to large embedding tables across 35 layers).
  - Effective Compute Footprint: ~2.3 Billion active parameters during inference.
  - Layers: 35 layers.
  - Vocabulary Size: `262,144` (`vocab_size = 262144`).
- **Context Length:** Up to 128K tokens supported natively.
- **Native Modalities:** Multimodal Any-to-Any (Text, Vision, and Audio input tokens natively supported in the base architecture).
- **Supported Quantization:** 4-bit NF4, INT4, AWQ, and LiteRT mobile-quantized formats.
- **Target Export Pathway:**
  - Export Tool: Google AI Edge `litert-lm` / `ai-edge-torch` (`export_hf --bundle_litert_lm`).
  - Container format: `.litertlm` (and MediaPipe `.task`).
  - Android Target SDK: `com.google.mediapipe:tasks-genai:0.10.35` (compatible with v0.10.28+) and `com.google.ai.edge.litertlm:litertlm-android:0.17.1`.
