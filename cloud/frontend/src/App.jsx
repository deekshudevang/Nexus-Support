import React, { useEffect, useState, useRef, useCallback } from 'react';
import './App.css';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import {
  getApiBaseUrl,
  getWsBaseUrl,
  fetchSosPackets,
  fetchNodes,
  requestWsTicket,
  loginOperator
} from './api';
import { playEmergencyAlert, requestNotificationPermission, sendBrowserNotification } from './sound';

// Fix default marker icon in Leaflet
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

const DefaultIcon = L.icon({
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
});
L.Marker.prototype.options.icon = DefaultIcon;

// Treat only the legacy (0, 0) sentinel as missing; zero latitude or longitude
// alone is a valid coordinate.
const hasUsableCoordinates = (latitude, longitude) =>
  Number.isFinite(Number(latitude)) &&
  Number.isFinite(Number(longitude)) &&
  latitude !== null &&
  longitude !== null &&
  !(Number(latitude) === 0 && Number(longitude) === 0);

// Map controller for animated panning to selected alert
function MapPanController({ targetCoords }) {
  const map = useMap();
  useEffect(() => {
    if (targetCoords && hasUsableCoordinates(targetCoords[0], targetCoords[1])) {
      map.flyTo(targetCoords, 14, { duration: 1.5 });
    }
  }, [targetCoords, map]);
  return null;
}

