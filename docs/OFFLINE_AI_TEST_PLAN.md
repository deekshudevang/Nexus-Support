# Offline AI Verification & Test Plan

## 1. Test Levels & Methodology

Tests follow strict tiered classification:
- **L1 (Static / Compile):** Gradle assemble and compile tasks verifying zero syntax, type, or build failures.
- **L2 (Unit & JVM):** Robolectric and MockK unit tests verifying business logic, state transitions, prompt engineering, RAG indexing, and error handling without hardware dependencies.
- **L3 (Device & Emulator):** Real hardware execution verifying local inference latency, RAM consumption, and offline operation in Airplane Mode.

---

## 2. Test Matrix

| ID | Test Category | Scenario | Expected Behavior | Level | Status |
|---|---|---|---|---|---|
| AI-T01 | State Machine | Coordinator transitions `NOT_INSTALLED` -> `VERIFYING` -> `READY` | State updates monotonically; invalid states rejected | L2 | NOT_STARTED |
| AI-T02 | SOS Preemption | Active SOS beacon triggered during inference | Active inference cancelled within 50ms; native resources released | L2 | NOT_STARTED |
| AI-T03 | Memory Pressure | Low memory notification received (`onTrimMemory`) | Model unloaded; state becomes `LOW_MEMORY` | L2 | NOT_STARTED |
| AI-T04 | Prompt Injection | Malicious instruction embedded in RAG emergency guide | Model prompted with injection barriers; instruction ignored | L2 | NOT_STARTED |
| AI-T05 | RAG Abstention | Query with zero relevant emergency guide matches | Clean fallback: explicitly states lack of local information | L2 | NOT_STARTED |
| AI-T06 | Offline Verification | Inference executed on physical hardware in Airplane mode | Response generated without network sockets or DNS lookups | L3 | NOT_RUN |
| AI-T07 | Checksum Integrity | Corrupted model binary passed to provisioner | Checksum mismatch detected; file quarantined/rejected | L2 | NOT_STARTED |
| AI-T08 | Summarization | Multi-turn mesh messages summarized | Original messages unaltered; summary generated as separate record | L2 | NOT_STARTED |
