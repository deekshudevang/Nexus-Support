"""Role-Based Access Control (RBAC) and Capability-Based Authorization for Nexus Support."""

import base64
import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass
from enum import Enum
from typing import Callable, Dict, List, Optional, Set

from fastapi import Depends, Header, HTTPException, Request, status
from sqlalchemy.orm import Session

from app.core.config import (
    GATEWAY_API_KEYS,
    OPERATOR_TOKENS,
    REVOKED_CREDENTIALS,
)
from app.core.database import get_db_session
from app.models.device import DeviceModel
from app.models.user import UserModel


class Role(str, Enum):
    ADMIN = "admin"
    COMMANDER = "commander"
    DISPATCHER = "dispatcher"
    RESPONDER = "responder"
    VIEWER = "viewer"
    DEVICE = "device"


class Permission(str, Enum):
    # Incident domain
    INCIDENT_CREATE = "incident:create"
    INCIDENT_READ = "incident:read"
    INCIDENT_TRIAGE = "incident:triage"
    INCIDENT_CLOSE = "incident:close"

    # Assignment domain
    ASSIGNMENT_DISPATCH = "assignment:dispatch"
    ASSIGNMENT_UPDATE = "assignment:update"
    ASSIGNMENT_UPDATE_SELF = "assignment:update_self"
    ASSIGNMENT_READ = "assignment:read"

    # Responder & Team domain
    RESPONDER_MANAGE = "responder:manage"
    RESPONDER_READ = "responder:read"
    RESPONDER_UPDATE_SELF = "responder:update_self"
    RESPONDER_TELEMETRY = "responder:telemetry"
    TEAM_MANAGE = "team:manage"
    TEAM_READ = "team:read"

    # Device & Mesh domain
    DEVICE_ENROLL = "device:enroll"
    DEVICE_TELEMETRY = "device:telemetry"
    DEVICE_SOS = "device:sos"

    # System & Audit
    AUDIT_READ = "audit:read"
    USER_MANAGE = "user:manage"


# Granular capability mapping per role
ROLE_PERMISSIONS: Dict[str, Set[Permission]] = {
    Role.ADMIN.value: {
        Permission.INCIDENT_CREATE,
        Permission.INCIDENT_READ,
        Permission.INCIDENT_TRIAGE,
        Permission.INCIDENT_CLOSE,
        Permission.ASSIGNMENT_DISPATCH,
        Permission.ASSIGNMENT_UPDATE,
        Permission.ASSIGNMENT_UPDATE_SELF,
        Permission.ASSIGNMENT_READ,
        Permission.RESPONDER_MANAGE,
        Permission.RESPONDER_READ,
        Permission.RESPONDER_UPDATE_SELF,
        Permission.RESPONDER_TELEMETRY,
        Permission.TEAM_MANAGE,
        Permission.TEAM_READ,
        Permission.DEVICE_ENROLL,
        Permission.DEVICE_TELEMETRY,
        Permission.DEVICE_SOS,
        Permission.AUDIT_READ,
        Permission.USER_MANAGE,
    },
    Role.COMMANDER.value: {
        Permission.INCIDENT_CREATE,
        Permission.INCIDENT_READ,
        Permission.INCIDENT_TRIAGE,
        Permission.INCIDENT_CLOSE,
        Permission.ASSIGNMENT_DISPATCH,
        Permission.ASSIGNMENT_UPDATE,
        Permission.ASSIGNMENT_UPDATE_SELF,
        Permission.ASSIGNMENT_READ,
        Permission.RESPONDER_MANAGE,
        Permission.RESPONDER_READ,
        Permission.RESPONDER_UPDATE_SELF,
        Permission.RESPONDER_TELEMETRY,
        Permission.TEAM_MANAGE,
        Permission.TEAM_READ,
        Permission.DEVICE_ENROLL,
        Permission.DEVICE_TELEMETRY,
        Permission.DEVICE_SOS,
        Permission.AUDIT_READ,
    },
    Role.DISPATCHER.value: {
        Permission.INCIDENT_CREATE,
        Permission.INCIDENT_READ,
        Permission.INCIDENT_TRIAGE,
        Permission.ASSIGNMENT_DISPATCH,
        Permission.ASSIGNMENT_UPDATE,
        Permission.ASSIGNMENT_READ,
        Permission.RESPONDER_READ,
        Permission.RESPONDER_TELEMETRY,
        Permission.TEAM_READ,
        Permission.DEVICE_TELEMETRY,
        Permission.DEVICE_SOS,
    },
    Role.RESPONDER.value: {
        Permission.INCIDENT_READ,
        Permission.ASSIGNMENT_READ,
        Permission.ASSIGNMENT_UPDATE_SELF,
        Permission.RESPONDER_UPDATE_SELF,
        Permission.RESPONDER_TELEMETRY,
        Permission.TEAM_READ,
        Permission.DEVICE_TELEMETRY,
        Permission.DEVICE_SOS,
    },
    Role.VIEWER.value: {
        Permission.INCIDENT_READ,
        Permission.ASSIGNMENT_READ,
        Permission.RESPONDER_READ,
        Permission.TEAM_READ,
    },
    Role.DEVICE.value: {
        Permission.DEVICE_ENROLL,
        Permission.DEVICE_TELEMETRY,
        Permission.DEVICE_SOS,
    },
}

