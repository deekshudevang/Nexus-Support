# Policy: Data Retention & Incident Archiving

## 1. Field Device Storage Management
- **Active Incidents & Unconfirmed SOS:** Retained indefinitely until server or responder acknowledgment is confirmed.
- **Resolved Incident Cache:** Retained locally for 7 days post-resolution, then tombstoned to conserve storage on low-end hardware.
- **Mesh Routing Tables & Topology:** Ephemeral in-memory state with a 15-minute sliding window. Stale nodes (> 30 min) pruned automatically.
- **Seen Message Deduplication Cache:** Retained for 24 hours (or 10,000 packet IDs) to prevent routing loops.

## 2. Central Operations & Backend Storage
- **Incident Audit Trail:** Immutable append-only log retained for 1 year for post-disaster review.
- **Telemetry Records:** Raw telemetry downsampled to 1-hour resolution after 30 days.

