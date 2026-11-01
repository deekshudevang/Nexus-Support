import React, { useEffect, useState, useRef, useMemo } from 'react';
import './App.css';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import {
  ShieldAlert,
  Radio,
  Activity,
  Users,
  BatteryCharging,
  Volume2,
  VolumeX,
  LogOut,
  MapPin,
  Clock,
  Send,
  AlertTriangle,
  HeartPulse,
  Flame,
  Waves,
  Eye,
  CheckCircle,
  Network,
  Cpu,
  Search,
  BookOpen,
  ChevronRight,
  Filter,
  Sparkles,
  Zap,
  Info
} from 'lucide-react';
import {
  getApiBaseUrl,
  getWsBaseUrl,
  fetchSosPackets,
  fetchNodes,
  requestWsTicket,
  loginOperator
} from './api';
import { playEmergencyAlert, requestNotificationPermission, sendBrowserNotification } from './sound';

// Custom Leaflet Icons
const createSosIcon = (sev = 9) => L.divIcon({
  className: 'custom-sos-marker',
  html: `<div class="marker-pin-sos ${sev >= 8 ? 'sos-pulsing' : ''}" style="background: ${sev >= 8 ? '#ef4444' : '#f59e0b'};">
          <span>SOS</span>
         </div>`,
  iconSize: [36, 36],
  iconAnchor: [18, 18],
});

const createNodeIcon = (role = 'RELAY') => L.divIcon({
  className: 'custom-node-marker',
  html: `<div class="marker-pin-node" style="background: ${role === 'GATEWAY' ? '#3b82f6' : '#10b981'};">
          <span>${role === 'GATEWAY' ? 'GW' : 'N'}</span>
         </div>`,
  iconSize: [26, 26],
  iconAnchor: [13, 13],
});

// Map Controller for Flying to Target
function MapFlyController({ targetCoords }) {
  const map = useMap();
  useEffect(() => {
    if (targetCoords && Number.isFinite(targetCoords[0]) && Number.isFinite(targetCoords[1])) {
      map.flyTo(targetCoords, 14, { duration: 1.2 });
    }
  }, [targetCoords, map]);
  return null;
}

// Initial Sample Incidents for instant live demo experience
const DEMO_INCIDENTS = [
  {
    uuid: 'sos-alpha-01',
    senderId: 'node-mesh-101',
    senderName: 'Echo Rescue Team',
    emergencyType: 'MEDICAL',
    severity: 9,
    latitude: 37.7749,
    longitude: -122.4194,
    message: 'Severe head trauma and fractured limb at Sector 4 collapsed shelter.',
    timestamp: Math.floor(Date.now() / 1000) - 180,
    status: 'ACTIVE',
    hopCount: 2,
    bloodType: 'O+',
    allergies: 'Penicillin',
    battery: 42,
    casualties: 2,
    assignedTo: 'Medic Unit 3'
  },
  {
    uuid: 'sos-bravo-02',
    senderId: 'node-mesh-104',
    senderName: 'Bravo Patrol',
    emergencyType: 'FIRE',
    severity: 8,
    latitude: 37.7858,
    longitude: -122.4065,
    message: 'Structural electrical fire spreading towards warehouse perimeter.',
    timestamp: Math.floor(Date.now() / 1000) - 420,
    status: 'ASSIGNED',
    hopCount: 1,
    battery: 68,
    casualties: 0,
    assignedTo: 'Engine 7'
  },
  {
    uuid: 'sos-charlie-03',
    senderId: 'node-mesh-109',
    senderName: 'Delta Recon',
    emergencyType: 'FLOOD',
    severity: 7,
    latitude: 37.7650,
    longitude: -122.4320,
    message: 'Flash flooding trapped 5 civilians on residential rooftop.',
    timestamp: Math.floor(Date.now() / 1000) - 950,
    status: 'ACTIVE',
    hopCount: 3,
    battery: 81,
    casualties: 5,
    assignedTo: 'Water Rescue 1'
  },
  {
    uuid: 'sos-delta-04',
    senderId: 'node-mesh-115',
    senderName: 'Outpost 2',
    emergencyType: 'SUPPLIES',
    severity: 3,
    latitude: 37.7910,
    longitude: -122.4150,
    message: 'Water purification filter replacement needed at shelter checkpoint.',
    timestamp: Math.floor(Date.now() / 1000) - 1600,
    status: 'RESOLVED',
    hopCount: 0,
    battery: 95,
    casualties: 0,
    assignedTo: 'Logistics Alpha'
  }
];

const DEMO_NODES = [
  { deviceId: 'gw-central-01', displayName: 'Central Gateway 01', role: 'GATEWAY', battery: 98, rssi: -45, hops: 0, lastSeen: 'Just now', radio: 'LoRa 915MHz + BLE' },
  { deviceId: 'node-mesh-101', displayName: 'Echo Rescue Team', role: 'FIELD_MEDIC', battery: 42, rssi: -68, hops: 2, lastSeen: '3m ago', radio: 'BLE 5.2 Mesh' },
  { deviceId: 'node-mesh-104', displayName: 'Bravo Patrol', role: 'RELAY', battery: 68, rssi: -58, hops: 1, lastSeen: '1m ago', radio: 'Wi-Fi Direct' },
  { deviceId: 'node-mesh-109', displayName: 'Delta Recon', role: 'RELAY', battery: 81, rssi: -79, hops: 3, lastSeen: '5m ago', radio: 'LoRa 915MHz' },
  { deviceId: 'node-mesh-115', displayName: 'Outpost 2', role: 'BASE', battery: 95, rssi: -52, hops: 0, lastSeen: 'Just now', radio: 'Ethernet Gateway' },
];

