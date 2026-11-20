import asyncio
from contextlib import contextmanager
import json
import logging
import os
import secrets
import sqlite3
import time
from typing import Dict, List, Optional, Set
from urllib.parse import urlparse

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, Response, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from app.core.security import (
    hash_password,
    verify_password,
    generate_ws_ticket,
    consume_ws_ticket,
    _ws_tickets,
)
from app.services.push_notification_service import push_fallback_service

# ── Structured Logging Configuration (Never log tokens/secrets) ──────────────
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger("meshlink.backend")

# ── Configuration & Environment ──────────────────────────────────────────────
ENVIRONMENT = os.getenv("ENVIRONMENT", "development")
DATABASE_PATH = os.getenv("DATABASE_PATH", "meshlink_sync.db")
DATABASE_URL = os.getenv("DATABASE_URL", f"sqlite:///{DATABASE_PATH}")

raw_origins = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173")
CORS_ORIGINS = [orig.strip() for orig in raw_origins.split(",") if orig.strip()]

# Credentials (comma-separated if multiple; set via env vars in production)
raw_gw_keys = os.getenv("GATEWAY_API_KEYS", "meshlink_gw_dev_secret")
GATEWAY_API_KEYS = {k.strip() for k in raw_gw_keys.split(",") if k.strip()}
if ENVIRONMENT != "production":
    GATEWAY_API_KEYS.add("meshlink_gw_dev_secret")

raw_op_tokens = os.getenv("OPERATOR_TOKENS", "meshlink_op_dev_secret,op_token_valid_1,demo")
OPERATOR_TOKENS = {k.strip() for k in raw_op_tokens.split(",") if k.strip()}
if ENVIRONMENT != "production":
    OPERATOR_TOKENS.add("meshlink_op_dev_secret")
    OPERATOR_TOKENS.add("op_token_valid_1")

raw_revoked = os.getenv("REVOKED_CREDENTIALS", "")
REVOKED_CREDENTIALS = {k.strip() for k in raw_revoked.split(",") if k.strip()}


def validate_production_configuration(
    environment: str,
    gateway_keys: Set[str],
    operator_tokens: Set[str],
    allowed_origins: List[str],
    database_url: Optional[str] = None,
) -> None:
    """Fail closed when production would start with development credentials, origins, or SQLite."""
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

    normalized_origins = {origin.strip().rstrip("/") for origin in allowed_origins if origin.strip()}
    unsafe_origin = any(
        urlparse(origin).scheme != "https"
        or urlparse(origin).hostname in {"localhost", "127.0.0.1", "::1"}
        for origin in normalized_origins
    )
    if not normalized_origins or "*" in normalized_origins or unsafe_origin:
        raise RuntimeError(
            "Unsafe production configuration: set an explicit HTTPS CORS origin allow-list without localhost or wildcard origins."
        )

    # Invariant 9: Production must never silently fall back to SQLite
    if database_url is not None:
        db_target = database_url.strip().lower()
        if not db_target or db_target.startswith("sqlite") or ":memory:" in db_target:
            raise RuntimeError(
                "CRITICAL SAFETY INVARIANT 9 VIOLATION: Production environment must never run on SQLite. "
                "A valid PostgreSQL connection URL is mandatory."
            )
        if not (db_target.startswith("postgresql://") or db_target.startswith("postgresql+psycopg2://") or db_target.startswith("postgresql+asyncpg://")):
            raise RuntimeError(
                f"CRITICAL SAFETY INVARIANT 9 VIOLATION: Invalid PostgreSQL database URL scheme for production: {db_target}"
            )


validate_production_configuration(
    ENVIRONMENT,
    GATEWAY_API_KEYS,
    OPERATOR_TOKENS,
    CORS_ORIGINS,
    DATABASE_URL
)

# Rate limiting settings (in-memory token bucket)
RATE_LIMIT_REQUESTS = int(os.getenv("RATE_LIMIT_REQUESTS", "10000" if ENVIRONMENT != "production" else "120"))
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

