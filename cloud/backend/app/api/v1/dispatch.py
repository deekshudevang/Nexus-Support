"""Dispatch, Responders, Teams, and Assignment Workflow Endpoints."""

import time
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.core.database import get_db_session
from app.core.rbac import (
    AuthenticatedActor,
    get_current_actor,
    require_permission,
    Permission,
)
from app.models.assignment import AssignmentModel
from app.models.responder import ResponderModel
from app.models.team import TeamModel
from app.services import dispatch_service

router = APIRouter(tags=["Dispatch & Responders"])


class ResponderCreateRequest(BaseModel):
    callsign: str = Field(..., min_length=2, max_length=64)
    responder_type: str = Field("SAR", max_length=64)
    skills: List[str] = []
    user_id: Optional[int] = None
    device_id: Optional[str] = None


class ResponderResponse(BaseModel):
    id: str
    callsign: str
    responder_type: str
    skills: List[str]
    team_id: Optional[str] = None
    availability: str
    last_latitude: Optional[float] = None
    last_longitude: Optional[float] = None
    device_id: Optional[str] = None
    created_at: int


class TeamCreateRequest(BaseModel):
    name: str = Field(..., min_length=2, max_length=128)
    callsign: str = Field(..., min_length=2, max_length=64)
    leader_id: Optional[str] = None


class TeamResponse(BaseModel):
    id: str
    name: str
    callsign: str
    leader_id: Optional[str] = None
    status: str
    created_at: int


class AddMemberRequest(BaseModel):
    responder_id: str
    role_in_team: str = "MEMBER"


class DispatchRequest(BaseModel):
    responder_id: str
    team_id: Optional[str] = None


class AssignmentTransitionRequest(BaseModel):
    new_status: str
    reason: Optional[str] = None


class AssignmentResponse(BaseModel):
    id: str
    incident_id: str
    responder_id: str
    team_id: Optional[str] = None
    status: str
    offered_at: int
    responded_at: Optional[int] = None
    completed_at: Optional[int] = None


# ── Responder Endpoints ──────────────────────────────────────────────────────

@router.post("/responders", response_model=ResponderResponse, status_code=status.HTTP_201_CREATED)
def create_responder_endpoint(
    req: ResponderCreateRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.RESPONDER_MANAGE)),
    db: Session = Depends(get_db_session),
):
    """Enroll a new certified responder into operational roster."""
    return dispatch_service.create_responder(
        db=db,
        callsign=req.callsign,
        responder_type=req.responder_type,
        skills=req.skills,
        user_id=req.user_id,
        device_id=req.device_id,
    )


@router.get("/responders", response_model=List[ResponderResponse])
def list_responders_endpoint(
    availability: Optional[str] = None,
    responder_type: Optional[str] = None,
    actor: AuthenticatedActor = Depends(require_permission(Permission.RESPONDER_READ)),
    db: Session = Depends(get_db_session),
):
    """List operational responders with availability filtering."""
    query = db.query(ResponderModel)
    if availability:
        query = query.filter(ResponderModel.availability == availability.upper().strip())
    if responder_type:
        query = query.filter(ResponderModel.responder_type == responder_type.upper().strip())
    return query.all()


@router.get("/responders/{responder_id}", response_model=ResponderResponse)
def get_responder_endpoint(
    responder_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.RESPONDER_READ)),
    db: Session = Depends(get_db_session),
):
    """Retrieve responder profile and telemetry."""
    responder = db.query(ResponderModel).filter(ResponderModel.id == responder_id).first()
    if not responder:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Responder not found")
    return responder


# ── Team Endpoints ───────────────────────────────────────────────────────────

@router.post("/teams", response_model=TeamResponse, status_code=status.HTTP_201_CREATED)
def create_team_endpoint(
    req: TeamCreateRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.TEAM_MANAGE)),
    db: Session = Depends(get_db_session),
):
    """Create a tactical search & rescue or medical team."""
    return dispatch_service.create_team(
        db=db,
        name=req.name,
        callsign=req.callsign,
        leader_id=req.leader_id,
    )


@router.get("/teams", response_model=List[TeamResponse])
def list_teams_endpoint(
    actor: AuthenticatedActor = Depends(require_permission(Permission.TEAM_READ)),
    db: Session = Depends(get_db_session),
):
    """List active operational teams."""
    return db.query(TeamModel).all()


@router.post("/teams/{team_id}/members")
def add_team_member_endpoint(
    team_id: str,
    req: AddMemberRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.TEAM_MANAGE)),
    db: Session = Depends(get_db_session),
):
    """Assign responder to team roster."""
    member = dispatch_service.add_team_member(
        db=db,
        team_id=team_id,
        responder_id=req.responder_id,
    )
    return {"status": "SUCCESS", "membership_id": member.id}


# ── Dispatch & Assignment Endpoints ──────────────────────────────────────────

@router.post("/incidents/{incident_id}/assignments", response_model=AssignmentResponse, status_code=status.HTTP_201_CREATED)
def dispatch_incident_endpoint(
    incident_id: str,
    req: DispatchRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.ASSIGNMENT_DISPATCH)),
    db: Session = Depends(get_db_session),
):
    """Dispatch responder to an incident with concurrency conflict protection."""
    return dispatch_service.dispatch_responder(
        db=db,
        incident_id=incident_id,
        responder_id=req.responder_id,
        team_id=req.team_id,
        actor_id=actor.actor_id,
    )


@router.get("/incidents/{incident_id}/assignments", response_model=List[AssignmentResponse])
def list_incident_assignments_endpoint(
    incident_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.ASSIGNMENT_READ)),
    db: Session = Depends(get_db_session),
):
    """List all assignments associated with an incident."""
    return db.query(AssignmentModel).filter(AssignmentModel.incident_id == incident_id).all()


@router.post("/assignments/{assignment_id}/transition", response_model=AssignmentResponse)
def transition_assignment_endpoint(
    assignment_id: str,
    req: AssignmentTransitionRequest,
    actor: AuthenticatedActor = Depends(get_current_actor),
    db: Session = Depends(get_db_session),
):
    """Advance assignment status through the operational workflow."""
    return dispatch_service.transition_assignment(
        db=db,
        assignment_id=assignment_id,
        new_status=req.new_status,
        actor=actor,
        reason=req.reason,
    )
