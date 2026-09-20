# Contributing to Nexus Support

First off, thanks for taking the time to contribute! Meshlink is meant to be a resilient, life-saving communication tool, and we rely on rigorous peer review to keep it secure and functional.

## Getting Started

1. **Prerequisites:**
   - JDK 17 or higher.
   - Android Studio (Ladybug or newer recommended).
   - At least 2 physical Android devices running API 26+. The Nearby Connections API (BLE / Wi-Fi Direct) relies heavily on hardware radios and does not work well in the emulator.

2. **Building the Project:**
   - Clone the repo: `git clone https://github.com/deekshudevang/Nexus-Support.git`
   - Run the automated tests and linter: `./gradlew test lint`
   - *Note: `assembleDebug` may fail if your local environment lacks the `jlink` executable for the data module.*

## How to Contribute

### 1. Atomic Commits
We prefer a history that tells a story. 
- Keep your commits small, atomic, and focused on a single logical change. 
- Avoid "mega-commits" that mix formatting changes with cryptographic logic.
- Reference GitHub issues in your commit messages (e.g., `fix(mesh): resolve TTL race condition - closes #42`).

### 2. Testing is Mandatory
If you fix a bug, add a failing test that reproduces the bug, then make it pass.
If you add a feature, especially in `core/mesh` or `core/crypto`, include unit tests. Our `LargeScaleMeshSimTest` is a great place to validate routing changes against a simulated 20-node mesh.

### 3. Architecture Decision Records (ADRs)
If you propose a major architectural change (e.g., swapping SQLCipher for another solution, or changing the routing heuristic), please submit an ADR in `docs/adr/` alongside your PR. This helps us document the *why* behind our decisions.

### 4. Structural Documentation Sync
Our CI pipeline enforces documentation consistency via the `docs-consistency` job (`scripts/verify-docs.sh`). To prevent your PR from failing:
1. **Code Change:** Make your logical changes.
2. **Test:** Run tests and collect evidence.
3. **Audit Evidence:** If you changed core features, run the physical or simulation procedures.
4. **Docs Update:** Update `FINAL_AUDIT.md` and `DEVICE_TEST_MATRIX.md` with your new results.
5. **Stamp the Audit:** Update the `**Last verified:**` line in `FINAL_AUDIT.md` with your exact commit hash so the CI script knows the docs are fresh.

Manual audit rewrites are the fallback, not the process. Keep the docs tightly coupled to the code.

### 5. Code Style
We use standard Kotlin conventions. Please do not over-comment your code. Strip redundant KDocs that merely restate function names. Instead, focus on commenting the *why* (e.g., `// HACK:`, `// NOTE:` for pragmatism).
