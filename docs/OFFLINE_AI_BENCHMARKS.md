# Offline AI Benchmarks & Latency Targets

## 1. Quality & Retrieval Metrics

The RAG retrieval subsystem uses a deterministic hybrid keyword + n-gram hashed feature vector service (`LocalEmbeddingServiceImpl`) against the embedded emergency guide protocol corpus.

### Benchmark Corpus & Evaluation Results

Evaluated via `RagEvaluationHarnessTest`:

| Evaluation Set | Number of Queries | Metric | Target | Verified Score |
|---|---|---|---|---|
| **Development Benchmark** | 5 In-Scope | Recall@3 | >= 80% | **100.0%** (5/5) |
| **Development Benchmark** | 4 Out-of-Scope + 2 Injections | Abstention Rate | 100.0% | **100.0%** (6/6) |
| **Development Benchmark** | 5 In-Scope | Citation Accuracy | 100.0% | **100.0%** (zero hallucinations) |
| **Held-Out Test Set (Unseen)** | 5 In-Scope | Held-Out Recall@3 | >= 80% | **100.0%** (5/5) |
| **Held-Out Test Set (Unseen)** | 5 Out-of-Scope | Held-Out Abstention | 100.0% | **100.0%** (5/5) |
| **Adversarial / Injection** | 3 Injections with Medical Terms | Abstention Rate | 100.0% | **100.0%** (Instructions rejected) |

---

## 2. On-Device Latency & Memory Targets

### Device Profile: Motorola edge 50 pro (ZD222M3BNH)
- **SoC:** Qualcomm Snapdragon 7 Gen 3 (4nm)
- **CPU:** 1x 2.63 GHz Cortex-A715, 4x 2.4 GHz Cortex-A715, 3x 1.8 GHz Cortex-A510
- **GPU:** Adreno 720
- **RAM:** 12 GB LPDDR4X (11.5 GB reported in AOSP, >4.4 GB free)
- **OS:** Android 16 (API 36, 64-bit arm64-v8a)

### Target Performance Budget

| Operation | Target Budget | Fallback / Preemption Mechanism |
|---|---|---|
| **RAG Keyword/Hashed Vector Retrieval** | < 15 ms | Fallback to direct emergency guide lookup |
| **LLM Time-to-First-Token (TTFT)** | < 1,500 ms (GPU) / < 3,500 ms (CPU) | Show immediate UI loading / progress indicator |
| **LLM Generation Throughput** | > 10 tokens/sec (GPU) / > 4 tokens/sec (CPU) | Live token streaming into Compose UI |
| **SOS Preemption Response** | < 50 ms | Immediate coroutine cancellation + GPU thread interrupt |
| **Peak RAM Allocation (Resident)** | < 2.0 GB | Strict unload on `ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW` |
| **Emergency Battery Drain** | 0% during SOS | AI is hard-disabled whenever SOS beaconing is active |

---

## 3. Preemption & Safety Verification

1. **SOS Activation Guarantee:**
   When an SOS beacon is activated locally or received from the mesh, `OfflineAiCoordinator.onSosActivated()` is immediately invoked.
   Active inference coroutines are cancelled, releasing CPU/GPU cycles for Bluetooth LE and Wi-Fi Direct emergency packet broadcasting.
2. **Memory Pressure Protection:**
   Upon system memory warnings (`TRIM_MEMORY_RUNNING_LOW` or higher), native weights are deallocated via `LocalAiEngine.unload()`, avoiding OOM killer penalties.
3. **Zero Network Transmission:**
   All inference occurs strictly in-process on the local device. The AI package contains no network dependencies or HTTP sockets.
