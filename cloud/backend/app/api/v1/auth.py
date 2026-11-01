"""Authentication and session management endpoints."""

import time
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.core.database import get_db_session
from app.core.rbac import (
    AuthenticatedActor,
    create_access_token,
    get_current_actor,
    require_permission,
    Permission,
)
from app.core.security import generate_ws_ticket, verify_password
from app.models.user import UserModel

router = APIRouter(prefix="/auth", tags=["Authentication"])


class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    token: str
    token_type: str = "Bearer"
    role: str
    username: str
    user_id: Optional[int] = None
    expires_in: int = 86400


class MeResponse(BaseModel):
    actor_id: str
    username: str
    role: str
    permissions: list[str]
    user_id: Optional[int] = None
    device_id: Optional[str] = None


class WsTicketResponse(BaseModel):
    ticket: str
    expires_in: int = 30


@router.post("/login", response_model=LoginResponse)
def login(req: LoginRequest, db: Session = Depends(get_db_session)):
    """Authenticate user with username/password or bootstrap operator credentials."""
    user = db.query(UserModel).filter(UserModel.username == req.username).first()

    if user:
        if not user.is_active or user.status != "active":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account is inactive")
        if not verify_password(req.password, user.password_hash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

        # Update last login
        user.last_login = int(time.time())
        db.commit()

        token = create_access_token(
            user_id=user.id,
            username=user.username,
            role=user.role,
            token_version=user.token_version,
            expires_in_sec=86400,
        )
        return LoginResponse(
            token=token,
            token_type="Bearer",
            role=user.role,
            username=user.username,
            user_id=user.id,
            expires_in=86400,
        )

    # Check for legacy operator bootstrap token matching username "operator"
    if req.username in ("operator", "admin") and verify_password(req.password, ""):
        # Match legacy operator credentials
        token = create_access_token(
            user_id=0,
            username=req.username,
            role="commander",
            token_version=1,
            expires_in_sec=86400,
        )
        return LoginResponse(
            token=token,
            token_type="Bearer",
            role="commander",
            username=req.username,
            user_id=0,
            expires_in=86400,
        )

    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")


@router.post("/logout")
def logout(
    actor: AuthenticatedActor = Depends(get_current_actor),
    db: Session = Depends(get_db_session),
):
    """Invalidate session by bumping user token version."""
    if actor.user_id and actor.user_id > 0:
        user = db.query(UserModel).filter(UserModel.id == actor.user_id).first()
        if user:
            user.token_version += 1
            db.commit()
    return {"status": "SUCCESS", "message": "Logged out and tokens invalidated"}


@router.get("/me", response_model=MeResponse)
def get_current_user_profile(actor: AuthenticatedActor = Depends(get_current_actor)):
    """Return identity and granted capabilities of the current actor."""
    return MeResponse(
        actor_id=actor.actor_id,
        username=actor.username,
        role=actor.role,
        permissions=[p.value for p in actor.permissions],
        user_id=actor.user_id,
        device_id=actor.device_id,
    )


@router.post("/ws-ticket", response_model=WsTicketResponse)
def issue_ws_ticket(
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_READ)),
):
    """Generate single-use, 30-second ticket for authenticating WebSocket connections."""
    ticket = generate_ws_ticket()
    return WsTicketResponse(ticket=ticket, expires_in=30)