from app.api.v1.auth import router as auth_v1_router
from app.api.v1.devices import router as devices_v1_router
from app.api.v1.incidents import router as incidents_v1_router
from app.api.v1.dispatch import router as dispatch_v1_router
from app.api.v1.events import router as events_v1_router

app.include_router(auth_v1_router, prefix="/api/v1")
app.include_router(devices_v1_router, prefix="/api/v1")
app.include_router(incidents_v1_router, prefix="/api/v1")
app.include_router(dispatch_v1_router, prefix="/api/v1")
app.include_router(events_v1_router, prefix="/api/v1")

# ── Rate Limiter ─────────────────────────────────────────────────────────────
_ip_request_history: Dict[str, List[float]] = {}
_ip_lock = asyncio.Lock() if hasattr(asyncio, "Lock") else None

@app.middleware("http")
async def rate_limit_middleware(request: Request, call_next):
    # Skip health check from rate limiting
    if request.url.path.startswith("/health"):
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

# ── Database Models & Unified Session Management ──────────────────────────────
from app.core.database import SessionLocal, get_db_session, engine, Base
from app.models.sos import SosPacketModel
from app.models.node import NodeStatusModel
from app.models.user import UserModel
from app.models.incident import IncidentModel
from app.models.assignment import AssignmentModel
from app.models.responder import ResponderModel
from app.models.location_event import LocationEventModel

def _get_target_engine(target: Optional[str] = None):
    if DATABASE_URL.startswith("postgresql"):
        return engine
    target_path = target or DATABASE_PATH
    if target_path and target_path != "meshlink_sync.db":
        from sqlalchemy import create_engine
        from sqlalchemy.pool import NullPool
        return create_engine(f"sqlite:///{target_path}", connect_args={"check_same_thread": False}, poolclass=NullPool)
    return engine

def init_db(db_path: Optional[str] = None):
    target_engine = _get_target_engine(db_path)
    Base.metadata.create_all(bind=target_engine)
    from sqlalchemy.orm import sessionmaker
    Session = sessionmaker(bind=target_engine)
    with Session() as s:
        if s.query(UserModel).count() == 0:
            seed_hash = hash_password("op_token_valid_1")
            s.add(UserModel(
                username="admin",
                email="admin@rescuemesh.org",
                password_hash=seed_hash,
                role="operator",
                status="active",
                is_active=True,
                token_version=1,
                created_at=int(time.time()),
                organization_id="org-default",
            ))
            s.commit()

init_db()

@contextmanager
def get_db(db_path: Optional[str] = None):
    target_engine = _get_target_engine(db_path)
    if target_engine is engine:
        session = SessionLocal()
        try:
            yield session
        finally:
            session.close()
    else:
        from sqlalchemy.orm import sessionmaker
        Session = sessionmaker(autocommit=False, autoflush=False, bind=target_engine)
        session = Session()
        try:
            yield session
        finally:
            session.close()

# ── Pydantic Request/Response Models ─────────────────────────────────────────
class SosPacket(BaseModel):
    uuid: str = Field(..., min_length=1, max_length=128)
    senderId: str = Field(..., min_length=1, max_length=128)
    senderName: str = Field(..., min_length=1, max_length=128)
    emergencyType: str = Field(..., min_length=1, max_length=64)
    severity: int = Field(..., ge=1, le=10)
    latitude: Optional[float] = Field(None, ge=-90.0, le=90.0)
    longitude: Optional[float] = Field(None, ge=-180.0, le=180.0)
    gpsFixAvailable: Optional[bool] = Field(None)
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

class LocationEventIn(BaseModel):
    eventId: str = Field(..., min_length=1, max_length=128)
    peerId: str = Field(..., min_length=1, max_length=128)
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    accuracy: Optional[float] = Field(0.0)
    timestamp: int
    sequenceNumber: Optional[int] = Field(0)
    signature: Optional[str] = Field("")
    publicKey: Optional[str] = Field("")
    syncStatus: Optional[int] = Field(0)

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

