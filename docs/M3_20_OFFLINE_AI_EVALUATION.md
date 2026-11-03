# NEXUS-SUPPORT M3.20 — OFFLINE GEMMA AI & EMERGENCY RAG EVALUATION

**Milestone:** M3.20  
**Date:** 2026-11-03  
**Status:** COMPLETE (L2 UNIT & ISOLATION VERIFIED) / BLOCKED (L5 PHYSICAL NPU BENCHMARKS)  
**Test Suites:**
- `app/src/test/java/com/meshlink/app/ai/coordinator/OfflineAiCoordinatorTest.kt` (18/18 passed)
- `app/src/test/java/com/meshlink/app/ai/security/AiSafetyGuardTest.kt` (7/7 passed)
- `app/src/test/java/com/meshlink/app/ai/rag/RagEvaluationHarnessTest.kt` (4/4 passed)
- `app/src/test/java/com/meshlink/app/ai/data/ModelAssetManagerTest.kt` (20/20 passed)
- Total AI Unit Tests in `:app`: 90/90 passed (100% green)

---

## 1. Executive Summary

Phase M3.20 evaluates the on-device offline AI architecture for Nexus-Support, encompassing Google MediaPipe LLM Inference with quantized Gemma, emergency retrieval-augmented generation (RAG), and strict safety invariants protecting the host device and mission-critical communications.

In strict compliance with our zero-fabrication charter, test results are stratified by validation layer:
- **L2 (Unit & Simulated Engine Isolation):** Verified via 90 automated tests running on the MS OpenJDK 21 test runner.
- **L5 (Physical Hardware NPU Benchmarks):** Formally marked `BLOCKED (PHYSICAL DEVICE REQUIRED)` until a physical Motorola Edge 50 Pro (Snapdragon 7 Gen 3 NPU/GPU) is connected.

---

## 2. Safety Invariant 8 — Non-Blocking AI Execution

### 2.1 The Invariant
> **Safety Invariant 8:** AI processing must never block, delay, or interrupt the SOS emergency pipeline. If an AI inference task is running when an emergency SOS is triggered, the AI workload must be immediately aborted and its resources yielded to the communication subsystem.

### 2.2 Architectural Implementation & Evidence
- **Immediate Preemption on SOS Activation:**
  `OfflineAiCoordinatorImpl.kt:215`:
  ```kotlin
  override fun onSosActivated() {
      AiLogger.w("SOS ACTIVATED: Immediately pre-empting all AI inference tasks")
      isSosActive.set(true)
      activeInferenceJob?.cancel()
      activeInferenceJob = null
  }
  ```
- **Automated Test Proof:**
  `OfflineAiCoordinatorTest.kt::onSosActivated_preemptsActiveInferenceImmediately`:
  A long-running 10s inference was launched. Upon triggering `onSosActivated()`, the inference job was cancelled in 100ms and returned `AiError.SosPriorityPreempted`.

---

## 3. Safety Invariant 10 — Component Failure Isolation

### 3.1 The Invariant
> **Safety Invariant 10:** Failed, crashed, or hanging non-critical components (AI model engine, map rendering, UI charts) must never cause application termination or prevent sending, receiving, or relaying emergency SOS beacons.

### 3.2 Architectural Implementation & Evidence
- **Native Crash Containment:**
  `OfflineAiCoordinatorTest.kt::initialize_whenEngineThrowsUncaughtException_transitionsToError`:
  When a mock native engine threw an unhandled `IllegalStateException("Native crash")`, the exception was safely caught, logged via `AiLogger.e`, and transitioned state cleanly to `AiCapabilityState.ERROR`. The host process and networking remained completely intact.
- **Low-Memory (OOM) Protection:**
  `OfflineAiCoordinatorTest.kt::onTrimMemory_whenRunningLow_unloadsEngineAndTransitionsToLowMemory`:
  On receiving Android system `TRIM_MEMORY_RUNNING_LOW`, the coordinator automatically unloads the AI engine, frees allocated memory buffers, and prevents system Out-Of-Memory termination.
- **Timeout Bounding:**
  `OfflineAiCoordinatorTest.kt::executeInference_timesOutProperly`:
  Inference jobs exceeding the configured timeout (e.g. 500ms or 5000ms) are aborted via `withTimeoutOrNull` and return `AiError.Timeout`.

---

## 4. Emergency RAG Safety & Hallucination Prevention

- **Adversarial Override Defense:**
  `AiSafetyGuard.kt` intercepts prompt injections (`"ignore previous instructions"`, `"system override"`, `"pretend to be DAN"`) and cyberattack generation requests (`"how to exploit SQL"`) with static refusals before consuming NPU or token cycles.
- **Rescue Hallucination Safeguard:**
  Language models are prohibited from hallucinating external rescue status (`"I have called 911"`, `"paramedics are en route"`). The `validateOutput` interceptor detects such claims and appends an explicit safety disclaimer:
  > *"[Safety Advisory: Nexus-Support offline AI operates entirely locally on-device and cannot dispatch emergency services or verify external rescue operations. Use the SOS beacon to broadcast emergency beacons to nearby mesh peers.]"*

---

## 5. Physical Device Benchmark Status (L5)

| Metric | Target Specification | Current Status | Note |
| :--- | :--- | :--- | :--- |
| **Gemma 2B Quantized Model Size** | < 1.6 GB INT4 | PENDING DOWNLOAD | Staged via `ModelAssetManager` slot system |
| **Snapdragon 7 Gen 3 NPU TTFT** | < 800 ms | **BLOCKED (HARDWARE)** | Requires physical Motorola Edge 50 Pro |
| **Generation Speed** | > 12 tokens/sec | **BLOCKED (HARDWARE)** | Requires physical Motorola Edge 50 Pro |
| **Peak RAM Consumption during inference** | < 2.2 GB | **BLOCKED (HARDWARE)** | Requires Android Studio Memory Profiler |
| **Battery drain per 50 queries** | < 3% battery | **BLOCKED (HARDWARE)** | Requires physical hardware discharge testing |
