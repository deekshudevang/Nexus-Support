# Offline AI Security & Threat Model

## 1. Threat Modeling (STRIDE)

| Threat Category | Potential Attack Vector | Mitigation in Nexus-Support |
|---|---|---|
| **Spoofing** | Attacker injects fake emergency knowledge packets into local RAG database | Guides must be cryptographically signed or bundled as verified immutable assets; user-imported documents require explicit trust approval. |
| **Tampering** | Model binary manipulated on disk to inject trojaned behavior or backdoor | SHA-256 integrity verification enforced prior to every model load; storage isolated to app-private internal storage (`context.filesDir/ai_models/`). |
| **Repudiation** | Attacker claims AI gave dangerous medical instruction | All AI responses stamped with disclaimer and source citations; user interaction logs retained locally in encrypted database without telemetry leakage. |
| **Information Disclosure** | Prompts or conversation history leaked via network telemetry or logs | Logging wrapper strictly redacts prompt, completion, image, and audio payloads; zero analytics, crash-reporting, or cloud telemetry configured for AI module. |
| **Denial of Service** | Complex or recursive prompt causing native thread freeze or OOM during SOS emergency | Inference scheduled on bounded background dispatcher with hard timeout (30s) and cancellation hook directly wired to SOS beacon listener. |
| **Elevation of Privilege** | Prompt injection in mesh messages tricking AI into sending unauthorized SOS or mesh packets | AI model output has **zero tool-execution authority**; AI cannot invoke network APIs, mesh transmission, or settings alteration. |

---

## 2. Privacy & Data Handling Principles

1. **Zero Cloud Inference:** All tensor computations execute on-device using local CPU/GPU accelerators.
2. **Encrypted Storage:** AI chat logs and derived message summaries are stored within the SQLCipher database.
3. **Data Erasure:** User-facing "Clear AI History" button completely purges conversation logs and temporary token caches.
4. **No Background Recording:** Audio capture is push-to-talk only with visible UI indicators.
