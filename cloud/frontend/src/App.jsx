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

// Zero-Fabrication Directive: Operational UI strictly reflects live backend state without synthetic fallbacks
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
  const [mobileTab, setMobileTab] = useState('QUEUE'); // 'QUEUE' | 'MAP' | 'DISPATCH'

  const [wsStatus, setWsStatus] = useState('CLOSED'); // 'CLOSED' | 'CONNECTING' | 'OPEN'
  const [wsRetryInfo, setWsRetryInfo] = useState(null); // { attempt, delayMs, error }
  const [soundEnabled, setSoundEnabled] = useState(true);

  const wsRef = useRef(null);
  const reconnectTimerRef = useRef(null);
  const reconnectAttemptRef = useRef(0);

  // 1. Initial Authentication Handler
  const handleAuthenticate = async (enteredToken) => {
    setIsAuthenticating(true);
    setAuthError(null);

    try {
      let resolvedToken = enteredToken;
      let packets;
      try {
        packets = await fetchSosPackets(enteredToken);
      } catch (_err) {
        const loginRes = await loginOperator(enteredToken);
        resolvedToken = loginRes.access_token || loginRes.token || enteredToken;
        packets = await fetchSosPackets(resolvedToken);
      }

      sessionStorage.setItem('meshlink_op_token', resolvedToken);
      setToken(resolvedToken);
      setIncidents(Array.isArray(packets) ? packets : []);
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
        setIncidents(packets.value);
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
          reconnectAttemptRef.current = 0;
          setWsRetryInfo(null);
          setWsStatus('OPEN');
        };

        socket.onmessage = (event) => {
          if (!isMounted) return;
          try {
            const data = JSON.parse(event.data);

            // Handle live SOS packet / Incident payload (backend emits "sos_alert")
            if (
              data.type === 'sos_alert' ||
              data.type === 'NEW_SOS' ||
              data.emergencyType ||
              data.severity
            ) {
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

            if (data.type !== 'pong' && data.type !== 'authenticated') {
              setStreamEvents((prev) => [...prev, data]);
            }
          } catch (err) {
            console.error('Failed to parse WebSocket message:', err);
          }
        };

        socket.onclose = () => {
          if (!isMounted) return;
          setWsStatus('CLOSED');
          const attempt = reconnectAttemptRef.current + 1;
          reconnectAttemptRef.current = attempt;
          const baseDelay = Math.min(30000, 1000 * Math.pow(2, attempt - 1));
          const delayMs = baseDelay + Math.floor(Math.random() * 500);
          setWsRetryInfo({ attempt, delayMs, error: null });
          reconnectTimerRef.current = setTimeout(connectWebSocket, delayMs);
        };

        socket.onerror = () => {
          if (socket.readyState !== WebSocket.CLOSED) {
            socket.close();
          }
        };
      } catch (err) {
        if (!isMounted) return;
        setWsStatus('CLOSED');
        if (err.status === 401 || err.status === 403) {
          setWsRetryInfo({ attempt: reconnectAttemptRef.current, delayMs: null, error: 'Session rejected (HTTP ' + err.status + ')' });
          setAuthError('Operator session expired or revoked. Please sign in again.');
          return;
        }
        const attempt = reconnectAttemptRef.current + 1;
        reconnectAttemptRef.current = attempt;
        const baseDelay = Math.min(30000, 1000 * Math.pow(2, attempt - 1));
        const delayMs = baseDelay + Math.floor(Math.random() * 500);
        setWsRetryInfo({ attempt, delayMs, error: err.message || 'Gateway unreachable' });
        reconnectTimerRef.current = setTimeout(connectWebSocket, delayMs);
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
    <div className="h-screen w-screen bg-[#0b0f19] text-[#f8fafc] flex flex-col overflow-hidden select-none">
      {/* 1. Top HUD & Emergency Broadcast Ticker */}
      <TopHUD
        wsStatus={wsStatus}
        wsRetryInfo={wsRetryInfo}
        soundEnabled={soundEnabled}
        setSoundEnabled={setSoundEnabled}
        onLogout={handleLogout}
        onRefresh={refreshTacticalState}
        activeNodesCount={nodes.length}
        totalNodesCount={nodes.length}
      />

      {/* Mobile Workspace View Switcher (< 768px) */}
      <nav
        aria-label="Mobile workspace view"
        className="md:hidden grid grid-cols-3 bg-[#111827] border-b border-[#334155] shrink-0 text-xs font-semibold"
      >
        {[
          { id: 'QUEUE', label: `QUEUE (${incidents.length})` },
          { id: 'MAP', label: 'RADAR MAP' },
          { id: 'DISPATCH', label: `ASSETS (${responders.length})` },
        ].map((tab) => (
          <button
            key={tab.id}
            data-testid={`mobile-tab-${tab.id.toLowerCase()}`}
            onClick={() => setMobileTab(tab.id)}
            className={`min-h-[44px] px-2 border-b-2 transition-colors ${
              mobileTab === tab.id
                ? 'bg-[#1e293b] text-[#f8fafc] border-[#38bdf8]'
                : 'text-[#94a3b8] border-transparent'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {/* 2. Main 3-Column Tactical Bento Workspace */}
      <div className="flex-1 flex overflow-hidden w-full relative">
        {/* Left Column: Incident Priority Queue */}
        <div className={`${mobileTab === 'QUEUE' ? 'flex w-full h-full' : 'hidden'} md:contents`}>
          <IncidentQueue
            incidents={incidents}
            selectedIncident={selectedIncident}
            onSelectIncident={(inc) => setSelectedIncident(inc)}
            onOpenDispatch={(inc) => setDispatchIncident(inc)}
          />
        </div>

        {/* Center Canvas: Tactical Radar Map with Zero GPS Fabrication */}
        <div className={`${mobileTab === 'MAP' ? 'flex flex-1 w-full h-full' : 'hidden'} md:contents`}>
          <TacticalMap
            incidents={incidents}
            nodes={nodes}
            responders={responders}
            selectedIncident={selectedIncident}
            onSelectIncident={(inc) => setSelectedIncident(inc)}
            onOpenDispatch={(inc) => setDispatchIncident(inc)}
          />
        </div>

        {/* Right Column: Live Responder Telemetry & Dispatch Panel */}
        <div className={`${mobileTab === 'DISPATCH' ? 'flex w-full h-full' : 'hidden'} md:contents`}>
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