# Secret key for JWT signing (defaults to secure constant if not provided in env)
JWT_SECRET = secrets.token_hex(32)


@dataclass
class AuthenticatedActor:
    actor_id: str
    username: str
    role: str
    permissions: Set[Permission]
    organization_id: str = "org-default"
    user_id: Optional[int] = None
    device_id: Optional[str] = None
    responder_id: Optional[str] = None

    def has_permission(self, permission: Permission) -> bool:
        return permission in self.permissions


def create_access_token(
    user_id: int,
    username: str,
    role: str,
    token_version: int,
    organization_id: str = "org-default",
    expires_in_sec: int = 86400,
) -> str:
    """Create a tamper-evident HS256 signed access token without external dependencies."""
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": str(user_id),
        "username": username,
        "role": role,
        "org_id": organization_id,
        "ver": token_version,
        "iat": now,
        "exp": now + expires_in_sec,
    }
    header_b64 = base64.urlsafe_b64encode(json.dumps(header, separators=(",", ":")).encode("utf-8")).rstrip(b"=").decode("utf-8")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8")).rstrip(b"=").decode("utf-8")
    signing_input = f"{header_b64}.{payload_b64}".encode("utf-8")
    signature = hmac.new(JWT_SECRET.encode("utf-8"), signing_input, hashlib.sha256).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).rstrip(b"=").decode("utf-8")
    return f"{header_b64}.{payload_b64}.{sig_b64}"


def decode_access_token(token: str) -> dict:
    """Verify and decode HS256 signed access token."""
    parts = token.split(".")
    if len(parts) != 3:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Malformed authorization token")
    header_b64, payload_b64, sig_b64 = parts
    signing_input = f"{header_b64}.{payload_b64}".encode("utf-8")
    expected_sig = hmac.new(JWT_SECRET.encode("utf-8"), signing_input, hashlib.sha256).digest()
    
    # Pad sig_b64 to multiple of 4
    rem = len(sig_b64) % 4
    padded_sig = sig_b64 + ("=" * (4 - rem) if rem else "")
    try:
        given_sig = base64.urlsafe_b64decode(padded_sig.encode("utf-8"))
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token signature encoding")

    if not hmac.compare_digest(expected_sig, given_sig):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token signature")

    rem = len(payload_b64) % 4
    padded_payload = payload_b64 + ("=" * (4 - rem) if rem else "")
    try:
        payload = json.loads(base64.urlsafe_b64decode(padded_payload.encode("utf-8")).decode("utf-8"))
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token payload JSON")

    now = int(time.time())
    if payload.get("exp", 0) < now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has expired")

    return payload


