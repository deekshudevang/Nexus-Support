import asyncio
from contextlib import contextmanager
import json
import logging
import os
import secrets
import sqlite3
import time
from typing import Dict, List, Optional, Set

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, Response, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ── Structured Logging Configuration (Never log tokens/secrets) ──────────────
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger("meshlink.backend")

# ── Configuration & Environment ──────────────────────────────────────────────
ENVIRONMENT = os.getenv("ENVIRONMENT", "development")
DATABASE_PATH = os.getenv("DATABASE_PATH", "meshlink_sync.db")

raw_origins = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173")
CORS_ORIGINS = [orig.strip() for orig in raw_origins.split(",") if orig.strip()]

# Credentials (comma-separated if multiple; set via env vars in production)
raw_gw_keys = os.getenv("GATEWAY_API_KEYS", "meshlink_gw_dev_secret")
GATEWAY_API_KEYS = {k.strip() for k in raw_gw_keys.split(",") if k.strip()}

raw_op_tokens = os.getenv("OPERATOR_TOKENS", "meshlink_op_dev_secret")
OPERATOR_TOKENS = {k.strip() for k in raw_op_tokens.split(",") if k.strip()}

raw_revoked = os.getenv("REVOKED_CREDENTIALS", "")
REVOKED_CREDENTIALS = {k.strip() for k in raw_revoked.split(",") if k.strip()}


def validate_production_configuration(
    environment: str,
    gateway_keys: Set[str],
    operator_tokens: Set[str],
    allowed_origins: List[str],
) -> None:
    """Fail closed when production would start with development credentials or origins."""
    if environment.strip().lower() != "production":
        return

    development_credentials = {"meshlink_gw_dev_secret", "meshlink_op_dev_secret"}
    if (
        not gateway_keys
        or not operator_tokens
        or gateway_keys.intersection(development_credentials)
        or operator_tokens.intersection(development_credentials)
    ):
        raise RuntimeError(
            "Unsafe production configuration: set explicit non-development gateway and operator credentials."
        )

    local_origins = {"http://localhost", "http://127.0.0.1", "http://localhost:5173",
                     "http://localhost:3000", "http://127.0.0.1:5173"}
    normalized_origins = {origin.strip().rstrip("/") for origin in allowed_origins if origin.strip()}
    if not normalized_origins or "*" in normalized_origins or normalized_origins.intersection(local_origins):
        raise RuntimeError(
            "Unsafe production configuration: set an explicit HTTPS CORS origin allow-list without localhost or wildcard origins."
        )


validate_production_configuration(ENVIRONMENT, GATEWAY_API_KEYS, OPERATOR_TOKENS, CORS_ORIGINS)

# Rate limiting settings (in-memory token bucket)
RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "120"))
RATE_LIMIT_WINDOW_SEC = int(os.getenv("RATE_LIMIT_WINDOW_SEC", "60"))

app = FastAPI(
    title="MeshLink Gateway Backend",
    version="1.0.0",
    docs_url="/docs" if ENVIRONMENT != "production" else None,
    redoc_url=None
)

# CORS configured with explicit allowlist (no wildcard in production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

# ── Rate Limiter ─────────────────────────────────────────────────────────────
_ip_request_history: Dict[str, List[float]] = {}
_ip_lock = asyncio.Lock() if hasattr(asyncio, "Lock") else None

@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    # Skip health check from rate limiting
    if request.url.path == "/health":
        return await call_next(request)

    client_ip = request.client.host if request.client else "unknown"
    now = time.time()

    # Clean old entries
    history = _ip_request_history.setdefault(client_ip, [])
    cutoff = now - RATE_LIMIT_WINDOW_SEC
    _ip_request_history[client_ip] = [t for t in history if t > cutoff]

    if len(_ip_request_history[client_ip]) >= RATE_LIMIT_REQUESTS:
        logger.warning("Rate limit exceeded for client %s", client_ip)
        return Response(
            content=json.dumps({"detail": "Rate limit exceeded. Please try again later."}),
            status_code=429,
            media_type="application/json"
        )

    _ip_request_history[client_ip].append(now)
    response = await call_next(request)
    return response

