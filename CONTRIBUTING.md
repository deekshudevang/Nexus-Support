# Contributing to Nexus Support

Thank you for your interest in contributing to **Nexus Support**! We welcome contributions ranging from bug fixes and security audits to new mesh routing algorithms and documentation improvements.

---

## 🧭 Code of Conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this standard.

---

## 🛠️ Development Setup

### Prerequisites
- **JDK 17+** (JDK 17/21 Temurin recommended)
- **Android Studio** (Meerkat / Ladybug or newer)
- **Android SDK** with platform `android-36` and Build Tools
- At least **two physical Android devices** (API 26+) for end-to-end mesh testing (Nearby Connections requires physical Bluetooth & Wi-Fi Direct radios)

### Building the Project

```bash
# Clone the repository
git clone https://github.com/deekshudevang/Nexus-Support.git
cd Nexus-Support

# Build debug APK
./gradlew assembleDebug

# Run JVM Unit Tests across all modules
./gradlew test
```

---

## 🌿 Branching Strategy & Workflow

We follow a GitHub Flow model:

1. **Fork** the repository and create your branch from `master` (or `main`):
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. **Naming Conventions**:
   - `feat/<feature-name>`: New functionality
   - `fix/<bug-name>`: Bug fixes
   - `security/<patch-name>`: Cryptographic or protocol security patches
   - `docs/<doc-name>`: Documentation improvements
   - `refactor/<cleanup>`: Code restructuring without behavior changes

---

## 💬 Commit Message Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat: add offline Mapsforge tile cache manager`
- `fix: prevent split-horizon echo loop in mesh packet router`
- `security: enforce StrongBox backing for EC P-256 identity key`
- `test: add 20-node network partition and reconnect simulation`
- `docs: update mesh protocol specification for vector clock sync`

---

## 🧪 Testing Requirements

Every PR must pass automated testing:

```bash
# Run mesh routing & simulation unit tests
./gradlew :core:mesh:test

# Run crypto & cipher tests
./gradlew :core:crypto:test

# Run full project unit test suite
./gradlew test
```

When modifying `MeshRouter` or packet serialization, ensure you add corresponding tests in `:core:mesh` or `:app`.

---

## 📦 Architecture Guidelines

1. **Strict Clean Architecture**:
   - `:core:domain`: Pure Kotlin entities and repository interfaces. No Android dependencies.
   - `:core:mesh`: Transport, routing table, deduplication, battery control, Protobuf wire models.
   - `:core:crypto`: Android KeyStore / StrongBox, ECIES, AES-GCM encryption.
   - `:core:data`: Room database with SQLCipher encryption, DAOs, Retrofit cloud sync.
   - `:app`: Jetpack Compose UI, ViewModels, Hilt DI, Navigation.
2. **Zero-Internet First**: Features must be fully functional offline. Internet connectivity is strictly an optional opportunistic enhancement.
3. **Memory & Battery Consciousness**: Background mesh scanning must adapt dynamically via `AdaptiveScanController`.

---

## 📬 Submitting a Pull Request

1. Push your changes to your fork.
2. Open a Pull Request against `master`.
3. Fill out the [Pull Request Template](.github/pull_request_template.md).
4. Ensure all CI checks pass.
5. Address any review feedback with clear, targeted commits.