export default function App() {
  // Session & Authentication (stored in memory / session storage, never logged or hardcoded)
  const [operatorToken, setOperatorToken] = useState(() => {
    return sessionStorage.getItem('meshlink_op_token') || '';
  });
  const [loginInput, setLoginInput] = useState('');
  const [loginError, setLoginError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  // Incidents and Nodes state
  const [sosPackets, setSosPackets] = useState([]);
  const [nodes, setNodes] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState('');
  const [severityFilter, setSeverityFilter] = useState('ALL'); // ALL, CRITICAL (>=8), HIGH (>=5), LOW (<5)
  const [selectedCoords, setSelectedCoords] = useState(null);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const soundEnabledRef = useRef(soundEnabled);

  useEffect(() => {
    soundEnabledRef.current = soundEnabled;
  }, [soundEnabled]);

  // WebSocket Connection State
  const [wsStatus, setWsStatus] = useState('DISCONNECTED'); // CONNECTED, RECONNECTING, POLLING, DISCONNECTED
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const pollingIntervalRef = useRef(null);
  const backoffDelayRef = useRef(1000);

  // Request browser notification permissions on mount
  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // Logout handler
  const handleLogout = () => {
    sessionStorage.removeItem('meshlink_op_token');
    setOperatorToken('');
    setSosPackets([]);
    setNodes([]);
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.onmessage = null;
      wsRef.current.onopen = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }
    setWsStatus('DISCONNECTED');
  };

  // Login handler
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    if (!loginInput.trim()) return;

    setIsAuthenticating(true);
    setLoginError('');

    try {
      // First attempt direct bearer token verification by testing fetch
      const testPackets = await fetchSosPackets(loginInput.trim());
      setOperatorToken(loginInput.trim());
      sessionStorage.setItem('meshlink_op_token', loginInput.trim());
      setSosPackets(testPackets);
    } catch (directErr) {
      // If direct bearer failed, try login credentials endpoint
      try {
        const loginRes = await loginOperator(loginInput.trim());
        const token = loginRes.access_token;
        setOperatorToken(token);
        sessionStorage.setItem('meshlink_op_token', token);
      } catch (authErr) {
        setLoginError('Invalid operator token or password. Access denied.');
      }
    } finally {
      setIsAuthenticating(false);
    }
  };

  // Load initial REST data
  const loadInitialData = useCallback(async (token) => {
    if (!token) return;
    setIsLoading(true);
    setErrorMessage('');
    try {
      const [packets, nodeList] = await Promise.all([
        fetchSosPackets(token),
        fetchNodes(token).catch(() => [])
      ]);
      setSosPackets(packets);
      setNodes(nodeList);
    } catch (err) {
      if (err.message.includes('401') || err.message.includes('403')) {
        handleLogout();
        setLoginError('Session expired or unauthorized. Please re-authenticate.');
      } else {
        setErrorMessage(err.message || 'Failed to connect to gateway API.');
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  // WebSocket connect with ticket handshake and exponential backoff
  const connectWebSocket = useCallback((token) => {
    if (!token) return;

    if (wsRef.current) {
      try {
        // Prevent an intentional replacement from triggering the old socket's
        // reconnect handler.
        wsRef.current.onclose = null;
        wsRef.current.close();
      } catch (e) {}
      wsRef.current = null;
    }

    setWsStatus('RECONNECTING');

    // Acquire short-lived single-use ticket
    requestWsTicket(token)
      .then((ticket) => {
        const wsUrl = `${getWsBaseUrl()}/ws/alerts?ticket=${encodeURIComponent(ticket)}`;
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
          setWsStatus('CONNECTED');
          backoffDelayRef.current = 1000; // Reset backoff
          // Stop fallback polling when live ws connects
          if (pollingIntervalRef.current) {
            clearInterval(pollingIntervalRef.current);
            pollingIntervalRef.current = null;
          }
        };

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (data.type === 'sos_alert' && data.packet) {
              const newPacket = data.packet;
              setSosPackets((prev) => {
                const existingIndex = prev.findIndex((p) => p.uuid === newPacket.uuid);
                if (existingIndex >= 0) {
                  const updated = [...prev];
                  updated[existingIndex] = newPacket;
                  return updated;
                }
                return [newPacket, ...prev];
              });

              // Trigger alert sound and notification
              if (soundEnabledRef.current && newPacket.severity >= 8) {
                playEmergencyAlert(newPacket.severity);
              }
              sendBrowserNotification(
                newPacket.emergencyType || 'EMERGENCY ALERT',
                `${newPacket.senderName}: ${newPacket.message}`,
                newPacket.severity
              );
            }
          } catch (err) {
            console.error('Error processing live alert frame:', err);
          }
        };

        ws.onerror = () => {
          setWsStatus('RECONNECTING');
        };

        ws.onclose = () => {
          setWsStatus('POLLING');
          // Start fallback polling while disconnected
          if (!pollingIntervalRef.current) {
            pollingIntervalRef.current = setInterval(() => {
              fetchSosPackets(token)
                .then((packets) => setSosPackets(packets))
                .catch(() => {});
            }, 5000);
          }

          // Schedule reconnect with exponential backoff
          const delay = backoffDelayRef.current;
          backoffDelayRef.current = Math.min(backoffDelayRef.current * 1.5, 15000);

          reconnectTimeoutRef.current = setTimeout(() => {
            connectWebSocket(token);
          }, delay);
        };
      })
      .catch((err) => {
        // Fallback to polling if ticket request fails
        setWsStatus('POLLING');
        if (!pollingIntervalRef.current) {
          pollingIntervalRef.current = setInterval(() => {
            fetchSosPackets(token)
              .then((packets) => setSosPackets(packets))
              .catch(() => {});
          }, 5000);
        }
        reconnectTimeoutRef.current = setTimeout(() => {
          connectWebSocket(token);
        }, 5000);
      });
  }, []);

  // Main lifecycle trigger
  useEffect(() => {
    if (operatorToken) {
      loadInitialData(operatorToken);
      connectWebSocket(operatorToken);
    }
    return () => {
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.onerror = null;
        wsRef.current.onmessage = null;
        wsRef.current.onopen = null;
        wsRef.current.close();
        wsRef.current = null;
      }
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, [operatorToken, loadInitialData, connectWebSocket]);

  // Filtered and searched packets
  const filteredPackets = sosPackets.filter((packet) => {
    if (severityFilter === 'CRITICAL' && packet.severity < 8) return false;
    if (severityFilter === 'HIGH' && packet.severity < 5) return false;
    if (severityFilter === 'LOW' && packet.severity >= 5) return false;

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchMsg = packet.message?.toLowerCase().includes(q);
      const matchSender = packet.senderName?.toLowerCase().includes(q);
      const matchType = packet.emergencyType?.toLowerCase().includes(q);
      const matchUuid = packet.uuid?.toLowerCase().includes(q);
      return matchMsg || matchSender || matchType || matchUuid;
    }
    return true;
  });

  const criticalCount = sosPackets.filter((p) => p.severity >= 8).length;

  // Center on a real incident when coordinates exist. Never imply a real incident
  // is located in an arbitrary city when the feed has no usable location data.
  const firstLocatedPacket = sosPackets.find((packet) =>
    hasUsableCoordinates(packet.latitude, packet.longitude)
  );
  const mapHasCoordinates = Boolean(firstLocatedPacket);
  const mapCenter = mapHasCoordinates
    ? [Number(firstLocatedPacket.latitude), Number(firstLocatedPacket.longitude)]
    : [20, 0];

  // ── Render Unauthorized State ──────────────────────────────────────────────
  if (!operatorToken) {
    return (
      <div className="auth-shell" style={styles.authContainer}>
        <div className="auth-card" style={styles.authCard}>
          <div style={styles.authHeader}>
            <div style={styles.authIcon}>🛡️</div>
            <h2 style={styles.authTitle}>MESHLINK COMMAND TERMINAL</h2>
            <p style={styles.authSubtitle}>Incident Dispatch & Mesh Gateway Sync</p>
          </div>

          <form onSubmit={handleLoginSubmit} style={styles.authForm}>
            <label style={styles.authLabel} htmlFor="token-input">
              Operator Bearer Token or Password
            </label>
            <input
              id="token-input"
              data-testid="token-input"
              type="password"
              placeholder="Enter operator token or password"
              value={loginInput}
              onChange={(e) => setLoginInput(e.target.value)}
              style={styles.authInput}
              autoFocus
              required
            />

            {loginError && (
              <div data-testid="auth-error" role="alert" aria-live="assertive" style={styles.authError}>
                ⚠️ {loginError}
              </div>
            )}

            <button
              data-testid="auth-submit"
              type="submit"
              disabled={isAuthenticating}
              style={styles.authButton}
            >
              {isAuthenticating ? 'AUTHENTICATING...' : 'ENTER DISPATCH TERMINAL'}
            </button>
          </form>

          <div style={styles.authFooter}>
            🔒 Operator read access. This credential is stored in this browser tab's session storage until sign-out or tab closure.
          </div>
        </div>
      </div>
    );
  }

  // ── Render Authenticated Dashboard ─────────────────────────────────────────
  return (
    <div className="dashboard-shell" style={styles.dashboardContainer}>
      {/* Tactical Top Bar */}
      <header className="dashboard-header" style={styles.topHeader}>
        <div style={styles.brandGroup}>
          <span style={styles.brandIcon}>📡</span>
          <div>
            <h1 style={styles.brandTitle}>MESHLINK COMMAND // INCIDENT DISPATCH</h1>
            <span style={styles.brandMeta}>
              GATEWAY: {getApiBaseUrl()} • {nodes.length} ACTIVE NODES
            </span>
          </div>
        </div>

        {/* Live Metrics & Status Indicators */}
        <div className="dashboard-header-actions" style={styles.headerRight}>
          <div style={styles.metricPill}>
            <span style={styles.metricLabel}>INCIDENTS</span>
            <span style={styles.metricValue}>{sosPackets.length}</span>
          </div>
          <div style={{ ...styles.metricPill, borderColor: '#ef4444' }}>
            <span style={{ ...styles.metricLabel, color: '#f87171' }}>CRITICAL</span>
            <span style={{ ...styles.metricValue, color: '#ef4444' }}>{criticalCount}</span>
          </div>

          {/* Connection Status Badge */}
          <div data-testid="ws-status-badge" style={styles.statusBadge(wsStatus)}>
            <span style={styles.statusDot(wsStatus)}></span>
            {wsStatus === 'CONNECTED' && 'WSS LIVE'}
            {wsStatus === 'RECONNECTING' && 'RECONNECTING'}
            {wsStatus === 'POLLING' && 'POLLING (FALLBACK)'}
            {wsStatus === 'DISCONNECTED' && 'OFFLINE'}
          </div>

          {/* Audio Alert Toggle */}
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            style={styles.iconButton}
            title={soundEnabled ? 'Mute emergency chime' : 'Enable emergency chime'}
          >
            {soundEnabled ? '🔔 SOUND ON' : '🔕 MUTED'}
          </button>

          {/* Logout Button */}
          <button
            data-testid="logout-button"
            onClick={handleLogout}
            style={styles.logoutButton}
          >
            LOGOUT
          </button>
        </div>
      </header>

      {/* Control Filter Bar */}
      <div className="dashboard-controls" style={styles.controlBar}>
        <div style={styles.searchContainer}>
          <input
            data-testid="search-input"
            type="search"
            aria-label="Search incidents by message, sender, emergency type or ID"
            placeholder="Search incidents, sender, type or ID…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={styles.searchInput}
          />
          {searchQuery && (
            <button onClick={() => setSearchQuery('')} style={styles.clearSearchBtn}>
              ✕
            </button>
          )}
        </div>

        <div className="severity-filters" style={styles.filterGroup}>
          <span style={styles.filterLabel}>SEVERITY:</span>
          {['ALL', 'CRITICAL', 'HIGH', 'LOW'].map((filter) => (
            <button
              key={filter}
              data-testid={`filter-${filter.toLowerCase()}`}
              aria-pressed={severityFilter === filter}
              onClick={() => setSeverityFilter(filter)}
              style={styles.filterTab(severityFilter === filter, filter)}
            >
              {filter}
            </button>
          ))}
        </div>

        <button
          onClick={() => loadInitialData(operatorToken)}
          disabled={isLoading}
          aria-label={isLoading ? 'Refreshing incidents' : 'Refresh incidents'}
          style={styles.refreshButton}
          title="Refresh incidents"
        >
          🔄 REFRESH
        </button>
      </div>

      {/* Main Content Area: Sidebar Feed + Map */}
      <div className="dashboard-main" style={styles.mainLayout}>
        {/* Incidents Sidebar */}
        <aside className="incident-sidebar" style={styles.sidebar}>
          <div style={styles.sidebarHeader}>
            <span style={styles.sidebarTitle}>ACTIVE FEED ({filteredPackets.length})</span>
            {isLoading && <span style={styles.loadingSpinner}>UPDATING...</span>}
          </div>

          {errorMessage && (
            <div data-testid="error-banner" role="alert" aria-live="polite" style={styles.errorBanner}>
              ⚠️ {errorMessage}
            </div>
          )}

          <div style={styles.cardList}>
            {filteredPackets.map((packet) => {
              const isCrit = packet.severity >= 8;
              const isHigh = packet.severity >= 5 && packet.severity < 8;
              const borderCol = isCrit ? '#ef4444' : isHigh ? '#f59e0b' : '#10b981';

              return (
                <div
                  key={packet.uuid}
                  data-testid="sos-card"
                  role="button"
                  tabIndex={0}
                  aria-label={`Show map location for ${packet.emergencyType || 'emergency'} from ${packet.senderName || 'unknown sender'}`}
                  onClick={() => {
                    if (hasUsableCoordinates(packet.latitude, packet.longitude)) {
                      setSelectedCoords([Number(packet.latitude), Number(packet.longitude)]);
                    }
                  }}
                  onKeyDown={(event) => {
                    if ((event.key === 'Enter' || event.key === ' ') &&
                        hasUsableCoordinates(packet.latitude, packet.longitude)) {
                      event.preventDefault();
                      setSelectedCoords([Number(packet.latitude), Number(packet.longitude)]);
                    }
                  }}
                  style={{ ...styles.card, borderLeftColor: borderCol }}
                >
                  <div style={styles.cardHeaderRow}>
                    <div style={styles.cardBadgeGroup}>
                      <span style={styles.sevBadge(packet.severity)}>
                        SEV {packet.severity}
                      </span>
                      <span style={styles.typeBadge}>
                        {packet.emergencyType || 'SOS'}
                      </span>
                      {packet.hopCount !== undefined && (
                        <span data-testid="hop-badge" style={styles.hopBadge}>
                          {packet.hopCount === 0 ? 'DIRECT RF' : `HOP: ${packet.hopCount}`}
                        </span>
                      )}
                    </div>
                    <span style={styles.cardTimestamp}>
                      {packet.timestamp ? new Date(packet.timestamp * 1000).toLocaleTimeString() : 'RECENT'}
                    </span>
                  </div>

                  <p style={styles.cardMessage}>{packet.message || 'Emergency signal dispatched.'}</p>

                  <div style={styles.cardFooter}>
                    <div style={styles.cardSenderInfo}>
                      <span>FROM: <strong>{packet.senderName}</strong></span>
                      <span style={{ color: '#94a3b8' }}>ID: {packet.senderId?.substring(0, 10)}</span>
                    </div>

                    {hasUsableCoordinates(packet.latitude, packet.longitude) && (
                      <button
                        style={styles.locateBtn}
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedCoords([Number(packet.latitude), Number(packet.longitude)]);
                        }}
                      >
                        📍 LOCATE
                      </button>
                    )}
                  </div>
                </div>
              );
            })}

            {filteredPackets.length === 0 && !isLoading && (
              <div data-testid="empty-state" role="status" aria-live="polite" style={styles.emptyState}>
                <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>📡</div>
                <div>No active incidents matching criteria.</div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.25rem' }}>
                  Monitoring live mesh gateway broadcast feed.
                </div>
              </div>
            )}
          </div>
        </aside>

        {/* Map View */}
        <section className="incident-map" style={styles.mapArea} aria-label="Incident locations map">
          <MapContainer
            center={mapCenter}
            zoom={mapHasCoordinates ? 13 : 2}
            style={{ height: '100%', width: '100%', backgroundColor: '#0f172a' }}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapPanController targetCoords={selectedCoords} />

            {sosPackets.map((packet) => (
              hasUsableCoordinates(packet.latitude, packet.longitude) && (
                <Marker key={packet.uuid} position={[Number(packet.latitude), Number(packet.longitude)]}>
                  <Popup>
                    <div style={{ minWidth: '180px', color: '#0f172a' }}>
                      <strong style={{ color: packet.severity >= 8 ? '#dc2626' : '#2563eb' }}>
                        [{packet.emergencyType}] SEVERITY {packet.severity}
                      </strong>
                      <p style={{ margin: '0.4rem 0' }}>{packet.message}</p>
                      <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        Sender: {packet.senderName} • Hops: {packet.hopCount || 0}
                      </div>
                    </div>
                  </Popup>
                </Marker>
              )
            ))}
          </MapContainer>
        </section>
      </div>
    </div>
  );
}