# ── SQLite Database Setup & Connection Pool ──────────────────────────────────
def init_db(db_path: Optional[str] = None):
    target = db_path or DATABASE_PATH
    conn = sqlite3.connect(target)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=5000;")
    conn.execute("""
    CREATE TABLE IF NOT EXISTS sos_packets (
        uuid TEXT PRIMARY KEY,
        senderId TEXT NOT NULL,
        senderName TEXT NOT NULL,
        emergencyType TEXT NOT NULL,
        severity INTEGER NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        message TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        status TEXT NOT NULL,
        hopCount INTEGER DEFAULT 0
    );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sos_packets_timestamp ON sos_packets(timestamp DESC);")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sos_packets_severity ON sos_packets(severity);")

    conn.execute("""
    CREATE TABLE IF NOT EXISTS node_status (
        deviceId TEXT PRIMARY KEY,
        displayName TEXT NOT NULL,
        role TEXT NOT NULL,
        batteryLevel INTEGER NOT NULL,
        needsHelp BOOLEAN NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        timestamp INTEGER NOT NULL
    );
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_node_status_timestamp ON node_status(timestamp DESC);")
    conn.commit()
    conn.close()

init_db()

@contextmanager
def get_db(db_path: Optional[str] = None):
    target = db_path or DATABASE_PATH
    conn = sqlite3.connect(target, timeout=5.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=5000;")
    try:
        yield conn
    finally:
        conn.close()

# ── Pydantic Request/Response Models ─────────────────────────────────────────
class SosPacket(BaseModel):
    uuid: str = Field(..., min_length=1, max_length=128)
    senderId: str = Field(..., min_length=1, max_length=128)
    senderName: str = Field(..., min_length=1, max_length=128)
    emergencyType: str = Field(..., min_length=1, max_length=64)
    severity: int = Field(..., ge=1, le=10)
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    message: str = Field(..., max_length=4096)
    timestamp: int
    status: str = Field(..., min_length=1, max_length=32)
    hopCount: Optional[int] = Field(0, ge=0, le=255)

class NodeStatus(BaseModel):
    deviceId: str = Field(..., min_length=1, max_length=128)
    displayName: str = Field(..., min_length=1, max_length=128)
    role: str = Field(..., min_length=1, max_length=64)
    batteryLevel: int = Field(..., ge=0, le=100)
    needsHelp: bool
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    timestamp: int

class OperatorLoginRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=64)
    password: str = Field(..., min_length=1, max_length=128)

# ── Role-Based Authentication & Authorization ────────────────────────────────
def _extract_credential(auth_header: Optional[str], custom_header: Optional[str]) -> Optional[str]:
    if custom_header:
        return custom_header.strip()
    if auth_header:
        parts = auth_header.split()
        if len(parts) == 2 and parts[0].lower() == "bearer":
            return parts[1].strip()
        return auth_header.strip()
    return None

def verify_gateway_write(
    authorization: Optional[str] = Header(None),
    x_gateway_key: Optional[str] = Header(None)
) -> str:
    cred = _extract_credential(authorization, x_gateway_key)
    if not cred:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Gateway credentials required")

    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Gateway credential has been revoked")

    for valid_key in GATEWAY_API_KEYS:
        if secrets.compare_digest(cred, valid_key):
            return cred

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid gateway credentials")

def verify_operator_read(
    authorization: Optional[str] = Header(None),
    x_operator_token: Optional[str] = Header(None)
) -> str:
    cred = _extract_credential(authorization, x_operator_token)
    if not cred:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Operator authentication required")

    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Operator token has been revoked")

    for valid_token in OPERATOR_TOKENS:
        if secrets.compare_digest(cred, valid_token):
            return cred

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid operator token")

# ── WebSocket Single-Use Ticket Store & Connection Manager ───────────────────
_ws_tickets: Dict[str, float] = {}  # ticket -> expiration timestamp

def generate_ws_ticket() -> str:
    ticket = secrets.token_urlsafe(32)
    # 30-second single-use ticket
    _ws_tickets[ticket] = time.time() + 30.0
    return ticket

def consume_ws_ticket(ticket: str) -> bool:
    if not ticket:
        return False
    now = time.time()
    # Clean expired
    expired = [t for t, exp in _ws_tickets.items() if exp < now]
    for t in expired:
        _ws_tickets.pop(t, None)

    if ticket in _ws_tickets and _ws_tickets[ticket] >= now:
        _ws_tickets.pop(ticket, None)  # strictly single-use
        return True
    return False

class AlertConnectionManager:
    def __init__(self):
        self.active_connections: Set[WebSocket] = set()
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket):
        async with self._lock:
            self.active_connections.add(websocket)

    async def disconnect(self, websocket: WebSocket):
        async with self._lock:
            self.active_connections.discard(websocket)

    async def broadcast_sos(self, packet: dict):
        dead_connections = []
        async with self._lock:
            for connection in self.active_connections:
                try:
                    await connection.send_text(json.dumps({"type": "sos_alert", "packet": packet}))
                except Exception:
                    dead_connections.append(connection)
            for dead in dead_connections:
                self.active_connections.discard(dead)

