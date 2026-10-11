# Defect N Evidence Manifest

Governing standard: Execution Control F. All entries must reflect actual commands, captured exit codes, and non-fabricated logs.

---

## Step 0: Preflight, Baseline & Work Item

- **Candidate Commit SHA:** `3ebf99808a757011790d3f2baf8dfe673931a42a`
- **Starting Branch:** `fix/sos-defect-n`
- **Tree State (`git status --short`):**
  - Tracked modifications: `M task.md` (checkpoint and work item specification)
  - Untracked files: None
- **Preflight Commands & Actual Exit Codes:**
  - `git --version`: `git version 2.55.0.windows.3` (Exit code: 0)
  - `git branch --show-current`: `fix/sos-defect-n` (Exit code: 0)
  - `git rev-parse HEAD`: `3ebf99808a757011790d3f2baf8dfe673931a42a` (Exit code: 0)
  - `.\gradlew.bat --version`: Gradle 8.14.5, Kotlin 2.0.21, JVM 25.0.4.1 (Exit code: 0)
  - `Get-Content settings.gradle.kts`: Includes `:app`, `:core:domain`, `:core:data`, `:core:mesh`, `:core:crypto` (Exit code: 0)
  - `adb devices`: `List of devices attached` (Empty - 0 physical devices attached; radio behavior remains unverified on physical hardware) (Exit code: 0)
  - `.\gradlew.bat help --console=plain`: BUILD SUCCESSFUL (Exit code: 0)
- **Baseline Git Worktree:**
  - Worktree Path: `build/baseline_worktree` (detached at commit `3ebf998`)
  - Command: `git worktree add --detach build/baseline_worktree 3ebf998` (Exit code: 0)
  - Configuration: `local.properties` copied to worktree for Android SDK binding
- **Baseline Test Suite Run:**
  - Command: `.\gradlew.bat :core:domain:test :core:mesh:testDebugUnitTest --console=plain` (in `build/baseline_worktree`)
  - Exit Code: 0
  - Duration: 56s
  - Actionable Tasks: 47 executed
- **Baseline Test Counts (from `build/baseline_worktree/**/TEST-*.xml`):**
  - XML Files: 14
  - Total Tests: 76
  - Failures: 0
  - Errors: 0
  - Skipped: 0
- **CI Status:** `CI NOT VERIFIED` (no remote CI run completed on candidate commit `3ebf99808a757011790d3f2baf8dfe673931a42a`)

---

## Step 1: N1 Serialization

- **Hypotheses Tested & Results:**
  - *Gson pinned directly in build.gradle.kts instead of version catalog:* REPRODUCED & FIXED. Added `gson = "2.10.1"` to `[versions]` and `gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }` to `[libraries]` in `gradle/libs.versions.toml`. Updated `core/domain/build.gradle.kts` to `implementation(libs.gson)`.
  - *Gson Licence Undocumented:* CONFIRMED from official Google Gson repository: Apache License 2.0 (`Apache-2.0`). Documented in `DEFECT_N_EVIDENCE.md` and compatibility docs.
  - *Defect register claims 6 SosPacketDataTest tests but file had 3:* REPRODUCED. The source file `SosPacketDataTest.kt` had 3 `@Test` methods. Reconciled by adding the 3 missing/required tests: (1) payload size bounding against transport limit with deterministic truncation, (2) golden test old receiver parses new output, (3) golden test new parser accepts old format payloads with missing optional fields and unknown future fields. The test file now has exactly 6 `@Test` methods.
  - *No byte-size assertion against transport payload limit:* REPRODUCED. Google Nearby Connections `Payload.Type.BYTES` enforces a maximum payload size of 32,768 bytes (`Payload.MAX_BYTES`), and `MeshPacketSerializer` enforces 65,536 bytes. Untruncated SOS packets containing extensive medical notes exceeded 32,000 bytes (48,616 bytes in test), failing with `AssertionError: Serialized SOS byte size (48616) must not exceed transport limit (32000 bytes)`.
  - *No proof OLD receiver parses NEW output:* REPRODUCED & FIXED. Added golden test verifying that standard JSON parser (as used by legacy receivers in `MeshRouter`/`NearbyRepositoryImpl`) parses new output and extracts all fields (`uuid`, `senderId`, `senderName`, `emergencyType`, `severity`, `latitude`, `longitude`, `message`, `timestamp`, `hopCount`, `status`).
  - *Deterministic truncation behavior:* When serialized payload exceeds `MAX_TRANSPORT_PAYLOAD_BYTES = 32_000`, `message` is deterministically truncated with `TRUNCATION_MARKER = "\n[TRUNCATED: Medical info exceeded transport limit]"`. Invariant: location, event ID (`uuid`), `senderId`, and emergency type are NEVER dropped.
