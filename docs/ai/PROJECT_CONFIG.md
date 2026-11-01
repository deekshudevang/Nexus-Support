# Nexus-Support Offline AI — Project Configuration (Phase 0A)

**Document Version:** 1.0.0
**Phase:** 0A (Project Configuration)
**Status:** VERIFIED (local repository & system audit)
**Last Updated:** 2026-10-16

---

## 1. Target Country and Regions for Emergency Guidance

- **Primary Baseline:** Global / United States baseline for first-aid protocols (aligned with American Red Cross and FEMA universal response guidance).
- **Target Regions:** Regional emergency dispatch integration is out of scope for fully offline mesh operation; guidance directs users to local emergency services.
- **Specific Country / Regional Localizations:** `UNAVAILABLE` (Pending stakeholder regional targeting specification; currently defaulted to universal disaster response protocols).

---

## 2. Target Languages and Evaluation Requirements

- **Primary Target Language:** English (`en`).
  - **Required Evaluation:**
    - 45-query verified RAG test suite (`RagEvaluationHarnessTest.kt`).
    - Emergency safety abstention & anti-hallucination suite (`AiSafetyGuardTest.kt`).
    - Mesh message summarization fact-preservation benchmark.
    - Instruction-following and refusal benchmark.
- **Secondary / Localized Languages (e.g., Spanish, Hindi):** `UNAVAILABLE` (No translation bundles or localized emergency guides currently exist in `app/src/main/res/values/` or `app/src/main/assets/emergency_packs/`).

---

## 3. Approved Authoritative Emergency Sources

All emergency guidance within the RAG subsystem must strictly trace to the following approved corpus:

| Guide ID | Guide Title | Authoritative Source | Version / Date | Status |
|---|---|---|---|---|
| `cpr` | CPR (Cardiopulmonary Resuscitation) | American Red Cross Guidelines | 2020/2024 Update (AOSP Bundled v1.0) | VERIFIED |
| `bleeding` | Severe Bleeding Control | American Red Cross / ACS Stop the Bleed | 2020 Update (AOSP Bundled v1.0) | VERIFIED |
| `burn_injury` | Burn Treatment | American Burn Association / Red Cross | 2021 Update (AOSP Bundled v1.0) | VERIFIED |
| `choking` | Choking Response | American Red Cross Guidelines | 2020/2024 Update (AOSP Bundled v1.0) | VERIFIED |
| `earthquake` | Earthquake Response | FEMA / USGS "Drop, Cover, and Hold On" | v1.0 (AOSP Bundled) | VERIFIED |
| `flood` | Flood Safety | FEMA / National Weather Service ("Turn Around Don't Drown") | v1.0 (AOSP Bundled) | VERIFIED |
| `fire` | Fire Emergency | NFPA / FEMA Fire Safety Guidelines | v1.0 (AOSP Bundled) | VERIFIED |
| `fracture` | Fracture & Bone Injury | Wilderness Medical Society / Red Cross | 2021 Update (AOSP Bundled v1.0) | VERIFIED |
| `shock` | Treating Shock | American Red Cross Emergency Care | 2020 Update (AOSP Bundled v1.0) | VERIFIED |
| `poisoning` | Poisoning Response | American Association of Poison Control Centers (AAPCC) | 2022 Update (AOSP Bundled v1.0) | VERIFIED |

*Note: Emergency facts belong exclusively in the RAG repository (`emergency_packs/guides.json`) and must not be hallucinated into fine-tuning weights.*

---

## 4. Minimum Android Device Requirements

- **Operating System Version:** Android 8.0 (Oreo, API Level 26) minimum (`minSdk = 26`), compileSdk = 36, targetSdk = 36.
- **CPU / ABI:** 64-bit ARM (`arm64-v8a`) with NEON instructions. 32-bit (`armeabi-v7a`) is NOT supported for LLM inference.
- **Minimum Device RAM:**
  - **Hard Minimum (Inference):** 6 GB total system RAM (with at least 2.5 GB free memory available to the app process).
  - **Recommended:** 8 GB+ RAM for stable LiteRT-LM execution of quantized Gemma 4 E2B models without risk of LowMemory/OOM kills.
  - **Devices with <4 GB RAM:** LLM inference is disabled by default; app falls back to direct RAG excerpt rendering without running on-device generative models.
- **Storage Headroom:** Minimum 3.5 GB free internal flash storage on `/data` partition for model weights and cache.
- **Hardware Acceleration:** Vulkan 1.1+ or OpenCL-capable GPU delegate supported; CPU fallback via XNNPack enabled when GPU acceleration is unavailable.

---

## 5. Cloud Resources & Budget Authorization

- **Paid Cloud GPU Authorization:** **NOT AUTHORIZED**. (Prompt section 4.2 requires explicit user approval before provisioning paid compute).
- **Budget Cap:** `$0.00` (All training and evaluation must run on the local development machine unless explicitly authorized by the user).

---

## 6. Current Android Model, Runtime, and Baseline Device

- **Current In-App Model:** No model file bundled in APK (preventing APK bloat >1 GB). Supports runtime import from app-private directory (`/data/user/0/com.meshlink.app/files/models/`).
- **Target Model Architecture:** Gemma 4 instruction-tuned Edge 2B (`google/gemma-4-E2B-it`).
- **Android Inference Runtime:**
  - MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai:0.10.35`)
  - Google AI Edge LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.17.1`)
- **Baseline Test Device:**
  - Device Model: Motorola edge 50 pro (`ZD222M3BNH`)
  - OS / ABI: Android 16 (SDK 36, `arm64-v8a`)
  - Physical Device Connection Status: `UNAVAILABLE` (No device currently attached via `adb devices`).
