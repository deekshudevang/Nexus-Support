# 3. 48h Store-and-Forward TTL

Date: 2024-05-18

## Status
Accepted

## Context
Nodes in a disaster-relief mesh frequently drop offline, move out of range, or lose battery. Messages sent to an offline node must be buffered by relays until the node rejoins the network. We need a mechanism to dictate how long these messages are kept alive in the network's distributed memory.

## Decision
We enforce a hard 48-hour Time-To-Live (TTL) on all messages in the `PendingMessageRepository`. 

## Consequences
**Pros:**
* **Storage Bounding:** Prevents unbounded SQLite database growth on resource-constrained Android devices.
* **Replay Mitigation:** Limits the temporal window in which an attacker could theoretically attempt to replay captured packets (though ECDSA temporal bounds also enforce this).
* **Pragmatism:** In a tactical or emergency scenario, a message older than 48 hours is usually obsolete.

**Cons:**
* **Data Loss:** If a partitioned subnet remains isolated for more than two days, queued messages will be silently dropped.
* **Synchronization Storms:** If a node reconnects at hour 47, it may receive a flood of nearly-expired messages, consuming battery and airtime.

**Alternatives Rejected:**
* *Infinite retention*: Bloats the database indefinitely.
* *Size-based eviction (e.g., max 1000 messages)*: Unpredictable for users. Time-based guarantees ("it will be delivered if they connect within 2 days") are easier to explain.
