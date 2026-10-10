# DEFECT_REGISTER.md

## Known Defects
- [x] **Defect A (Home Online Status):** UI hardcoded to show active channels as online. **CLAIMED FIXED, NOT VERIFIED this session**: Bound to `nearbyRepository.connectionStates`. (HomeViewModelTest covers L2 state updates).
- [x] **Defect B (Backend Validation):** Ensure backend endpoints are real, configurable, and non-mocked. **CLAIMED FIXED, NOT VERIFIED this session**: Implemented offline-mode interceptor in NetworkModule.
- [x] **Defect C (AI Inference):** `LlmInferenceManager.kt` needs explicit state handling, not canned fallbacks. **CLAIMED FIXED, NOT VERIFIED this session**: Used explicit `[System: AI Unavailable]` strings instead of fake chat messages.
- [x] **Defect D (Security Tests):** Replace mocked/simulated tests in `ThreatModelValidationTest.kt` with actual path validation. **CLAIMED FIXED, NOT VERIFIED this session**: ThreatModelValidationTest covers isolated crypto/cache logic only, not the real router (L2, partial).

*Additional defects discovered during the audit process will be logged here.*

- [ ] **Defect E (Stale Entry Risk in NearbyRepository):** CONNECTED/AUTHENTICATED entries are cleared only by `onDisconnected` (210) and the `sendPayload` failure listener (585). There is no idle liveness check. CONFIRMED RISK, NOT FIXED: a liveness/heartbeat check was prototyped and rejected (false-disconnect risk under Doze); not implemented.
