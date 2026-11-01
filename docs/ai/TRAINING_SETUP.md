# Local Training Environment Setup & Operations Guide (`docs/ai/TRAINING_SETUP.md`)

**Document Version:** 1.0.0
**Phase:** 1 (Environment)
**Status:** VERIFIED
**Hardware Target:** Windows 11 Laptop, NVIDIA GeForce RTX 4060 Laptop GPU (8 GB VRAM)

---

## 1. System Specifications & Preflight Verification

- **Operating System:** Windows 11 Home 64-bit (Build 26300)
- **Host GPU:** NVIDIA GeForce RTX 4060 Laptop GPU (8,188 MiB GDDR6 VRAM)
- **Driver:** NVIDIA Driver Version 617.14 (CUDA UMD 13.4, runtime compatible with CUDA 12.6 / 12.4)
- **Host RAM:** 16 GB physical RAM
- **Host Paging File:** Minimum 24.0 GB allocated at `C:\pagefile.sys`
- **Disk Storage:** Minimum 30 GB free on `C:\` partition for checkpoints, cache, and exports.

Run the automated preflight verification before any training or evaluation job:
```powershell
python scripts\preflight.py
```

---

## 2. Virtual Environment Setup (Windows Native)

Create and activate the isolated virtual environment:
```powershell
# 1. Create isolated virtual environment
py -3.14 -m venv .venv

# 2. Activate environment
.\.venv\Scripts\Activate.ps1

# 3. Install CUDA-enabled PyTorch (CUDA 12.6 build)
pip install --force-reinstall --no-deps --index-url https://download.pytorch.org/whl/cu126 torch

# 4. Install pipeline dependencies
pip install -r requirements.txt
```

Verify GPU availability:
```powershell
python scripts\check_gpu.py
python scripts\check_env.py
```

---

## 3. Windows-Specific Optimizations & Safeguards

1. **DataLoader Workers:**
   Always set `dataloader_num_workers=0` on Windows. Python `multiprocessing` on Windows uses `spawn` instead of `fork`, which causes CUDA context corruption and process handle leaks if `num_workers > 0`.

2. **CUDA Memory Fragmentation (`PYTORCH_CUDA_ALLOC_CONF`):**
   Windows WDDM driver allocates GPU memory through the OS compositor. To prevent memory fragmentation from prematurely triggering OOM on the 8 GB GPU:
   ```powershell
   $env:PYTORCH_CUDA_ALLOC_CONF = "expandable_segments:True"
   ```

3. **Background Process Hygiene:**
   Close all non-essential GPU-accelerated applications (Edge, Chrome, Teams, WhatsApp, Discord, Claude Desktop) prior to launching training. This frees ~1.0 GB of VRAM, providing the full ~7.1 GB required for LoRA gradient checkpointing.

4. **Power & Thermal Throttling:**
   Ensure the laptop is plugged into AC power with OEM charger. Set Windows Power Mode to "Best Performance". Monitor temperatures using `nvidia-smi` (maintain GPU temperature < 75°C to avoid thermal frequency throttling).

---

## 4. WSL2 Ubuntu Fallback Environment

If Windows-specific library linking or Triton issues arise during complex ops, WSL2 provides direct Linux GPU passthrough:

```bash
# Launch WSL2
wsl -d Ubuntu

# Navigate to project mount
cd /mnt/c/Users/deeks/OneDrive/Desktop/Meshlink--Offline-messaging-main

# Setup Linux virtualenv
python3 -m venv .venv_wsl
source .venv_wsl/bin/activate
pip install --upgrade pip
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu124
pip install -r requirements.txt
```

---

## 5. Model Access Authentication

`google/gemma-4-E2B-it` is a gated model requiring explicit license agreement:

1. Accept model terms at: `https://huggingface.co/google/gemma-4-E2B-it`
2. Authenticate locally via CLI or environment variable:
   ```powershell
   huggingface-cli login
   # OR
   $env:HF_TOKEN = "your_personal_access_token_here"
   ```
3. Verify access without printing tokens:
   ```powershell
   python scripts\check_model_access.py
   ```

---

## 6. Failure Recovery and Checkpoint Resumption

If training terminates unexpectedly (e.g., thermal throttle, transient CUDA OOM, or manual interruption):

1. **Examine Log:** Review the last lines in `logs/phase3/latest_train.log`.
2. **Free GPU Memory:** Run `nvidia-smi` and kill any dangling Python processes.
3. **Resume from Checkpoint:**
   ```powershell
   python train.py --config configs/lora_gemma4_e2b.yaml --resume_from_checkpoint runs/latest/checkpoint-best
   ```
4. **VRAM Reduction Ladder (if OOM occurred):**
   - Step 1: Reduce `max_seq_length` from 512 to 256.
   - Step 2: Restrict LoRA target modules from all projection layers to attention projections only (`q_proj`, `v_proj`).