alert_manager = AlertConnectionManager()

# ── Health & Diagnostic Endpoints ────────────────────────────────────────────
@app.get("/health")
def health():
    return {
        "status": "ok",
        "environment": ENVIRONMENT,
        "timestamp": int(time.time()),
        "service": "MeshLink Gateway Backend"
    }

# ── Operator Authentication & Ticket Issuance ────────────────────────────────
@app.post("/api/auth/token")
def operator_token_login(req: OperatorLoginRequest):
    # In production, this verifies hashed password against user database
    # For operator tokens, verify against configured secret
    if not any(secrets.compare_digest(req.password, op_token) for op_token in OPERATOR_TOKENS):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    
    # Return operator access token
    token = next(iter(OPERATOR_TOKENS))
    return {
        "access_token": token,
        "token_type": "bearer",
        "role": "operator"
    }

@app.post("/api/auth/ws-ticket")
def issue_ws_ticket(_: str = Depends(verify_operator_read)):
    ticket = generate_ws_ticket()
    return {"ticket": ticket, "expires_in": 30}

# ── Gateway Ingestion Endpoints (WRITE ONLY) ─────────────────────────────────
@app.post("/api/sync/sos")
async def sync_sos(packets: List[SosPacket], _: str = Depends(verify_gateway_write)):
    if len(packets) > 500:
        raise HTTPException(status_code=400, detail="Batch exceeds maximum allowed size of 500 packets")

    with get_db() as conn:
        count = 0
        try:
            for p in packets:
                conn.execute("""
                INSERT OR REPLACE INTO sos_packets 
                (uuid, senderId, senderName, emergencyType, severity, latitude, longitude, message, timestamp, status, hopCount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    p.uuid, p.senderId, p.senderName, p.emergencyType,
                    p.severity, p.latitude, p.longitude, p.message,
                    p.timestamp, p.status, p.hopCount or 0
                ))
                count += 1
            conn.commit()
        except Exception as e:
            conn.rollback()
            logger.error("Failed to sync SOS packets: %s", str(e))
            raise HTTPException(status_code=500, detail="Database insertion error")

    # Broadcast to authenticated WebSocket dashboard clients in real-time
    for p in packets:
        await alert_manager.broadcast_sos(p.model_dump())

    return {"status": "success", "synced_count": count}

@app.post("/api/sync/nodes")
def sync_nodes(nodes: List[NodeStatus], _: str = Depends(verify_gateway_write)):
    if len(nodes) > 1000:
        raise HTTPException(status_code=400, detail="Batch exceeds maximum allowed size of 1000 nodes")

    with get_db() as conn:
        count = 0
        try:
            for n in nodes:
                conn.execute("""
                INSERT OR REPLACE INTO node_status
                (deviceId, displayName, role, batteryLevel, needsHelp, latitude, longitude, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    n.deviceId, n.displayName, n.role,
                    n.batteryLevel, 1 if n.needsHelp else 0,
                    n.latitude, n.longitude, n.timestamp
                ))
                count += 1
            conn.commit()
        except Exception as e:
            conn.rollback()
            logger.error("Failed to sync nodes: %s", str(e))
            raise HTTPException(status_code=500, detail="Database insertion error")

    return {"status": "success", "synced_count": count}

# ── Operator Query Endpoints (READ ONLY) ─────────────────────────────────────
@app.get("/api/sos")
def get_sos(
    limit: int = Query(100, ge=1, le=1000),
    severity_min: Optional[int] = Query(None, ge=1, le=10),
    _: str = Depends(verify_operator_read)
):
    with get_db() as conn:
        if severity_min is not None:
            cursor = conn.execute(
                "SELECT * FROM sos_packets WHERE severity >= ? ORDER BY timestamp DESC LIMIT ?",
                (severity_min, limit)
            )
        else:
            cursor = conn.execute(
                "SELECT * FROM sos_packets ORDER BY timestamp DESC LIMIT ?",
                (limit,)
            )
        rows = cursor.fetchall()
        return [dict(ix) for ix in rows]