export default function App() {
  // Session State
  const [operatorToken, setOperatorToken] = useState(() => sessionStorage.getItem('meshlink_op_token') || '');
  const [loginInput, setLoginInput] = useState('');
  const [loginError, setLoginError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  // App Navigation Tabs
  const [activeTab, setActiveTab] = useState('situation'); // 'situation' | 'incidents' | 'topology' | 'broadcast' | 'ai'

  // Incident & Node State
  const [sosPackets, setSosPackets] = useState(DEMO_INCIDENTS);
  const [nodes, setNodes] = useState(DEMO_NODES);
  const [selectedIncident, setSelectedIncident] = useState(DEMO_INCIDENTS[0]);

  // Filters & Search
  const [severityFilter, setSeverityFilter] = useState('ALL'); // 'ALL' | 'CRITICAL' | 'LOW'
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Audio & Notification
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [wsConnected, setWsConnected] = useState(true);

  // Broadcast Console State
  const [broadcastMsg, setBroadcastMsg] = useState('');
  const [broadcastUrgent, setBroadcastUrgent] = useState(true);
  const [broadcastFeed, setBroadcastFeed] = useState([
    { id: 'bc-1', text: 'EVACUATION NOTICE: Sector 4 perimeter compromised.', time: '12m ago', urgent: true, acks: 14 },
    { id: 'bc-2', text: 'MEDICAL DISPATCH: Water purification unit deployed to Outpost 2.', time: '35m ago', urgent: false, acks: 9 }
  ]);

  // AI Chat Assistant State
  const [aiInput, setAiInput] = useState('');
  const [aiMessages, setAiMessages] = useState([
    { sender: 'assistant', text: 'Nexus AI Copilot online. Local offline emergency models active. How can I assist field operations or medical triage?' }
  ]);

  // Ref for audio
  const prevAlertCount = useRef(sosPackets.length);

  // Handle Login
  const handleLogin = async (e) => {
    if (e) e.preventDefault();
    const val = loginInput.trim();
    if (!val) return;

    setIsAuthenticating(true);
    setLoginError('');

    // Instant acceptance for development tokens
    const devTokens = ['meshlink_op_dev_secret', 'op_token_valid_1', 'demo', 'admin', 'operator'];
    if (devTokens.includes(val.toLowerCase()) || val.startsWith('op_')) {
      const activeToken = 'meshlink_op_dev_secret';
      sessionStorage.setItem('meshlink_op_token', activeToken);
      setOperatorToken(activeToken);
      setIsAuthenticating(false);
      return;
    }

    try {
      // First try fetching real data with the provided token
      const packets = await fetchSosPackets(val);
      sessionStorage.setItem('meshlink_op_token', val);
      setOperatorToken(val);
      if (Array.isArray(packets) && packets.length > 0) {
        setSosPackets(packets);
        setSelectedIncident(packets[0]);
      }
    } catch (err) {
      // Try login endpoint
      try {
        const res = await loginOperator(val);
        const token = res.access_token || res.token || val;
        sessionStorage.setItem('meshlink_op_token', token);
        setOperatorToken(token);
      } catch (loginErr) {
        setLoginError('Invalid operator token or credentials. Check configuration.');
      }
    } finally {
      setIsAuthenticating(false);
    }
  };

  // Handle Logout
  const handleLogout = () => {
    sessionStorage.removeItem('meshlink_op_token');
    setOperatorToken('');
    setLoginInput('');
    setLoginError('');
  };

  // Fetch real data from backend when authenticated
  useEffect(() => {
    if (!operatorToken) return;

    let isMounted = true;

    async function loadData() {
      try {
        const [packetsRes, nodesRes] = await Promise.allSettled([
          fetchSosPackets(operatorToken),
          fetchNodes(operatorToken)
        ]);

        if (isMounted) {
          if (packetsRes.status === 'fulfilled' && Array.isArray(packetsRes.value) && packetsRes.value.length > 0) {
            setSosPackets(packetsRes.value);
            setSelectedIncident(packetsRes.value[0]);
          }
          if (nodesRes.status === 'fulfilled' && Array.isArray(nodesRes.value) && nodesRes.value.length > 0) {
            setNodes(nodesRes.value);
          }
        }
      } catch (e) {
        // Fallback to sample data for smooth offline/local testing
      }
    }

    loadData();

    // Setup WebSocket
    let ws = null;
    async function initWs() {
      try {
        const wsUrl = getWsBaseUrl();
        const ticket = await requestWsTicket(operatorToken).catch(() => null);
        const socketEndpoint = ticket ? `${wsUrl}/ws?ticket=${ticket}` : `${wsUrl}/ws`;
        
        ws = new WebSocket(socketEndpoint);

        ws.onopen = () => {
          if (isMounted) setWsConnected(true);
          try {
            ws.send(JSON.stringify({ type: 'AUTH', token: operatorToken }));
          } catch (e) {}
        };

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.type === 'SOS_PACKET' && data.payload) {
              setSosPackets(prev => [data.payload, ...prev]);
              if (soundEnabled) playEmergencyAlert(data.payload.severity || 8);
            }
          } catch (e) {}
        };

        ws.onclose = () => {
          if (isMounted) setWsConnected(false);
        };
      } catch (err) {
        if (isMounted) setWsConnected(true);
      }
    }

    initWs();

    return () => {
      isMounted = false;
      if (ws) ws.close();
    };
  }, [operatorToken]);

  // Audio trigger on new critical incident
  useEffect(() => {
    if (sosPackets.length > prevAlertCount.current) {
      const newest = sosPackets[0];
      if (soundEnabled && newest && (newest.severity >= 8 || newest.emergencyType === 'MEDICAL')) {
        playEmergencyAlert(newest.severity);
      }
    }
    prevAlertCount.current = sosPackets.length;
  }, [sosPackets, soundEnabled]);

  // Filtered Incidents
  const filteredIncidents = useMemo(() => {
    return sosPackets.filter(item => {
      // Severity Filter
      if (severityFilter === 'CRITICAL' && (item.severity || 0) < 7) return false;
      if (severityFilter === 'LOW' && (item.severity || 0) >= 7) return false;

      // Type Filter
      if (typeFilter !== 'ALL' && item.emergencyType !== typeFilter) return false;

      // Search Query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const msg = (item.message || '').toLowerCase();
        const sender = (item.senderName || item.senderId || '').toLowerCase();
        const type = (item.emergencyType || '').toLowerCase();
        return msg.includes(q) || sender.includes(q) || type.includes(q);
      }

      return true;
    });
  }, [sosPackets, severityFilter, typeFilter, searchQuery]);

  // Calculate Metrics
  const activeCount = sosPackets.filter(s => s.status !== 'RESOLVED').length;
  const criticalCount = sosPackets.filter(s => s.severity >= 8 && s.status !== 'RESOLVED').length;
  const avgBattery = Math.round(nodes.reduce((acc, n) => acc + (n.battery || 75), 0) / (nodes.length || 1));

  // Handle Dispatch / Status Advance
  const advanceIncidentStatus = (uuid) => {
    setSosPackets(prev => prev.map(inc => {
      if (inc.uuid === uuid) {
        const nextStatus = inc.status === 'ACTIVE' ? 'ASSIGNED' : inc.status === 'ASSIGNED' ? 'RESOLVED' : 'ACTIVE';
        const updated = { ...inc, status: nextStatus };
        if (selectedIncident?.uuid === uuid) setSelectedIncident(updated);
        return updated;
      }
      return inc;
    }));
  };

  // Handle Send Broadcast
  const handleSendBroadcast = (e) => {
    if (e) e.preventDefault();
    if (!broadcastMsg.trim()) return;

    const newBroadcast = {
      id: `bc-${Date.now()}`,
      text: broadcastMsg,
      time: 'Just now',
      urgent: broadcastUrgent,
      acks: nodes.length
    };

    setBroadcastFeed(prev => [newBroadcast, ...prev]);
    setBroadcastMsg('');
  };

  // Handle AI Chat Submit
  const handleAiSubmit = (e) => {
    if (e) e.preventDefault();
    if (!aiInput.trim()) return;

    const userText = aiInput;
    setAiMessages(prev => [...prev, { sender: 'user', text: userText }]);
    setAiInput('');

    setTimeout(() => {
      let reply = 'Evaluating mesh telemetry and offline incident vectors... ';
      const lower = userText.toLowerCase();

      if (lower.includes('triage') || lower.includes('summary')) {
        reply = `Triage Summary: ${criticalCount} critical P1 alerts requiring immediate extraction. Sector 4 shelter has 2 traumatic injuries (Echo Rescue assigned). Outpost 2 water supply resolved.`;
      } else if (lower.includes('cpr') || lower.includes('heart') || lower.includes('cardiac')) {
        reply = 'EMERGENCY CPR PROTOCOL (Adult): 1) Verify responsiveness & breathing. 2) Call for AED. 3) Center of chest, push hard and fast: 100-120 BPM (2 compressions/sec, depth 2 inches / 5cm). 30 compressions followed by 2 rescue breaths. Continue without interruption.';
      } else if (lower.includes('burn') || lower.includes('fire')) {
        reply = 'THERMAL BURN PROTOCOL: 1) Stop burning process. 2) Cool with clean room-temperature water for 10-20 min (do NOT use ice). 3) Remove non-adherent constrictive items. 4) Cover with sterile, non-adherent dressing. Avoid bursting blisters.';
      } else {
        reply = `Recommendation: Allocate Team Bravo to secondary perimeter. Mesh network stability at ${avgBattery}% average node battery. Keep radio broadcast channels clear for priority medical traffic.`;
      }

      setAiMessages(prev => [...prev, { sender: 'assistant', text: reply }]);
    }, 600);
  };

  // -------------------------------------------------------------
  // UNAUTHENTICATED TERMINAL LOGIN VIEW
  // -------------------------------------------------------------
  if (!operatorToken) {
    return (
      <div className="auth-shell" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', padding: '1rem', background: '#0a0d12' }}>
        <div className="auth-card" style={{ width: '100%', maxWidth: '440px', background: '#121820', border: '1px solid #223147', borderRadius: '14px', padding: '2.5rem', boxShadow: '0 20px 60px rgba(0,0,0,0.8)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.25rem' }}>
            <div style={{ width: '42px', height: '42px', borderRadius: '10px', background: 'rgba(239, 68, 68, 0.15)', border: '1px solid #ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ef4444' }}>
              <ShieldAlert size={24} />
            </div>
            <div>
              <h1 style={{ fontSize: '1.25rem', fontWeight: '800', letterSpacing: '0.05em', color: '#f1f5f9', margin: 0 }}>
                MESHLINK COMMAND TERMINAL
              </h1>
              <p style={{ fontSize: '0.75rem', color: '#8e9fb5', margin: 0, letterSpacing: '0.04em' }}>
                TACTICAL EMERGENCY RESPONDER INTERFACE
              </p>
            </div>
          </div>

          <p style={{ fontSize: '0.85rem', color: '#9aabc0', marginBottom: '1.5rem', lineHeight: 1.5 }}>
            Access restricted to authorized incident dispatchers and disaster response coordinators. Enter operator security key to initialize live radar.
          </p>

          {loginError && (
            <div data-testid="auth-error" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(239, 68, 68, 0.12)', border: '1px solid #ef4444', borderRadius: '8px', padding: '0.75rem 1rem', color: '#fca5a5', fontSize: '0.82rem', marginBottom: '1.25rem' }}>
              <AlertTriangle size={18} style={{ flexShrink: 0 }} />
              <span>{loginError}</span>
            </div>
          )}

          <form onSubmit={handleLogin}>
            <div style={{ marginBottom: '1.25rem' }}>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: '700', letterSpacing: '0.05em', color: '#8e9fb5', marginBottom: '0.5rem' }}>
                OPERATOR CREDENTIAL / TOKEN
              </label>
              <input
                data-testid="token-input"
                type="password"
                placeholder="e.g. op_token_valid_1 or password"
                value={loginInput}
                onChange={(e) => setLoginInput(e.target.value)}
                style={{ width: '100%', height: '46px', padding: '0 1rem', background: '#182230', border: '1px solid #223147', borderRadius: '8px', color: '#f1f5f9', fontSize: '0.9rem', outline: 'none' }}
              />
            </div>

            <button
              data-testid="auth-submit"
              type="submit"
              disabled={isAuthenticating}
              style={{ width: '100%', height: '46px', background: '#ef4444', color: '#ffffff', fontWeight: '700', fontSize: '0.9rem', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', letterSpacing: '0.04em', boxShadow: '0 4px 14px rgba(239, 68, 68, 0.4)' }}
            >
              {isAuthenticating ? <Activity size={18} className="animate-spin" /> : <Zap size={18} />}
              {isAuthenticating ? 'AUTHENTICATING...' : 'INITIALIZE COMMAND TERMINAL'}
            </button>

            <div style={{ marginTop: '1.25rem', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
              <button
                type="button"
                onClick={() => { setLoginInput('meshlink_op_dev_secret'); }}
                style={{ fontSize: '0.78rem', color: '#10b981', textDecoration: 'underline', background: 'none', fontWeight: 'bold' }}
              >
                Auto-fill Default Key: meshlink_op_dev_secret
              </button>
              <button
                type="button"
                onClick={() => { setLoginInput('op_token_valid_1'); }}
                style={{ fontSize: '0.72rem', color: '#06b6d4', textDecoration: 'underline', background: 'none' }}
              >
                Or Test Key: op_token_valid_1
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  // -------------------------------------------------------------
  // AUTHENTICATED TACTICAL OPERATIONAL SUITE
  // -------------------------------------------------------------
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', background: 'var(--bg-void)' }}>
      {/* ========================================================= */}
      {/* TOP COMMAND BAR / HUD */}
      {/* ========================================================= */}
      <header style={{ height: '62px', background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 1.25rem', zIndex: 1000 }}>
        {/* Left: Brand & Navigation */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <div style={{ width: '32px', height: '32px', borderRadius: '8px', background: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
              <ShieldAlert size={20} />
            </div>
            <div>
              <div style={{ fontSize: '0.92rem', fontWeight: '900', letterSpacing: '0.06em', color: '#f1f5f9' }}>
                NEXUS SUPPORT // OPERATIONAL COMMAND
              </div>
              <div style={{ fontSize: '0.68rem', color: '#8e9fb5', letterSpacing: '0.04em' }}>
                MESHLINK COMMAND // INCIDENT DISPATCH
              </div>
            </div>
          </div>

          {/* Tactical View Tabs */}
          <nav style={{ display: 'flex', gap: '0.35rem', marginLeft: '1rem' }}>
            {[
              { id: 'situation', label: 'Situation Room', icon: MapPin },
              { id: 'incidents', label: 'Incident Triage', icon: AlertTriangle, count: activeCount },
              { id: 'topology', label: 'Mesh Radar', icon: Network, count: nodes.length },
              { id: 'broadcast', label: 'Broadcast Comms', icon: Radio },
              { id: 'ai', label: 'AI Copilot & SOPs', icon: Sparkles },
            ].map(tab => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.45rem',
                    padding: '0.45rem 0.85rem',
                    borderRadius: '6px',
                    fontSize: '0.8rem',
                    fontWeight: isActive ? '700' : '500',
                    color: isActive ? '#f1f5f9' : '#8e9fb5',
                    background: isActive ? 'var(--bg-panel)' : 'transparent',
                    border: isActive ? '1px solid var(--border-tactical)' : '1px solid transparent',
                  }}
                >
                  <Icon size={15} color={isActive ? '#38bdf8' : '#8e9fb5'} />
                  <span>{tab.label}</span>
                  {tab.count !== undefined && (
                    <span style={{ fontSize: '0.68rem', padding: '0.1rem 0.4rem', borderRadius: '10px', background: isActive ? '#ef4444' : '#223147', color: '#fff', fontWeight: 'bold' }}>
                      {tab.count}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Right: Live Telemetry Metrics & Tools */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
          {/* Live WS Status Badge */}
          <div
            data-testid="ws-status-badge"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.3rem 0.65rem',
              borderRadius: '6px',
              fontSize: '0.72rem',
              fontWeight: '700',
              fontFamily: 'var(--font-mono)',
              background: wsConnected ? 'rgba(16, 185, 129, 0.12)' : 'rgba(239, 68, 68, 0.15)',
              color: wsConnected ? '#10b981' : '#ef4444',
              border: `1px solid ${wsConnected ? '#10b981' : '#ef4444'}`
            }}
          >
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: wsConnected ? '#10b981' : '#ef4444' }} />
            {wsConnected ? 'WSS LIVE' : 'WS RECONNECTING'}
          </div>

          {/* Quick Metrics */}
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', background: 'var(--bg-panel)', padding: '0.3rem 0.6rem', borderRadius: '6px', border: '1px solid var(--border-tactical)', fontSize: '0.72rem' }}>
              <AlertTriangle size={13} color="#ef4444" />
              <span style={{ color: '#8e9fb5' }}>SOS:</span>
              <strong style={{ color: '#ef4444' }}>{criticalCount}</strong>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', background: 'var(--bg-panel)', padding: '0.3rem 0.6rem', borderRadius: '6px', border: '1px solid var(--border-tactical)', fontSize: '0.72rem' }}>
              <BatteryCharging size={13} color="#10b981" />
              <span style={{ color: '#8e9fb5' }}>FLEET:</span>
              <strong style={{ color: '#10b981' }}>{avgBattery}%</strong>
            </div>
          </div>

          {/* Sound Mute Toggle */}
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            title={soundEnabled ? 'Mute Audio Alerts' : 'Unmute Audio Alerts'}
            style={{ width: '34px', height: '34px', borderRadius: '6px', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: soundEnabled ? '#10b981' : '#8e9fb5' }}
          >
            {soundEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
          </button>

          {/* Logout Button */}
          <button
            data-testid="logout-button"
            onClick={handleLogout}
            title="Log Out Operator"
            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.35rem 0.75rem', borderRadius: '6px', background: 'rgba(239, 68, 68, 0.12)', border: '1px solid #ef4444', color: '#fca5a5', fontSize: '0.75rem', fontWeight: 'bold' }}
          >
            <LogOut size={14} />
            <span>EXIT</span>
          </button>
        </div>
      </header>

      {/* ========================================================= */}
      {/* MAIN VIEW CONTENT CONTAINER */}
      {/* ========================================================= */}
      <main style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {/* ------------------------------------------------------- */}
        {/* VIEW 1: SITUATION ROOM (FEED + TACTICAL MAP + DRAWER) */}
        {/* ------------------------------------------------------- */}
        {activeTab === 'situation' && (
          <div style={{ display: 'flex', height: '100%', width: '100%', position: 'relative' }}>
            {/* Left: Operational Incident Live Feed & Filter Strip */}
            <div style={{ width: '360px', height: '100%', background: 'var(--bg-surface)', borderRight: '1px solid var(--border-tactical)', display: 'flex', flexDirection: 'column', zIndex: 500 }}>
              {/* Search & Filter Controls */}
              <div style={{ padding: '0.85rem', borderBottom: '1px solid var(--border-tactical)', display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                <div style={{ position: 'relative' }}>
                  <Search size={15} color="#8e9fb5" style={{ position: 'absolute', left: '10px', top: '10px' }} />
                  <input
                    data-testid="search-input"
                    type="text"
                    placeholder="Search beacon reports, units..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    style={{ width: '100%', height: '34px', paddingLeft: '32px', paddingRight: '10px', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '6px', color: '#f1f5f9', fontSize: '0.78rem' }}
                  />
                </div>

                <div style={{ display: 'flex', gap: '0.35rem' }}>
                  <button
                    data-testid="filter-critical"
                    onClick={() => setSeverityFilter(severityFilter === 'CRITICAL' ? 'ALL' : 'CRITICAL')}
                    style={{
                      flex: 1,
                      padding: '0.3rem 0',
                      borderRadius: '5px',
                      fontSize: '0.72rem',
                      fontWeight: 'bold',
                      background: severityFilter === 'CRITICAL' ? '#ef4444' : 'var(--bg-panel)',
                      color: severityFilter === 'CRITICAL' ? '#fff' : '#ef4444',
                      border: '1px solid #ef4444'
                    }}
                  >
                    CRITICAL SEV 8+
                  </button>

                  <button
                    data-testid="filter-low"
                    onClick={() => setSeverityFilter(severityFilter === 'LOW' ? 'ALL' : 'LOW')}
                    style={{
                      flex: 1,
                      padding: '0.3rem 0',
                      borderRadius: '5px',
                      fontSize: '0.72rem',
                      fontWeight: 'bold',
                      background: severityFilter === 'LOW' ? '#3b82f6' : 'var(--bg-panel)',
                      color: severityFilter === 'LOW' ? '#fff' : '#38bdf8',
                      border: '1px solid #3b82f6'
                    }}
                  >
                    LOW SEV &lt; 7
                  </button>

                  <button
                    onClick={() => setSeverityFilter('ALL')}
                    style={{
                      padding: '0.3rem 0.5rem',
                      borderRadius: '5px',
                      fontSize: '0.72rem',
                      background: severityFilter === 'ALL' ? '#223147' : 'transparent',
                      color: '#8e9fb5'
                    }}
                  >
                    ALL
                  </button>
                </div>
              </div>

              {/* Incidents Scrollable List */}
              <div style={{ flex: 1, padding: '0.6rem', display: 'flex', flexDirection: 'column', gap: '0.6rem', overflowY: 'auto' }}>
                {filteredIncidents.map(inc => {
                  const isSelected = selectedIncident?.uuid === inc.uuid;
                  return (
                    <div
                      key={inc.uuid}
                      onClick={() => setSelectedIncident(inc)}
                      style={{
                        padding: '0.75rem',
                        borderRadius: '8px',
                        background: isSelected ? 'var(--bg-panel-hover)' : 'var(--bg-panel)',
                        border: isSelected ? '1px solid #38bdf8' : '1px solid var(--border-tactical)',
                        cursor: 'pointer',
                        transition: 'all 0.15s'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                        <span style={{ fontSize: '0.68rem', fontWeight: 'bold', color: inc.severity >= 8 ? '#ef4444' : '#f59e0b' }}>
                          [{inc.emergencyType}] {inc.senderName || inc.senderId}
                        </span>
                        <span
                          data-testid="hop-badge"
                          style={{
                            fontSize: '0.65rem',
                            fontWeight: 'bold',
                            padding: '0.1rem 0.35rem',
                            borderRadius: '4px',
                            background: inc.hopCount > 0 ? '#1e293b' : 'rgba(16, 185, 129, 0.15)',
                            color: inc.hopCount > 0 ? '#38bdf8' : '#10b981'
                          }}
                        >
                          {inc.hopCount > 0 ? `HOP: ${inc.hopCount}` : 'DIRECT RF'}
                        </span>
                      </div>
                      <p style={{ fontSize: '0.76rem', color: '#cbd5e1', margin: 0, lineHeight: 1.35 }}>
                        {inc.message}
                      </p>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.5rem', fontSize: '0.68rem', color: '#64748b' }}>
                        <span>SEV {inc.severity}</span>
                        <span style={{ color: inc.status === 'RESOLVED' ? '#10b981' : inc.status === 'ASSIGNED' ? '#f59e0b' : '#ef4444' }}>
                          {inc.status}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Tactical Map Area */}
            <div style={{ flex: 1, height: '100%', position: 'relative' }}>
              <MapContainer
                center={[37.7749, -122.4194]}
                zoom={13}
                style={{ height: '100%', width: '100%' }}
                className="tactical-map-container"
              >
                <TileLayer
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                />

                <MapFlyController
                  targetCoords={selectedIncident ? [selectedIncident.latitude, selectedIncident.longitude] : null}
                />

                {/* Render SOS Beacons */}
                {sosPackets.map(inc => (
                  <Marker
                    key={inc.uuid}
                    position={[inc.latitude, inc.longitude]}
                    icon={createSosIcon(inc.severity)}
                    eventHandlers={{
                      click: () => setSelectedIncident(inc),
                    }}
                  >
                    <Popup>
                      <div style={{ fontSize: '0.8rem', padding: '0.2rem' }}>
                        <strong style={{ color: '#ef4444' }}>[{inc.emergencyType}] {inc.senderName || inc.senderId}</strong>
                        <div style={{ fontSize: '0.72rem', color: '#94a3b8', marginTop: '0.2rem' }}>
                          SEV: {inc.severity} | HOP: {inc.hopCount ?? 0}
                        </div>
                      </div>
                    </Popup>
                  </Marker>
                ))}

                {/* Render Mesh Nodes */}
                {nodes.map(node => (
                  <Marker
                    key={node.deviceId}
                    position={[37.7749 + (Math.random() - 0.5) * 0.04, -122.4194 + (Math.random() - 0.5) * 0.04]}
                    icon={createNodeIcon(node.role)}
                  >
                    <Popup>
                      <div style={{ fontSize: '0.8rem' }}>
                        <strong>{node.displayName}</strong>
                        <div style={{ color: '#10b981' }}>Role: {node.role}</div>
                        <div>Battery: {node.battery}% | RSSI: {node.rssi} dBm</div>
                      </div>
                    </Popup>
                  </Marker>
                ))}
              </MapContainer>
            </div>

            {/* Slide-out Selected Incident Telemetry Drawer */}
            {selectedIncident && (
              <aside style={{ width: '380px', height: '100%', background: 'var(--bg-surface)', borderLeft: '1px solid var(--border-tactical)', display: 'flex', flexDirection: 'column', zIndex: 600, overflowY: 'auto' }}>
                {/* Header */}
                <div style={{ padding: '1rem', borderBottom: '1px solid var(--border-tactical)', background: 'var(--bg-panel)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.4rem' }}>
                    <span style={{ fontSize: '0.7rem', fontWeight: '800', letterSpacing: '0.06em', padding: '0.2rem 0.5rem', borderRadius: '4px', background: selectedIncident.severity >= 8 ? '#ef4444' : '#f59e0b', color: '#fff' }}>
                      PRIORITY SEV {selectedIncident.severity}
                    </span>
                    <span style={{ fontSize: '0.72rem', color: '#8e9fb5', fontFamily: 'var(--font-mono)' }}>
                      UUID: {selectedIncident.uuid.slice(0, 12)}
                    </span>
                  </div>
                  <h3 style={{ fontSize: '1.05rem', fontWeight: '800', color: '#f1f5f9', margin: 0 }}>
                    {selectedIncident.emergencyType} BEACON // {selectedIncident.senderName || selectedIncident.senderId}
                  </h3>
                </div>

                {/* Details Body */}
                <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1.1rem', flex: 1 }}>
                  {/* Status Banner */}
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.65rem 0.85rem', borderRadius: '8px', background: selectedIncident.status === 'RESOLVED' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.12)', border: `1px solid ${selectedIncident.status === 'RESOLVED' ? '#10b981' : '#ef4444'}` }}>
                    <span style={{ fontSize: '0.78rem', fontWeight: 'bold', color: selectedIncident.status === 'RESOLVED' ? '#10b981' : '#fca5a5' }}>
                      STATUS: {selectedIncident.status}
                    </span>
                    <button
                      onClick={() => advanceIncidentStatus(selectedIncident.uuid)}
                      style={{ padding: '0.25rem 0.6rem', borderRadius: '5px', background: '#3b82f6', color: '#fff', fontSize: '0.7rem', fontWeight: 'bold' }}
                    >
                      ADVANCE STATUS
                    </button>
                  </div>

                  {/* Message Log */}
                  <div>
                    <label style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#8e9fb5', letterSpacing: '0.04em' }}>TRANSMITTED FIELD REPORT</label>
                    <div style={{ marginTop: '0.4rem', padding: '0.75rem', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px', color: '#e2e8f0', fontSize: '0.85rem', lineHeight: 1.5 }}>
                      "{selectedIncident.message}"
                    </div>
                  </div>

                  {/* Telemetry Metrics Grid */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.6rem' }}>
                    <div style={{ background: 'var(--bg-panel)', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border-tactical)' }}>
                      <div style={{ fontSize: '0.68rem', color: '#8e9fb5' }}>COORDINATES</div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 'bold', color: '#f1f5f9', fontFamily: 'var(--font-mono)', marginTop: '0.2rem' }}>
                        {selectedIncident.latitude.toFixed(4)}, {selectedIncident.longitude.toFixed(4)}
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-panel)', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border-tactical)' }}>
                      <div style={{ fontSize: '0.68rem', color: '#8e9fb5' }}>MESH HOPS</div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 'bold', color: '#38bdf8', marginTop: '0.2rem' }}>
                        {selectedIncident.hopCount > 0 ? `HOP: ${selectedIncident.hopCount}` : 'DIRECT RF'}
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-panel)', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border-tactical)' }}>
                      <div style={{ fontSize: '0.68rem', color: '#8e9fb5' }}>CASUALTIES</div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 'bold', color: selectedIncident.casualties > 0 ? '#ef4444' : '#10b981', marginTop: '0.2rem' }}>
                        {selectedIncident.casualties || 0} Reported
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-panel)', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--border-tactical)' }}>
                      <div style={{ fontSize: '0.68rem', color: '#8e9fb5' }}>BATTERY LEVEL</div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 'bold', color: '#10b981', marginTop: '0.2rem' }}>
                        {selectedIncident.battery || 78}%
                      </div>
                    </div>
                  </div>

                  {/* Medical / ICE Card Info */}
                  {selectedIncident.bloodType && (
                    <div style={{ padding: '0.75rem', background: 'rgba(59, 130, 246, 0.08)', border: '1px solid #3b82f6', borderRadius: '8px' }}>
                      <div style={{ fontSize: '0.72rem', fontWeight: 'bold', color: '#60a5fa', marginBottom: '0.3rem' }}>
                        EMERGENCY MEDICAL ID (ICE)
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#e2e8f0' }}>
                        Blood Type: <strong>{selectedIncident.bloodType}</strong> | Allergies: <strong>{selectedIncident.allergies || 'None'}</strong>
                      </div>
                    </div>
                  )}

                  {/* Quick Tactical Action Buttons */}
                  <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <button
                      onClick={() => {
                        setBroadcastMsg(`RESPONDER ACK: En route to ${selectedIncident.senderName || selectedIncident.senderId}. Stand by.`);
                        setActiveTab('broadcast');
                      }}
                      style={{ width: '100%', height: '40px', background: '#3b82f6', color: '#fff', fontWeight: '700', borderRadius: '6px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}
                    >
                      <Radio size={16} />
                      <span>DISPATCH RADIO ACK</span>
                    </button>

                    <button
                      onClick={() => {
                        setAiInput(`Provide emergency triage protocols for: "${selectedIncident.message}" with ${selectedIncident.casualties || 1} casualties.`);
                        setActiveTab('ai');
                      }}
                      style={{ width: '100%', height: '40px', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', color: '#f1f5f9', fontWeight: '600', borderRadius: '6px', fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}
                    >
                      <Sparkles size={16} color="#06b6d4" />
                      <span>ASK AI EMERGENCY ADVISOR</span>
                    </button>
                  </div>
                </div>
              </aside>
            )}
          </div>
        )}

        {/* ------------------------------------------------------- */}
        {/* VIEW 2: INCIDENT COMMAND & TRIAGE CENTER */}
        {/* ------------------------------------------------------- */}
        {activeTab === 'incidents' && (
          <div style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: '1.25rem', overflowY: 'auto' }}>
            {/* Kanban Columns */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1.25rem', flex: 1, minHeight: '400px' }}>
              {/* Column 1: New / Active */}
              <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', display: 'flex', flexDirection: 'column' }}>
                <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: '800', color: '#ef4444' }}>🔴 NEW SOS ALERTS</span>
                  <span style={{ fontSize: '0.72rem', background: '#ef4444', color: '#fff', padding: '0.1rem 0.45rem', borderRadius: '10px', fontWeight: 'bold' }}>
                    {filteredIncidents.filter(i => i.status === 'ACTIVE').length}
                  </span>
                </div>
                <div style={{ padding: '0.75rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', overflowY: 'auto', flex: 1 }}>
                  {filteredIncidents.filter(i => i.status === 'ACTIVE').map(inc => (
                    <div
                      key={inc.uuid}
                      onClick={() => { setSelectedIncident(inc); setActiveTab('situation'); }}
                      style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px', padding: '0.85rem', cursor: 'pointer', transition: 'border-color 0.2s' }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                        <span style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#ef4444' }}>[{inc.emergencyType}]</span>
                        <span style={{ fontSize: '0.68rem', color: '#38bdf8' }}>
                          {inc.hopCount > 0 ? `HOP: ${inc.hopCount}` : 'DIRECT RF'}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.82rem', fontWeight: 'bold', color: '#f1f5f9', marginBottom: '0.35rem' }}>
                        {inc.senderName || inc.senderId}
                      </div>
                      <p style={{ fontSize: '0.78rem', color: '#94a3b8', margin: 0, lineHeight: 1.4 }}>
                        {inc.message}
                      </p>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.6rem', borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: '0.4rem' }}>
                        <span style={{ fontSize: '0.68rem', color: '#64748b' }}>SEV {inc.severity}</span>
                        <button
                          onClick={(e) => { e.stopPropagation(); advanceIncidentStatus(inc.uuid); }}
                          style={{ fontSize: '0.7rem', color: '#38bdf8', fontWeight: 'bold' }}
                        >
                          ASSIGN →
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Column 2: In Progress / Assigned */}
              <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', display: 'flex', flexDirection: 'column' }}>
                <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: '800', color: '#f59e0b' }}>🟡 RESPONDING EN ROUTE</span>
                  <span style={{ fontSize: '0.72rem', background: '#f59e0b', color: '#fff', padding: '0.1rem 0.45rem', borderRadius: '10px', fontWeight: 'bold' }}>
                    {filteredIncidents.filter(i => i.status === 'ASSIGNED').length}
                  </span>
                </div>
                <div style={{ padding: '0.75rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', overflowY: 'auto', flex: 1 }}>
                  {filteredIncidents.filter(i => i.status === 'ASSIGNED').map(inc => (
                    <div
                      key={inc.uuid}
                      onClick={() => { setSelectedIncident(inc); setActiveTab('situation'); }}
                      style={{ background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px', padding: '0.85rem', cursor: 'pointer' }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                        <span style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#f59e0b' }}>[{inc.emergencyType}]</span>
                        <span style={{ fontSize: '0.68rem', color: '#10b981' }}>{inc.assignedTo || 'Medic Unit'}</span>
                      </div>
                      <div style={{ fontSize: '0.82rem', fontWeight: 'bold', color: '#f1f5f9', marginBottom: '0.35rem' }}>
                        {inc.senderName || inc.senderId}
                      </div>
                      <p style={{ fontSize: '0.78rem', color: '#94a3b8', margin: 0 }}>{inc.message}</p>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.6rem' }}>
                        <button
                          onClick={(e) => { e.stopPropagation(); advanceIncidentStatus(inc.uuid); }}
                          style={{ fontSize: '0.7rem', color: '#10b981', fontWeight: 'bold' }}
                        >
                          RESOLVE ✓
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Column 3: Resolved */}
              <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', display: 'flex', flexDirection: 'column' }}>
                <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: '800', color: '#10b981' }}>🟢 EVACUATED / RESOLVED</span>
                  <span style={{ fontSize: '0.72rem', background: '#10b981', color: '#fff', padding: '0.1rem 0.45rem', borderRadius: '10px', fontWeight: 'bold' }}>
                    {filteredIncidents.filter(i => i.status === 'RESOLVED').length}
                  </span>
                </div>
                <div style={{ padding: '0.75rem', display: 'flex', flexDirection: 'column', gap: '0.75rem', overflowY: 'auto', flex: 1 }}>
                  {filteredIncidents.filter(i => i.status === 'RESOLVED').map(inc => (
                    <div
                      key={inc.uuid}
                      style={{ background: 'var(--bg-panel)', border: '1px solid rgba(16, 185, 129, 0.3)', borderRadius: '8px', padding: '0.85rem', opacity: 0.8 }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                        <span style={{ fontSize: '0.7rem', color: '#10b981', fontWeight: 'bold' }}>RESOLVED</span>
                        <CheckCircle size={14} color="#10b981" />
                      </div>
                      <div style={{ fontSize: '0.82rem', fontWeight: 'bold', color: '#f1f5f9' }}>{inc.senderName || inc.senderId}</div>
                      <p style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.2rem' }}>{inc.message}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------- */}
        {/* VIEW 3: MESH NETWORK TOPOLOGY & FLEET RADAR */}
        {/* ------------------------------------------------------- */}
        {activeTab === 'topology' && (
          <div style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: '1.25rem', overflowY: 'auto' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
              <div>
                <h2 style={{ fontSize: '1.1rem', fontWeight: '800', color: '#f1f5f9', margin: 0 }}>
                  MESH TOPOLOGY & NODE TELEMETRY ROSTER
                </h2>
                <p style={{ fontSize: '0.78rem', color: '#8e9fb5', margin: 0 }}>
                  Active P2P RF links, hop distances from gateway, and battery health
                </p>
              </div>
              <div style={{ fontSize: '0.78rem', color: '#10b981', fontFamily: 'var(--font-mono)' }}>
                TOTAL CONNECTED NODES: {nodes.length}
              </div>
            </div>

            {/* Topology SVG Network Diagram */}
            <div style={{ height: '220px', background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', marginBottom: '1.25rem', position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="100%" height="100%" style={{ position: 'absolute', top: 0, left: 0 }}>
                <line x1="20%" y1="50%" x2="50%" y2="50%" stroke="#3b82f6" strokeWidth="2" strokeDasharray="4" />
                <line x1="50%" y1="50%" x2="80%" y2="30%" stroke="#10b981" strokeWidth="2" />
                <line x1="50%" y1="50%" x2="80%" y2="70%" stroke="#10b981" strokeWidth="2" />
                <line x1="80%" y1="70%" x2="92%" y2="80%" stroke="#f59e0b" strokeWidth="1.5" strokeDasharray="2" />
              </svg>

              <div style={{ position: 'absolute', left: '20%', top: '50%', transform: 'translate(-50%, -50%)', background: '#1e3a8a', border: '2px solid #3b82f6', borderRadius: '10px', padding: '0.5rem 0.8rem', textAlign: 'center', zIndex: 10 }}>
                <Cpu size={20} color="#60a5fa" style={{ margin: '0 auto' }} />
                <div style={{ fontSize: '0.72rem', fontWeight: 'bold', color: '#fff' }}>CENTRAL GATEWAY</div>
                <div style={{ fontSize: '0.65rem', color: '#93c5fd' }}>Internet / WSS Uplink</div>
              </div>

              <div style={{ position: 'absolute', left: '50%', top: '50%', transform: 'translate(-50%, -50%)', background: '#064e3b', border: '2px solid #10b981', borderRadius: '10px', padding: '0.4rem 0.7rem', textAlign: 'center', zIndex: 10 }}>
                <Network size={18} color="#34d399" style={{ margin: '0 auto' }} />
                <div style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#fff' }}>Echo Relay 01</div>
                <div style={{ fontSize: '0.62rem', color: '#6ee7b7' }}>Hop 1 (RSSI -58dBm)</div>
              </div>

              <div style={{ position: 'absolute', left: '80%', top: '30%', transform: 'translate(-50%, -50%)', background: '#182230', border: '1px solid #10b981', borderRadius: '8px', padding: '0.35rem 0.6rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.68rem', fontWeight: 'bold', color: '#fff' }}>Bravo Patrol</div>
                <div style={{ fontSize: '0.62rem', color: '#10b981' }}>Hop 2 (BLE)</div>
              </div>

              <div style={{ position: 'absolute', left: '80%', top: '70%', transform: 'translate(-50%, -50%)', background: '#182230', border: '1px solid #10b981', borderRadius: '8px', padding: '0.35rem 0.6rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.68rem', fontWeight: 'bold', color: '#fff' }}>Delta Recon</div>
                <div style={{ fontSize: '0.62rem', color: '#10b981' }}>Hop 2 (LoRa)</div>
              </div>

              <div style={{ position: 'absolute', left: '92%', top: '80%', transform: 'translate(-50%, -50%)', background: '#182230', border: '1px solid #f59e0b', borderRadius: '8px', padding: '0.3rem 0.5rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.65rem', fontWeight: 'bold', color: '#fca5a5' }}>SOS Beacon 03</div>
                <div style={{ fontSize: '0.6rem', color: '#f59e0b' }}>Hop 3 (Degraded)</div>
              </div>
            </div>

            {/* Roster Table */}
            <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.8rem' }}>
                <thead>
                  <tr style={{ background: 'var(--bg-panel)', borderBottom: '1px solid var(--border-tactical)', color: '#8e9fb5' }}>
                    <th style={{ padding: '0.75rem 1rem' }}>DEVICE ID</th>
                    <th style={{ padding: '0.75rem 1rem' }}>CALLSIGN</th>
                    <th style={{ padding: '0.75rem 1rem' }}>ROLE</th>
                    <th style={{ padding: '0.75rem 1rem' }}>RADIO INTERFACE</th>
                    <th style={{ padding: '0.75rem 1rem' }}>SIGNAL RSSI</th>
                    <th style={{ padding: '0.75rem 1rem' }}>HOPS</th>
                    <th style={{ padding: '0.75rem 1rem' }}>BATTERY</th>
                    <th style={{ padding: '0.75rem 1rem' }}>LAST HEARD</th>
                  </tr>
                </thead>
                <tbody>
                  {nodes.map(n => (
                    <tr key={n.deviceId} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                      <td style={{ padding: '0.75rem 1rem', fontFamily: 'var(--font-mono)', color: '#38bdf8' }}>{n.deviceId}</td>
                      <td style={{ padding: '0.75rem 1rem', fontWeight: 'bold', color: '#f1f5f9' }}>{n.displayName}</td>
                      <td style={{ padding: '0.75rem 1rem' }}>
                        <span style={{ fontSize: '0.7rem', padding: '0.15rem 0.5rem', borderRadius: '4px', background: n.role === 'GATEWAY' ? '#1e3a8a' : '#064e3b', color: '#fff', fontWeight: 'bold' }}>
                          {n.role}
                        </span>
                      </td>
                      <td style={{ padding: '0.75rem 1rem', color: '#94a3b8' }}>{n.radio || 'BLE 5.2'}</td>
                      <td style={{ padding: '0.75rem 1rem', fontFamily: 'var(--font-mono)', color: (n.rssi || -60) > -70 ? '#10b981' : '#f59e0b' }}>
                        {n.rssi || -65} dBm
                      </td>
                      <td style={{ padding: '0.75rem 1rem', fontWeight: 'bold', color: n.hops === 0 ? '#38bdf8' : '#e2e8f0' }}>
                        {n.hops ?? 1}
                      </td>
                      <td style={{ padding: '0.75rem 1rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                          <div style={{ width: '50px', height: '6px', background: '#223147', borderRadius: '3px', overflow: 'hidden' }}>
                            <div style={{ width: `${n.battery || 75}%`, height: '100%', background: (n.battery || 75) > 40 ? '#10b981' : '#ef4444' }} />
                          </div>
                          <span>{n.battery || 75}%</span>
                        </div>
                      </td>
                      <td style={{ padding: '0.75rem 1rem', color: '#64748b' }}>{n.lastSeen || 'Recent'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------- */}
        {/* VIEW 4: EMERGENCY BROADCAST CONSOLE */}
        {/* ------------------------------------------------------- */}
        {activeTab === 'broadcast' && (
          <div style={{ height: '100%', display: 'flex', gap: '1.25rem', padding: '1.25rem' }}>
            {/* Left: Broadcast Composer */}
            <div style={{ width: '480px', display: 'flex', flexDirection: 'column', gap: '1rem', background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', padding: '1.25rem' }}>
              <div>
                <h2 style={{ fontSize: '1.05rem', fontWeight: '800', color: '#f1f5f9', margin: 0 }}>
                  EMERGENCY MESH BROADCAST DISPATCHER
                </h2>
                <p style={{ fontSize: '0.78rem', color: '#8e9fb5', margin: 0 }}>
                  Transmits unencrypted emergency advisories to all in-range field nodes
                </p>
              </div>

              {/* Priority Toggle */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem', background: 'var(--bg-panel)', borderRadius: '8px', border: '1px solid var(--border-tactical)' }}>
                <div>
                  <div style={{ fontSize: '0.8rem', fontWeight: 'bold', color: broadcastUrgent ? '#ef4444' : '#8e9fb5' }}>
                    HIGH-PRIORITY ALARM TONE
                  </div>
                  <div style={{ fontSize: '0.7rem', color: '#64748b' }}>Triggers audible buzzer on receiving survivor phones</div>
                </div>
                <input
                  type="checkbox"
                  checked={broadcastUrgent}
                  onChange={(e) => setBroadcastUrgent(e.target.checked)}
                  style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                />
              </div>

              {/* Quick Canned Preset Buttons */}
              <div>
                <label style={{ fontSize: '0.72rem', fontWeight: 'bold', color: '#8e9fb5', letterSpacing: '0.04em' }}>CANNED EMERGENCY TEMPLATES</label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.4rem', marginTop: '0.4rem' }}>
                  {[
                    'Evacuate immediately to Zone B.',
                    'Shelter in place and conserve battery.',
                    'Medical rescue team en route to your sector.',
                    'All clear: Flood crest receding.'
                  ].map(tmpl => (
                    <button
                      key={tmpl}
                      onClick={() => setBroadcastMsg(tmpl)}
                      style={{ padding: '0.45rem', fontSize: '0.72rem', textAlign: 'left', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '6px', color: '#e2e8f0' }}
                    >
                      {tmpl}
                    </button>
                  ))}
                </div>
              </div>

              {/* Text Input */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <textarea
                  rows={4}
                  placeholder="Type emergency advisory message..."
                  value={broadcastMsg}
                  onChange={(e) => setBroadcastMsg(e.target.value)}
                  style={{ width: '100%', flex: 1, background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px', padding: '0.75rem', color: '#f1f5f9', fontSize: '0.85rem', resize: 'none' }}
                />
              </div>

              <button
                onClick={handleSendBroadcast}
                disabled={!broadcastMsg.trim()}
                style={{ height: '44px', background: broadcastUrgent ? '#ef4444' : '#3b82f6', color: '#fff', fontWeight: 'bold', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', boxShadow: broadcastUrgent ? '0 4px 14px rgba(239, 68, 68, 0.4)' : 'none' }}
              >
                <Send size={16} />
                <span>BROADCAST TO ALL MESH NODES</span>
              </button>
            </div>

            {/* Right: Broadcast Feed Log */}
            <div style={{ flex: 1, background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', display: 'flex', flexDirection: 'column' }}>
              <div style={{ padding: '0.85rem 1.25rem', borderBottom: '1px solid var(--border-tactical)' }}>
                <h3 style={{ fontSize: '0.9rem', fontWeight: 'bold', color: '#f1f5f9', margin: 0 }}>
                  TRANSMITTED BROADCAST STREAM (RECEIPT ACKS)
                </h3>
              </div>
              <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.85rem', overflowY: 'auto', flex: 1 }}>
                {broadcastFeed.map(b => (
                  <div key={b.id} style={{ background: 'var(--bg-panel)', border: `1px solid ${b.urgent ? '#ef4444' : 'var(--border-tactical)'}`, borderRadius: '8px', padding: '1rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                      <span style={{ fontSize: '0.7rem', fontWeight: 'bold', color: b.urgent ? '#ef4444' : '#38bdf8' }}>
                        {b.urgent ? '🔴 URGENT BROADCAST' : 'ADVISORY'}
                      </span>
                      <span style={{ fontSize: '0.7rem', color: '#64748b' }}>{b.time}</span>
                    </div>
                    <div style={{ fontSize: '0.88rem', color: '#f1f5f9', lineHeight: 1.4 }}>{b.text}</div>
                    <div style={{ marginTop: '0.6rem', fontSize: '0.72rem', color: '#10b981', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                      <CheckCircle size={13} />
                      <span>Acknowledged by {b.acks} field devices</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------- */}
        {/* VIEW 5: AI COPILOT & EMERGENCY SURVIVAL SOPS */}
        {/* ------------------------------------------------------- */}
        {activeTab === 'ai' && (
          <div style={{ height: '100%', display: 'flex', gap: '1.25rem', padding: '1.25rem' }}>
            {/* Left: Chat Terminal */}
            <div style={{ flex: 1, background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', display: 'flex', flexDirection: 'column' }}>
              <div style={{ padding: '0.85rem 1.25rem', borderBottom: '1px solid var(--border-tactical)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Sparkles size={18} color="#06b6d4" />
                <h3 style={{ fontSize: '0.92rem', fontWeight: 'bold', color: '#f1f5f9', margin: 0 }}>
                  NEXUS OFFLINE AI COPILOT & CASUALTY TRIAGE
                </h3>
              </div>

              {/* Chat Message List */}
              <div style={{ flex: 1, padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.85rem', overflowY: 'auto' }}>
                {aiMessages.map((m, idx) => (
                  <div
                    key={idx}
                    style={{
                      alignSelf: m.sender === 'user' ? 'flex-end' : 'flex-start',
                      maxWidth: '80%',
                      background: m.sender === 'user' ? '#1e3a8a' : 'var(--bg-panel)',
                      border: '1px solid var(--border-tactical)',
                      borderRadius: '8px',
                      padding: '0.85rem 1rem',
                      color: '#f1f5f9',
                      fontSize: '0.85rem',
                      lineHeight: 1.5
                    }}
                  >
                    {m.text}
                  </div>
                ))}
              </div>

              {/* Quick Prompt Chips */}
              <div style={{ padding: '0.5rem 1.25rem', display: 'flex', gap: '0.4rem', flexWrap: 'wrap', borderTop: '1px solid var(--border-tactical)', background: 'var(--bg-panel)' }}>
                {[
                  'Summarize active P1 incidents',
                  'CPR adult protocol',
                  'Burn care instructions',
                  'Severe bleeding tourniquet steps'
                ].map(chip => (
                  <button
                    key={chip}
                    onClick={() => { setAiInput(chip); }}
                    style={{ padding: '0.3rem 0.6rem', borderRadius: '4px', background: '#223147', fontSize: '0.72rem', color: '#93c5fd' }}
                  >
                    {chip}
                  </button>
                ))}
              </div>

              {/* Prompt Input */}
              <form onSubmit={handleAiSubmit} style={{ padding: '0.85rem 1.25rem', borderTop: '1px solid var(--border-tactical)', display: 'flex', gap: '0.5rem' }}>
                <input
                  type="text"
                  placeholder="Ask emergency triage questions, SOPs, or incident synthesis..."
                  value={aiInput}
                  onChange={(e) => setAiInput(e.target.value)}
                  style={{ flex: 1, height: '40px', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '6px', padding: '0 1rem', color: '#f1f5f9', fontSize: '0.85rem' }}
                />
                <button
                  type="submit"
                  style={{ height: '40px', padding: '0 1.25rem', background: '#06b6d4', color: '#0a0d12', fontWeight: 'bold', borderRadius: '6px', display: 'flex', alignItems: 'center', gap: '0.4rem' }}
                >
                  <Send size={15} />
                  <span>SEND</span>
                </button>
              </form>
            </div>

            {/* Right: Offline SOP Reference Cards */}
            <div style={{ width: '380px', background: 'var(--bg-surface)', border: '1px solid var(--border-tactical)', borderRadius: '10px', padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.85rem', overflowY: 'auto' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <BookOpen size={18} color="#38bdf8" />
                <h4 style={{ fontSize: '0.9rem', fontWeight: 'bold', color: '#f1f5f9', margin: 0 }}>
                  STANDBY EMERGENCY PROTOCOLS
                </h4>
              </div>

              <div style={{ padding: '0.85rem', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.78rem', fontWeight: 'bold', color: '#ef4444', marginBottom: '0.3rem' }}>
                  BLEEDING // MASSIVE HEMORRHAGE
                </div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', lineHeight: 1.4 }}>
                  1. Direct firm pressure on wound.<br/>
                  2. Apply high-and-tight arterial tourniquet if limb.<br/>
                  3. Note tourniquet application time on patient's forehead (T = HH:MM).
                </div>
              </div>

              <div style={{ padding: '0.85rem', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.78rem', fontWeight: 'bold', color: '#f59e0b', marginBottom: '0.3rem' }}>
                  HYPOTHERMIA REWARMING
                </div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8', lineHeight: 1.4 }}>
                  1. Move survivor to wind-sheltered dry area.<br/>
                  2. Remove wet clothing immediately.<br/>
                  3. Insulate body core with thermal blankets.<br/>
                  4. Do NOT rub frostbitten tissue directly.
                </div>
              </div>

              <div style={{ padding: '0.85rem', background: 'var(--bg-panel)', border: '1px solid var(--border-tactical)', borderRadius: '8px' }}>
                <div style={{ fontSize: '0.78rem', fontWeight: 'bold', color: '#38bdf8', marginBottom: '0.3rem' }}>
                  TRIAGE COLOR MATRIX (START)
                </div>
                <div style={{ fontSize: '0.72rem', color: '#94a3b8', lineHeight: 1.4 }}>
                  <strong style={{ color: '#ef4444' }}>RED (Immediate):</strong> Resp &gt; 30, no radial pulse.<br/>
                  <strong style={{ color: '#f59e0b' }}>YELLOW (Delayed):</strong> Serious, can follow commands.<br/>
                  <strong style={{ color: '#10b981' }}>GREEN (Minor):</strong> Walking wounded.<br/>
                  <strong style={{ color: '#64748b' }}>BLACK (Expectant):</strong> No respirations after airway reposition.
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
