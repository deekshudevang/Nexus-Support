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

export async function requestWsTicket(token) {
  const baseUrl = getApiBaseUrl();
  const response = await fetch(`${baseUrl}/api/auth/ws-ticket`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

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
