# SOS Protocol Specification & Security Architecture

This document specifies the end-to-end design, wire formats, authenticity boundaries, correlation rules, collision bounds, and replay protections for the Nexus-Support SOS emergency alert subsystem.

---

## 1. Dual-Path Architecture Overview

The Nexus-Support emergency system transmits life-safety alerts over two complementary radio pathways simultaneously:

```
                          [User Triggers SOS]
                                   │
                    NearbyRepositoryImpl.sendSos()
                     (Generates authoritative UUID
                     and single beaconKey triple)
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
[Authenticated Mesh Path]                           [BLE Beacon Path]
  - Full SosPacketData (JSON)                         - Compact 19-byte frame
  - High-priority mesh flood                          - Manufacturer BLE advertisement
  - Multi-hop store-and-forward                       - Single-hop radio line-of-sight
  - Carries optional beaconKey                        - Broadcasts (deviceHash, delta, nonce)
  - Strict mutual authentication                      - Zero connection required
         │                                                   │
         ▼                                                   ▼
MeshRouter.route(fromEndpointId = peerId)           MeshRouter.route(fromEndpointId = "sos-beacon")
  - Verified sender identity                          - Authenticity: UNVERIFIED
  - Generates ACK to peer                             - ZERO ACKs generated
  - Relays to mesh neighbors                          - ZERO relay/forwarding
  - Persists authenticated alert in Room              - Capped memory / rate limited
         │                                                   │
         └─────────────────────────┬─────────────────────────┘
                                   │
                         [Correlation Engine]
        - Matches beaconKey triple to unverified beacon alert
        - Upgrades unverified beacon to single authenticated alert
        - Zero duplicate alerts; zero alert suppression
```

---

## 2. Authenticity Boundaries and Namespaces

### 2.1 Authenticity Separation Invariants
1. **Unverified Beacon Namespace:** Packets originating from `fromEndpointId == "sos-beacon"` represent unauthenticated radio broadcasts. They are routed into an isolated cache (`seenBeaconCache`) separate from `seenMessageCache`.
2. **Zero ACKs for Beacons:** An unauthenticated beacon never generates an acknowledgment (`ACK`) packet. `MeshRouter.route()` strictly asserts `if (isSosFromPeer && !isBeacon)` before creating ACK frames.
3. **Zero Relay for Beacons:** An unauthenticated beacon is never forwarded or flooded across connected mesh links. `forwardTargets` is strictly evaluated as `emptyList()` for beacon packets.
4. **UI Labeling:** Unauthenticated beacons display sender as `"Unverified Nearby SOS"` until upgraded by an authenticated mesh SOS packet.
5. **Non-Suppression Invariant:** An unauthenticated beacon (whether genuine, forged, or colliding) **never suppresses an authenticated mesh SOS alert**. If correlation is ambiguous, both alerts are displayed rather than dropping the authenticated event.

---

## 3. Authoritative Identity vs. Ephemeral Correlation Hint

| Dimension | Authenticated Mesh SOS | BLE Beacon Hint |
|---|---|---|
| **Authoritative Identifier** | **128-bit RFC 4122 UUID** (`SosPacketData.uuid`) | **Ephemeral Triple** `(deviceHash, delta, nonce)` |
| **Entropy / State Space** | $2^{128} \approx 3.4 \times 10^{38}$ states | $2^{32} \times 2^{16} \times 2^8 = 2^{56}$ states (single emission: $2^8$) |
| **Collision Likelihood** | Negligible ($< 10^{-18}$ over human history) | $P(N=20) = 53.41\%$ for 8-bit nonce across sender activations |
| **Cryptographic Authentication** | Verified via mesh protocol identity | Unauthenticated (susceptible to spoofing/replay) |
| **Purpose** | Authoritative event record, ACK target, mesh relay | Fast line-of-sight awareness when disconnected from mesh |

