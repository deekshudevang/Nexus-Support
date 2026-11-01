# Nexus-Support Release Guide

## 1. Release Configuration
- **Application ID:** `com.meshlink.app`
- **Minimum SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 36 (Android 16)
- **Compile SDK:** 36
- **Version Code:** 1
- **Version Name:** 1.0.0
- **R8 / Minification:** Enabled (`isMinifyEnabled = true`, `isShrinkResources = true`)

---

## 2. Generating the Release APK

Execute from repository root:

```bash
./gradlew :app:assembleRelease --console=plain
```

On Windows:
```cmd
.\gradlew.bat :app:assembleRelease --console=plain
```

### Build Artifacts
- **Location:** `app/build/outputs/apk/release/`
- **Output Artifact:** `app-release-unsigned.apk` (~158 MiB)
  - Includes MediaPipe GenAI / LiteRT native C++ inference runtimes (`libtasks_genai_jni.so`), OsmDroid native renderers, and embedded emergency SQLite assets.
  - Large LLM weights (Gemma 2B/4B/Gemma-3-1B) are intentionally not bundled into the APK to keep download size manageable; they are dynamically imported to app-private storage.

---

## 3. Cryptographic Signing Procedure

Production releases must never be distributed with debug keys or unsigned.

### Step 1: Align APK (zipalign)
```bash
zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk
```

### Step 2: Sign with Production Keystore (apksigner)
```bash
apksigner sign --ks release.keystore \
               --ks-key-alias nexus_release_key \
               --out app-release-signed.apk \
               app-release-aligned.apk
```

### Step 3: Verify Signature
```bash
apksigner verify --verbose app-release-signed.apk
```

> **Security Mandate:** Never commit `release.keystore` or signing passwords to Git. CI pipelines inject keystore credentials via GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

---

## 4. On-Device Smoke Test & Sideloading
To install and test on a connected physical device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or for signed release:
adb install -r app-release-signed.apk
```
Launch and verify:
1. Permissions: Bluetooth, Nearby Devices, Location.
2. Mesh peer discovery & heartbeat.
3. Hardware SOS beacon activation and peer acknowledgement.
4. Offline AI emergency RAG guidance queries.