async def get_current_actor(
    request: Request,
    authorization: Optional[str] = Header(None),
    x_operator_token: Optional[str] = Header(None),
    x_gateway_key: Optional[str] = Header(None),
    x_organization_id: Optional[str] = Header(None),
    x_device_id: Optional[str] = Header(None),
    x_device_timestamp: Optional[str] = Header(None),
    x_device_signature: Optional[str] = Header(None),
    db: Session = Depends(get_db_session),
) -> AuthenticatedActor:
    """
    Authenticate incoming request across supported actor channels:
    1. Signed JWT User Session (Header Authorization: Bearer <jwt>)
    2. ECDSA Challenge-Response Device Authentication (X-Device-Id, X-Device-Timestamp, X-Device-Signature)
    3. Legacy Operator Token (Authorization: Bearer <op_token> or X-Operator-Token)
    4. Legacy Gateway Key (Authorization: Bearer <gw_key> or X-Gateway-Key)
    """
    # ── Channel 1 & 3: Bearer or Custom Operator Token ──────────────────────────
    token = None
    if authorization:
        parts = authorization.split()
        if len(parts) == 2 and parts[0].lower() == "bearer":
            token = parts[1].strip()
        else:
            token = authorization.strip()
    elif x_operator_token:
        token = x_operator_token.strip()

    if token:
        if token in REVOKED_CREDENTIALS:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Token credential has been revoked")

        # Case A: Signed JWT
        if token.count(".") == 2:
            payload = decode_access_token(token)
            user_id = int(payload["sub"])
            user = db.query(UserModel).filter(UserModel.id == user_id).first()
            if not user or not user.is_active or user.status != "active":
                raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account is inactive or disabled")
            if user.token_version != payload.get("ver", 1):
                raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has been invalidated by newer session")

            role_val = user.role.lower()
            perms = ROLE_PERMISSIONS.get(role_val, set())
            org_id = payload.get("org_id") or getattr(user, "organization_id", "org-default")
            return AuthenticatedActor(
                actor_id=f"user:{user.id}",
                username=user.username,
                role=role_val,
                permissions=perms,
                organization_id=org_id,
                user_id=user.id,
            )

        # Case B: Legacy Operator Token (bootstrap / dev / test)
        for valid_token in OPERATOR_TOKENS:
            if secrets.compare_digest(token, valid_token):
                role_val = Role.COMMANDER.value
                org_id = x_organization_id or "org-default"
                return AuthenticatedActor(
                    actor_id=f"operator:{token[:8]}",
                    username="operator",
                    role=role_val,
                    permissions=ROLE_PERMISSIONS[role_val],
                    organization_id=org_id,
                )

    # ── Channel 2: ECDSA Device Signature Authentication ────────────────────────
    if x_device_id and x_device_timestamp and x_device_signature:
        from app.core.device_auth import verify_device_signature
        body_bytes = await request.body()
        device = verify_device_signature(
            device_id=x_device_id,
            timestamp_str=x_device_timestamp,
            signature_b64=x_device_signature,
            method=request.method,
            path=request.url.path,
            body_bytes=body_bytes,
            db=db,
        )
        perms = ROLE_PERMISSIONS[Role.DEVICE.value]
        org_id = getattr(device, "organization_id", "org-default")
        return AuthenticatedActor(
            actor_id=f"device:{device.device_id}",
            username=f"node_{device.device_id[:8]}",
            role=Role.DEVICE.value,
            permissions=perms,
            organization_id=org_id,
            device_id=device.device_id,
        )

    # ── Channel 4: Legacy Gateway API Key ───────────────────────────────────────
    gw_key = x_gateway_key or token
    if gw_key:
        if gw_key in REVOKED_CREDENTIALS:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Gateway credential has been revoked")

        for valid_gw in GATEWAY_API_KEYS:
            if secrets.compare_digest(gw_key, valid_gw):
                org_id = x_organization_id or "org-default"
                return AuthenticatedActor(
                    actor_id="gateway:legacy",
                    username="mesh_gateway",
                    role=Role.DEVICE.value,
                    permissions=ROLE_PERMISSIONS[Role.DEVICE.value],
                    organization_id=org_id,
                )

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication credentials required")


def require_permission(permission: Permission) -> Callable:
    """Dependency factory checking that the authenticated actor possesses a specific capability."""
    def _dependency(actor: AuthenticatedActor = Depends(get_current_actor)) -> AuthenticatedActor:
        if not actor.has_permission(permission):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Insufficient permissions: '{permission.value}' required for role '{actor.role}'",
            )
        return actor
    return _dependency