// ── Styles (Tactical Dark Command Theme) ──────────────────────────────────────
const styles = {
  authContainer: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0b0f19',
    fontFamily: '"SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace',
    padding: '1rem',
  },
  authCard: {
    width: '100%',
    maxWidth: '460px',
    backgroundColor: '#111827',
    border: '1px solid #1f2937',
    borderRadius: '12px',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
    padding: '2.5rem',
  },
  authHeader: {
    textAlign: 'center',
    marginBottom: '2rem',
  },
  authIcon: {
    fontSize: '2.5rem',
    marginBottom: '0.5rem',
  },
  authTitle: {
    fontSize: '1.25rem',
    fontWeight: '700',
    letterSpacing: '1px',
    color: '#f8fafc',
    margin: '0 0 0.25rem 0',
  },
  authSubtitle: {
    fontSize: '0.85rem',
    color: '#94a3b8',
    margin: 0,
  },
  authForm: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  authLabel: {
    fontSize: '0.8rem',
    fontWeight: '600',
    color: '#cbd5e1',
    letterSpacing: '0.5px',
  },
  authInput: {
    backgroundColor: '#0f172a',
    border: '1px solid #334155',
    borderRadius: '6px',
    padding: '0.75rem 1rem',
    color: '#f8fafc',
    fontSize: '0.95rem',
    outline: 'none',
    boxSizing: 'border-box',
    width: '100%',
  },
  authError: {
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    border: '1px solid rgba(239, 68, 68, 0.3)',
    color: '#f87171',
    borderRadius: '6px',
    padding: '0.65rem 0.85rem',
    fontSize: '0.85rem',
  },
  authButton: {
    backgroundColor: '#dc2626',
    color: '#ffffff',
    border: 'none',
    borderRadius: '6px',
    padding: '0.85rem',
    fontWeight: '700',
    fontSize: '0.9rem',
    letterSpacing: '0.75px',
    cursor: 'pointer',
    marginTop: '0.5rem',
    transition: 'background-color 0.15s ease',
  },
  authFooter: {
    marginTop: '2rem',
    fontSize: '0.75rem',
    color: '#64748b',
    textAlign: 'center',
    lineHeight: '1.4',
  },
  dashboardContainer: {
    display: 'flex',
    flexDirection: 'column',
    height: '100vh',
    backgroundColor: '#0b0f19',
    color: '#f8fafc',
    fontFamily: '"SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace',
    overflow: 'hidden',
  },
  topHeader: {
    height: '64px',
    backgroundColor: '#0f172a',
    borderBottom: '1px solid #1e293b',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 1.25rem',
  },
  brandGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
  },
  brandIcon: {
    fontSize: '1.75rem',
  },
  brandTitle: {
    margin: 0,
    fontSize: '1.05rem',
    fontWeight: '800',
    letterSpacing: '0.75px',
    color: '#f8fafc',
  },
  brandMeta: {
    fontSize: '0.72rem',
    color: '#64748b',
    letterSpacing: '0.5px',
  },
  headerRight: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.85rem',
  },
  metricPill: {
    backgroundColor: '#1e293b',
    border: '1px solid #334155',
    borderRadius: '6px',
    padding: '0.35rem 0.65rem',
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
  },
  metricLabel: {
    fontSize: '0.65rem',
    fontWeight: '700',
    color: '#94a3b8',
    letterSpacing: '0.5px',
  },
  metricValue: {
    fontSize: '0.9rem',
    fontWeight: '800',
    color: '#f8fafc',
  },
  statusBadge: (status) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '0.4rem',
    fontSize: '0.75rem',
    fontWeight: '700',
    padding: '0.35rem 0.65rem',
    borderRadius: '6px',
    backgroundColor: status === 'CONNECTED' ? 'rgba(16, 185, 129, 0.15)' : status === 'POLLING' ? 'rgba(59, 130, 246, 0.15)' : 'rgba(239, 68, 68, 0.15)',
    color: status === 'CONNECTED' ? '#34d399' : status === 'POLLING' ? '#60a5fa' : '#f87171',
    border: `1px solid ${status === 'CONNECTED' ? 'rgba(16, 185, 129, 0.3)' : status === 'POLLING' ? 'rgba(59, 130, 246, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`,
  }),
  statusDot: (status) => ({
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    backgroundColor: status === 'CONNECTED' ? '#10b981' : status === 'POLLING' ? '#3b82f6' : '#ef4444',
  }),
  iconButton: {
    backgroundColor: '#1e293b',
    border: '1px solid #334155',
    color: '#cbd5e1',
    padding: '0.35rem 0.65rem',
    borderRadius: '6px',
    fontSize: '0.75rem',
    fontWeight: '600',
    cursor: 'pointer',
  },
  logoutButton: {
    backgroundColor: '#27272a',
    border: '1px solid #3f3f46',
    color: '#f43f5e',
    padding: '0.35rem 0.75rem',
    borderRadius: '6px',
    fontSize: '0.75rem',
    fontWeight: '700',
    cursor: 'pointer',
  },
  controlBar: {
    height: '52px',
    backgroundColor: '#111827',
    borderBottom: '1px solid #1e293b',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 1.25rem',
    gap: '1rem',
  },
  searchContainer: {
    position: 'relative',
    flex: '1',
    maxWidth: '420px',
  },
  searchInput: {
    width: '100%',
    backgroundColor: '#0b0f19',
    border: '1px solid #1e293b',
    borderRadius: '6px',
    padding: '0.45rem 2rem 0.45rem 0.75rem',
    color: '#f8fafc',
    fontSize: '0.85rem',
    outline: 'none',
    boxSizing: 'border-box',
  },
  clearSearchBtn: {
    position: 'absolute',
    right: '8px',
    top: '50%',
    transform: 'translateY(-50%)',
    background: 'none',
    border: 'none',
    color: '#64748b',
    cursor: 'pointer',
    fontSize: '0.85rem',
  },
  filterGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.35rem',
  },
  filterLabel: {
    fontSize: '0.7rem',
    fontWeight: '700',
    color: '#64748b',
    marginRight: '0.25rem',
  },
  filterTab: (isActive, filter) => ({
    backgroundColor: isActive ? (filter === 'CRITICAL' ? '#dc2626' : '#2563eb') : '#1e293b',
    color: isActive ? '#ffffff' : '#94a3b8',
    border: `1px solid ${isActive ? 'transparent' : '#334155'}`,
    borderRadius: '4px',
    padding: '0.3rem 0.65rem',
    fontSize: '0.75rem',
    fontWeight: '700',
    cursor: 'pointer',
  }),
  refreshButton: {
    backgroundColor: '#1e293b',
    border: '1px solid #334155',
    color: '#cbd5e1',
    borderRadius: '4px',
    padding: '0.35rem 0.65rem',
    fontSize: '0.75rem',
    fontWeight: '700',
    cursor: 'pointer',
  },
  mainLayout: {
    display: 'flex',
    flex: '1',
    overflow: 'hidden',
  },
  sidebar: {
    width: '420px',
    backgroundColor: '#0f172a',
    borderRight: '1px solid #1e293b',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  sidebarHeader: {
    padding: '0.75rem 1rem',
    borderBottom: '1px solid #1e293b',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sidebarTitle: {
    fontSize: '0.8rem',
    fontWeight: '800',
    letterSpacing: '0.5px',
    color: '#94a3b8',
  },
  loadingSpinner: {
    fontSize: '0.7rem',
    color: '#38bdf8',
    fontWeight: '700',
  },
  errorBanner: {
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderBottom: '1px solid rgba(239, 68, 68, 0.3)',
    color: '#f87171',
    padding: '0.65rem 1rem',
    fontSize: '0.8rem',
  },
  cardList: {
    flex: '1',
    overflowY: 'auto',
    padding: '0.75rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.65rem',
  },
  card: {
    backgroundColor: '#1e293b',
    border: '1px solid #334155',
    borderLeftWidth: '5px',
    borderRadius: '6px',
    padding: '0.85rem',
    cursor: 'pointer',
    transition: 'background-color 0.15s ease',
  },
  cardHeaderRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '0.5rem',
  },
  cardBadgeGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.35rem',
  },
  sevBadge: (sev) => ({
    backgroundColor: sev >= 8 ? '#dc2626' : sev >= 5 ? '#d97706' : '#059669',
    color: '#ffffff',
    fontSize: '0.68rem',
    fontWeight: '800',
    padding: '0.15rem 0.4rem',
    borderRadius: '3px',
  }),
  typeBadge: {
    backgroundColor: '#334155',
    color: '#f1f5f9',
    fontSize: '0.68rem',
    fontWeight: '700',
    padding: '0.15rem 0.4rem',
    borderRadius: '3px',
  },
  hopBadge: {
    backgroundColor: '#0f172a',
    border: '1px solid #334155',
    color: '#38bdf8',
    fontSize: '0.68rem',
    fontWeight: '700',
    padding: '0.15rem 0.4rem',
    borderRadius: '3px',
  },
  cardTimestamp: {
    fontSize: '0.7rem',
    color: '#94a3b8',
  },
  cardMessage: {
    fontSize: '0.88rem',
    lineHeight: '1.4',
    color: '#f8fafc',
    margin: '0 0 0.65rem 0',
  },
  cardFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderTop: '1px solid rgba(255, 255, 255, 0.05)',
    paddingTop: '0.5rem',
  },
  cardSenderInfo: {
    fontSize: '0.75rem',
    color: '#cbd5e1',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.15rem',
  },
  locateBtn: {
    backgroundColor: '#0f172a',
    border: '1px solid #334155',
    color: '#38bdf8',
    padding: '0.25rem 0.5rem',
    borderRadius: '4px',
    fontSize: '0.7rem',
    fontWeight: '700',
    cursor: 'pointer',
  },
  emptyState: {
    padding: '3rem 1rem',
    textAlign: 'center',
    color: '#94a3b8',
    fontSize: '0.9rem',
  },
  mapArea: {
    flex: '1',
    height: '100%',
    backgroundColor: '#0b0f19',
  },
};
