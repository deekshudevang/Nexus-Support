# Offline AI Compatibility & Runtime Matrix

## 1. Runtime Selection & Upstream Justification (MediaPipe Tasks GenAI vs. LiteRT-LM)

### Upstream Evaluation Matrix

| Criterion | MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai`) | LiteRT-LM (`com.google.ai.edge.litert`) |
|---|---|---|
| **Version Selected** | `0.10.14` | `1.0.0-alpha` / Native C++ |
| **Release Date** | June 2024 (Stable Google Maven release) | August 2024 (Rebrand from TFLite, alpha bindings) |
| **Official Documentation** | [Google AI Edge: LLM Inference for Android](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android) | [Google AI Edge: LiteRT Overview](https://ai.google.dev/edge/litert) |
| **Architecture** | High-level Android SDK wrapping native LiteRT engine + OpenCL/Vulkan delegates | Low-level C++ runtime engine |
| **Android Integration** | Direct Kotlin/Java AAR (`LlmInference`, `LlmInferenceOptions`) | Requires custom JNI wrappers or experimental early-access bindings |
| **Model Format** | Bundled `.bin` / `.task` with embedded SentencePiece tokenizer & metadata | FlatBuffer `.tflite` / `.litert` with external tokenization |
| **Production Stability** | Verified stable in field, active in AOSP/Android ecosystem | Active migration target for Google AI Edge, but C++ first |

**Architectural Decision:**
We select `com.google.mediapipe:tasks-genai:0.10.14`. Under the hood, MediaPipe Tasks GenAI utilizes the LiteRT engine with GPU acceleration delegates (Adreno/Mali via OpenCL/Vulkan), but packages the complete pipeline (SentencePiece tokenization, KV cache management, and token generation loops) into a clean, thread-safe Android API. Switching to raw LiteRT-LM at this stage would require maintaining custom JNI bindings and manual tokenizer loops in C++ without providing any latency advantage on mobile.

---

## 2. Supported Model Configurations

### Primary LLM: Gemma 2B / Gemma 3 1B IT (Quantized)
- **Model Architecture:** Gemma Decoder-only Transformer
- **Format:** MediaPipe Task binary (`.bin` / `.task`) with bundled SentencePiece tokenizer
- **Quantization:** int4 / int8 weight quantization for mobile acceleration
- **Target Memory Footprint:** ~1.5 GB - 2.2 GB resident RAM
- **Hardware Acceleration:** GPU (OpenCL/Vulkan) via LiteRT delegate, with automatic CPU fallback
- **Licence:** Gemma Terms of Use (Permits redistribution with standard attribution)
- **Official Provenance:** Kaggle / Hugging Face Google repository

### Emergency Knowledge Base (RAG)
- **Format:** Local encrypted SQLite (SQLCipher v4.5.4)
- **Chunking:** 256 tokens per chunk with 32-token overlap
- **Search:** FTS5 full-text keyword indexing + lightweight local embeddings

---

## 3. Verified Physical Hardware Baseline

- **Device:** Motorola edge 50 pro (`ZD222M3BNH`)
- **OS Version:** Android 16 (API 36, arm64-v8a)
- **RAM:** 11.5 GB Total, 4.4 GB Free
- **Storage:** 154 GB available on `/data`
- **SoC:** Qualcomm Snapdragon 7 Gen 3 (Adreno 720 GPU)
- **Status:** Supported for full int4/int8 local Gemma 2B/4B/1B execution.
