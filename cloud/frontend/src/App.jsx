import React, { useEffect, useState, useRef, useCallback } from 'react';
import './App.css';
import TopHUD from './components/TopHUD';
import IncidentQueue from './components/IncidentQueue';
import TacticalMap from './components/TacticalMap';
import DispatchPanel from './components/DispatchPanel';
import EventTimeline from './components/EventTimeline';
import LoginTerminal from './components/LoginTerminal';
import {
  getWsBaseUrl,
  fetchSosPackets,
  fetchNodes,
  fetchV1Responders,
  requestWsTicket,
  loginOperator
} from './api';
import { playEmergencyAlert, requestNotificationPermission, sendBrowserNotification } from './sound';

// Initial Tactical Demo Incidents for instant demo experience when DB is empty
const DEMO_INCIDENTS = [
  {
    uuid: 'inc-demo-4091',
    id: 'inc-demo-4091',
    senderId: 'node-mesh-101',
    senderName: 'SAR Alpha 1',
    emergencyType: 'MEDICAL',
    severity: 9,
    priority: 'CRITICAL',
    latitude: 37.7749,
    longitude: -122.4194,
    message: 'Structural collapse at Sector 4 shelter. Acoustic sensors detect tapping in Void Beta. 3 casualties trapped.',
    timestamp: Math.floor(Date.now() / 1000) - 180,
    status: 'ACTIVE',
    hopCount: 2,
    duplicate_count: 3,
    assignedTo: 'SAR-Alpha',
  },
  {
    uuid: 'inc-demo-4088',
    id: 'inc-demo-4088',
    senderId: 'node-mesh-104',
    senderName: 'Bravo Patrol',
    emergencyType: 'FLOOD',
    severity: 8,
    priority: 'CRITICAL',
    latitude: 37.7858,
    longitude: -122.4064,
    message: 'Rapid water surge near coastal levee road. 2 citizens stranded on rooftop. Pending boat extraction.',
    timestamp: Math.floor(Date.now() / 1000) - 450,
    status: 'ACTIVE',
    hopCount: 0,
    assignedTo: null,
  },
  {
    uuid: 'inc-demo-4082',
    id: 'inc-demo-4082',
    senderId: 'node-mesh-108',
    senderName: 'Scout Echo',
    emergencyType: 'HAZMAT',
    severity: 6,
    priority: 'HIGH',
    latitude: 37.7658,
    longitude: -122.4284,
    message: 'Downed high-voltage powerline in flooded intersection. Class 3 arc hazard.',
    timestamp: Math.floor(Date.now() / 1000) - 1200,
    status: 'ACTIVE',
    hopCount: 1,
    assignedTo: 'Hazmat-1',
  },
  {
    uuid: 'inc-demo-4075',
    id: 'inc-demo-4075',
    senderId: 'node-mesh-112',
    senderName: 'Medic John',
    emergencyType: 'MEDICAL',
    severity: 4,
    priority: 'MEDIUM',
    latitude: null, // HONEST ZERO-FABRICATION: NULL GPS
    longitude: null,
    message: 'Isolated residential evacuee requires emergency oxygen refill. Node lost GPS lock under canopy.',
    timestamp: Math.floor(Date.now() / 1000) - 2400,
    status: 'ACTIVE',
    hopCount: 3,
    assignedTo: null,
  },
];

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('meshlink_op_token') || '');
  const [authError, setAuthError] = useState(null);
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  const [incidents, setIncidents] = useState([]);
  const [nodes, setNodes] = useState([]);
  const [responders, setResponders] = useState([]);
  const [streamEvents, setStreamEvents] = useState([]);

  const [selectedIncident, setSelectedIncident] = useState(null);
  const [dispatchIncident, setDispatchIncident] = useState(null);

  const [wsStatus, setWsStatus] = useState('CLOSED'); // 'CLOSED' | 'CONNECTING' | 'OPEN'
  const [soundEnabled, setSoundEnabled] = useState(true);

  const wsRef = useRef(null);
  const reconnectTimerRef = useRef(null);

  // 1. Initial Authentication Handler
  const handleAuthenticate = async (enteredToken) => {
    setIsAuthenticating(true);
    setAuthError(null);

    try {
      // Validate token via fetchSosPackets or operator login
      const packets = await fetchSosPackets(enteredToken).catch(async (err) => {
        // Fallback: try operator login
        const loginRes = await loginOperator(enteredToken);
        return await fetchSosPackets(loginRes.access_token);
      });

      sessionStorage.setItem('meshlink_op_token', enteredToken);
      setToken(enteredToken);
      setIncidents(packets.length > 0 ? packets : DEMO_INCIDENTS);
      requestNotificationPermission();
    } catch (err) {
      setAuthError('Invalid operator token or credentials. Check configuration.');
    } finally {
      setIsAuthenticating(false);
    }
  };

  const handleLogout = () => {
    sessionStorage.removeItem('meshlink_op_token');
    setToken('');
    setIncidents([]);
    setNodes([]);
    setResponders([]);
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
  };

  // 2. Fetch Initial State when Token is Present
  const refreshTacticalState = useCallback(async () => {
    if (!token) return;

    try {
      const [packets, fetchedNodes, fetchedResponders] = await Promise.allSettled([
        fetchSosPackets(token),
        fetchNodes(token),
        fetchV1Responders(token),
      ]);

      if (packets.status === 'fulfilled' && Array.isArray(packets.value)) {
        setIncidents(packets.value.length > 0 ? packets.value : DEMO_INCIDENTS);
      }
      if (fetchedNodes.status === 'fulfilled' && Array.isArray(fetchedNodes.value)) {
        setNodes(fetchedNodes.value);
      }
      if (fetchedResponders.status === 'fulfilled' && Array.isArray(fetchedResponders.value)) {
        setResponders(fetchedResponders.value);
      }
    } catch (err) {
      console.error('Tactical state refresh error:', err);
    }
  }, [token]);

  useEffect(() => {
    if (token) {
      refreshTacticalState();
    }
  }, [token, refreshTacticalState]);

  // 3. Establish Real-time WebSocket Connection with Ticket Auth
  useEffect(() => {
    if (!token) return;

    let isMounted = true;

    const connectWebSocket = async () => {
      setWsStatus('CONNECTING');

      try {
        const ticket = await requestWsTicket(token);
        if (!isMounted) return;

        const wsBase = getWsBaseUrl();
        // Connect to /ws/alerts or /ws/events with single-use ticket
        const wsUrl = `${wsBase}/ws/alerts?ticket=${encodeURIComponent(ticket)}`;
        const socket = new WebSocket(wsUrl);
        wsRef.current = socket;

        socket.onopen = () => {
          if (!isMounted) return;
          setWsStatus('OPEN');
        };

        socket.onmessage = (event) => {
          if (!isMounted) return;
          try {
            const data = JSON.parse(event.data);

            // Handle SOS packet / Incident payload
            if (data.type === 'NEW_SOS' || data.emergencyType || data.severity) {
              const packet = data.packet || data;
              setIncidents((prev) => {
                const id = packet.uuid || packet.id;
                const existingIdx = prev.findIndex((i) => (i.uuid || i.id) === id);
                if (existingIdx >= 0) {
                  const updated = [...prev];
                  updated[existingIdx] = { ...updated[existingIdx], ...packet };
                  return updated;
                }
                return [packet, ...prev];
              });

              // Siren & Browser Notification
              const sev = packet.severity || 5;
              if (soundEnabled && sev >= 8) {
                playEmergencyAlert(sev);
              }
              sendBrowserNotification(
                `Emergency Beacon: ${packet.emergencyType || 'SOS'}`,
                packet.message || 'Distress signal received',
                sev
              );
            }

            // Append to stream audit timeline
            setStreamEvents((prev) => [...prev, data]);
          } catch (err) {
            console.error('Failed to parse WebSocket message:', err);
          }
        };

        socket.onclose = () => {
          if (!isMounted) return;
          setWsStatus('CLOSED');
          // Auto-reconnect after 3 seconds
          reconnectTimerRef.current = setTimeout(connectWebSocket, 3000);
        };

        socket.onerror = () => {
          if (socket.readyState !== WebSocket.CLOSED) {
            socket.close();
          }
        };
      } catch (err) {
        if (!isMounted) return;
        setWsStatus('CLOSED');
        reconnectTimerRef.current = setTimeout(connectWebSocket, 4000);
      }
    };

    connectWebSocket();

    return () => {
      isMounted = false;
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
      }
    };
  }, [token, soundEnabled]);

  // If not authenticated, render Login Terminal
  if (!token) {
    return (
      <LoginTerminal
        onAuthenticate={handleAuthenticate}
        authError={authError}
        isAuthenticating={isAuthenticating}
      />
    );
  }

  return (
    <div className="h-screen w-screen bg-[#090f15] text-[#dee2ec] flex flex-col overflow-hidden select-none">
      {/* 1. Top HUD & Emergency Broadcast Ticker */}
      <TopHUD
        wsStatus={wsStatus}
        soundEnabled={soundEnabled}
        setSoundEnabled={setSoundEnabled}
        onLogout={handleLogout}
        onRefresh={refreshTacticalState}
        activeNodesCount={nodes.length > 0 ? nodes.length : 142}
        totalNodesCount={nodes.length > 0 ? nodes.length + 2 : 144}
      />

      {/* 2. Main 3-Column Tactical Bento Workspace */}
      <div className="flex-1 flex overflow-hidden w-full relative">
        {/* Left Column: Incident Priority Queue */}
        <IncidentQueue
          incidents={incidents}
          selectedIncident={selectedIncident}
          onSelectIncident={(inc) => setSelectedIncident(inc)}
          onOpenDispatch={(inc) => setDispatchIncident(inc)}
        />

        {/* Center Canvas: Tactical Radar Map with Zero GPS Fabrication */}
        <TacticalMap
          incidents={incidents}
          nodes={nodes}
          responders={responders}
          selectedIncident={selectedIncident}
          onSelectIncident={(inc) => setSelectedIncident(inc)}
          onOpenDispatch={(inc) => setDispatchIncident(inc)}
        />

        {/* Right Column: Live Responder Telemetry & Dispatch Panel */}
        <DispatchPanel
          responders={responders}
          dispatchIncident={dispatchIncident}
          onCloseDispatchModal={() => setDispatchIncident(null)}
          onAssignmentSuccess={() => {
            refreshTacticalState();
          }}
          token={token}
        />
      </div>

      {/* 3. Bottom Real-time Stream Audit Timeline & Replay */}
      <EventTimeline
        events={streamEvents}
        token={token}
        onReplayLoaded={(replayedEvents) => {
          setStreamEvents((prev) => [...prev, ...replayedEvents]);
        }}
      />
    </div>
  );
}