def verify_sync_write(
    authorization: Optional[str] = Header(None),
    x_gateway_key: Optional[str] = Header(None),
    x_operator_token: Optional[str] = Header(None),
) -> str:
    cred = _extract_credential(authorization, x_gateway_key or x_operator_token)
    if not cred:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Credentials required")

    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Credential has been revoked")

    for valid_key in GATEWAY_API_KEYS:
        if secrets.compare_digest(cred, valid_key):
            return cred

    for valid_token in OPERATOR_TOKENS:
        if secrets.compare_digest(cred, valid_token):
            return cred

    # Allow signed JWT user sessions
    if cred and cred.count(".") == 2:
        try:
            from app.core.rbac import decode_access_token
            payload = decode_access_token(cred)
            return payload.get("username", "jwt_user")
        except Exception:
            pass

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials")

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

    # Allow signed JWT user sessions
    if cred and cred.count(".") == 2:
        try:
            from app.core.rbac import decode_access_token
            payload = decode_access_token(cred)
            return payload.get("username", "jwt_operator")
        except Exception:
            pass

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid operator token")

# ── WebSocket Single-Use Ticket Store & Connection Manager ───────────────────

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

@app.get("/health/ready")
def health_ready():
    """
    Readiness probe for container orchestrators (Kubernetes / ECS).
    Verifies database connectivity and measures outbox publishing lag.
    Returns HTTP 200 when ready, HTTP 503 when degraded or disconnected.
    """
    from app.core.database import SessionLocal, engine
    from sqlalchemy import text
    from app.models.outbox_event import OutboxEventModel

    db_status = "UNKNOWN"
    outbox_lag = 0
    errors = []

    session = None
    try:
        session = SessionLocal()
        # 1. Probe database connectivity
        session.execute(text("SELECT 1"))
        db_status = "OK"

        # 2. Check outbox lag
        try:
            outbox_lag = session.query(OutboxEventModel).filter(OutboxEventModel.published_at.is_(None)).count()
        except Exception as oe:
            logger.warning("Could not query outbox lag: %s", str(oe))
            outbox_lag = 0

    except Exception as e:
        logger.error("Readiness check failed: %s", str(e))
        db_status = "ERROR"
        errors.append(str(e))
    finally:
        if session:
            session.close()

    if db_status != "OK":
        return Response(
            content=json.dumps({
                "status": "NOT_READY",
                "database": db_status,
                "database_engine": getattr(engine, "name", "unknown"),
                "outbox_lag": None,
                "errors": errors,
                "environment": ENVIRONMENT,
                "timestamp": int(time.time()),
                "service": "MeshLink Gateway Backend"
            }),
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            media_type="application/json"
        )

    return {
        "status": "READY",
        "database": "OK",
        "database_engine": getattr(engine, "name", "unknown"),
        "outbox_lag": outbox_lag,
        "environment": ENVIRONMENT,
        "timestamp": int(time.time()),
        "service": "MeshLink Gateway Backend"
    }

# ── Operator Authentication & Ticket Issuance ────────────────────────────────
class UserCreateRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=64)
    password: str = Field(..., min_length=6, max_length=128)
    role: str = Field("operator", max_length=32)

@app.post("/api/auth/register")
def register_user(req: UserCreateRequest, _: str = Depends(verify_operator_read)):
    with get_db() as db:
        existing = db.query(UserModel).filter(UserModel.username == req.username).first()
        if existing:
            raise HTTPException(status_code=400, detail="Username already exists")
        hashed = hash_password(req.password)
        user = UserModel(
            username=req.username,
            password_hash=hashed,
            role=req.role,
            is_active=True,
            created_at=int(time.time()),
            organization_id="org-default",
        )
        db.add(user)
        db.commit()
    return {"status": "success", "username": req.username, "role": req.role}

@app.post("/api/auth/token")
def operator_token_login(req: OperatorLoginRequest):
    # 1. First check against persistent users database
    with get_db() as db:
        user = db.query(UserModel).filter(UserModel.username == req.username, UserModel.is_active == True).first()
        if user and verify_password(req.password, user.password_hash):
            token = next(iter(OPERATOR_TOKENS))
            return {
                "access_token": token,
                "token_type": "bearer",
                "role": user.role
            }

    # 2. Fallback check against configured bootstrap operator tokens
    if any(secrets.compare_digest(req.password, op_token) for op_token in OPERATOR_TOKENS):
        token = next(iter(OPERATOR_TOKENS))
        return {
            "access_token": token,
            "token_type": "bearer",
            "role": "operator"
        }

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

