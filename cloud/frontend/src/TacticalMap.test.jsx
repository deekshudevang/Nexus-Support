import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import TacticalMap from './components/TacticalMap';
import DispatchPanel from './components/DispatchPanel';

describe('TacticalMap & DispatchPanel Enhanced Tests (Task 5 / IMP-HV-02 & IMP-HV-03)', () => {
  it('renders TacticalMap with default RF MESH tile layer and switches to TERRAIN and SATELLITE', () => {
    render(<TacticalMap incidents={[]} nodes={[]} responders={[]} />);

    const tileLayer = screen.getByTestId('tile-layer');
    expect(tileLayer).toBeInTheDocument();
    expect(tileLayer.getAttribute('data-url')).toContain('openstreetmap.org');

    // Switch to TERRAIN / TOPO
    const terrainBtn = screen.getByRole('button', { name: /TERRAIN \/ TOPO/i });
    fireEvent.click(terrainBtn);
    expect(screen.getByTestId('tile-layer').getAttribute('data-url')).toContain('opentopomap.org');

    // Switch to SATELLITE
    const satBtn = screen.getByRole('button', { name: /SATELLITE/i });
    fireEvent.click(satBtn);
    expect(screen.getByTestId('tile-layer').getAttribute('data-url')).toContain('arcgisonline.com');

    // Switch back to RF MESH
    const rfBtn = screen.getByRole('button', { name: /^RF MESH$/i });
    fireEvent.click(rfBtn);
    expect(screen.getByTestId('tile-layer').getAttribute('data-url')).toContain('openstreetmap.org');
  });

  it('displays ONLINE TILES badge by default when online', () => {
    render(<TacticalMap incidents={[]} nodes={[]} responders={[]} />);
    const badge = screen.getByTestId('tile-source-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('ONLINE TILES');
  });

  it('renders LOCAL OFFLINE button and switches to offline tiles when localTileUrl is provided', () => {
    render(
      <TacticalMap
        incidents={[]}
        nodes={[]}
        responders={[]}
        localTileUrl="http://localhost:8080/tiles/{z}/{x}/{y}.png"
      />
    );

    const localBtn = screen.getByRole('button', { name: /LOCAL OFFLINE/i });
    expect(localBtn).toBeInTheDocument();

    fireEvent.click(localBtn);
    const tileLayer = screen.getByTestId('tile-layer');
    expect(tileLayer.getAttribute('data-url')).toBe('http://localhost:8080/tiles/{z}/{x}/{y}.png');

    const badge = screen.getByTestId('tile-source-badge');
    expect(badge).toHaveTextContent('OFFLINE TILES');
  });

  it('displays NO MAP TILES badge and warning banner when browser goes offline without local tiles', () => {
    render(<TacticalMap incidents={[]} nodes={[]} responders={[]} />);

    fireEvent(window, new Event('offline'));

    const badge = screen.getByTestId('tile-source-badge');
    expect(badge).toHaveTextContent('NO MAP TILES');
    expect(screen.getByTestId('no-tiles-warning')).toBeInTheDocument();

    // Come back online
    fireEvent(window, new Event('online'));
    expect(badge).toHaveTextContent('ONLINE TILES');
    expect(screen.queryByTestId('no-tiles-warning')).not.toBeInTheDocument();
  });

  it('plots real responders with valid GPS coordinates on the TacticalMap', () => {
    const mockResponders = [
      {
        id: 'resp-01',
        callsign: 'SAR-LEAD-1',
        name: 'Capt. Rogers',
        last_latitude: 37.775,
        last_longitude: -122.418,
        availability: 'AVAILABLE',
        responder_type: 'SAR',
      },
      {
        id: 'resp-02',
        callsign: 'MEDEVAC-1',
        name: 'Dr. Banner',
        last_latitude: 37.785,
        last_longitude: -122.408,
        availability: 'ACTIVE',
        responder_type: 'MEDEVAC',
      },
      {
        id: 'resp-unmapped',
        callsign: 'SCOUT-NO-GPS',
        name: 'Field Scout',
        last_latitude: null, // Zero GPS Fabrication: must NOT be plotted
        last_longitude: null,
        availability: 'AVAILABLE',
      },
    ];

    render(<TacticalMap incidents={[]} nodes={[]} responders={mockResponders} />);

    // Responders count HUD badge
    expect(screen.getByText(/FIELD UNITS: 2/i)).toBeInTheDocument();

    // Verify 2 markers plotted for valid coordinates
    const markers = screen.getAllByTestId('marker');
    expect(markers.length).toBe(2);

    const positions = markers.map((m) => JSON.parse(m.getAttribute('data-position')));
    expect(positions).toContainEqual([37.775, -122.418]);
    expect(positions).toContainEqual([37.785, -122.408]);
  });

  it('renders explicit empty state in DispatchPanel when no responders are online (Zero-Fabrication)', () => {
    render(<DispatchPanel responders={[]} />);

    expect(screen.getByTestId('empty-responders')).toBeInTheDocument();
    expect(screen.getByText(/NO FIELD ASSETS ONLINE/i)).toBeInTheDocument();
    expect(screen.getByText(/Zero-Fabrication active/i)).toBeInTheDocument();

    // Verify no fake demo responders (e.g. Capt. Chen) exist when empty
    expect(screen.queryByText(/Capt\. Chen/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/resp-alpha-01/i)).not.toBeInTheDocument();
  });

  it('renders real responder cards and statistics in DispatchPanel when assets are online', () => {
    const mockResponders = [
      {
        id: 'resp-alpha',
        callsign: 'SAR-ALPHA',
        name: 'Officer Parker',
        availability: 'AVAILABLE',
        responder_type: 'SAR',
        heartRate: 72,
        o2Level: 99,
        battery: 94,
        rssi: -65,
      },
      {
        id: 'resp-bravo',
        callsign: 'K9-SEARCH',
        name: 'Officer Wayne',
        availability: 'ON_SCENE',
        responder_type: 'K9',
        heartRate: 85,
        o2Level: 98,
        battery: 88,
        rssi: -70,
      },
    ];

    render(<DispatchPanel responders={mockResponders} />);

    expect(screen.queryByTestId('empty-responders')).not.toBeInTheDocument();
    expect(screen.getByText(/SAR-ALPHA/i)).toBeInTheDocument();
    expect(screen.getByText(/K9-SEARCH/i)).toBeInTheDocument();
    expect(screen.getByText(/72 BPM/i)).toBeInTheDocument();
    expect(screen.getByText(/85 BPM/i)).toBeInTheDocument();
  });
});