### 3.1 Single Authoritative Generator
To prevent divergent state across pathways, `NearbyRepositoryImpl.sendSos()` acts as the single authoritative generator:
```kotlin
val now = System.currentTimeMillis()
val deviceHash = (crc32(myDeviceId) and 0xFFFFFFFFL).toInt()
val timestampDelta = ((now / 1000) and 0xFFFF).toInt()
val nonce = SosNonceGenerator.nextNonce() // SecureRandom draw in [-128, 127]

// Beacon key embedded into structured SOS packet
val beaconKey = "sos-$deviceHash-$timestampDelta-$nonce"
val packetData = basePacketData.copy(beaconKey = beaconKey)
```

---

## 4. Correlation and Matching Rules

When a receiver processes incoming packets, `MeshRouter` correlates unverified beacon alerts with authenticated mesh packets under strict deterministic rules:

1. **Correlation Key Matching:** The incoming authenticated `SosPacketData` carries `beaconKey`.
2. **Device Hash Verification:** The receiver computes `crc32(packet.originId)` and asserts it matches the `deviceHash` contained in `beaconKey`.
3. **Ambiguity Resolution:**
   - If exactly **one** unverified beacon alert matching `beaconKey` exists in the local database or recent buffer: the beacon record is removed via `messageRepository.deleteMessage()`, and the authenticated packet is persisted, cleanly upgrading the alert to a single verified record.
   - If **zero** matching beacon alerts exist: the authenticated packet is inserted as usual.
   - If **multiple** matching candidates exist (e.g. colliding nonces from different sources or forged keys): neither alert is suppressed; all records remain visible with unambiguous labeling.
4. **Out-of-Order Delivery Resilience:**
   - *Beacon arrives first, then Mesh:* Beacon displayed as "Unverified Nearby SOS". Mesh arrival upgrades it in-place to authenticated alert. Exactly 1 final alert, exactly 1 ACK.
   - *Mesh arrives first, then Beacon:* Mesh displayed as authenticated alert. Subsequent beacon is detected as matching an active event and dropped without creating a secondary unverified alert. Exactly 1 final alert, exactly 1 ACK.

---

## 5. Mathematical Proof of 8-Bit Nonce Collision Bounds

### 5.1 Single Sender Activations
An 8-bit nonce provides $S = 2^8 = 256$ possible values drawn uniformly at random.
By the Birthday Problem, the exact probability of at least one collision after $n$ independent activations from the same sender is:
$$P(\text{collision}) = 1 - \prod_{k=0}^{n-1} \left(1 - \frac{k}{256}\right) = 1 - \frac{256!}{256^n (256-n)!}$$

- $n = 2$: $P = 1 - \frac{255}{256} = 0.39\%$
- $n = 10$: $P = 16.26\%$
- $n = 19$: $P = 49.86\%$
- **$n = 20$:** **$P = 53.41\% > 50\%$**
- $n = 30$: $P = 83.19\%$

**Empirical Verification:** A Monte Carlo simulation with fixed seed `Random(42L)` across 10,000 trials confirmed $53.4\% \pm 2\%$ collision probability at $n=20$ (`SosNonceAndReplayTest`).
**Architectural Consequence:** The 8-bit nonce alone **cannot serve as a durable identity**. It is strictly a short-lived correlation hint for disambiguating bursts within a single activation window.

### 5.2 Multi-Sender Population in Radio Range
For distinct senders emitting simultaneously within the same 1-second timestamp delta bucket, the state space is $S = 2^{32} \times 2^8 = 2^{40} \approx 1.0995 \times 10^{12}$.
The 50% collision crossover point $M$ across distinct senders is:
$$M \approx \sqrt{2 \ln(2) \cdot S} \approx \sqrt{2 \cdot 0.69315 \cdot 1.0995 \times 10^{12}} \approx 1,232,875 \text{ concurrent devices}$$
In any realistic disaster scenario (e.g., thousands of devices in radio range), accidental multi-sender collision of the full `(deviceHash, delta, nonce)` triple within the same second is virtually zero ($P < 10^{-6}$).