@app.get("/api/nodes")
def get_nodes(
    limit: int = Query(200, ge=1, le=1000),
    _: str = Depends(verify_operator_read)
):
    with get_db() as conn:
        cursor = conn.execute(
            "SELECT * FROM node_status ORDER BY timestamp DESC LIMIT ?",
            (limit,)
        )
        rows = cursor.fetchall()
        return [
            {
                "deviceId": row["deviceId"],
                "displayName": row["displayName"],
                "role": row["role"],
                "batteryLevel": row["batteryLevel"],
                "needsHelp": bool(row["needsHelp"]),
                "latitude": row["latitude"],
                "longitude": row["longitude"],
                "timestamp": row["timestamp"]
            }
            for row in rows
        ]

# ── WebSocket Live Alert Stream ──────────────────────────────────────────────
@app.websocket("/ws/alerts")
async def websocket_alerts(
    websocket: WebSocket,
    ticket: Optional[str] = Query(None)
):
    # 1. Origin validation against allowed CORS origins
    origin = websocket.headers.get("origin")
    if origin and CORS_ORIGINS != ["*"]:
        normalized_origin = origin.rstrip("/")
        allowed = any(normalized_origin == o.rstrip("/") for o in CORS_ORIGINS)
        if not allowed:
            logger.warning("Rejected WebSocket connection from unauthorized origin: %s", origin)
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

    # 2. Production TLS/WSS check
    if ENVIRONMENT == "production":
        forwarded_proto = websocket.headers.get("x-forwarded-proto", "")
        if websocket.url.scheme != "wss" and forwarded_proto != "https":
            logger.warning("Rejected non-TLS WebSocket connection in production environment")
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

    # 3. Connection cap check
    if len(alert_manager.active_connections) >= 50:
        logger.warning("Rejected WebSocket connection: active connection limit reached")
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    authenticated = False

    # Check 1: single-use ticket in query param
    if ticket:
        if not consume_ws_ticket(ticket):
            logger.warning("WebSocket rejected: invalid or expired ticket in query param")
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return
        authenticated = True

    # Check 2: subprotocol ticket (Sec-WebSocket-Protocol)
    if not authenticated:
        sec_protocol = websocket.headers.get("sec-websocket-protocol", "")
        if "ticket-" in sec_protocol:
            sub_ticket = None
            for part in sec_protocol.split(","):
                p = part.strip()
                if p.startswith("ticket-"):
                    sub_ticket = p[7:]
                    break
            if sub_ticket:
                if not consume_ws_ticket(sub_ticket):
                    logger.warning("WebSocket rejected: invalid or expired subprotocol ticket")
                    await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
                    return
                authenticated = True

    # Accept connection to conduct communications
    await websocket.accept()

    # Check 3: If not authenticated via ticket, enforce strictly time-limited initial authentication message (5.0s window)
    if not authenticated:
        try:
            auth_msg_raw = await asyncio.wait_for(websocket.receive_text(), timeout=5.0)
            auth_msg = json.loads(auth_msg_raw)
            if auth_msg.get("type") == "auth":
                provided_ticket = auth_msg.get("ticket")
                provided_token = auth_msg.get("token")
                if provided_ticket and consume_ws_ticket(provided_ticket):
                    authenticated = True
                elif provided_token:
                    if (provided_token not in REVOKED_CREDENTIALS and
                        any(secrets.compare_digest(provided_token, op) for op in OPERATOR_TOKENS)):
                        authenticated = True
        except (asyncio.TimeoutError, json.JSONDecodeError, Exception):
            authenticated = False

        if not authenticated:
            logger.warning("WebSocket client failed timed authentication message or timed out")
            try:
                await websocket.send_text(json.dumps({"type": "error", "message": "Authentication required"}))
                await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            except Exception:
                pass
            return

    # Client successfully authenticated: register with alert manager
    await alert_manager.connect(websocket)
    try:
        await websocket.send_text(json.dumps({
            "type": "authenticated",
            "message": "Connected to authenticated live SOS alert stream"
        }))
        # Keepalive loop: listen for client pings or heartbeats
        while True:
            data = await websocket.receive_text()
            try:
                parsed = json.loads(data)
                if parsed.get("type") == "ping":
                    await websocket.send_text(json.dumps({"type": "pong", "timestamp": int(time.time())}))
            except Exception:
                pass
    except (WebSocketDisconnect, Exception):
        pass
    finally:
        await alert_manager.disconnect(websocket)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
