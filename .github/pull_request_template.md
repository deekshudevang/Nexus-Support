## 📝 Description

<!-- Briefly describe the problem solved or feature added by this PR. -->

## 🔗 Related Issues

<!-- Link issues here: Fixes #123, Closes #456 -->

## 🏗️ Architectural Impact

- [ ] Modifies wire packet protocol (`:core:mesh` / `mesh.proto`)
- [ ] Modifies cryptographic identity or encryption logic (`:core:crypto`)
- [ ] Modifies database schema or migrations (`:core:data`)
- [ ] Modifies UI / Jetpack Compose components (`:app`)
- [ ] Modifies background synchronization or WorkManager jobs (`:app`)

## ⚠️ Breaking Changes

- [ ] Does this PR introduce a breaking change? (e.g., changes to protocol schema, database schema, or public APIs)
- [ ] If yes, have backward compatibility or migrations been provided?

## 🧪 Testing & Verification

<!-- Describe how this was tested. Please provide test commands or steps. -->

- [ ] **Automated Testing:** Tests run and pass: `./gradlew testDebugUnitTest lintDebug --continue assembleDebug` (List specific test names added/modified)
- [ ] **Device Testing:** Verified on real hardware / multi-device environment. (List exact device models and OS versions)
- [ ] **Simulation Testing:** (Note: Simulation is never presented as field validation)
- [ ] **Docs Consistency:** Docs updated to match code state (`README.md`, `FINAL_AUDIT.md`, `DEVICE_TEST_MATRIX.md`).
  - *Trigger for Regeneration:* Any PR that alters a core feature (routing, crypto, UI workflow) or performs a physical field test MUST regenerate `FINAL_AUDIT.md` and `DEVICE_TEST_MATRIX.md` before merge.

## 📷 Screenshots / Artifacts (if applicable)

| Feature / UI Flow | Evidence / Screenshot |
|---|---|
| _Flow Name_ | _Screenshot or Log Output_ |

## 🛡️ Security & Zero-Internet Checklist

- [ ] Functions 100% offline without cellular or Wi-Fi internet access
- [ ] No hardcoded secrets, keys, or endpoints
- [ ] Passphrases and sensitive data protected via Android KeyStore / SQLCipher
- [ ] Clean Architecture boundaries preserved (no Android framework imports in `:core:domain`)
