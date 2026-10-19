# Offline AI Architecture: Nexus-Support

## 1. System Overview & Life-Safety Isolation

The Nexus-Support Offline AI subsystem provides localized intelligence for disaster response, emergency triage, message summarization, and interactive assistance strictly without internet connectivity.

```
+-----------------------------------------------------------------------------------+
|                                  USER INTERFACE                                   |
|   +--------------------+     +---------------------+     +--------------------+   |
|   |  AiAssistantScreen |     | EmergencyGuideCards |     |  SummarizationBar  |   |
|   +---------+----------+     +----------+----------+     +---------+----------+   |
+-------------|---------------------------|--------------------------|--------------+
              |                           |                          |
              v                           v                          v
+-----------------------------------------------------------------------------------+
|                              COORDINATION & DOMAIN                                |
|   +---------------------------------------------------------------------------+   |
|   |                           OfflineAiCoordinator                            |   |
|   |   - Capability & State Machine (NOT_INSTALLED -> READY -> RUNNING)         |   |
|   |   - Feature Flag & Emergency Kill Switch Enforcement                      |   |
|   |   - SOS Preemption Gate (Immediately pauses/cancels AI on active SOS)      |   |
|   +---------------------------------------------------------------------------+   |
+-----------------------------------------------------------------------------------+
              |                           |                          |
              v                           v                          v
+-----------------------------------------------------------------------------------+
|                                SPECIALIZED SERVICES                               |
|   +-------------------+       +--------------------+      +-------------------+   |
|   | EmergencyRagService|       |MessageSummaryService|      | VoiceAssistant    |   |
|   +---------+---------+       +----------+---------+      +---------+---------+   |
+-------------|----------------------------|--------------------------|--------------+
              |                            |                          |
              v                            v                          v
+-----------------------------------------------------------------------------------+
|                             INFERENCE & RUNTIME LAYER                             |
|   +---------------------------------------------------------------------------+   |
|   |                               LocalAiEngine                               |   |
|   |   - MediaPipe Tasks GenAI (LiteRT) LLM Inference Engine                   |   |
|   |   - Quantized Gemma 2B/4B / Gemma 3 IT model execution                     |   |
|   |   - Memory Pressure Monitoring & Native Resource Deallocation             |   |
|   +---------------------------------------------------------------------------+   |
+-----------------------------------------------------------------------------------+
```

### Safety Priority Invariants
1. **SOS Primacy:** Any SOS transmission, receipt, or relay event pre-empts active AI inference tasks immediately.
2. **Crash Containment:** Any AI runtime failure, native library exception, or Out-Of-Memory (OOM) error is intercepted in an isolated coroutine handler and never crashes the host application or disrupts mesh routing.
3. **No Network Leakage:** The AI engine and RAG pipeline execute 100% locally with zero cloud API dependencies.

---

## 2. Component Taxonomy

### 2.1 Capability State Machine
```
   [NOT_INSTALLED] ------------ (Model Imported) ------------> [VERIFYING]
          ^                                                         |
          |                                             (Checksum Match)
          |                                                         v
   [UNSUPPORTED] <------- (Hardware Check Failed) <----------- [READY]
                                                                    |
                                                            (Inference Triggered)
                                                                    v
   [LOW_MEMORY] <-------- (OOM / TrimMemory) <---------------- [RUNNING]
```

States:
- `NOT_INSTALLED`: No verified model weights available in app storage.
- `VERIFYING`: SHA-256 verification in progress.
- `READY`: Model verified and idle, ready to execute inference requests.
- `RUNNING`: Actively processing an inference request.
- `LOW_MEMORY`: Device under RAM pressure; model weights unloaded to protect mesh communications.
- `UNSUPPORTED`: Device hardware or architecture does not meet execution constraints.
- `ERROR`: Unrecoverable model or runtime error.

### 2.2 Error Taxonomy
All AI operations return sealed `AiResult<T>` types with explicit error categories:
- `ModelMissing`: Model weights file absent from private storage.
- `ModelCorrupt`: Checksum mismatch or invalid header.
- `Unsupported`: Hardware / accelerator incompatibility.
- `LowMemory`: High RAM watermark exceeded; pre-empted to prevent OOM crash.
- `LowStorage`: Insufficient disk space to unpack or verify assets.
- `Timeout`: Inference exceeded bounded execution window (default 30 seconds).
- `Cancelled`: Request aborted by user navigation or timeout.
- `SosPriorityPreempted`: Request immediately aborted because an active SOS beacon was broadcast or received.
- `InvalidInput`: Query or context payload exceeded token budget.
- `RuntimeError`: Native runtime internal exception.

---

## 3. Data Flow & RAG Pipeline

1. **User Query Input:** Normalized and token-budgeted.
2. **Local Context Retrieval:** Exact keyword + semantic vector search against local `EmergencyGuideRepository` stored in SQLCipher database.
3. **Prompt Framing:** Strict delimiting of context blocks (`<context>...</context>`) with system prompt commanding the model to ignore any instructions inside documents (Prompt Injection Defense).
4. **Grounded Generation:** Streamed or synchronous completion generated by local Gemma model.
5. **Attribution & Provenance:** Source guide title, section, and date presented to the user alongside the response.