- **Before-Fix Failure Evidence:**
  - Command: `.\gradlew.bat :core:domain:test --console=plain` (task-6525)
  - Exit Code: 1 (BUILD FAILED)
  - Failure: `SosPacketDataTest > serialized SOS payload for worst realistic case must not exceed transport limit of 32KB FAILED (java.lang.AssertionError at SosPacketDataTest.kt:138)`
- **After-Fix Verification Evidence:**
  - Command: `.\gradlew.bat :core:domain:test --console=plain` (task-6533)
  - Exit Code: 0 (BUILD SUCCESSFUL in 21s)
  - Actionable Tasks: 4 executed
- **Fresh JUnit Test Results (`core/domain/build/test-results/test/TEST-com.meshlink.app.domain.model.SosPacketDataTest.xml`):**
  - Total Tests: 6
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - Tests Executed:
    1. `backward compatibility - tolerant parser accepts old format payload with 0_0 coordinates`
    2. `golden test - new parser accepts old format payloads with missing optional fields and unknown future fields`
    3. `location validation explicitly represents missing and invalid GPS`
    4. `serialized SOS payload for worst realistic case must not exceed transport limit of 32KB`
    5. `golden test - old receiver parser accepts new output`
    6. `toJson should produce valid JSON when message contains multiline text and backslashes`
- **Candidate Commit Message:** `fix(sos): N1 robust JSON serialization, payload size bounding, and golden wire compatibility`

---

## Step 2: N2 Location

- **Data Path Trace:**
  `SosViewModel (acquires location, validates finite and bounds [-90,90], [-180,180], excludes (0,0))`
  `-> nearbyRepository.sendSos(packetData.toJson())`
  `-> NearbyRepositoryImpl.sendSos(content)`
  `-> meshRouter.buildSos(content, peers) -> dispatchToNearby(forwardTargets)`
  `-> BLE beacon encoding via SosBeaconCodec.encode(deviceId, lat, lon, emergencyType, severity, nonce)`
  `-> SosBeaconAdvertiser.startAdvertising(encoded)`
  `-> [BLE over-the-air broadcast] -> SosBeaconAdvertiser.startScanning()`
  `-> detectedBeacons.collect { beacon -> ... }`
  `-> MeshRouter.route("sos-beacon", domainPacket, ...)`
  `-> persistence in Room (MessageEntity via messageRepository.insertMessage)`
  `-> deviceRepository.updateLocation (only called if valid, finite, and not (0,0))`
  `-> Map / Notification / UI (if lastLatitude is null -> shows "Location unavailable" and does NOT plot on map)`.
- **Hypotheses Tested & Results:**
  - *`NearbyRepositoryImpl.sendSos()` initializes `beaconLat`/`beaconLon` to 0.0 and passes them to beacon encoder when coordinates are absent/null:* REPRODUCED & FIXED. Lines 673-674 previously initialized `var beaconLat = 0.0`, `var beaconLon = 0.0`. If JSON coordinates were absent or null, it encoded `0.0, 0.0` into the beacon. Now changed to `var beaconLat: Double? = null`, `var beaconLon: Double? = null`, requiring both coordinates to be non-null, finite, and within valid ranges `[-90, 90]` and `[-180, 180]`, with `(0.0, 0.0)` strictly treated as unavailable.
  - *No finite/range validation before encoding:* REPRODUCED & FIXED. Added bounds checking `lat in -90.0..90.0` and `lon in -180.0..180.0` and `!isNaN() && !isInfinite()`.
  - *Beacon with unavailable location:* Encoded using `Float.NaN` in the IEEE 754 4-byte float fields. The wire size remains strictly 18 bytes (or 19 bytes with nonce) with CRC-16 integrity check preserved. When an old receiver reads `Float.NaN`, it formats `"lat": NaN`, and `MeshRouter`'s `optDouble("lat", Double.NaN).isNaN()` evaluates to true, causing `extractedLat = null`, completely preventing Null Island (0,0) plotting.
  - *Legacy (0.0, 0.0) policy:* Treated as unavailable because GPS at the Gulf of Guinea (0.0, 0.0) is indistinguishable from uninitialized/defaulted coordinates.
  - *N2 tests used mocked repositories only:* REPRODUCED & FIXED. Created `SosLocationPipelineTest.kt` using production `MeshRouter`, production `SosBeaconCodec`, production `DeviceRepositoryImpl`, production `MessageRepositoryImpl`, and a real in-memory Room database (`AppDatabase`).
