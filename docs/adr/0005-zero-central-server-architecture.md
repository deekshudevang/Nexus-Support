# 5. Zero Central Server Architecture

Date: 2024-06-20

## Status
Accepted

## Context
Most messaging apps (WhatsApp, Signal, Telegram) rely on central servers for message routing, user discovery, and push notifications. Our deployment environment (disaster zones, remote wilderness) explicitly lacks cellular or internet backhaul.

## Decision
Meshlink is designed to function with 100% autonomy using a zero central server architecture. All routing, discovery, and encryption happens client-side. The network exists only as long as the participating devices are powered on and in range of each other.

## Consequences
**Pros:**
* **True Resilience:** Cannot be taken down by ISP failures, DNS outages, or government firewalls.
* **Privacy:** No central honey-pot of metadata or communication graphs.

**Cons:**
* **Battery Drain:** Devices must actively scan (BLE/Wi-Fi Direct) and maintain background connections, which consumes significantly more power than sleeping while waiting for an FCM push notification.
* **Asynchronous Delivery:** If two nodes are never connected to the same mesh component at the same time (or via a bridging relay), messages will never deliver. There is no central queue to hold messages indefinitely.

**Alternatives Rejected:**
* *Hybrid Cloud/Mesh*: We do have "opportunistic sync" on the roadmap for telemetry, but the core messaging path is strictly localized to guarantee predictability in the field.
