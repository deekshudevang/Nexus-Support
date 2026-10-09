# DEFECT_REGISTER.md

## Known Defects
- [x] **Defect A (Home Online Status):** UI hardcoded to show active channels as online. **FIXED**: Bound to `nearbyRepository.connectionStates`.
- [ ] **Defect B (Backend Validation):** Ensure backend endpoints are real, configurable, and non-mocked.
- [ ] **Defect C (AI Inference):** `LlmInferenceManager.kt` needs explicit state handling, not canned fallbacks.
- [ ] **Defect D (Security Tests):** Replace mocked/simulated tests in `ThreatModelValidationTest.kt` with actual path validation.

*Additional defects discovered during the audit process will be logged here.*
