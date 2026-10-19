import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from './App';
import * as api from './api';

describe('MeshLink Tactical Responder Dashboard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();

    // Mock WebSocket globally
    class MockWebSocket {
      constructor(url) {
        this.url = url;
        setTimeout(() => {
          if (this.onopen) this.onopen();
        }, 10);
      }
      send(data) {}
      close() {
        if (this.onclose) this.onclose();
      }
    }
    global.WebSocket = MockWebSocket;
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('renders unauthorized terminal when no operator token is stored', () => {
    render(<App />);
    expect(screen.getByText(/MESHLINK COMMAND TERMINAL/i)).toBeInTheDocument();
    expect(screen.getByTestId('token-input')).toBeInTheDocument();
    expect(screen.getByTestId('auth-submit')).toBeInTheDocument();
    expect(screen.queryByTestId('logout-button')).not.toBeInTheDocument();
  });

  it('shows error banner on invalid login attempt', async () => {
    vi.spyOn(api, 'fetchSosPackets').mockRejectedValueOnce(new Error('401 Unauthorized'));
    vi.spyOn(api, 'loginOperator').mockRejectedValueOnce(new Error('Invalid operator credentials'));

    render(<App />);

    const input = screen.getByTestId('token-input');
    const submitBtn = screen.getByTestId('auth-submit');

    fireEvent.change(input, { target: { value: 'wrong-token' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByTestId('auth-error')).toBeInTheDocument();
      expect(screen.getByText(/Invalid operator token/i)).toBeInTheDocument();
    });
  });

  it('authenticates successfully and displays dashboard with incidents and hop counts', async () => {
    const mockPackets = [
      {
        uuid: 'sos-1',
        senderId: 'node-alpha-123',
        senderName: 'Alpha Unit',
        emergencyType: 'MEDICAL',
        severity: 9,
        latitude: 37.7749,
        longitude: -122.4194,
        message: 'Severe injuries at station',
        timestamp: 1726815600,
        status: 'ACTIVE',
        hopCount: 2,
      },
      {
        uuid: 'sos-2',
        senderId: 'node-beta-456',
        senderName: 'Beta Unit',
        emergencyType: 'SUPPLIES',
        severity: 4,
        latitude: 37.7849,
        longitude: -122.4094,
        message: 'Need potable water',
        timestamp: 1726815500,
        status: 'ACTIVE',
        hopCount: 0,
      },
    ];

    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([{ deviceId: 'node-alpha-123', displayName: 'Alpha Unit' }]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('valid-ws-ticket-123');

    render(<App />);

    // Enter valid token
    const input = screen.getByTestId('token-input');
    const submitBtn = screen.getByTestId('auth-submit');

    fireEvent.change(input, { target: { value: 'op_token_valid_1' } });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText(/MESHLINK COMMAND \/\/ INCIDENT DISPATCH/i)).toBeInTheDocument();
    });

    // Check incident cards rendered
    expect(screen.getAllByText('Severe injuries at station')[0]).toBeInTheDocument();
    expect(screen.getAllByText('Need potable water')[0]).toBeInTheDocument();

    // Check hop count badges
    const hopBadges = screen.getAllByTestId('hop-badge');
    expect(hopBadges[0]).toHaveTextContent('HOP: 2');
    expect(hopBadges[1]).toHaveTextContent('DIRECT RF');

    // Check connection badge shows WSS LIVE
    await waitFor(() => {
      expect(screen.getByTestId('ws-status-badge')).toHaveTextContent(/WSS LIVE/i);
    });
  });

  it('filters incidents by severity level', async () => {
    const mockPackets = [
      {
        uuid: 'sos-crit',
        senderId: 'n1',
        senderName: 'Leader',
        emergencyType: 'FIRE',
        severity: 9,
        latitude: 0,
        longitude: 0,
        message: 'Structure wildfire',
        timestamp: 1726815600,
        status: 'ACTIVE',
      },
      {
        uuid: 'sos-low',
        senderId: 'n2',
        senderName: 'Scout',
        emergencyType: 'CHECKIN',
        severity: 3,
        latitude: 0,
        longitude: 0,
        message: 'All clear here',
        timestamp: 1726815500,
        status: 'ACTIVE',
      },
    ];

    sessionStorage.setItem('meshlink_op_token', 'valid-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Structure wildfire')).toBeInTheDocument();
      expect(screen.getByText('All clear here')).toBeInTheDocument();
    });

    // Click Critical Filter
    const critBtn = screen.getByTestId('filter-critical');
    fireEvent.click(critBtn);

    expect(screen.getByText('Structure wildfire')).toBeInTheDocument();
    expect(screen.queryByText('All clear here')).not.toBeInTheDocument();

    // Click Low Filter
    const lowBtn = screen.getByTestId('filter-low');
    fireEvent.click(lowBtn);

    expect(screen.queryByText('Structure wildfire')).not.toBeInTheDocument();
    expect(screen.getByText('All clear here')).toBeInTheDocument();
  });

  it('filters incidents using search query', async () => {
    const mockPackets = [
      {
        uuid: 'sos-1',
        senderId: 'n1',
        senderName: 'Medic John',
        emergencyType: 'MEDICAL',
        severity: 8,
        latitude: 0,
        longitude: 0,
        message: 'Broken arm requires splint',
        timestamp: 1726815600,
        status: 'ACTIVE',
      },
      {
        uuid: 'sos-2',
        senderId: 'n2',
        senderName: 'Guard Alex',
        emergencyType: 'PERIMETER',
        severity: 6,
        latitude: 0,
        longitude: 0,
        message: 'Gate perimeter breached',
        timestamp: 1726815500,
        status: 'ACTIVE',
      },
    ];

    sessionStorage.setItem('meshlink_op_token', 'valid-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Broken arm requires splint')).toBeInTheDocument();
      expect(screen.getByText('Gate perimeter breached')).toBeInTheDocument();
    });

    // Search for "gate"
    const searchInput = screen.getByTestId('search-input');
    fireEvent.change(searchInput, { target: { value: 'gate' } });

    expect(screen.getByText('Gate perimeter breached')).toBeInTheDocument();
    expect(screen.queryByText('Broken arm requires splint')).not.toBeInTheDocument();
  });

  it('handles operator logout and clears session', async () => {
    sessionStorage.setItem('meshlink_op_token', 'valid-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue([]);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('logout-button')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('logout-button'));

    expect(screen.getByTestId('token-input')).toBeInTheDocument();
    expect(sessionStorage.getItem('meshlink_op_token')).toBeNull();
  });
});
