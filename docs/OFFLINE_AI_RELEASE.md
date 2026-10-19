# Nexus-Support Offline AI Release Notes & Operational Manual

## 1. Feature Summary

The Nexus-Support Offline AI capability provides on-device disaster response assistance, first-aid protocol guidance, and incident message summarization without requiring an internet connection or cellular signal.

### Capabilities Included
1. **Offline Emergency RAG Assistant:**
   Retrieval-Augmented Generation grounded directly in the WHO/IFRC emergency guides stored in the local SQLite database. Provides verifiable step-by-step guidance for bleeding, burns, choking, hypothermia, and CPR.
2. **Deterministic Citation Verification:**
   Every generated response cites its authoritative guide source and step. If user queries are out-of-scope or lack sufficient evidence in the local emergency protocol corpus, the system abstains cleanly without hallucination.
3. **Emergency Preemption & SOS Protection:**
   The AI subsystem immediately stops and relinquishes CPU/GPU/memory resources the moment an SOS beacon is triggered or received from the mesh network.
4. **SAF Model Importer & Privacy Clean-up:**
   Users can import a Gemma model binary (`.bin` / `.task`) directly from device storage or SD card via the Android Storage Access Framework (SAF). A "Clear AI Data" button deletes all imported model files and cached embeddings instantly.
5. **Prompt Injection Hardening:**
   Adversarial instructions embedded in mesh messages or queries cannot override emergency instructions or trigger malicious commands.
6. **Zero-Network Privacy Architecture:**
   The entire AI pipeline runs offline. No prompts, summaries, or metadata are transmitted over internet or mesh channels.

---

## 2. Model Installation & Setup

1. **Obtaining Model Weights:**
   Download the quantized Gemma model binary (e.g., `gemma-2b-it-cpu-int4.bin` or `gemma-2b-it-gpu-int4.bin`) from Kaggle or Hugging Face. Acceptance of the Gemma Terms of Use is required.
2. **Importing into Nexus-Support:**
   - Open Nexus-Support Settings.
   - Navigate to the **Offline AI Settings** section.
   - Tap **Import Model (SAF)**.
   - Select the downloaded `.bin` or `.task` file from your local storage.
   - The app verifies file integrity and places it securely in internal app storage (`context.filesDir/gemma.bin`).
3. **Verification:**
   Once imported, the capability status transitions to `READY`. The AI Assistant screen becomes fully active.

---

## 3. Security & Safety Checklist

- [x] **SOS Priority Guarantee:** Tested under simulated and real SOS activation.
- [x] **No Internet Dependency:** Zero HTTP/socket operations in the AI module.
- [x] **Audit Logging:** No raw prompts, personal identifiers, or message text recorded in logs.
- [x] **Memory Management:** Listens to `ComponentCallbacks2.onTrimMemory()` to gracefully evict native weights during memory pressure.
- [x] **Emergency Kill Switch:** Instant deactivation available in Settings.
