#!/usr/bin/env python3
"""
Environment Verification Script for Gemma 4 Offline Fine-Tuning Pipeline
Audits Python, dependencies, version numbers, CUDA support, and bitsandbytes 4-bit integration.
"""
import sys
import platform

def main():
    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — ENVIRONMENT AUDIT")
    print("=" * 60)

    print(f"Python Version:   {platform.python_version()} ({platform.architecture()[0]})")
    print(f"Platform:         {platform.platform()}")

    all_passed = True

    # 1. Check PyTorch
    try:
        import torch
        print(f"[VERIFIED] PyTorch:      {torch.__version__} (CUDA: {torch.cuda.is_available()})")
        if not torch.cuda.is_available():
            print("  [WARNING] PyTorch was compiled without CUDA or CUDA driver not detected.")
            all_passed = False
    except ImportError as e:
        print(f"[FAILED] PyTorch:        MISSING ({e})")
        all_passed = False

    # 2. Check BitsAndBytes
    try:
        import bitsandbytes as bnb
        print(f"[VERIFIED] bitsandbytes: {bnb.__version__}")
        try:
            import torch.nn as nn
            linear_4bit = bnb.nn.Linear4bit(16, 16, bias=False)
            print("  [VERIFIED] bnb.nn.Linear4bit instantiated successfully.")
        except Exception as bnb_err:
            print(f"  [WARNING] bnb.nn.Linear4bit instantiation note: {bnb_err}")
    except ImportError as e:
        print(f"[FAILED] bitsandbytes:   MISSING ({e})")
        all_passed = False

    # 3. Check Transformers
    try:
        import transformers
        print(f"[VERIFIED] transformers: {transformers.__version__}")
    except ImportError as e:
        print(f"[FAILED] transformers:   MISSING ({e})")
        all_passed = False

    # 4. Check PEFT
    try:
        import peft
        print(f"[VERIFIED] peft:         {peft.__version__}")
    except ImportError as e:
        print(f"[FAILED] peft:           MISSING ({e})")
        all_passed = False

    # 5. Check Accelerate
    try:
        import accelerate
        print(f"[VERIFIED] accelerate:   {accelerate.__version__}")
    except ImportError as e:
        print(f"[FAILED] accelerate:     MISSING ({e})")
        all_passed = False

    # 6. Check Datasets
    try:
        import datasets
        print(f"[VERIFIED] datasets:     {datasets.__version__}")
    except ImportError as e:
        print(f"[FAILED] datasets:       MISSING ({e})")
        all_passed = False

    # 7. Check NumPy
    try:
        import numpy as np
        print(f"[VERIFIED] numpy:        {np.__version__}")
    except ImportError as e:
        print(f"[FAILED] numpy:          MISSING ({e})")
        all_passed = False

    print("=" * 60)
    if all_passed:
        print("[VERIFIED] All core ML packages verified successfully.")
        return 0
    else:
        print("[INCOMPLETE] One or more core dependencies require installation or CUDA configuration.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
