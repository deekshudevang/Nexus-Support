// API client configuration for MeshLink Dashboard
// HARD RULE: Zero secrets in VITE_* environment variables or client bundle.

export const getApiBaseUrl = () => {
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL.replace(/\/+$/, '');
  }
  // Fallback to relative or localhost
  return 'http://localhost:8000';
};

export const getWsBaseUrl = () => {
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL.replace(/\/+$/, '');
  }
  const apiBase = getApiBaseUrl();
  if (apiBase.startsWith('https://')) {
    return apiBase.replace('https://', 'wss://');
  }
  return apiBase.replace('http://', 'ws://');
};

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
    throw new Error(errorBody.detail || `Failed to obtain WebSocket ticket (${response.status})`);
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
  const url = new URL(`${baseUrl}/api/v1/dispatch/responders`);
  if (params.status) url.searchParams.set('status', params.status);
  if (params.team_id) url.searchParams.set('team_id', params.team_id);

  const response = await fetch(url.toString(), {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to fetch responders (${response.status})`);
  }

  return response.json();
}

export async function fetchV1Teams(token) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/v1/dispatch/teams`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    throw new Error(errorBody.detail || `Failed to fetch teams (${response.status})`);
  }

  return response.json();
}

export async function createV1Assignment(token, assignmentData) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/v1/dispatch/assignments`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(assignmentData),
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}));
    const err = new Error(errorBody.detail || `Assignment failed (${response.status})`);
    err.status = response.status;
    err.detail = errorBody.detail;
    throw err;
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
