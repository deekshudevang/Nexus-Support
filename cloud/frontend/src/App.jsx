import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

// Fix for default marker icons in React-Leaflet
import L from 'leaflet';
import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

let DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41]
});
L.Marker.prototype.options.icon = DefaultIcon;

const styles = {
  header: {
    backgroundColor: '#1976d2',
    color: 'white',
    padding: '1rem',
    margin: 0,
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
  },
  container: {
    display: 'flex',
    height: 'calc(100vh - 60px)',
  },
  sidebar: {
    width: '350px',
    backgroundColor: 'white',
    overflowY: 'auto',
    borderRight: '1px solid #ccc',
  },
  card: {
    padding: '1rem',
    borderBottom: '1px solid #eee',
    cursor: 'pointer',
  },
  critical: {
    borderLeft: '4px solid #f44336',
  },
  high: {
    borderLeft: '4px solid #ff9800',
  },
  low: {
    borderLeft: '4px solid #4caf50',
  },
  mapArea: {
    flex: 1,
  }
};

function App() {
  const [sosPackets, setSosPackets] = useState([]);

  useEffect(() => {
    fetch('http://localhost:8000/api/sos')
      .then(res => res.json())
      .then(data => setSosPackets(data))
      .catch(err => console.error("Error fetching SOS data:", err));
      
    // Set up polling
    const interval = setInterval(() => {
      fetch('http://localhost:8000/api/sos')
        .then(res => res.json())
        .then(data => setSosPackets(data))
        .catch(err => console.error("Error fetching SOS data:", err));
    }, 5000);
    
    return () => clearInterval(interval);
  }, []);

  const getBorderColor = (severity) => {
    if (severity >= 8) return styles.critical;
    if (severity >= 4) return styles.high;
    return styles.low;
  };

  const center = sosPackets.length > 0 && sosPackets[0].latitude !== 0 
    ? [sosPackets[0].latitude, sosPackets[0].longitude] 
    : [37.7749, -122.4194]; // Default to SF

  return (
    <div>
      <h2 style={styles.header}>MeshLink Global Dashboard (Sync Server)</h2>
      <div style={styles.container}>
        <div style={styles.sidebar}>
          <h3 style={{padding: '0 1rem'}}>Recent Alerts ({sosPackets.length})</h3>
          {sosPackets.map(packet => (
            <div key={packet.uuid} style={{...styles.card, ...getBorderColor(packet.severity)}}>
              <div style={{display: 'flex', justifyContent: 'space-between'}}>
                <strong>{packet.emergencyType}</strong>
                <span style={{fontSize: '0.8rem', color: '#666'}}>
                  {new Date(packet.timestamp).toLocaleTimeString()}
                </span>
              </div>
              <p style={{margin: '0.5rem 0'}}>{packet.message}</p>
              <div style={{fontSize: '0.85rem', color: '#555'}}>
                From: {packet.senderName} (Role: {packet.status})
              </div>
            </div>
          ))}
          {sosPackets.length === 0 && (
            <div style={{padding: '1rem', color: '#666'}}>No active incidents.</div>
          )}
        </div>
        <div style={styles.mapArea}>
          <MapContainer center={center} zoom={13} style={{ height: '100%', width: '100%' }}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {sosPackets.map(packet => (
               packet.latitude !== 0 && (
                 <Marker key={packet.uuid} position={[packet.latitude, packet.longitude]}>
                   <Popup>
                     <strong>{packet.emergencyType}</strong><br/>
                     {packet.message}<br/>
                     <em>{packet.senderName}</em>
                   </Popup>
                 </Marker>
               )
            ))}
          </MapContainer>
        </div>
      </div>
    </div>
  );
}

export default App;