- **Before-Fix Failure Evidence:**
  - `SosLocationPipelineTest.kt` failed compilation and verification: `SosBeaconCodec.encode` rejected nullable coordinates (`Null cannot be a value of a non-null type 'kotlin.Double'`), `SosBeacon` lacked `hasValidLocation`, and `NearbyRepositoryImpl` passed non-null default 0.0 coordinates.
- **After-Fix Verification Evidence:**
  - Command: `.\gradlew.bat :core:mesh:testDebugUnitTest --console=plain` (task-6726)
  - Exit Code: 0 (BUILD SUCCESSFUL in 1m 5s)
  - Actionable Tasks: 21 executed, 66 up-to-date
- **Fresh JUnit Test Results (`core/mesh/build/test-results/testDebugUnitTest/`):**
  - Total Tests: 74
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - `SosLocationPipelineTest` Tests:
    1. `golden test - raw IEEE 754 float reader on unavailable beacon bytes yields NaN and never plots` (Passed)
    2. `real DB integration - SOS routes and persists in Room without plotting missing or invalid GPS` (Passed)
    3. `SosBeaconCodec encodes sentinel when location is null, out of range, or legacy 0_0` (Passed)
  - `SosBeaconCodecTest` Tests: 5 passed (symmetric encode/decode, bad size, bad CRC, v1 with nonce, non-18/19 sizes).
- **Candidate Commit Message:** `fix(sos): N2 explicit nullable location, coordinate bounds validation, and sentinel encoding`

---

## Step 3: N3 One Event, Two Paths

- **Data Path Trace & Authenticity Separation:**
  1. *Unauthenticated Beacon Path:*
     `SosBeaconAdvertiser.detectedBeacons -> NearbyRepositoryImpl -> MeshRouter.route(fromEndpointId = "sos-beacon", domainPacket, ...)`
     - *Authenticity Boundary:* `packet.fromEndpointId == "sos-beacon"` marks the packet as an unauthenticated BLE beacon.
     - *Namespace Separation:* Beacon IDs are checked and cached exclusively in `seenBeaconCache: SeenMessageCache` (separate from `seenMessageCache`).
     - *ACK Invariant:* `isSosFromPeer` specifically excludes `isBeacon`, guaranteeing **0 ACKs are ever created or dispatched for beacons**.
     - *Relay Invariant:* `forwardTargets` is strictly set to `emptyList()`, guaranteeing beacons are **never relayed/flooded as authenticated mesh packets**.
     - *UI Invariant:* Stored with `senderName = "Unverified Nearby SOS"`.
  2. *Authenticated Mesh SOS Path:*
     `NearbyRepositoryImpl.onPayloadReceived -> MeshRouter.route(fromEndpointId = peerId, domainPacket, ...)`
     - *Authentication Boundary:* Verified cryptographic mesh packet from authenticated origin.
     - *ACK Handling:* Creates and queues an authenticated ACK to sender (`ACK_RECEIVED` / `AckManager`).
     - *Relay Handling:* Forwards to other connected peers if TTL / hopCount allows.
     - *UI Status:* Displayed as fully authenticated emergency alert with verified sender identity.
  3. *Correlation Key (`beaconKey`):*
     - Single authoritative generator in `NearbyRepositoryImpl.sendSos()` creates the beacon triple `beaconKey = "sos-$deviceHash-$delta-$nonce"` once.
     - Sent as an optional additive field `beaconKey` in `SosPacketData` JSON over mesh.
     - Golden tests prove existing/old JSON parsers ignore `beaconKey` without error.
  4. *Matching & Merging Invariants:*
     - Beacon $B$ matches authenticated event $E$ iff: (1) `E.beaconKey == B.messageId`; (2) `crc32(E.originId) == B.deviceHash`; (3) declared origin equals ID hash; (4) exactly 1 matching $E$ exists in dedup window.
     - If matched: linked without duplicate alert.
     - If beacon arrives before mesh SOS: persisted as unverified alert. When matching authenticated mesh SOS arrives, beacon message is removed from Room via `messageRepository.deleteMessage(matchingBeacon.messageId)`, upgrading to single authenticated alert.
     - If ambiguous (e.g. multiple events with same key) or forged: kept separate as unverified alert; authenticated alert is never suppressed.
