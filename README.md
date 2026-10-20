<div align="center">

# Nexus Support
### Offline-first emergency communication and incident coordination

[![CI](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml/badge.svg)](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml)
[![CodeQL](https://github.com/deekshudevang/Nexus-Support/actions/workflows/codeql.yml/badge.svg)](https://github.com/deekshudevang/Nexus-Support/actions/workflows/codeql.yml)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**A mobile-first emergency mesh communication project with encrypted local storage, offline capabilities, and an optional incident-monitoring dashboard.**

</div>

> **Release honesty:** CI passing is not the same as field readiness. Physical multi-hop/RF behavior, historical database migration coverage, 16 KB page-size compatibility, production HTTPS/WSS deployment, and manual accessibility checks remain release gates. See the [acceptance criteria](docs/ACCEPTANCE_CRITERIA.md) and [run checkpoint](docs/RUN_CHECKPOINT.md) for evidence and current status.

## What is in this repository?

- **Android app** — Kotlin, Jetpack Compose and Material 3 UI.
- **Domain and data modules** — shared domain models and local persistence.
- **Mesh and transport modules** — peer communication, routing and transport abstractions.
- **Cryptography** — identity/key handling and message-protection components.
- **Offline assistant** — on-device AI integration for emergency-oriented workflows; model availability and device performance depend on supported hardware and installed assets.
- **Cloud backend** — FastAPI service for authorized incident and node APIs.
- **Operations dashboard** — React/Vite interface with a Leaflet map, incident filtering, and live updates where the backend is configured.

The app's offline communication path and the optional cloud dashboard have different connectivity requirements. The dashboard/backend must not be described as an offline service.

## Architecture at a glance

```text
Android UI (Compose)
        |
 ViewModels / use cases
        |
 Domain contracts
   |          |
Local data   Mesh / transports
(encrypted)  (peer communication)

Optional cloud path:
Android / field clients -> FastAPI -> authorized dashboard
```

## Repository guide

| Path | Responsibility |
|---|---|
| `app/` | Android application, navigation and UI integration |
| `core/domain/` | Domain models and contracts |
| `core/data/` | Local persistence and data access |
| `core/mesh/` | Peer discovery, transport and routing |
| `core/crypto/` | Cryptographic primitives and identity workflows |
| `cloud/backend/` | FastAPI endpoints and backend tests |
| `cloud/frontend/` | React/Vite incident dashboard |
| `docs/` | Architecture, security, operations, test matrices and release evidence |
| `scripts/` | Repository validation and maintenance scripts |

For detailed module ownership, see [Architecture](docs/architecture/ARCHITECTURE.md). For protocol and security details, see [Mesh Protocol](docs/protocol/MESH_PROTOCOL.md) and [Security Specification](docs/security/SECURITY_SPEC.md).

## Getting started

### Android

Requirements: Android Studio, Android SDK configured locally, and JDK 21 (the CI configuration uses JDK 21).

```bash
git clone https://github.com/deekshudevang/Nexus-Support.git
cd Nexus-Support
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`.

### Backend

Requirements: Python 3.12 recommended for matching CI. Use a virtual environment and review the backend environment/configuration documentation before exposing the service.

```bash
cd cloud/backend
python -m venv .venv
# macOS/Linux:
source .venv/bin/activate
# Windows PowerShell:
# .venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
pytest -v
```

Do not commit production secrets or real operator credentials. Configure authentication, allowed origins, database settings and TLS according to the deployment environment.

### Dashboard

Requirements: Node.js 24 to match CI and npm.

```bash
cd cloud/frontend
npm ci
npm run test
npm run build
npm run dev
```

Set the dashboard API/WebSocket endpoints using the documented environment variables for the deployment. Never place passwords, bearer tokens, signing keys, or other secrets in `VITE_*` variables: frontend variables are included in the browser bundle. Use HTTPS/WSS in production and configure a restrictive backend CORS/origin allow-list.

## Quality checks

From the repository root:

```bash
./gradlew testDebugUnitTest :core:domain:test --continue
./gradlew lintDebug --continue
./gradlew assembleDebug
```

Backend and dashboard checks are listed above. GitHub Actions runs the configured checks on supported branches; inspect the exact commit's run and test artifacts before relying on a result.

- [CI workflow](https://github.com/deekshudevang/Nexus-Support/actions/workflows/ci.yml)
- [CodeQL workflow](https://github.com/deekshudevang/Nexus-Support/actions/workflows/codeql.yml)
- [Product UI/UX and accessibility standards](docs/design/PRODUCT_UX_GUIDELINES.md)
- [Acceptance criteria](docs/ACCEPTANCE_CRITERIA.md)
- [Engineering checkpoint](docs/RUN_CHECKPOINT.md)
- [Defect register](docs/audit/DEFECT_REGISTER.md)
- [Device test matrix](docs/testing/DEVICE_TEST_MATRIX.md)

## Release gates

Before declaring a production release, close or explicitly scope each applicable gate with evidence tied to the exact release commit:

1. Recover authentic Room schema history or define a safe, supported legacy-upgrade policy and test it.
2. Exercise Meshtastic BLE/LoRa transmit, receive, acknowledgements and multi-hop routing on physical devices.
3. Verify APK alignment **and** native ELF 16 KB page-size compatibility with current Android tooling.
4. Deploy and verify real HTTPS/WSS, authentication, origin policy, expiry and reconnect behavior.
5. Complete TalkBack/accessibility checks on the critical SOS, chat, map and settings flows.
6. Build and publish the release artifact from the candidate SHA; record its checksum and signing status.
7. Retain test reports and report passed, failed and skipped counts without combining results from different commits.

See [open repository issues](https://github.com/deekshudevang/Nexus-Support/issues) for tracked follow-up work.

## Contributing

Please keep changes focused, preserve reproducible tests, update documentation when behavior changes, and distinguish automated evidence from device or production validation. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE).
