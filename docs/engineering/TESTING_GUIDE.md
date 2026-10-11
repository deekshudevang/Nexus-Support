# Nexus-Support Testing Guide

## 1. Prerequisites & Environment
- **JDK:** OpenJDK 21 (Temurin / Microsoft OpenJDK 21.0.x)
- **Android SDK:** API 36 (Android 16), minSdk 26
- **Gradle:** 8.14.5 via `./gradlew` wrapper

---

## 2. Test Suites Architecture

The repository employs a multi-tiered automated verification strategy across 5 Gradle modules:

| Module | Scope | Key Test Suites |
|:---|:---|:---|
| `:core:crypto` | Cryptographic Primitives | Keystore, ECDH key agreement, AES-256-GCM symmetric encryption |
| `:core:data` | Room Persistence & DAOs | Database migrations, MessageDao, ContactDao, LocationDao |
| `:core:domain` | Domain Models & Business Logic | `MeshPacketTest`, packet prioritization |
| `:core:mesh` | Mesh Networking & Routing | `MeshRouterTest`, `SosMessageIdTest`, `NearbyRepositoryImplEndpointLostTest`, `MeshPacketSerializerSecurityTest`, `LargeScaleMeshSimTest` |
| `:app` | UI, ViewModels, & Offline AI | `AiAssistantViewModelTest`, `AiSafetyGuardTest`, `EmergencyRagServiceTest`, `SosViewModelTest`, `MainViewModelTest` |

---

## 3. Running Tests Locally

### Run All Unit Tests
```bash
./gradlew testDebugUnitTest --console=plain
```

### Run Module-Specific Test Suites
```bash
# Core Mesh protocol & routing tests
./gradlew :core:mesh:testDebugUnitTest --console=plain

# Core Data & Room DAO tests
./gradlew :core:data:testDebugUnitTest --console=plain

# App & Offline AI safety tests
./gradlew :app:testDebugUnitTest --console=plain
```

### Run Focused Security & Reliability Regressions
```bash
# SOS 19-byte beacon collision resistance (Defect F)
./gradlew :core:mesh:testDebugUnitTest --tests "com.meshlink.app.mesh.transport.SosMessageIdTest"

# Stale peer eviction & connection lifecycle (Defects E, J, M)
./gradlew :core:mesh:testDebugUnitTest --tests "com.meshlink.app.mesh.repository.NearbyRepositoryImplEndpointLostTest"

# SOS reliable delivery & peer ACK generation (Defect H)
./gradlew :core:mesh:testDebugUnitTest --tests "com.meshlink.app.mesh.routing.MeshRouterTest"
./gradlew :app:testDebugUnitTest --tests "com.meshlink.app.ui.sos.SosViewModelTest"

# Mesh packet frame bounds & DoS defense
./gradlew :core:mesh:testDebugUnitTest --tests "com.meshlink.app.mesh.util.MeshPacketSerializerSecurityTest"

# Offline AI input & output safety guardrails
./gradlew :app:testDebugUnitTest --tests "com.meshlink.app.ai.security.AiSafetyGuardTest"
```

---

## 4. Test Reporting & Continuous Integration
In GitHub Actions (`.github/workflows/ci.yml`), HTML test reports and XML JUnit artifacts are captured and published under:
- `**/build/reports/tests/testDebugUnitTest/`
- `**/build/test-results/testDebugUnitTest/`