- **Hypotheses Tested & Results:**
  - *Beacon path feeds same route() as PacketType.SOS triggering ACKs:* REPRODUCED & FIXED. Before fix, `MeshRouter.route()` treated beacons as standard SOS from peers and sent ACKs (`ackManager.sendAck(...)` / `RoutingResult.Processed(ackPacket = ...)`). Now fixed with `val isBeacon = (fromEndpointId == "sos-beacon")` and `if (isSosFromPeer && !isBeacon)`.
  - *Beacon relayed to connected peers:* REPRODUCED & FIXED. Before fix, beacons were flooded to connected peers. Now `forwardTargets = if (isBeacon) emptyList() else ...`.
  - *Single alert claim was false (inserted 2 IDs via MockK):* REPRODUCED & FIXED. Verified with production `AppDatabase` Room integration tests that previous code inserted two distinct alerts into Room. Now properly upgrades beacon to single authenticated alert on mesh SOS arrival.
- **Before-Fix Failure Evidence:**
  - `SosTwoPathsIntegrationTest` failed on unmodified code:
    `java.lang.AssertionError: S1 - Beacons must NEVER generate ACKs (expected: 0, actual: 1)`
    `java.lang.AssertionError: S1 - Expected exactly 1 visible alert after mesh arrival (expected: 1, actual: 2)`
- **After-Fix Verification Evidence:**
  - Command: `.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:mesh:testDebugUnitTest --console=plain` (task-7010)
  - Exit Code: 0 (BUILD SUCCESSFUL in 29s)
  - Actionable Tasks: 20 executed, 86 up-to-date
- **Fresh JUnit Test Results (`core/mesh/build/test-results/testDebugUnitTest/TEST-com.meshlink.app.mesh.repository.SosTwoPathsIntegrationTest.xml`):**
  - Total Tests: 12
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - Integration Tests Executed:
    1. `S1 - beacon then matching mesh produces 1 alert and 1 ACK for mesh only` (Passed)
    2. `S2 - mesh then matching beacon produces 1 alert and 1 ACK` (Passed)
    3. `S3 - beacon only produces 1 unverified alert and 0 ACKs` (Passed)
    4. `S4 - mesh only produces 1 authenticated alert and 1 ACK` (Passed)
    5. `S5 - old-version sender without beaconKey produces 2 alerts and 1 ACK for mesh only` (Passed)
    6. `S6 - duplicate beacon triple produces 1 unverified alert and 0 ACKs` (Passed)
    7. `S7 - duplicate authenticated mesh SOS produces 1 alert and 1 ACK` (Passed)
    8. `S8 - replay of genuine beacon after S1 or S2 creates no new alert and 0 ACKs` (Passed)
    9. `S9 - forged beacon with never-seen triple produces 1 unverified alert without changing authenticated alert` (Passed)
    10. `S10 - forged beacon with real event beaconKey but different deviceHash does not match or suppress authenticated alert` (Passed)
    11. `S11 - two authenticated events with same beaconKey followed by beacon are ambiguous and produce 3 alerts without suppression` (Passed)
    12. `S12 - beacon expires before matching mesh SOS arrives leaving 1 authenticated alert` (Passed)
- **Candidate Commit Message:** `fix(sos): N3 beacon authenticity separation, beaconKey correlation, and integration scenarios`

---

## Step 4: N4 Identity, Nonce, Replay

- **Production Identity Specification:**
  - *Authoritative Identity:* Authenticated SOS UUID (`SosPacketData.uuid` / `Message.id`, 128-bit RFC 4122 random UUID) is the sole authoritative identity of an emergency event.
  - *Beacon Triple:* `(deviceHash, timestampDelta, nonce)` is an ephemeral correlation hint only.
  - *Single Authoritative Generator:* `NearbyRepositoryImpl.sendSos()` generates `(deviceHash, timestampDelta, nonce)` once and populates both `SosPacketData.beaconKey` and the BLE beacon frame via `SosBeaconCodec.encode`. Competing or divergent generators eliminated.
  - *Cryptographic Nonce:* `SosNonceGenerator` uses `java.security.SecureRandom()` to draw uniformly distributed 8-bit nonces across $[-128, 127]$.
