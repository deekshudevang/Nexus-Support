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

- [ ] **Automated Testing:** Tests run and pass: `./gradlew test` (List specific test names added/modified)
- [ ] **Device Testing:** Verified on real hardware / multi-device environment. (List exact device models and OS versions)
- [ ] **Simulation Testing:** (Note: Simulation is never presented as field validation)

## 📷 Screenshots / Artifacts (if applicable)

| Feature / UI Flow | Evidence / Screenshot |
|---|---|
| _Flow Name_ | _Screenshot or Log Output_ |

## 🛡️ Security & Zero-Internet Checklist

- [ ] Functions 100% offline without cellular or Wi-Fi internet access
- [ ] No hardcoded secrets, keys, or endpoints
- [ ] Passphrases and sensitive data protected via Android KeyStore / SQLCipher
- [ ] Clean Architecture boundaries preserved (no Android framework imports in `:core:domain`)
