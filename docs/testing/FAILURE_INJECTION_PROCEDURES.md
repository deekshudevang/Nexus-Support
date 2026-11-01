# Failure Injection Procedures

This document specifies the exact physical steps required to inject failures into a 3-node Nexus Support mesh and observe the network's resilience. These procedures must be run on physical hardware (e.g., Pixel 7, Galaxy S23, OnePlus 9).

## 1. Relay Node Power-Off Mid-Transfer (A ↔ B ↔ C)
**Goal:** Verify that a multi-hop payload is safely stored by the sender if the active relay suddenly disappears.

1. **Topology Setup:**
   * Device A (Sender) and Device C (Receiver) are separated by 60m (out of direct range).
   * Device B (Relay) is positioned in the middle (30m from both).
   * Confirm the mesh has formed: Device A should show Device C as reachable (2 hops).
2. **Action (Failure Injection):**
   * On Device A, type a large message or queue an image to Device C.
   * *Immediately* upon tapping "Send" on Device A, completely power off Device B (hold power button -> Power off).
3. **Verification:**
   * **Device A:** The message status should change from `SENDING` to `PENDING` (yellow icon) within 30 seconds.
   * **Device C:** Should receive nothing.
4. **Recovery:**
   * Power Device B back on and launch Nexus Support.
   * **Device C:** Should receive the message within 10 seconds of Device B rejoining the mesh, without any user interaction on Device A.

## 2. Network Partition & Reconnection Sync
**Goal:** Verify that distributed CRDT state (like peer location tracking) successfully merges after a hard network split.

1. **Topology Setup:**
   * Devices A, B, and C are all in the same room (fully connected).
   * Allow 1 minute for all devices to sync locations. All screens should show the same 3 pins on the offline map.
2. **Action (Partition):**
   * Place Device C inside a Faraday bag (or a microwave/metal pot) to completely sever RF communication.
   * On Device A, manually change its location pin by tapping a new area on the map.
   * On Device C (inside the bag, if accessible, or right after removing), change its location pin.
3. **Verification (Split Brain):**
   * Device B will see Device A's new location, but keep Device C's old location.
   * Device C will only see its own new location.
4. **Recovery (Merge):**
   * Remove Device C from the Faraday bag.
   * Wait up to 60 seconds for BLE/Wi-Fi Direct to re-establish.
5. **Final State Verification:**
   * All 3 devices must converge to show the updated locations for both A and C without duplicate markers.
