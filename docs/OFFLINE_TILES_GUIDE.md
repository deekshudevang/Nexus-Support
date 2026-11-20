# Nexus Support — Offline Map Tiles Hosting Guide

## Overview

In disaster-relief and austere operational environments, external internet access is frequently degraded or entirely unavailable. Nexus Support's web command dashboard supports fully air-gapped, offline map rendering using standard OpenStreetMap XYZ raster tile sets (`{z}/{x}/{y}.png`).

When offline, the dashboard detects the absence of internet connectivity and displays an indicator badge:
- **`ONLINE TILES`**: Connected to global upstream tile servers (OpenStreetMap, OpenTopoMap, Esri).
- **`OFFLINE TILES`**: Actively serving raster tiles from an on-premise / local LAN tile cache.
- **`NO MAP TILES`**: Internet disconnected and no local tile cache configured.

---

## 1. Obtaining Offline Tile Packages

Standard web maps represent map areas as hierarchical pyramids of 256x256 pixel PNG images organized by zoom level ($z$), tile column ($x$), and tile row ($y$):
```
/tiles/{z}/{x}/{y}.png
```

### Option A: Pre-packaged Regional MBTiles via HOT Export Tool
For humanitarian response areas, Humanitarian OpenStreetMap Team (HOT) provides pre-clipped exports:
1. Visit [export.hotosm.org](https://export.hotosm.org/).
2. Define your operational bounding box (AOI).
3. Select MBTiles or raw OSM directory output.

### Option B: Downloading XYZ Tiles with open-source tools
You can pre-render or download bounded zoom levels (e.g., zoom levels 10 to 16 for a metropolitan disaster region) using tools like:
- **`tl`** (Tilelive CLI): `tl copy mbtiles://area.mbtiles file://./tiles/{z}/{x}/{y}.png`
- **`mb-util`**: `mb-util area.mbtiles ./tiles --image_format=png`

---

## 2. Serving Tiles on Local Area Network (LAN)

Store the downloaded tile directory on the field workstation or tactical mesh gateway server. The folder layout must follow:
```
tiles/
  ├── 10/
  │    └── 163/
  │         └── 395.png
  ├── 11/
  └── 12/
```

### Method 1: Python HTTP Server (Zero Dependencies)
Run a CORS-enabled Python server from inside the `tiles` directory:
```bash
# Windows / Linux / macOS
python -m http.server 8080 --directory ./tiles
```

### Method 2: Node.js `serve`
```bash
npx serve -p 8080 ./tiles --cors
```

### Method 3: Dockerized NGINX (Production / Gateway Server)
For field base stations running Docker:
```bash
docker run -d \
  --name nexus-tile-server \
  -p 8080:80 \
  -v /path/to/tiles:/usr/share/nginx/html:ro \
  nginx:alpine
```

Verify tile availability in your browser:
```bash
curl -I http://localhost:8080/12/656/1582.png
# Expected: HTTP/1.1 200 OK, Content-Type: image/png
```

---

## 3. Configuring Nexus Support Dashboard

Configure the frontend application to point to the local tile server via the environment variable `VITE_LOCAL_TILE_URL`.

1. Open or create `cloud/frontend/.env.local`:
```env
VITE_LOCAL_TILE_URL=http://localhost:8080/{z}/{x}/{y}.png
```
*(For network deployments, replace `localhost` with the gateway LAN IP, e.g., `http://192.168.1.100:8080/{z}/{x}/{y}.png`)*

2. Start or build the frontend:
```bash
cd cloud/frontend
npm run dev
# or for production:
npm run build
```

3. Verification in Tactical Command Center:
- In the map HUD controls, observe the **`LOCAL OFFLINE`** layer toggle button.
- Click **`LOCAL OFFLINE`**: the map will immediately render tiles from `VITE_LOCAL_TILE_URL` and the HUD badge will update to **`OFFLINE TILES`** in cyan.
- If internet connectivity drops while browsing, the system flags the connection state without crashing or freezing.