- **Collision Quantification:**
  - *Single Sender:*
    Exact birthday formula: $P(\text{collision}) = 1 - \prod_{k=0}^{n-1} \frac{256 - k}{256}$.
    $P(n=2) = 0.39\%$, $P(n=10) = 16.26\%$, $P(n=19) = 49.86\%$, $P(n=20) = 53.41\%$ (>50%), $P(n=30) = 83.19\%$.
    *Conclusion:* An 8-bit nonce alone cannot guarantee uniqueness across activations. True uniqueness requires the 128-bit UUID channel.
  - *Simulation with Fixed Seed:* `Random(42L)` across 10,000 trials with $n=20$ samples from $S=256$ demonstrates empirical collision probability of $53.4\% \pm 2\%$, confirming theoretical mathematical derivation.
  - *Multi-Sender Area Population:*
    For concurrent senders in an area emitting within the identical 1-second timestamp delta bucket, the state space is $2^{32} \times 2^8 = 2^{40} \approx 1.1 \times 10^{12}$. The 50% collision crossover point is $M \approx \sqrt{2 \ln(2) \cdot 2^{40}} \approx 1,232,875$ concurrent emitting devices.
- **Replay, Deduplication & Anti-Flood Invariants:**
  - *Injectable Clock:* `SeenMessageCache` and `MeshRouter` accept injectable clock providers `clock: () -> Long`.
  - *TTL Expiry:* Entries older than 10 minutes (`DEFAULT_TTL_MS = 600_000L`) are evicted from the LRU cache and treated as unseen, allowing retransmission after prolonged disconnection.
  - *Rate Limiting:*
    - Per-source anti-flood: `BEACON_MIN_INTERVAL_PER_SOURCE_MS = 2_000L` drops subsequent bursts from the same device hash.
    - Global sliding window: `MAX_BEACONS_PER_MINUTE_GLOBAL = 30` bounds total unverified beacons to 30 per minute.
    - Memory bounding: `MAX_UNVERIFIED_BEACONS = 100` hard capacity cap with proactive pruning of records older than 10 minutes.
  - *Restart Durability (Real Room Database):*
    Process death and cold start clears in-memory caches, but Room table primary key constraint (`@PrimaryKey val id: String` with `OnConflictStrategy.REPLACE`) guarantees zero duplicate alerts in database when the same authenticated SOS is replayed after restart. Zero schema changes or migrations required.
  - *Beacon Non-Suppression Invariant:*
    A colliding unauthenticated beacon never suppresses an authenticated SOS event.
- **After-Fix Verification Evidence:**
  - Command: `.\gradlew.bat :core:mesh:testDebugUnitTest --console=plain` (task-7116)
  - Exit Code: 0 (BUILD SUCCESSFUL in 36s)
  - Actionable Tasks: 4 executed, 83 up-to-date
- **Fresh JUnit Test Results (`core/mesh/build/test-results/testDebugUnitTest/TEST-com.meshlink.app.mesh.repository.SosNonceAndReplayTest.xml`):**
  - Total Tests: 11
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - Tests Executed:
    1. `prove birthday bound collision probability for 1-byte nonce` (Passed)
    2. `simulation with fixed seed confirms 1-byte nonce collision exceeds 50 percent for 20 emissions` (Passed)
    3. `quantify collision probability across distinct senders in an area` (Passed)
    4. `nonce generator produces values across wraparound without exception` (Passed)
    5. `seen message cache suppresses replay inside 10-minute TTL using injectable clock` (Passed)
    6. `seen message cache allows retransmission after 10-minute TTL expiry using injectable clock` (Passed)
    7. `process restart resets in-memory seen message cache but real DB guarantees restart durability` (Passed)
    8. `per-source rate limiting drops beacon flood from single source` (Passed)
    9. `global sliding window rate limiting bounds unverified beacons to 30 per minute` (Passed)
    10. `hard memory bound and TTL pruning on unverified beacon alert records` (Passed)
    11. `colliding unauthenticated beacon cannot suppress authenticated SOS event` (Passed)
- **Candidate Commit Message:** `fix(sos): N4 nonce collision bounds, injectable clock dedup, and replay durability`