---

## 6. Replay Protection, Durability & Rate Limiting

### 6.1 Injectable Clock & TTL Deduplication
- `SeenMessageCache` and `MeshRouter` accept an injectable clock lambda `clock: () -> Long` for fully deterministic testability.
- **TTL Duration:** `DEFAULT_TTL_MS = 600_000L` (10 minutes).
- **Eviction Invariant:** Entries older than 10 minutes are expired from the deduplication cache, enabling intentional retransmissions after extended disconnections while suppressing immediate network loop storms.

### 6.2 Multi-Layered Anti-Flooding Controls
To prevent denial-of-service and battery exhaustion from unauthenticated BLE spam:
1. **Per-Source Interval Bound:** `BEACON_MIN_INTERVAL_PER_SOURCE_MS = 2_000L`. Subsequent beacon frames from the same `deviceHash` arriving within 2 seconds are dropped silently.
2. **Global Sliding Window:** `MAX_BEACONS_PER_MINUTE_GLOBAL = 30`. A maximum of 30 unverified beacons per minute are accepted across all sources.
3. **Hard Memory Capacity Cap:** `MAX_UNVERIFIED_BEACONS = 100`. The unverified beacon buffer is capped at 100 entries. Expired entries (>10 min) are pruned proactively; if still full, the oldest unverified entry is dropped.

### 6.3 Restart Durability Without Database Migrations
- Cold starts or process death clear volatile in-memory LRU caches (`seenMessageCache`).
- To guarantee that retransmitted or replayed mesh alerts do not create duplicate records in Room, `MessageEntity` relies on `@PrimaryKey val id: String` with `OnConflictStrategy.REPLACE`.
- When an identical authenticated SOS packet is replayed after process restart, Room updates the existing record in-place. Total alerts in Room remain strictly at 1. No Room schema migrations or breaking table alterations were introduced.

---

## 7. Wire Compatibility and IEEE 754 Sentinels

### 7.1 Location Encoding & Unavailable Sentinel
The 19-byte BLE beacon format allocates 4 bytes for latitude and 4 bytes for longitude as IEEE 754 32-bit single-precision floats:
- **Valid Coordinates:** Encoded directly within ranges `lat in -90.0..90.0` and `lon in -180.0..180.0`.
- **Unavailable Sentinel:** If GPS is missing, disabled, out of bounds, or set to legacy `(0.0, 0.0)`, both fields are encoded as **`Float.NaN`** (`0x7FC00000`).
- **Null Island Policy:** Coordinates of `(0.0, 0.0)` (Gulf of Guinea) are strictly treated as uninitialized placeholders and converted to `Float.NaN`.
- **Backward Compatibility:** Legacy parsers decoding `Float.NaN` produce `"lat": NaN`, which evaluates to `Double.NaN` in Android JSON parsers. `MeshRouter`'s `optDouble("lat", Double.NaN).isNaN()` evaluates to `true`, preventing erroneous plotting on UI map screens.

### 7.2 Transport Payload Size Bounding
- **Transport Limit:** Google Nearby Connections enforces `Payload.MAX_BYTES = 32_768`.
- **Safety Bound:** `MAX_TRANSPORT_PAYLOAD_BYTES = 32_000`.
- **Deterministic Truncation:** If a serialized `SosPacketData` with extensive medical details exceeds 32,000 bytes, `message` is deterministically truncated with the marker `"\n[TRUNCATED: Medical info exceeded transport limit]"`.
- **Critical Field Invariant:** Event UUID, sender ID, location, emergency type, and severity are **never truncated or dropped**.
- **Golden Wire Compatibility:** Verified bidirectionally by `SosPacketDataTest`:
  - Existing legacy parsers parse new truncated and untruncated payloads without error.
  - New parsers accept legacy payloads containing missing or unknown optional fields.
