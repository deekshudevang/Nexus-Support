# M3.14 — PHYSICAL ANDROID DEVICE VALIDATION REPORT
**Milestone:** Nexus-Support M3.14 — Release Blocker Closure & Go/No-Go Validation  
**Date:** 2026-11-01  
**Device Target:** Motorola Edge 50 Pro (`eqe_g`)  
**Status:** **BLOCKED (DEVICE DISCONNECTED AFTER ENUMERATION) / ARTIFACTS FULLY STAGED**

---

## 1. Device Identification & Hardware Enumeration

During M3.14 verification at `2026-11-01T21:57:43+05:30`, the physical device was detected and enumerated via Android Debug Bridge (ADB):

```text
List of devices attached
ZD222M3BNH             device product:eqe_g model:motorola_edge_50_pro device:eqe transport_id:1
```

### Windows PnP Bus Enumeration:
- **USB ADB Interface:** `USB\VID_22B8&PID_2E81\ZD222M3BNH` (Motorola ADB Interface)
- **MTP / WPD Interface:** `USB\VID_22B8&PID_2E82\ZD222M3BNH` (`motorola edge 50 pro`)
- **Bluetooth MAC:** `BTHENUM\DEV_74BEF3E68369` (Paired, Hands-Free / A2DP Sink)
- **Wi-Fi Direct Peer:** `SWD\WIFIDIRECT\9E:A7:98:8A:79:76` (`motorolaedge50pro_TAzo`)

---

## 2. Real Connectivity Assessment & Failure Analysis

Immediately following initial ADB daemon initialization, subsequent ADB commands at `21:58:17` and `22:07:06` returned:
```text
List of devices attached
(no devices/emulators found)
```

Windows Plug-and-Play status for `USB\VID_22B8&PID_2E81\ZD222M3BNH` transitioned to `Unknown`, indicating the physical USB connection was detached, power-cycled, or the handset locked into "Charging only" mode with USB debugging disabled when the screen is locked. Network ADB (`adb connect 192.168.1.4:5555`) was rejected (`WSAECONNREFUSED 10061`).

In strict adherence to the **Zero-Fabrication Mandate**, live on-device APK execution, logcat capture, and touch interactions are documented as **BLOCKED** until the user reconnects the device and taps "Always allow from this computer".

---

## 3. Staged Android Release Artifacts

The Android client build is clean, compiled, verified against 132 Gradle tasks, and ready for deployment:

| Artifact Type | File Path | File Size | Target ABIs |
| :--- | :--- | :--- | :--- |
| **Universal Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | 192.42 MB | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| **Android App Bundle (AAB)** | `app/build/outputs/bundle/debug/app-debug.aab` | 91.52 MB | Split by ABI, density, and language |

---

## 4. Manual Installation & Verification Runbook

When the Motorola Edge 50 Pro is re-engaged:

1. **Verify ADB Connection:**
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
   ```
2. **Install Universal APK:**
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s ZD222M3BNH install -r "app\build\outputs\apk\debug\app-debug.apk"
   ```
3. **Launch MainActivity:**
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s ZD222M3BNH shell am start -n com.rescuemesh.app/.MainActivity
   ```
4. **Monitor Mesh & SQLite Room Logs:**
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s ZD222M3BNH logcat -s RescueMesh:* MeshEngine:*
   ```

---

## 5. Decision & Blocker Summary

- **Physical Device:** Motorola Edge 50 Pro confirmed present on host environment.
- **Blocker:** Physical USB re-engagement / USB debugging authorization required.
- **Classification:** **PILOT BLOCKED (HARDWARE PHYSICAL ACCESS PENDING)**.