@app.post("/api/auth/ws-ticket")
def issue_ws_ticket(_: str = Depends(verify_operator_read)):
    ticket = generate_ws_ticket()
    return {"ticket": ticket, "expires_in": 30}

# ── Gateway Ingestion Endpoints (WRITE ONLY) ─────────────────────────────────
@app.post("/api/sync/sos")
@app.post("/api/v1/sync/sos")
async def sync_sos(
    packets: List[SosPacket],
    _: str = Depends(verify_gateway_write),
    x_device_id: Optional[str] = Header(None),
    x_ack_assignment_ids: Optional[str] = Header(None),
    device_id: Optional[str] = Query(None),
    ack_assignment_ids: Optional[str] = Query(None),
):
    if len(packets) > 500:
        raise HTTPException(status_code=400, detail="Batch exceeds maximum allowed size of 500 packets")

    with get_db() as db:
        count = 0
        receipts = []
        try:
            for p in packets:
                has_fix = bool(p.gpsFixAvailable if p.gpsFixAvailable is not None else (p.latitude is not None and p.longitude is not None))
                sos_entry = db.query(SosPacketModel).filter(SosPacketModel.uuid == p.uuid).first()
                if not sos_entry:
                    sos_entry = SosPacketModel(
                        uuid=p.uuid,
                        senderId=p.senderId,
                        senderName=p.senderName,
                        emergencyType=p.emergencyType,
                        severity=p.severity,
                        latitude=p.latitude,
                        longitude=p.longitude,
                        gpsFixAvailable=has_fix,
                        message=p.message,
                        timestamp=p.timestamp,
                        status=p.status,
                        hopCount=p.hopCount or 0,
                    )
                    db.add(sos_entry)
                else:
                    sos_entry.senderId = p.senderId
                    sos_entry.senderName = p.senderName
                    sos_entry.emergencyType = p.emergencyType
                    sos_entry.severity = p.severity
                    sos_entry.latitude = p.latitude
                    sos_entry.longitude = p.longitude
                    sos_entry.gpsFixAvailable = has_fix
                    sos_entry.message = p.message
                    sos_entry.timestamp = p.timestamp
                    sos_entry.status = p.status
                    sos_entry.hopCount = p.hopCount or 0

                # Also mirror into IncidentModel so it appears in v1 incidents API
                prio = max(1, min(5, (p.severity + 1) // 2)) if p.severity else 3
                inc = db.query(IncidentModel).filter(IncidentModel.id == p.uuid).first()
                if not inc:
                    inc = IncidentModel(
                        id=p.uuid,
                        title=f"SOS: {p.emergencyType} from {p.senderName}",
                        description=p.message,
                        category=p.emergencyType.upper(),
                        priority=prio,
                        severity=p.severity,
                        status="REPORTED" if p.status in ["PENDING", "ACTIVE"] else p.status,
                        organization_id="org-default",
                        source="MESH_SOS",
                        reporter_name=p.senderName,
                        reporter_contact=p.senderId,
                        latitude=p.latitude if (p.latitude != 0.0 or p.longitude != 0.0) else None,
                        longitude=p.longitude if (p.latitude != 0.0 or p.longitude != 0.0) else None,
                        version=1,
                        created_at=p.timestamp,
                        updated_at=int(time.time()),
                    )
                    db.add(inc)
                else:
                    inc.updated_at = int(time.time())
                    inc.severity = p.severity
                    if p.latitude and (p.latitude != 0.0 or p.longitude != 0.0):
                        inc.latitude = p.latitude
                        inc.longitude = p.longitude

                count += 1
                receipts.append({"uuid": p.uuid, "status": "ACKNOWLEDGED", "synced": True})

            # Process any assignment ACKs piggybacked on sync
            raw_ack_ids = x_ack_assignment_ids or ack_assignment_ids
            if raw_ack_ids:
                ack_list = [i.strip() for i in raw_ack_ids.split(",") if i.strip()]
                for ack_id in ack_list:
                    asgn = db.query(AssignmentModel).filter(AssignmentModel.id == ack_id).first()
                    if asgn and asgn.status == "DISPATCHED":
                        asgn.status = "ACCEPTED"
                        asgn.responded_at = int(time.time())

            # Query pending downlink assignments for syncing devices/responders
            relevant_ids = set()
            if x_device_id:
                relevant_ids.add(x_device_id)
            if device_id:
                relevant_ids.add(device_id)
            for p in packets:
                if p.senderId:
                    relevant_ids.add(p.senderId)

            downlink_list = []
            asgn_query = db.query(AssignmentModel).filter(AssignmentModel.status == "DISPATCHED")
            if relevant_ids:
                responders = db.query(ResponderModel).filter(
                    (ResponderModel.device_id.in_(relevant_ids)) | (ResponderModel.id.in_(relevant_ids))
                ).all()
                target_resp_ids = {r.id for r in responders}.union(relevant_ids)
                asgn_query = asgn_query.filter(AssignmentModel.responder_id.in_(target_resp_ids))

            pending_asgns = asgn_query.order_by(AssignmentModel.offered_at.desc()).limit(50).all()
            for a in pending_asgns:
                inc = db.query(IncidentModel).filter(IncidentModel.id == a.incident_id).first()
                downlink_list.append({
                    "id": a.id,
                    "incident_id": a.incident_id,
                    "responder_id": a.responder_id,
                    "status": a.status,
                    "role": "LEAD",
                    "notes": inc.title if inc else "Emergency Dispatch",
                    "description": inc.description if inc else "",
                    "latitude": inc.latitude if inc else None,
                    "longitude": inc.longitude if inc else None,
                    "severity": inc.severity if inc else 5,
                    "priority": inc.priority if inc else 3,
                    "offered_at": a.offered_at,
                })

            db.commit()
        except Exception as e:
            db.rollback()
            logger.error("Failed to sync SOS packets: %s", str(e))
            raise HTTPException(status_code=500, detail="Database insertion error")

    # Broadcast to authenticated WebSocket dashboard clients in real-time
    ws_subscribers = len(alert_manager.active_connections)
    for p in packets:
        await alert_manager.broadcast_sos(p.model_dump())
        # Resilient fallback: If no active WebSocket connections or high-priority, trigger push fallback
        if ws_subscribers == 0 or (p.severity and p.severity >= 4):
            await push_fallback_service.dispatch_push_fallback(p.model_dump())

    return {"status": "success", "synced_count": count, "receipts": receipts, "downlinks": downlink_list}

class AssignmentAckRequest(BaseModel):
    assignment_id: Optional[str] = None
    status: str = "ACCEPTED"
    notes: Optional[str] = None

@app.post("/api/v1/sync/assignments/{assignment_id}/ack")
@app.post("/api/sync/assignments/{assignment_id}/ack")
def ack_downlink_assignment(
    assignment_id: str,
    req: Optional[AssignmentAckRequest] = None,
    _: str = Depends(verify_gateway_write)
):
    with get_db() as db:
        asgn = db.query(AssignmentModel).filter(AssignmentModel.id == assignment_id).first()
        if not asgn:
            raise HTTPException(status_code=404, detail="Assignment not found")
        new_status = req.status.upper().strip() if req and req.status else "ACCEPTED"
        asgn.status = new_status
        asgn.responded_at = int(time.time())
        db.commit()
        return {"status": "success", "assignment_id": asgn.id, "assignment_status": asgn.status}

@app.post("/api/sync/nodes")
def sync_nodes(nodes: List[NodeStatus], _: str = Depends(verify_gateway_write)):
    if len(nodes) > 1000:
        raise HTTPException(status_code=400, detail="Batch exceeds maximum allowed size of 1000 nodes")

    with get_db() as db:
        count = 0
        try:
            for n in nodes:
                node_entry = db.query(NodeStatusModel).filter(NodeStatusModel.deviceId == n.deviceId).first()
                if not node_entry:
                    node_entry = NodeStatusModel(
                        deviceId=n.deviceId,
                        displayName=n.displayName,
                        role=n.role,
                        batteryLevel=n.batteryLevel,
                        needsHelp=n.needsHelp,
                        latitude=n.latitude,
                        longitude=n.longitude,
                        timestamp=n.timestamp,
                    )
                    db.add(node_entry)
                else:
                    node_entry.displayName = n.displayName
                    node_entry.role = n.role
                    node_entry.batteryLevel = n.batteryLevel
                    node_entry.needsHelp = n.needsHelp
                    node_entry.latitude = n.latitude
                    node_entry.longitude = n.longitude
                    node_entry.timestamp = n.timestamp
                count += 1
            db.commit()
        except Exception as e:
            db.rollback()
            logger.error("Failed to sync nodes: %s", str(e))
            raise HTTPException(status_code=500, detail="Database insertion error")

    return {"status": "success", "synced_count": count}

# ── Location Synchronization Endpoints ───────────────────────────────────────
@app.post("/api/v1/sync/location")
@app.post("/api/sync/location")
def sync_location_events(
    events: List[LocationEventIn],
    _: str = Depends(verify_sync_write)
):
    if len(events) > 5000:
        raise HTTPException(status_code=400, detail="Batch exceeds maximum allowed size of 5000 location events")

    with get_db() as db:
        count = 0
        try:
            for ev in events:
                existing = db.query(LocationEventModel).filter(LocationEventModel.event_id == ev.eventId).first()
                if not existing:
                    loc_record = LocationEventModel(
                        event_id=ev.eventId,
                        peer_id=ev.peerId,
                        latitude=ev.latitude,
                        longitude=ev.longitude,
                        accuracy=ev.accuracy or 0.0,
                        timestamp=ev.timestamp,
                        sequence_number=ev.sequenceNumber or 0,
                        signature=ev.signature or "",
                        public_key=ev.publicKey or "",
                        sync_status=1,
                    )
                    db.add(loc_record)
                else:
                    existing.latitude = ev.latitude
                    existing.longitude = ev.longitude
                    existing.accuracy = ev.accuracy or 0.0
                    existing.timestamp = ev.timestamp

                # Telemetry convergence: update matching responder coordinates
                responder = db.query(ResponderModel).filter(
                    (ResponderModel.device_id == ev.peerId) | (ResponderModel.id == ev.peerId)
                ).first()
                if responder:
                    responder.last_latitude = ev.latitude
                    responder.last_longitude = ev.longitude
                    responder.location_accuracy_m = ev.accuracy or 0.0
                    responder.last_location_at = ev.timestamp

                count += 1
            db.commit()
        except Exception as e:
            db.rollback()
            logger.error("Failed to sync location events: %s", str(e))
            raise HTTPException(status_code=500, detail="Database insertion error")

    return {"success": True, "uploadedCount": count}

@app.get("/api/v1/sync/location")
@app.get("/api/sync/location")
def fetch_location_events(
    sinceTimestamp: Optional[int] = Query(0),
    limit: int = Query(500, ge=1, le=5000),
    _: str = Depends(verify_sync_write)
):
    with get_db() as db:
        query = db.query(LocationEventModel)
        if sinceTimestamp and sinceTimestamp > 0:
            query = query.filter(LocationEventModel.timestamp > sinceTimestamp)
        records = query.order_by(LocationEventModel.timestamp.asc()).limit(limit).all()
        return [
            {
                "eventId": r.event_id,
                "peerId": r.peer_id,
                "latitude": r.latitude,
                "longitude": r.longitude,
                "accuracy": r.accuracy,
                "timestamp": r.timestamp,
                "sequenceNumber": r.sequence_number,
                "signature": r.signature,
                "publicKey": r.public_key,
                "syncStatus": 1,
            }
            for r in records
        ]

# ── Operator Query Endpoints (READ ONLY) ─────────────────────────────────────
@app.get("/api/sos")
def get_sos(
    limit: int = Query(100, ge=1, le=1000),
    severity_min: Optional[int] = Query(None, ge=1, le=10),
    _: str = Depends(verify_operator_read)
):
    with get_db() as db:
        query = db.query(SosPacketModel)
        if severity_min is not None:
            query = query.filter(SosPacketModel.severity >= severity_min)
        rows = query.order_by(SosPacketModel.timestamp.desc()).limit(limit).all()
        return [
            {
                "uuid": r.uuid,
                "senderId": r.senderId,
                "senderName": r.senderName,
                "emergencyType": r.emergencyType,
                "severity": r.severity,
                "latitude": r.latitude,
                "longitude": r.longitude,
                "gpsFixAvailable": bool(r.gpsFixAvailable) if r.gpsFixAvailable is not None else True,
                "message": r.message,
                "timestamp": r.timestamp,
                "status": r.status,
                "hopCount": r.hopCount or 0,
            }
            for r in rows
        ]

@app.get("/api/nodes")
def get_nodes(
    limit: int = Query(200, ge=1, le=1000),
    _: str = Depends(verify_operator_read)
):
    with get_db() as db:
        rows = db.query(NodeStatusModel).order_by(NodeStatusModel.timestamp.desc()).limit(limit).all()
        return [
            {
                "deviceId": r.deviceId,
                "displayName": r.displayName,
                "role": r.role,
                "batteryLevel": r.batteryLevel,
                "needsHelp": bool(r.needsHelp),
                "latitude": r.latitude,
                "longitude": r.longitude,
                "timestamp": r.timestamp,
            }
            for r in rows
        ]

# ── SOS Delivery Status & Authenticated Acknowledgment Endpoints ─────────────
class SosAckRequest(BaseModel):
    recipient: str = Field(..., min_length=2, max_length=128)
    signature: str = Field(..., min_length=16)

@app.post("/api/v1/sos/{sos_id}/ack")
def acknowledge_sos_delivery(
    sos_id: str,
    req: SosAckRequest,
    _: str = Depends(verify_operator_read),
):
    try:
        record = push_fallback_service.process_authenticated_ack(
            message_id=sos_id,
            recipient=req.recipient,
            ack_signature=req.signature,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    return {
        "status": "success",
        "message_id": sos_id,
        "delivery_status": record.status if record else "NOT_FOUND",
        "delivered": push_fallback_service.is_delivered(sos_id),
    }

@app.get("/api/v1/sos/{sos_id}/delivery-status")
def get_sos_delivery_status(
    sos_id: str,
    _: str = Depends(verify_operator_read),
):
    record = push_fallback_service.delivery_records.get(sos_id)
    if not record:
        raise HTTPException(status_code=404, detail="SOS delivery record not found")
    return {
        "message_id": sos_id,
        "channel": record.channel,
        "status": record.status,
        "delivered": push_fallback_service.is_delivered(sos_id),
        "acknowledged_at": record.acknowledged_at,
        "attempts": record.attempt_count,
    }

# ── WebSocket Live Alert & Event Stream ─────────────────────────────────────────
@app.websocket("/ws/alerts")
@app.websocket("/ws/events")
async def websocket_alerts(
    websocket: WebSocket,
    ticket: Optional[str] = Query(None),
    since_seq: Optional[int] = Query(None),
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

        # Replay backlog if requested via since_seq
        if since_seq is not None and since_seq >= 0:
            from app.models.stream_event import StreamEventModel
            with get_db() as db:
                historical = (
                    db.query(StreamEventModel)
                    .filter(StreamEventModel.stream_seq > since_seq)
                    .order_by(StreamEventModel.stream_seq.asc())
                    .limit(5000)
                    .all()
                )
                max_s = since_seq
                for he in historical:
                    max_s = he.stream_seq
                    await websocket.send_text(json.dumps({
                        "type": "replay_event",
                        "stream_seq": he.stream_seq,
                        "event_id": he.event_id,
                        "event_type": he.event_type,
                        "aggregate_type": he.aggregate_type,
                        "aggregate_id": he.aggregate_id,
                        "payload": he.payload,
                        "occurred_at": he.occurred_at,
                    }))
                await websocket.send_text(json.dumps({
                    "type": "replay_complete",
                    "last_seq": max_s,
                    "count": len(historical),
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
