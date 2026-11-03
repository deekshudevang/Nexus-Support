import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from './App';
import * as api from './api';

describe('Tactical Command Center Enhanced Tests (PG-P6)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();

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

  it('honors zero-fabrication rule for incidents with null GPS by displaying explicit unmapped badge', async () => {
    const mockPackets = [
      {
        uuid: 'sos-unmapped-01',
        senderId: 'node-canopy-9',
        senderName: 'Deep Forest Scout',
        emergencyType: 'TRAUMA',
        severity: 7,
        latitude: null, // NULL GPS
        longitude: null,
        message: 'Fell from rock face. No GPS satellite lock.',
        timestamp: 1726815600,
        status: 'ACTIVE',
        hopCount: 1,
      },
    ];

    sessionStorage.setItem('meshlink_op_token', 'valid-op-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'fetchV1Responders').mockResolvedValue([]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Fell from rock face. No GPS satellite lock.')).toBeInTheDocument();
    });

    // Verify explicit honest location badge appears in the card
    expect(screen.getByText(/LOCATION UNAVAILABLE/i)).toBeInTheDocument();

    // Verify unmapped incidents tray button appears on the tactical map
    expect(screen.getByText(/AWAITING GPS FIX/i)).toBeInTheDocument();
  });

  it('opens dispatch modal and displays HTTP 409 concurrency conflict when responder is already assigned', async () => {
    const mockPackets = [
      {
        uuid: 'sos-active-1',
        senderId: 'node-101',
        senderName: 'Alpha Scout',
        emergencyType: 'FIRE',
        severity: 9,
        latitude: 37.77,
        longitude: -122.41,
        message: 'Industrial structural fire',
        status: 'ACTIVE',
        hopCount: 1,
      },
    ];

    const mockResponders = [
      {
        id: 'resp-busy-1',
        name: 'Capt. Chen',
        callsign: 'SAR-ALPHA 1',
        status: 'ASSIGNED',
        team: 'Search Team',
      },
    ];

    sessionStorage.setItem('meshlink_op_token', 'valid-op-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'fetchV1Responders').mockResolvedValue(mockResponders);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    // Simulate 409 Conflict from backend
    const conflictError = new Error('Responder is already actively assigned');
    conflictError.status = 409;
    conflictError.detail = 'Responder is already actively assigned';
    vi.spyOn(api, 'createV1Assignment').mockRejectedValue(conflictError);

    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Industrial structural fire')[0]).toBeInTheDocument();
    });

    // Click dispatch button on incident card
    const dispatchButtons = screen.getAllByText('DISPATCH');
    fireEvent.click(dispatchButtons[0]);

    // Modal should open
    await waitFor(() => {
      expect(screen.getByText(/DISPATCH ASSIGNMENT \/\/ COMMAND OVERRIDE/i)).toBeInTheDocument();
    });

    // Select responder
    const responderSelect = screen.getByTestId('responder-select');
    fireEvent.change(responderSelect, { target: { value: 'resp-busy-1' } });

    // Click Confirm Dispatch
    const confirmBtn = screen.getByTestId('confirm-dispatch-btn');
    fireEvent.click(confirmBtn);

    // Verify 409 concurrency error banner is displayed
    await waitFor(() => {
      expect(screen.getByText(/CONCURRENCY CONFLICT \(HTTP 409\)/i)).toBeInTheDocument();
    });
  });

  it('expands stream audit timeline and displays buffered stream events', async () => {
    sessionStorage.setItem('meshlink_op_token', 'valid-op-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue([]);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'fetchV1Responders').mockResolvedValue([]);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/REAL-TIME STREAM AUDIT TIMELINE/i)).toBeInTheDocument();
    });

    // Click to expand timeline
    const timelineHeader = screen.getByText(/REAL-TIME STREAM AUDIT TIMELINE/i);
    fireEvent.click(timelineHeader);

    // Should show replay controls
    expect(screen.getByText(/REPLAY FROM STREAM_SEQ:/i)).toBeInTheDocument();
    expect(screen.getByText(/TRIGGER REPLAY/i)).toBeInTheDocument();
  });

  it('successfully executes end-to-end dispatch workflow: dispatches available responder and displays confirmation', async () => {
    const mockPackets = [
      {
        uuid: 'sos-e2e-workflow-1',
        senderId: 'node-sar-9',
        senderName: 'Echo Team',
        emergencyType: 'FLOOD',
        severity: 8,
        latitude: 37.7749,
        longitude: -122.4194,
        message: 'Trapped on roof by rising floodwaters',
        status: 'ACTIVE',
        hopCount: 1,
      },
    ];

    const mockResponders = [
      {
        id: 'resp-available-1',
        name: 'Sarah Connor',
        callsign: 'RESCUE-1',
        status: 'AVAILABLE',
        team: 'Water Rescue',
      },
    ];

    sessionStorage.setItem('meshlink_op_token', 'valid-op-token');
    vi.spyOn(api, 'fetchSosPackets').mockResolvedValue(mockPackets);
    vi.spyOn(api, 'fetchNodes').mockResolvedValue([]);
    vi.spyOn(api, 'fetchV1Responders').mockResolvedValue(mockResponders);
    vi.spyOn(api, 'requestWsTicket').mockResolvedValue('test-ticket');
    vi.spyOn(api, 'createV1Assignment').mockResolvedValue({
      id: 'assign-101',
      incident_id: 'sos-e2e-workflow-1',
      responder_id: 'resp-available-1',
      status: 'DISPATCHED',
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.getAllByText('Trapped on roof by rising floodwaters')[0]).toBeInTheDocument();
    });

    // Click dispatch button
    const dispatchButtons = screen.getAllByText('DISPATCH');
    fireEvent.click(dispatchButtons[0]);

    // Modal appears
    await waitFor(() => {
      expect(screen.getByText(/DISPATCH ASSIGNMENT \/\/ COMMAND OVERRIDE/i)).toBeInTheDocument();
    });

    // Select available responder
    const responderSelect = screen.getByTestId('responder-select');
    fireEvent.change(responderSelect, { target: { value: 'resp-available-1' } });

    // Click Confirm Dispatch
    const confirmBtn = screen.getByTestId('confirm-dispatch-btn');
    fireEvent.click(confirmBtn);

    // Verify success banner appears
    await waitFor(() => {
      expect(screen.getByText(/Tactical assignment successfully committed and relayed to field mesh/i)).toBeInTheDocument();
    });
  });
});
