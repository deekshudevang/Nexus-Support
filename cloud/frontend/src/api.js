// API client configuration for MeshLink Dashboard
// HARD RULE: Zero secrets in VITE_* environment variables or client bundle.

export const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_URL && import.meta.env.VITE_API_URL.trim()) {
    return import.meta.env.VITE_API_URL.trim().replace(/\/+$/, '');
  }
  if (import.meta.env.DEV || import.meta.env.MODE === 'test') {
    return 'http://localhost:8000';
  }
  throw new Error('VITE_API_URL is not configured for production dashboard bundle.');
};

export const getWsBaseUrl = () => {
  if (import.meta.env.VITE_WS_URL && import.meta.env.VITE_WS_URL.trim()) {
    return import.meta.env.VITE_WS_URL.trim().replace(/\/+$/, '');
  }
  const apiBase = getApiBaseUrl();
  if (apiBase.startsWith('https://')) {
    return apiBase.replace('https://', 'wss://');
  }
  return apiBase.replace('http://', 'ws://');
};

function buildHttpError(response, errorBody, fallbackMessage) {
  const err = new Error(errorBody?.detail || `${fallbackMessage} (${response.status})`);
  err.status = response.status;
  err.detail = errorBody?.detail;
  return err;
}

// Legacy compatible fetch for SOS packets
export async function fetchSosPackets(token, severityMin = null) {
  const baseUrl = getApiBaseUrl();
  const url = new URL(`${baseUrl}/api/sos`);
  if (severityMin !== null && severityMin !== undefined) {
    url.searchParams.set('severity_min', severityMin);
  }

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to fetch SOS packets (${response.status})`);
  }

  return response.json();
}

// Legacy compatible fetch for mesh nodes
export async function fetchNodes(token) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/nodes`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to fetch nodes (${response.status})`);
  }

  return response.json();
}

// Request single-use ticket for WebSocket authorization
export async function requestWsTicket(token) {
  const baseUrl = getApiBaseUrl();
  // Try v1 first, fall back to legacy
  let response = await fetch(`${baseUrl}/api/v1/auth/ws-ticket`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  }).catch(() => null);

  if (!response || !response.ok) {
    response = await fetch(`${baseUrl}/api/auth/ws-ticket`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
      },
    });
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw buildHttpError(response, errorBody, 'Failed to obtain WebSocket ticket');
  }

  const data = await response.json();
  return data.ticket;
}

export async function loginOperator(password) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/auth/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      username: 'operator',
      password: password,
    }),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || 'Invalid operator credentials');
  }

  return response.json();
}

// --- V1 Enhanced API Services ---

export async function fetchV1Incidents(token, params = {}) {
  const baseUrl = getApiBaseUrl();
  const url = new URL(`${baseUrl}/api/v1/incidents`);
  if (params.status) url.searchParams.set('status', params.status);
  if (params.priority) url.searchParams.set('priority', params.priority);
  if (params.severity_min) url.searchParams.set('severity_min', params.severity_min);

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to fetch v1 incidents (${response.status})`);
  }

  return response.json();
}

export async function fetchV1Responders(token, params = {}) {
  const baseUrl = getApiBaseUrl();
  const url = new URL(`${baseUrl}/api/v1/responders`);
  if (params.availability) url.searchParams.set('availability', params.availability);
  if (params.responder_type) url.searchParams.set('responder_type', params.responder_type);

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw buildHttpError(response, errorBody, 'Failed to fetch responders');
  }

  return response.json();
}

export async function fetchV1Teams(token) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/v1/teams`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw buildHttpError(response, errorBody, 'Failed to fetch teams');
  }

  return response.json();
}

export async function createV1Assignment(token, assignmentData) {
  const baseUrl = getApiBaseUrl();
  const incidentId = assignmentData.incident_id;
  if (!incidentId) {
    throw new Error('incident_id is required to create an assignment');
  }
  const payload = {
    responder_id: assignmentData.responder_id,
    team_id: assignmentData.team_id || null,
  };
  const response = await fetch(`${baseUrl}/api/v1/incidents/${encodeURIComponent(incidentId)}/assignments`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw buildHttpError(response, errorBody, 'Assignment failed');
  }

  return response.json();
}

export async function fetchV1Replay(token, sinceSeq = 0, limit = 50) {
  const baseUrl = getApiBaseUrl();
  const url = new URL(`${baseUrl}/api/v1/events/replay`);
  url.searchParams.set('since_seq', sinceSeq);
  url.searchParams.set('limit', limit);

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to replay events (${response.status})`);
  }

  return response.json();
}
