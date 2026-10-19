#!/usr/bin/env python3
"""
Preflight Safety and Resource Headroom Checker for Gemma 4 Offline AI
Audits free disk, system RAM, Windows paging file, VRAM headroom, and active GPU processes.
"""
import sys
import os
import shutil
import subprocess
import platform

MIN_FREE_DISK_GB = 20.0
MIN_TOTAL_RAM_GB = 12.0
MIN_FREE_VRAM_GB = 6.0
MIN_PAGEFILE_GB = 16.0

def check_disk():
    total, used, free = shutil.disk_usage(".")
    free_gb = free / (1024 ** 3)
    total_gb = total / (1024 ** 3)
    print(f"Disk Space:       {free_gb:.1f} GB free of {total_gb:.1f} GB total")
    if free_gb < MIN_FREE_DISK_GB:
        print(f"  [FAILED] Free disk space ({free_gb:.1f} GB) is below minimum threshold ({MIN_FREE_DISK_GB} GB).")
        return False
    print("  [VERIFIED] Sufficient disk headroom available.")
    return True

def check_ram_and_paging():
    is_windows = platform.system() == "Windows"
    if not is_windows:
        print("System RAM:       Linux / non-Windows platform detected.")
        return True

    passed = True
    try:
        # Check RAM via powershell CIM
        ps_ram = 'powershell -NoProfile -Command "(Get-CimInstance Win32_OperatingSystem).TotalVisibleMemorySize; (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory"'
        res = subprocess.check_output(ps_ram, shell=True, text=True).strip().split()
        if len(res) >= 2:
            total_kb = float(res[0])
            free_kb = float(res[1])
            total_gb = total_kb / (1024 ** 2)
            free_gb = free_kb / (1024 ** 2)
            print(f"Physical RAM:     {free_gb:.2f} GB free of {total_gb:.2f} GB total")
            if total_gb < MIN_TOTAL_RAM_GB:
                print(f"  [FAILED] Total RAM ({total_gb:.1f} GB) is below required {MIN_TOTAL_RAM_GB} GB.")
                passed = False
            else:
                print("  [VERIFIED] System RAM threshold met.")

        # Check Paging File
        ps_pf = 'powershell -NoProfile -Command "(Get-CimInstance Win32_PageFileUsage).AllocatedBaseSize"'
        pf_res = subprocess.check_output(ps_pf, shell=True, text=True).strip()
        if pf_res:
            pf_mb = float(pf_res.split()[0])
            pf_gb = pf_mb / 1024
            print(f"Paging File:      {pf_gb:.1f} GB allocated")
            if pf_gb < MIN_PAGEFILE_GB:
                print(f"  [WARNING] Paging file is {pf_gb:.1f} GB (recommended >= {MIN_PAGEFILE_GB} GB for safety on Windows).")
            else:
                print("  [VERIFIED] Windows paging file headroom verified.")
    except Exception as e:
        print(f"  [WARNING] Could not complete WMI memory audit: {e}")

    return passed

def check_gpu_and_vram():
    print("\n--- Auditing GPU & VRAM Headroom ---")
    try:
        cmd = "nvidia-smi --query-gpu=name,memory.total,memory.used,memory.free,driver_version --format=csv,noheader,nounits"
        output = subprocess.check_output(cmd, shell=True, text=True).strip()
        parts = [p.strip() for p in output.split(",")]
        name = parts[0]
        total_mb = float(parts[1])
        used_mb = float(parts[2])
        free_mb = float(parts[3])
        driver = parts[4]

        total_gb = total_mb / 1024
        used_gb = used_mb / 1024
        free_gb = free_mb / 1024

        print(f"GPU Model:        {name}")
        print(f"Driver Version:   {driver}")
        print(f"VRAM Status:      {free_gb:.2f} GB free / {total_gb:.2f} GB total (In use: {used_gb:.2f} GB)")

        if free_gb < MIN_FREE_VRAM_GB:
            print(f"  [WARNING] Free VRAM ({free_gb:.2f} GB) is below optimal threshold ({MIN_FREE_VRAM_GB} GB).")
            print("  Recommended: Close background GPU-accelerated applications (browsers, IDEs, chat clients).")
        else:
            print("  [VERIFIED] VRAM headroom meets minimum requirement.")

        # List competing GPU processes
        proc_cmd = "nvidia-smi --query-compute-apps=pid,process_name,used_memory --format=csv,noheader,nounits"
        proc_out = subprocess.check_output(proc_cmd, shell=True, text=True).strip()
        if proc_out:
            print("\nActive Compute Processes on GPU:")
            for line in proc_out.splitlines():
                print(f"  {line}")
        else:
            print("No background compute processes actively locking GPU compute.")

        return True
    except Exception as e:
        print(f"  [FAILED] Could not query nvidia-smi: {e}")
        return False

def main():
    print("=" * 60)
    print("NEXUS-SUPPORT OFFLINE AI — PREFLIGHT HEADROOM AUDIT")
    print("=" * 60)

    disk_ok = check_disk()
    mem_ok = check_ram_and_paging()
    gpu_ok = check_gpu_and_vram()

    print("\n" + "=" * 60)
    if disk_ok and mem_ok and gpu_ok:
        print("[VERIFIED] Preflight checks passed. Hardware is ready for offline pipeline.")
        return 0
    else:
        print("[WARNING] Preflight detected resource constraints. Review recommendations above.")
        return 1

if __name__ == "__main__":
    sys.exit(main())
