"""Device Keystore & Node Management API endpoints."""

import time
from typing import List, Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.database import get_db_session
from app.core.rbac import (
    AuthenticatedActor,
    ROLE_PERMISSIONS,
    decode_access_token,
    get_current_actor,
    require_permission,
    Permission,
)
from app.core.config import OPERATOR_TOKENS, REVOKED_CREDENTIALS
from app.models.device import DeviceModel
from app.models.user import UserModel
import secrets

router = APIRouter(prefix="/devices", tags=["Devices"])


class DeviceEnrollRequest(BaseModel):
    device_id: str = Field(..., min_length=4, max_length=128)
    public_key_pem: str
    role: str = Field("field_node", max_length=32)
    enrollment_token_id: Optional[str] = None


class DeviceResponse(BaseModel):
    id: str
    device_id: str
    role: str
    status: str
    last_seen_at: Optional[int] = None
    created_at: int


class DeviceHeartbeatRequest(BaseModel):
    battery_level: Optional[int] = None
    rssi: Optional[int] = None
    active_mesh_peers: Optional[int] = None


def _authorize_device_key_rotation(
    authorization: Optional[str],
    x_operator_token: Optional[str],
    db: Session,
) -> None:
    """Require operator/admin authorization before rotating or reactivating an already-enrolled device."""
    cred = None
    if x_operator_token:
        cred = x_operator_token.strip()
    elif authorization:
        parts = authorization.split()
        cred = parts[1].strip() if len(parts) == 2 and parts[0].lower() == "bearer" else authorization.strip()

    if not cred:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization required to update an already-enrolled device key",
        )
    if cred in REVOKED_CREDENTIALS:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Credential has been revoked")

    if cred.count(".") == 2:
        payload = decode_access_token(cred)
        user = db.query(UserModel).filter(UserModel.id == int(payload["sub"])).first()
        if not user or not user.is_active or user.status != "active":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Inactive user account")
        perms = ROLE_PERMISSIONS.get(user.role.lower(), set())
        if Permission.RESPONDER_MANAGE not in perms and Permission.DEVICE_ENROLL not in perms:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Insufficient permissions to rotate enrolled device key",
            )
        return

    if any(secrets.compare_digest(cred, op) for op in OPERATOR_TOKENS):
        return

    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not authorized to update enrolled device key")


@router.post("/enroll", response_model=DeviceResponse)
def enroll_device(
    req: DeviceEnrollRequest,
    authorization: Optional[str] = Header(None),
    x_operator_token: Optional[str] = Header(None),
    db: Session = Depends(get_db_session),
):
    """
    Enroll a field node or mesh gateway device by registering its P-256 public key.
    First-time enrollment registers a new device; rotating an existing device's key requires authorization.
    """
    try:
        pubkey = serialization.load_pem_public_key(req.public_key_pem.encode("utf-8"))
        if not isinstance(pubkey, ec.EllipticCurvePublicKey):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Public key must be an Elliptic Curve (P-256) public key",
            )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid PEM public key format: {str(e)}",
        )

    now = int(time.time())
    device = db.query(DeviceModel).filter(DeviceModel.device_id == req.device_id).first()

    if device:
        if device.public_key_pem != req.public_key_pem or device.status.upper() != "ACTIVE":
            _authorize_device_key_rotation(authorization, x_operator_token, db)
            device.token_version += 1
        # Update public key & reactivate under authorized operator/admin call (or idempotent active re-sync)
        device.public_key_pem = req.public_key_pem
        device.role = req.role
        device.status = "ACTIVE"
        device.last_seen_at = now
    else:
        device = DeviceModel(
            id=f"dev_{req.device_id}",
            device_id=req.device_id,
            public_key_pem=req.public_key_pem,
            enrollment_token_id=req.enrollment_token_id,
            role=req.role,
            status="ACTIVE",
            last_seen_at=now,
            token_version=1,
            created_at=now,
        )
        db.add(device)

    db.commit()
    db.refresh(device)
    return DeviceResponse(
        id=device.id,
        device_id=device.device_id,
        role=device.role,
        status=device.status,
        last_seen_at=device.last_seen_at,
        created_at=device.created_at,
    )


@router.get("", response_model=List[DeviceResponse])
def list_devices(
    actor: AuthenticatedActor = Depends(require_permission(Permission.TEAM_READ)),
    db: Session = Depends(get_db_session),
):
    """List all registered devices in the system."""
    devices = db.query(DeviceModel).order_by(DeviceModel.created_at.desc()).all()
    return [
        DeviceResponse(
            id=d.id,
            device_id=d.device_id,
            role=d.role,
            status=d.status,
            last_seen_at=d.last_seen_at,
            created_at=d.created_at,
        )
        for d in devices
    ]


@router.get("/{device_id}", response_model=DeviceResponse)
def get_device(
    device_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.TEAM_READ)),
    db: Session = Depends(get_db_session),
):
    """Retrieve details for a single enrolled device."""
    device = db.query(DeviceModel).filter(DeviceModel.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    return DeviceResponse(
        id=device.id,
        device_id=device.device_id,
        role=device.role,
        status=device.status,
        last_seen_at=device.last_seen_at,
        created_at=device.created_at,
    )


@router.delete("/{device_id}")
def revoke_device(
    device_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.RESPONDER_MANAGE)),
    db: Session = Depends(get_db_session),
):
    """Revoke a device, immediately blocking all further signed requests."""
    device = db.query(DeviceModel).filter(DeviceModel.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    device.status = "REVOKED"
    device.token_version += 1
    db.commit()
    return {"status": "SUCCESS", "message": f"Device '{device_id}' has been revoked"}


@router.post("/{device_id}/heartbeat")
def device_heartbeat(
    device_id: str,
    req: DeviceHeartbeatRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.DEVICE_TELEMETRY)),
    db: Session = Depends(get_db_session),
):
    """Heartbeat and RF telemetry submission from an authenticated field device."""
    # IDOR Check: If the actor is a device, it can only send telemetry for its own device_id!
    if actor.device_id and actor.device_id != device_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Device IDOR violation: cannot submit telemetry for device '{device_id}'",
        )

    device = db.query(DeviceModel).filter(DeviceModel.device_id == device_id).first()
    if not device:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

    device.last_seen_at = int(time.time())
    db.commit()
    return {"status": "SUCCESS", "device_id": device_id, "timestamp": device.last_seen_at}
