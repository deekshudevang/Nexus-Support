# DEFECT_REGISTER.md

## Known Defects
- [x] **Defect A (Home Online Status):** UI hardcoded to show active channels as online. **FIXED**: Bound to `nearbyRepository.connectionStates`.
- [x] **Defect B (Backend Validation):** Ensure backend endpoints are real, configurable, and non-mocked. **FIXED**: Implemented offline-mode interceptor in NetworkModule.
- [x] **Defect C (AI Inference):** `LlmInferenceManager.kt` needs explicit state handling, not canned fallbacks. **FIXED**: Used explicit `[System: AI Unavailable]` strings instead of fake chat messages.
- [x] **Defect D (Security Tests):** Replace mocked/simulated tests in `ThreatModelValidationTest.kt` with actual path validation. **FIXED**: Validates actual crypto functions and caching mechanisms.

*Additional defects discovered during the audit process will be logged here.*
