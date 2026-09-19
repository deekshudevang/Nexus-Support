# Offline Vector Maps Architecture

Nexus Support includes an offline mapping and geospatial visualization engine capable of rendering topographic and street maps without internet connectivity.

---

## 🗺️ Offline Engine Overview

The mapping layer is powered by **OsmDroid** and **Mapsforge**:
- **Format**: Highly compressed OpenStreetMap vector `.map` files.
- **Rendering**: Vector tiles rendered on-the-fly directly on the device GPU/CPU.
- **Storage**: Maps stored in the app's sandboxed private storage directory (`context.filesDir/maps/`).

---

## 📥 Region Downloader & Storage Pipeline

```
[Online Preparation / Staging]
               │
               ▼
[MapDownloadScreen / Worker]
               │
               ▼
Downloads regional .map tile packages (e.g., California.map, Tokyo.map)
               │
               ▼
Stored to /data/user/0/com.meshlink.app/files/maps/
               │
               ▼
[MeshMapScreen] opens Mapsforge tile provider
               │
               ▼
100% Offline Rendering & Multi-Peer GPS Overlays
```

---

## 📍 Real-Time Peer Geospatial Visualization

When peers in the mesh broadcast high-accuracy location events:
1. Coordinates are extracted and validated via `LocationSyncManager`.
2. Digital signatures are checked against the peer's public key.
3. Pins are rendered with status markers (Active, Stale, SOS Emergency).
4. Distance and relative bearings are calculated using the Haversine formula directly on device.
