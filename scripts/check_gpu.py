#!/usr/bin/env python3
"""
GPU Verification Script for Gemma 4 Offline Fine-Tuning Pipeline
Audits CUDA availability, device name, VRAM limits, fp16/bf16 support, and compute capability.
"""
import sys
import os

def main():
    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — GPU HARDWARE AUDIT")
    print("=" * 60)

    try:
        import torch
    except ImportError:
        print("[FAILED] PyTorch is not installed in the active environment.")
        sys.exit(1)

    print(f"PyTorch Version: {torch.__version__}")
    cuda_available = torch.cuda.is_available()
    print(f"CUDA Available:  {cuda_available}")

    if not cuda_available:
        print("\n[FAILED] CUDA is NOT available to PyTorch.")
        print("Remediation: Install CUDA-enabled PyTorch via:")
        print("  pip install --index-url https://download.pytorch.org/whl/cu126 torch")
        print("Or use WSL2 Ubuntu environment where native CUDA passthrough is verified.")
        sys.exit(2)

    device_count = torch.cuda.device_count()
    print(f"Device Count:    {device_count}")

    device_id = 0
    props = torch.cuda.get_device_properties(device_id)
    print(f"\nDevice 0: {props.name}")
    print(f"Compute Capability: {props.major}.{props.minor}")
    total_vram_gb = props.total_memory / (1024 ** 3)
    print(f"Total VRAM: {total_vram_gb:.2f} GB ({props.total_memory} bytes)")

    # Test allocation
    print("\n--- Testing CUDA Tensor Allocation & MatMul ---")
    try:
        x = torch.randn(1024, 1024, device="cuda", dtype=torch.float32)
        y = torch.matmul(x, x)
        torch.cuda.synchronize()
        print("[VERIFIED] Float32 tensor allocation and matrix multiplication passed.")
    except Exception as e:
        print(f"[FAILED] Float32 allocation failed: {e}")
        sys.exit(3)

    # Test FP16
    try:
        x_fp16 = torch.randn(1024, 1024, device="cuda", dtype=torch.float16)
        y_fp16 = torch.matmul(x_fp16, x_fp16)
        torch.cuda.synchronize()
        print("[VERIFIED] Float16 (fp16) allocation and compute passed.")
    except Exception as e:
        print(f"[FAILED] Float16 allocation failed: {e}")
        sys.exit(4)

    # Test BF16
    bf16_supported = torch.cuda.is_bf16_supported()
    print(f"BFloat16 Supported by Hardware: {bf16_supported}")
    if bf16_supported:
        try:
            x_bf16 = torch.randn(1024, 1024, device="cuda", dtype=torch.bfloat16)
            y_bf16 = torch.matmul(x_bf16, x_bf16)
            torch.cuda.synchronize()
            print("[VERIFIED] BFloat16 (bf16) allocation and compute passed.")
        except Exception as e:
            print(f"[FAILED] BFloat16 allocation failed: {e}")
            sys.exit(5)

    allocated_mb = torch.cuda.memory_allocated() / (1024 ** 2)
    reserved_mb = torch.cuda.memory_reserved() / (1024 ** 2)
    print(f"\nMemory Allocated: {allocated_mb:.2f} MB")
    print(f"Memory Reserved:  {reserved_mb:.2f} MB")

    # Clean up
    del x, y, x_fp16, y_fp16
    if bf16_supported:
        del x_bf16, y_bf16
    torch.cuda.empty_cache()

    print("\n[VERIFIED] GPU audit successfully passed.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
