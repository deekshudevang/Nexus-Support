# Phase 0 Audit Script for Hardware, Git, and Environment
$ErrorActionPreference = "Continue"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logDir = "logs\phase0"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
}

$gitLog = Join-Path $logDir "${timestamp}_git_audit.log"
$sysLog = Join-Path $logDir "${timestamp}_system_audit.log"
$pyLog  = Join-Path $logDir "${timestamp}_python_cuda_audit.log"
$droidLog = Join-Path $logDir "${timestamp}_android_audit.log"

Write-Host "Running Git Audit -> $gitLog"
& {
    Write-Host "=== GIT STATUS ==="
    git status
    Write-Host "`n=== CURRENT BRANCH ==="
    git branch --show-current
    Write-Host "`n=== RECENT COMMITS ==="
    git log --oneline -n 5
    Write-Host "`n=== UNCOMMITTED CHANGES SUMMARY ==="
    git diff --stat
} *>&1 | Tee-Object -FilePath $gitLog

Write-Host "Running System Audit -> $sysLog"
& {
    Write-Host "=== OPERATING SYSTEM ==="
    Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version, BuildNumber, OSArchitecture, TotalVisibleMemorySize, FreePhysicalMemory | Format-List

    Write-Host "`n=== PAGING FILE ==="
    Get-CimInstance Win32_PageFileUsage | Select-Object Name, AllocatedBaseSize, CurrentUsage, PeakUsage | Format-List

    Write-Host "`n=== LOGICAL DISKS ==="
    Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | Select-Object DeviceID, VolumeName, @{Name="FreeSpaceGB";Expression={[math]::Round($_.FreeSpace/1GB,2)}}, @{Name="TotalSizeGB";Expression={[math]::Round($_.Size/1GB,2)}} | Format-Table

    Write-Host "`n=== NVIDIA SMI ==="
    nvidia-smi
} *>&1 | Tee-Object -FilePath $sysLog

Write-Host "Running Python and CUDA Audit -> $pyLog"
& {
    Write-Host "=== PYTHON PATH & VERSION ==="
    py -0p
    & "C:\Users\deeks\AppData\Local\Python\bin\python.exe" --version

    Write-Host "`n=== PIP PACKAGES ==="
    & "C:\Users\deeks\AppData\Local\Python\bin\python.exe" -m pip list

    Write-Host "`n=== WSL STATUS ==="
    wsl --status
    wsl -l -v
} *>&1 | Tee-Object -FilePath $pyLog

Write-Host "Running Android Audit -> $droidLog"
& {
    Write-Host "=== ADB DEVICES ==="
    & "C:\Users\deeks\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices -l

    Write-Host "`n=== ANDROID MANIFEST NETWORK PERMISSIONS ==="
    Select-String -Path "app\src\main\AndroidManifest.xml" -Pattern "permission"

    Write-Host "`n=== GRADIO / MEDIAPIPE DEPENDENCIES IN APP ==="
    Select-String -Path "app\build.gradle.kts" -Pattern "mediapipe|litert|tensorflow"

    Write-Host "`n=== LOCAL ASSET MODELS ==="
    Get-ChildItem -Path "app\src\main\assets" -Recurse -ErrorAction SilentlyContinue | Select-Object FullName, Length
} *>&1 | Tee-Object -FilePath $droidLog

Write-Host "`nPhase 0 Audit Scripts Complete."
