# Benchmark Procedure

This document defines the exact repeatable steps required to measure discovery time, latency, delivery rates, and battery impact for Nexus-Support on physical hardware, specifically adhering to the 3-device hardware constraint.

## Prerequisites
* **Hardware**: Exactly 3 physical devices (e.g., Pixel 7, Galaxy S23, OnePlus 9).
* **Environment**: A clear area allowing for variable distancing (0m to 60m+).
* **Build**: Release APK or Debug APK with `assembleDebug`. Both must be installed cleanly.

## 1. Discovery Time Measurement
**Goal:** Measure time from app launch to first cryptographic handshake.
1. Clear app data on Device A and Device B.
2. Ensure Bluetooth and Wi-Fi are enabled on both devices.
3. Start a stopwatch and simultaneously launch the app on both devices, standing 2 meters apart.
4. Stop the stopwatch when the "Connected to peer" indicator appears.
5. Record the time. Repeat 5 times and average.

## 2. Latency (Avg & p95)
**Goal:** Measure end-to-end packet delivery time over a single hop.
1. Place Device A and Device B 10 meters apart.
2. On Device A, send 100 automated ping messages (or tap send 100 times consistently).
3. Extract the SQLite database (`meshlink.db`) from Device B via Android Studio Device Explorer.
4. Calculate the time difference between `timestamp` (sender generation time) and the local insertion time into `LocationEventDao` or `MessageDao`.
5. Calculate the average and 95th percentile (p95).

## 3. Delivery Rate & Duplicate Rate (3-Node Multi-Hop)
**Goal:** Measure packet loss and deduplication efficiency in a multi-hop relay.
1. **Topology:** A ↔ B ↔ C (String topology). Device A and C must be completely out of radio range of each other (e.g., 60m apart, with B in the middle at 30m).
2. From Device A, send 50 messages to Device C (destination).
3. From Device B (relay), ensure the screen is on and the app is in the foreground.
4. Extract `meshlink.db` from Device C. 
5. **Delivery Rate:** (Received Messages / 50) * 100.
6. **Duplicate Rate:** Count instances where the `SeenMessageCache` on Device C rejected a packet with the same UUID but different hop path. (Requires ADB logcat: `adb logcat | grep "Message deduplicated"`).

## 4. Battery Drain / Hour
**Goal:** Measure standby and active battery consumption of the MeshRouter.
1. Charge Device A (e.g., Pixel 7) to exactly 100%.
2. Launch Nexus-Support. Keep it in the foreground with screen brightness at minimum, but do not interact with it.
3. Place Device B nearby to maintain an active BLE/Wi-Fi Direct connection.
4. Leave for exactly 1 hour.
5. Record the final battery percentage on Device A.

## Running the Equivalent Simulation
To generate `[SIMULATOR]` numbers for topologies larger than 3 nodes, use the automated test harness:
```bash
# Run the large scale mesh simulator
./gradlew testDebugUnitTest --tests "com.meshlink.app.mesh.routing.LargeScaleMeshSimTest"
```
The console output will yield the Delivery Rate, Average Hops, and Duplicate counts under idealized Robolectric execution without RF collision.
