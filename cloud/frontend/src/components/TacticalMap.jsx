import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import {
  Layers,
  Compass,
  AlertTriangle,
  Send,
  Radio,
  MapPin,
  ChevronDown,
  ChevronUp,
  Crosshair
} from 'lucide-react';

// Custom Marker Icons for Leaflet
const createSosMarker = (sev = 9) =>
  L.divIcon({
    className: 'custom-sos-marker',
    html: `<div class="marker-pin-sos ${sev >= 8 ? 'sos-pulsing' : ''}" style="background: ${
      sev >= 8 ? '#dc2626' : '#d97706'
    };">
            <span style="font-size: 10px; font-weight: 800; color: #fff;">SOS</span>
           </div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });

const createNodeMarker = (role = 'RELAY') =>
  L.divIcon({
    className: 'custom-node-marker',
    html: `<div class="marker-pin-node" style="background: ${
      role === 'GATEWAY' ? '#0284c7' : '#16a34a'
    };">
            <span style="font-size: 9px; font-weight: 800; color: #ffffff;">${role === 'GATEWAY' ? 'GW' : 'N'}</span>
           </div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  });

const createResponderMarker = (availability = 'AVAILABLE') => {
  const isAvail = availability === 'AVAILABLE';
  const isAssigned = availability === 'ASSIGNED' || availability === 'ACTIVE';
  const bg = isAvail ? '#10b981' : isAssigned ? '#f59e0b' : '#64748b';
  return L.divIcon({
    className: 'custom-responder-marker',
    html: `<div class="marker-pin-responder" style="background: ${bg}; border: 2px solid #ffffff; width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 0 8px ${bg}aa;">
            <span style="font-size: 8px; font-weight: 800; color: #ffffff; line-height: 24px; text-align: center;">SAR</span>
           </div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
};

const BASE_TILE_LAYERS = {
  RF_MESH: {
    name: 'RF MESH',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
    isOffline: false,
  },
  THERMAL: {
    name: 'TERRAIN / TOPO',
    url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',
    attribution: '&copy; OpenStreetMap contributors, SRTM | Map style: &copy; OpenTopoMap (CC-BY-SA)',
    maxZoom: 17,
    isOffline: false,
  },
  SAT: {
    name: 'SATELLITE',
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    attribution: 'Tiles &copy; Esri &mdash; Source: Esri, USGS, NOAA',
    maxZoom: 18,
    isOffline: false,
  },
};

// Map Controller for smooth flyTo
function MapFlyController({ targetCoords }) {
  const map = useMap();
  useEffect(() => {
    if (
      targetCoords &&
      Number.isFinite(targetCoords[0]) &&
      Number.isFinite(targetCoords[1])
    ) {
      map.flyTo(targetCoords, 14, { duration: 1.2 });
    }
  }, [targetCoords, map]);
  return null;
}

export default function TacticalMap({
  incidents = [],
  nodes = [],
  responders = [],
  selectedIncident,
  onSelectIncident,
  onOpenDispatch,
  localTileUrl = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_LOCAL_TILE_URL) || null,
}) {
  const effectiveTileLayers = {
    ...(localTileUrl
      ? {
          OFFLINE_LOCAL: {
            name: 'LOCAL OFFLINE',
            url: localTileUrl,
            attribution: 'Local Offline Tile Server',
            maxZoom: 18,
            isOffline: true,
          },
        }
      : {}),
    ...BASE_TILE_LAYERS,
  };

  const [isOnline, setIsOnline] = useState(
    typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean'
      ? navigator.onLine
      : true
  );

  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  const [activeLayer, setActiveLayer] = useState(() => {
    if (localTileUrl && !isOnline) {
      return 'OFFLINE_LOCAL';
    }
    return 'RF_MESH';
  });
  const [showUnmappedDrawer, setShowUnmappedDrawer] = useState(false);

  const currentLayerObj = effectiveTileLayers[activeLayer] || effectiveTileLayers.RF_MESH;
  const isCurrentLayerOffline = Boolean(currentLayerObj?.isOffline);

  let tileBadgeText = 'ONLINE TILES';
  let tileBadgeClass = 'text-[#10b981] border-[#10b981]/40 bg-[#10b981]/10';

  if (isCurrentLayerOffline) {
    tileBadgeText = 'OFFLINE TILES';
    tileBadgeClass = 'text-[#38bdf8] border-[#38bdf8]/40 bg-[#38bdf8]/10';
  } else if (!isOnline) {
    tileBadgeText = 'NO MAP TILES';
    tileBadgeClass = 'text-[#ef4444] border-[#ef4444]/40 bg-[#ef4444]/10';
  }

  // Separate mapped and unmapped incidents (ZERO GPS FABRICATION)
  const mappedIncidents = incidents.filter(
    (inc) =>
      inc.latitude !== null &&
      inc.latitude !== undefined &&
      inc.longitude !== null &&
      inc.longitude !== undefined &&
      Number.isFinite(inc.latitude) &&
      Number.isFinite(inc.longitude) &&
      (inc.latitude !== 0 || inc.longitude !== 0) // Explicit 0,0 check
  );

  const unmappedIncidents = incidents.filter(
    (inc) =>
      inc.latitude === null ||
      inc.latitude === undefined ||
      inc.longitude === null ||
      inc.longitude === undefined ||
      !Number.isFinite(inc.latitude) ||
      !Number.isFinite(inc.longitude) ||
      (inc.latitude === 0 && inc.longitude === 0)
  );

  // Separate mapped responders with valid coordinates (Zero GPS Fabrication)
  const mappedResponders = (responders || []).filter((r) => {
    const lat = r.last_latitude ?? r.latitude;
    const lng = r.last_longitude ?? r.longitude;
    return (
      lat !== null &&
      lat !== undefined &&
      lng !== null &&
      lng !== undefined &&
      Number.isFinite(lat) &&
      Number.isFinite(lng) &&
      (lat !== 0 || lng !== 0)
    );
  });

  // Selected coords for flyTo
  const targetCoords =
    selectedIncident &&
    selectedIncident.latitude !== null &&
    selectedIncident.longitude !== null &&
    Number.isFinite(selectedIncident.latitude) &&
    Number.isFinite(selectedIncident.longitude) &&
    (selectedIncident.latitude !== 0 || selectedIncident.longitude !== 0)
      ? [selectedIncident.latitude, selectedIncident.longitude]
      : mappedIncidents.length > 0
      ? [mappedIncidents[0].latitude, mappedIncidents[0].longitude]
      : [37.7749, -122.4194];

  return (
    <main className="flex-1 relative flex flex-col bg-[#0b0f19] overflow-hidden select-none">
      {/* Top Floating Map Controls HUD */}
      <div className="absolute top-2 left-2 right-2 z-30 flex items-center justify-between pointer-events-none">
        {/* Layer Toggles */}
        <div className="flex items-center gap-1 pointer-events-auto bg-[#111827]/95 backdrop-blur-sm border border-[#334155] rounded p-1 shadow">
          <span className="text-[10px] font-sans font-semibold text-[#94a3b8] px-1.5 border-r border-[#334155]">
            MAP LAYERS:
          </span>
          {localTileUrl && (
            <button
              onClick={() => setActiveLayer('OFFLINE_LOCAL')}
              className={`font-sans text-[11px] font-medium px-2 py-0.5 rounded transition-colors ${
                activeLayer === 'OFFLINE_LOCAL'
                  ? 'bg-[#0284c7] text-white'
                  : 'text-[#94a3b8] hover:text-white hover:bg-[#1e293b]'
              }`}
            >
              LOCAL OFFLINE
            </button>
          )}
          <button
            onClick={() => setActiveLayer('RF_MESH')}
            className={`font-sans text-[11px] font-medium px-2 py-0.5 rounded transition-colors ${
              activeLayer === 'RF_MESH'
                ? 'bg-[#2563eb] text-white'
                : 'text-[#94a3b8] hover:text-white hover:bg-[#1e293b]'
            }`}
          >
            RF MESH
          </button>
          <button
            onClick={() => setActiveLayer('THERMAL')}
            className={`font-sans text-[11px] font-medium px-2 py-0.5 rounded transition-colors ${
              activeLayer === 'THERMAL'
                ? 'bg-[#2563eb] text-white'
                : 'text-[#94a3b8] hover:text-white hover:bg-[#1e293b]'
            }`}
          >
            TERRAIN / TOPO
          </button>
          <button
            onClick={() => setActiveLayer('SAT')}
            className={`font-sans text-[11px] font-medium px-2 py-0.5 rounded transition-colors ${
              activeLayer === 'SAT'
                ? 'bg-[#2563eb] text-white'
                : 'text-[#94a3b8] hover:text-white hover:bg-[#1e293b]'
            }`}
          >
            SATELLITE
          </button>
        </div>

        {/* Tile Source / Offline Status Badge */}
        <div
          data-testid="tile-source-badge"
          className={`flex items-center gap-1.5 px-2 py-1 rounded text-[11px] font-mono border backdrop-blur-sm shadow pointer-events-auto ${tileBadgeClass}`}
        >
          <span
            className={`w-2 h-2 rounded-full ${
              tileBadgeText === 'ONLINE TILES'
                ? 'bg-[#10b981]'
                : tileBadgeText === 'OFFLINE TILES'
                ? 'bg-[#38bdf8]'
                : 'bg-[#ef4444] animate-pulse'
            }`}
          />
          <span className="font-semibold">{tileBadgeText}</span>
        </div>

        {/* Responders Count Badge */}
        <div className="flex items-center gap-1.5 px-2 py-1 bg-[#111827]/95 backdrop-blur-sm border border-[#334155] rounded text-[11px] font-mono text-[#94a3b8] shadow pointer-events-auto">
          <span className="w-2 h-2 rounded-full bg-[#10b981]"></span>
          <span>FIELD UNITS: {mappedResponders.length}</span>
        </div>

        {/* Unmapped Incidents Alert Badge (Zero-Fabrication) */}
        {unmappedIncidents.length > 0 && (
          <div className="pointer-events-auto">
            <button
              onClick={() => setShowUnmappedDrawer(!showUnmappedDrawer)}
              className="flex items-center gap-1.5 bg-[#7f1d1d] hover:bg-[#991b1b] text-white border border-[#dc2626] rounded px-3 py-1 text-xs font-semibold shadow transition-colors"
            >
              <AlertTriangle className="w-3.5 h-3.5 text-[#fca5a5]" />
              <span>AWAITING GPS FIX ({unmappedIncidents.length})</span>
              {showUnmappedDrawer ? (
                <ChevronUp className="w-3 h-3" />
              ) : (
                <ChevronDown className="w-3 h-3" />
              )}
            </button>
          </div>
        )}
      </div>

      {/* Unmapped Incidents Flyout Drawer (Zero-Fabrication) */}
      {showUnmappedDrawer && unmappedIncidents.length > 0 && (
        <div className="absolute top-12 right-2 w-80 bg-[#111827]/98 backdrop-blur-md border border-[#dc2626] rounded-lg p-3 z-40 shadow-2xl text-xs">
          <div className="flex items-center justify-between border-b border-[#334155] pb-2 mb-2">
            <span className="font-bold text-[#fecaca] text-xs flex items-center gap-1.5">
              <AlertTriangle className="w-4 h-4 text-[#ef4444]" />
              UNMAPPED INCIDENTS (NO GPS FIX)
            </span>
            <button
              onClick={() => setShowUnmappedDrawer(false)}
              className="text-[#94a3b8] hover:text-white text-xs font-semibold"
            >
              [CLOSE]
            </button>
          </div>
          <p className="text-[11px] text-[#94a3b8] mb-2 leading-relaxed">
            These nodes transmitted distress beacons without GPS coordinate locks. Coordinates are NOT fabricated.
          </p>
          <div className="max-h-48 overflow-y-auto space-y-1.5">
            {unmappedIncidents.map((inc) => (
              <div
                key={inc.uuid || inc.id}
                onClick={() => {
                  onSelectIncident(inc);
                  setShowUnmappedDrawer(false);
                }}
                className="p-2 bg-[#1e293b] border border-[#334155] hover:border-[#dc2626] rounded cursor-pointer transition-colors"
              >
                <div className="flex justify-between items-center text-xs">
                  <span className="text-[#ef4444] font-bold">
                    {inc.emergencyType || 'SOS'}
                  </span>
                  <span className="text-[#94a3b8] font-mono text-[11px]">
                    {inc.senderName || inc.senderId || 'NODE'}
                  </span>
                </div>
                <p className="text-[#f8fafc] text-xs line-clamp-1 mt-1">
                  {inc.message || 'Distress message awaiting fix'}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Real Map Canvas via React-Leaflet */}
      <div className="w-full h-full relative tactical-map-container">
        {tileBadgeText === 'NO MAP TILES' && (
          <div
            data-testid="no-tiles-warning"
            className="absolute top-12 left-2 z-30 bg-[#7f1d1d]/90 border border-[#dc2626] text-white text-xs px-3 py-1.5 rounded shadow flex items-center gap-2 pointer-events-auto"
          >
            <AlertTriangle className="w-4 h-4 text-[#fca5a5]" />
            <span>NO MAP TILES: Internet disconnected and no local tile server configured. Set VITE_LOCAL_TILE_URL for offline maps.</span>
          </div>
        )}

        <MapContainer
          center={targetCoords}
          zoom={13}
          style={{ height: '100%', width: '100%' }}
          zoomControl={false}
        >
          <TileLayer
            key={activeLayer}
            attribution={currentLayerObj?.attribution || BASE_TILE_LAYERS.RF_MESH.attribution}
            url={currentLayerObj?.url || BASE_TILE_LAYERS.RF_MESH.url}
            maxZoom={currentLayerObj?.maxZoom || 19}
          />

          <MapFlyController targetCoords={targetCoords} />

          {/* Plot Active Field Responders */}
          {mappedResponders.map((resp) => {
            const lat = resp.last_latitude ?? resp.latitude;
            const lng = resp.last_longitude ?? resp.longitude;
            const status = resp.availability || resp.status || 'AVAILABLE';
            return (
              <Marker
                key={resp.id || resp.callsign}
                position={[lat, lng]}
                icon={createResponderMarker(status)}
              >
                <Popup>
                  <div className="font-mono text-xs p-1 text-[#dee2ec] bg-[#171c23]">
                    <div className="font-bold text-[#10b981] mb-1">
                      {resp.callsign || resp.name || 'SAR RESPONDER'}
                    </div>
                    <div className="text-[10px] text-[#94a3b8]">
                      Type: {resp.responder_type || 'SAR'} | Status: {status}
                    </div>
                    {resp.skills && resp.skills.length > 0 && (
                      <div className="text-[10px] text-[#cbd5e1] mt-1">
                        Skills: {Array.isArray(resp.skills) ? resp.skills.join(', ') : resp.skills}
                      </div>
                    )}
                    {resp.device_id && (
                      <div className="text-[9px] text-[#64748b] mt-0.5">
                        Device: {resp.device_id}
                      </div>
                    )}
                  </div>
                </Popup>
              </Marker>
            );
          })}

          {/* Plot Mapped SOS Incidents */}
          {mappedIncidents.map((inc) => (
            <Marker
              key={inc.uuid || inc.id}
              position={[inc.latitude, inc.longitude]}
              icon={createSosMarker(inc.severity)}
              eventHandlers={{
                click: () => onSelectIncident(inc),
              }}
            >
              <Popup>
                <div className="font-mono text-xs p-1 text-[#dee2ec] bg-[#171c23]">
                  <div className="font-bold text-[#ef4444] mb-1">
                    SOS: {inc.emergencyType || 'EMERGENCY'}
                  </div>
                  <div className="text-[11px] mb-1 text-[#dee2ec]">
                    {inc.message}
                  </div>
                  <div className="text-[10px] text-[#8b949e]">
                    Node: {inc.senderName || inc.senderId} | Hop: {inc.hopCount || 0}
                  </div>
                  <button
                    onClick={() => onOpenDispatch(inc)}
                    className="mt-2 w-full py-1 bg-[#ff5625] text-black font-bold text-[10px] tracking-wider"
                  >
                    DISPATCH UNIT
                  </button>
                </div>
              </Popup>
            </Marker>
          ))}

          {/* Plot Mesh Relays / Nodes */}
          {nodes
            .filter((n) => n.latitude && n.longitude && Number.isFinite(n.latitude) && Number.isFinite(n.longitude))
            .map((node) => (
              <Marker
                key={node.deviceId || node.id}
                position={[node.latitude, node.longitude]}
                icon={createNodeMarker(node.role)}
              >
                <Popup>
                  <div className="font-mono text-xs p-1 text-[#dee2ec] bg-[#171c23]">
                    <div className="font-bold text-[#00e3fd]">
                      {node.displayName || node.deviceId}
                    </div>
                    <div className="text-[10px] text-[#8b949e]">
                      Role: {node.role || 'RELAY'} | Battery: {node.batteryLevel || 100}%
                    </div>
                  </div>
                </Popup>
              </Marker>
            ))}
        </MapContainer>

        {/* Radar HUD Graphic Overlay (Non-blocking Pointer Events) */}
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center overflow-hidden">
          {/* Circular Radar Rings */}
          <div className="w-[500px] h-[500px] rounded-full border border-[#00e3fd]/20 flex items-center justify-center relative">
            <div className="w-[340px] h-[340px] rounded-full border border-[#00e3fd]/15 flex items-center justify-center">
              <div className="w-[180px] h-[180px] rounded-full border border-[#00e3fd]/10"></div>
            </div>
            {/* Degree Cardinal Ticks */}
            <span className="absolute top-1 text-[9px] font-mono text-[#8b949e]">000° N</span>
            <span className="absolute bottom-1 text-[9px] font-mono text-[#8b949e]">180° S</span>
            <span className="absolute left-1 text-[9px] font-mono text-[#8b949e]">270° W</span>
            <span className="absolute right-1 text-[9px] font-mono text-[#8b949e]">090° E</span>
          </div>

          {/* Crosshair Lines */}
          <div className="absolute inset-x-0 h-px bg-[#30353d]/30"></div>
          <div className="absolute inset-y-0 w-px bg-[#30353d]/30"></div>
        </div>

        {/* Selected Incident Tactical Flyout Card (Bottom Right) */}
        {selectedIncident && (
          <div className="absolute bottom-3 right-3 w-88 md:w-96 bg-[#171c23]/95 backdrop-blur-md border border-[#00e3fd] p-3 shadow-2xl z-30 font-mono text-xs">
            <div className="flex items-center justify-between border-b border-[#30353d] pb-1.5 mb-2">
              <div className="flex items-center gap-1.5">
                <Crosshair className="w-4 h-4 text-[#ef4444]" />
                <span className="font-bold text-[#dee2ec] tracking-wide">
                  ACTIVE TARGET LOCK
                </span>
              </div>
              <span
                className={`text-[10px] font-bold px-1.5 py-0.5 ${
                  selectedIncident.severity >= 8 || selectedIncident.priority === 'CRITICAL'
                    ? 'bg-[#93000a] text-white'
                    : 'bg-[#3b2d00] text-[#ffddb8]'
                }`}
              >
                {selectedIncident.emergencyType || 'INCIDENT'}
              </span>
            </div>

            <p className="text-[#dee2ec] font-sans text-xs mb-2">
              {selectedIncident.message || 'Beacon signal without text notes.'}
            </p>

            <div className="grid grid-cols-2 gap-1 bg-[#090f15] p-1.5 border border-[#30353d] text-[10px] mb-2.5">
              <div>
                <span className="text-[#8b949e] block text-[9px]">SENDER NODE:</span>
                <span className="text-[#9cf0ff]">
                  {selectedIncident.senderName || selectedIncident.senderId || 'UNKNOWN'}
                </span>
              </div>
              <div>
                <span className="text-[#8b949e] block text-[9px]">COORDINATES:</span>
                {selectedIncident.latitude !== null && selectedIncident.longitude !== null ? (
                  <span className="text-[#4edea3]">
                    {selectedIncident.latitude.toFixed(4)}°, {selectedIncident.longitude.toFixed(4)}°
                  </span>
                ) : (
                  <span className="text-[#ff9500] font-bold">UNAVAILABLE</span>
                )}
              </div>
            </div>

            <button
              onClick={() => onOpenDispatch(selectedIncident)}
              className="w-full bg-[#ff5625] hover:bg-[#b12d00] text-[#090f15] font-bold py-1.5 tracking-wider flex items-center justify-center gap-1 transition-colors"
            >
              <Send className="w-3.5 h-3.5" />
              <span>DISPATCH REINFORCEMENTS</span>
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
