"""Device Keystore & Node Management API endpoints."""

import time
from typing import List, Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.database import get_db_session
from app.core.rbac import (
    AuthenticatedActor,
    get_current_actor,
    require_permission,
    Permission,
)
from app.models.device import DeviceModel

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


@router.post("/enroll", response_model=DeviceResponse)
def enroll_device(
    req: DeviceEnrollRequest,
    db: Session = Depends(get_db_session),
):
    """
    Enroll a field node or mesh gateway device by registering its P-256 public key.
    Validates EC public key cryptographic format.
    """
    try:
        pubkey = serialization.load_pem_public_key(req.public_key_pem.encode("utf-8"))
        if not isinstance(pubkey, ec.EllipticCurvePublicKey):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Public key must be an Elliptic Curve (P-256) public key",
            )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid PEM public key format: {str(e)}",
        )

    now = int(time.time())
    device = db.query(DeviceModel).filter(DeviceModel.device_id == req.device_id).first()

    if device:
        # Update public key & reactivate
        device.public_key_pem = req.public_key_pem
        device.role = req.role
        device.status = "ACTIVE"
        device.token_version += 1
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
