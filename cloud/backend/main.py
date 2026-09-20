from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import sqlite3
import datetime
import json
from typing import List, Optional

app = FastAPI(title="MeshLink Gateway Backend")

# Enable CORS for React frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# SQLite setup
def get_db():
    conn = sqlite3.connect("meshlink_sync.db")
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    conn.execute('''
    CREATE TABLE IF NOT EXISTS sos_packets (
        uuid TEXT PRIMARY KEY,
        senderId TEXT,
        senderName TEXT,
        emergencyType TEXT,
        severity INTEGER,
        latitude REAL,
        longitude REAL,
        message TEXT,
        timestamp INTEGER,
        status TEXT
    )
    ''')
    conn.execute('''
    CREATE TABLE IF NOT EXISTS node_status (
        deviceId TEXT PRIMARY KEY,
        displayName TEXT,
        role TEXT,
        batteryLevel INTEGER,
        needsHelp BOOLEAN,
        latitude REAL,
        longitude REAL,
        timestamp INTEGER
    )
    ''')
    conn.commit()
    conn.close()

init_db()

# Models
class SosPacket(BaseModel):
    uuid: str
    senderId: str
    senderName: str
    emergencyType: str
    severity: int
    latitude: float
    longitude: float
    message: str
    timestamp: int
    status: str
    hopCount: Optional[int] = 0

class NodeStatus(BaseModel):
    deviceId: str
    displayName: str
    role: str
    batteryLevel: int
    needsHelp: bool
    latitude: float
    longitude: float
    timestamp: int

@app.post("/api/sync/sos")
def sync_sos(packets: List[SosPacket]):
    conn = get_db()
    count = 0
    try:
        for p in packets:
            conn.execute('''
            INSERT OR REPLACE INTO sos_packets 
            (uuid, senderId, senderName, emergencyType, severity, latitude, longitude, message, timestamp, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (p.uuid, p.senderId, p.senderName, p.emergencyType, p.severity, p.latitude, p.longitude, p.message, p.timestamp, p.status))
            count += 1
        conn.commit()
        return {"status": "success", "synced_count": count}
    except Exception as e:
        conn.rollback()
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        conn.close()

@app.get("/api/sos")
def get_sos():
    conn = get_db()
    cursor = conn.execute("SELECT * FROM sos_packets ORDER BY timestamp DESC")
    rows = cursor.fetchall()
    conn.close()
    return [dict(ix) for ix in rows]

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
