# Policy: Critical Message Retry and Outbox Retention

## 1. Scope
This policy governs retry scheduling, backoff dynamics, queue retention, and battery safeguards for emergency SOS beacons, peer-to-peer messages, and gateway synchronization in Nexus Support.

## 2. Core Principles
1. **Zero Silent Loss:** Critical SOS messages must never be dropped silently. If unacknowledged, they remain visible to the user as `DELIVERY_UNCONFIRMED` or `NEEDS_ATTENTION`.
2. **Persistent Local Outbox:** All outbound emergency events must be persisted to the encrypted local database (Room/SQLCipher) before the first transmission attempt.
3. **Battery-Aware Bounded Backoff:** Retries must use randomized jittered exponential backoff to prevent channel contention (broadcast storm) and avoid draining survivor battery.

## 3. Retry Parameters
- **Initial Retry Delay:** 2.0 seconds.
- **Backoff Multiplier:** 1.5x.
- **Maximum Delay Cap:** 120 seconds.
- **Random Jitter:** +/- 20% random interval to desynchronize relay nodes.
- **Maximum Urgent Attempts:** 10 active transmission attempts in the first 30 minutes.
- **Low Battery Threshold (< 15%):** Switch to low-duty cycle beaconing (1 attempt every 5 minutes) to maintain device lifeline.

## 4. Retention & Deduplication
- Messages are stored with a unique `eventId` (UUIDv4) and origin timestamp.
- Outbox entries are retained until an authenticated server or recipient delivery ACK is recorded, or marked archived after 72 hours of persistent retention with user override.

