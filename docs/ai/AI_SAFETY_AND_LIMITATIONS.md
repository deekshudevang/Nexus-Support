# Nexus-Support Offline AI Safety Architecture & Limitations

## Overview
Nexus-Support provides genuinely offline-capable AI assistance for disaster-response, emergency triage, and first-aid operations. The AI subsystem operates strictly on-device using Google MediaPipe GenAI / LiteRT runtime and an embedded SQLite/RAG knowledge base.

---

## 1. Safety Architecture

### A. Strict Input Policy & Classification (`AiSafetyGuard.kt`)
The AI safety guard enforces policy classification prior to model inference:
1. **Instruction Hierarchy & Prompt Injection Protection:**
   - Detects and rejects system override directives, context jailbreaks (e.g. DAN, Developer Mode), instruction bypasses, and prompts attempting to exfiltrate system instructions.
   - Refusal response: `AiSafetyGuard.INJECTION_REFUSAL_MESSAGE`.
2. **Harmful Content & Hazardous Activity Filtering:**
   - Blocks generation of explosive devices, pipe bombs, chemical/biological poisons (ricin, sarin, cyanide), kitchen-knife surgery/amputations, and ingestion of hazardous chemicals.
   - Refusal response: `AiSafetyGuard.HARMFUL_CONTENT_REFUSAL`.
3. **Cyberattack & Offensive Exploitation Restrictions:**
   - Differentiates offensive hacking (SQL injection exploits, exploit scripts, ransomware payloads) from legitimate defensive educational queries.
   - Dual-use defense: Allows questions on parameterized queries, prepared statements, input sanitization, and security best practices while refusing exploit generation.
   - Refusal response: `AiSafetyGuard.CYBER_ATTACK_REFUSAL`.

### B. Output Validation & Anti-Hallucination Guardrails
To prevent dangerous real-world misunderstandings during disasters:
- The model is strictly prohibited from claiming active real-world intervention (e.g. "I have dispatched the rescue team", "Emergency services are on their way to your coordinates").
- `AiSafetyGuard.validateOutput(text)` parses generated tokens. If dispatch hallucinations are detected, an explicit safety advisory is appended:
  > *[Safety Advisory: Nexus-Support offline AI operates entirely locally on-device and cannot dispatch emergency services or verify external rescue operations. Use the SOS beacon to broadcast emergency beacons to nearby mesh peers.]*

### C. Emergency RAG Grounding & Isolation
- Emergency guidance queries are routed through `EmergencyRagServiceImpl` with 100% verified first-aid and disaster protocols.
- Retrieved context passages are enclosed in delimited untrusted context markers (`<context>...</context>`).
- If evidence is insufficient, the system abstains rather than inventing medical procedures.
- Every grounded answer includes source citations (e.g., FEMA, Red Cross guidelines).

### D. Life-Safety Preemption
- The hardware SOS beacon and mesh transport take strict priority over AI inference.
- Activating an SOS beacon (`SosViewModel.toggleSos()` or receipt of an incoming mesh SOS packet) triggers `OfflineAiCoordinator.onSosActivated()`, which instantly cancels running AI inference and preserves CPU, memory, and battery life for mesh broadcasts.

---

## 2. Limitations

1. **Hardware Dependencies:**
   - General conversation and mixed RAG reasoning require Gemma 2B/4B/Gemma-3-1B model weights stored in app-private storage (`/data/user/0/com.meshlink.app/files/models/`).
   - If the model is not imported, the app gracefully falls back to displaying verified guide excerpts directly from the emergency database.
2. **Local Scope:**
   - Nexus-Support cannot call 911, dial cellular emergency dispatch, or trigger satellite beacons without external gateway nodes.
3. **Hardware Accelerators:**
   - On devices without Vulkan/OpenCL GPU acceleration, inference falls back to CPU delegates, which may increase generation latency.
