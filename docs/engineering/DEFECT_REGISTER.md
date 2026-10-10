# DEFECT_REGISTER.md
Last updated: Saturday, October 10, 2026 9:41:50 PM | HEAD: f9dd205cf242502f7d72eed3fe5e476ca5e3cf24 | Source fingerprint: 4CCAC4FB40260A075F34C6F679966C503F1901CDB511E89EF0903EEE69183BBB
Levels: L1 compiles, L2 automated tests, L3 physical devices, L4 deployment. Nothing here is L3/L4 verified.

| ID | Sev | Status | Evidence | Test | Note |
|---|---|---|---|---|---|
| A Home online status | P2 | CLAIMED FIXED, NOT VERIFIED this session | HomeViewModel.kt | HomeViewModelTest (10, L2) | State mapping and initial state tested at L2; banner UI untested |
| B Backend validation | P2 | CLAIMED FIXED, NOT VERIFIED this session | NetworkModule | none | README says cloud sync experimental/mock; not inspected this session |
| C AI inference | P2 | CLAIMED FIXED, NOT VERIFIED this session | LlmInferenceManager.kt | none | Real vs canned not inspected this session |
| D Security tests | P1 | CLAIMED FIXED, NOT VERIFIED this session | ThreatModelValidationTest | ThreatModelValidationTest (4, L2) | Isolated crypto/cache only, not the real router |
| E Stale CONNECTED/AUTHENTICATED entries | P1 | CONFIRMED RISK, NOT FIXED | NearbyRepositoryImpl.kt: onDisconnected 210, sendPayload failure listener 585 | none | Entries cleared only when those callbacks fire; a silently vanished peer stays CONNECTED. Liveness prototype rejected (false disconnects under Doze) |
| F SOS messageId repeats | P1 | FIXED, VERIFIED this session | SosBeaconCodec.kt | SosMessageIdTest (1, L2) | Delta was clamping at 65535 for seconds-since-midnight. Changed to 2-second resolution to fit 24h into 16-bit without clamping. |
| G SOS re-alert after dedup expiry | P1 | UNCONFIRMED (code reading) | SeenMessageCache.kt:59-60 isCritical returns false after the LRU check (10-min TTL) | none yet | A still-advertising critical beacon may be persisted again after expiry |
| H No ACK for SOS broadcasts | P1 | CONFIRMED BY CODE | MeshRouter.kt:142 excludes PacketType.BROADCAST | none | SOS UI shows no delivery state (SosScreen/SosViewModel), so no false "delivered" claim; senders get no confirmation |
| I Release build | P2 | OPEN | RUN_CHECKPOINT.md | none | APK unsigned; works only with ~/.gradle/gradle.properties: org.gradle.workers.max=2, org.gradle.parallel=false, kotlin.daemon.jvmargs=-Xmx1g; R8 zze Companion warning not analysed; latest run: NOT RUN this session |
| J DISCONNECTED entries never removed | P3 | CONFIRMED BY CODE | writers at NearbyRepositoryImpl.kt 168, 205, 212, 541, 587, 676 only add/overwrite | none | connectionStates map grows |
| K Paused banner hidden when connected-but-paused | P3 | NOT VERIFIED at UI level | HomeScreen.kt:352 networkState == PAUSED | HomeViewModelTest PAUSED case | Design change, no UI test |
| L Process note | - | RECORDED | RUN_CHECKPOINT.md | - | Earlier revert overwrote documents with HEAD versions; rebuilt by hand |
| M sendPayload failure vs Nearby link | P3 | UNVERIFIED | NearbyRepositoryImpl.kt:585-587 | NearbyRepositoryImplEndpointLostTest (sendPayload case) | Marks peer DISCONNECTED and restarts discovery without calling disconnectFromEndpoint; a live link could remain |
