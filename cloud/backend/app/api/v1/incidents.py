"""Incident Management and Triage API endpoints."""

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
from app.models.incident import IncidentModel
from app.models.incident_event import IncidentEventModel
from app.models.incident_link import IncidentLinkModel
from app.services import incident_service

router = APIRouter(prefix="/incidents", tags=["Incidents"])


class IncidentCreateRequest(BaseModel):
    title: str = Field(..., min_length=3, max_length=256)
    category: str = Field(..., max_length=64)
    priority: int = Field(3, ge=1, le=5)
    severity: int = Field(3, ge=1, le=5)
    description: str = Field(..., min_length=1)
    source: str = Field("MANUAL", max_length=64)
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_accuracy_m: Optional[float] = None
    location_description: Optional[str] = None
    reporter_name: Optional[str] = None
    reporter_contact: Optional[str] = None
    client_incident_id: Optional[str] = None


class IncidentTransitionRequest(BaseModel):
    new_status: str = Field(..., min_length=3, max_length=32)
    notes: Optional[str] = None


class IncidentLinkRequest(BaseModel):
    target_incident_id: str
    link_type: str = "DUPLICATE"
    reason: Optional[str] = None
    confidence: float = 1.0


class IncidentResponse(BaseModel):
    id: str
    incident_number: Optional[int] = None
    title: str
    description: str
    category: str
    priority: int
    severity: int
    status: str
    organization_id: str = "org-default"
    source: str
    reporter_name: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_accuracy_m: Optional[float] = None
    location_description: Optional[str] = None
    commander_id: Optional[str] = None
    version: int
    created_at: int
    updated_at: int
    resolved_at: Optional[int] = None


class IncidentDetailResponse(IncidentResponse):
    links: List[dict] = []
    recent_events: List[dict] = []


@router.post("", response_model=IncidentResponse, status_code=status.HTTP_201_CREATED)
def create_incident_endpoint(
    req: IncidentCreateRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_CREATE)),
    db: Session = Depends(get_db_session),
):
    """Create a new operational incident with validation and automated duplicate linking."""
    inc = incident_service.create_incident(
        db=db,
        title=req.title,
        category=req.category,
        priority=req.priority,
        severity=req.severity,
        description=req.description,
        source=req.source,
        actor_id=actor.actor_id,
        organization_id=actor.organization_id,
        latitude=req.latitude,
        longitude=req.longitude,
        location_accuracy_m=req.location_accuracy_m,
        location_description=req.location_description,
        reporter_name=req.reporter_name,
        reporter_contact=req.reporter_contact,
        client_incident_id=req.client_incident_id,
    )
    return inc


@router.get("", response_model=List[IncidentResponse])
def list_incidents_endpoint(
    status_filter: Optional[str] = Query(None, alias="status"),
    category: Optional[str] = None,
    priority: Optional[int] = None,
    min_severity: Optional[int] = None,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_READ)),
    db: Session = Depends(get_db_session),
):
    """List operational incidents with situational filtering and tenant isolation."""
    query = db.query(IncidentModel).filter(IncidentModel.organization_id == actor.organization_id)

    if status_filter:
        statuses = [s.strip().upper() for s in status_filter.split(",") if s.strip()]
        query = query.filter(IncidentModel.status.in_(statuses))
    if category:
        query = query.filter(IncidentModel.category == category.upper().strip())
    if priority:
        query = query.filter(IncidentModel.priority == priority)
    if min_severity:
        query = query.filter(IncidentModel.severity >= min_severity)

    incidents = query.order_by(IncidentModel.created_at.desc()).offset(offset).limit(limit).all()
    return incidents


@router.get("/{incident_id}", response_model=IncidentDetailResponse)
def get_incident_endpoint(
    incident_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_READ)),
    db: Session = Depends(get_db_session),
):
    """Retrieve full incident details with strict tenant isolation."""
    inc = (
        db.query(IncidentModel)
        .filter(IncidentModel.id == incident_id, IncidentModel.organization_id == actor.organization_id)
        .first()
    )
    if not inc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Incident not found")

    links = (
        db.query(IncidentLinkModel)
        .filter(
            (IncidentLinkModel.incident_id == incident_id)
            | (IncidentLinkModel.target_incident_id == incident_id)
        )
        .all()
    )
    events = (
        db.query(IncidentEventModel)
        .filter(IncidentEventModel.incident_id == incident_id)
        .order_by(IncidentEventModel.created_at.desc())
        .limit(20)
        .all()
    )

    data = {c.name: getattr(inc, c.name) for c in inc.__table__.columns}
    data["links"] = [
        {
            "id": l.id,
            "incident_id": l.incident_id,
            "target_incident_id": l.target_incident_id,
            "link_type": l.link_type,
            "reason": l.reason,
            "reviewer_id": l.reviewer_id,
            "created_at": l.created_at,
        }
        for l in links
    ]
    data["recent_events"] = [
        {
            "id": e.id,
            "event_type": e.event_type,
            "actor_id": e.actor_id,
            "from_status": e.from_status,
            "to_status": e.to_status,
            "reason": e.reason,
            "payload": e.payload,
            "created_at": e.created_at,
        }
        for e in events
    ]
    return data


@router.post("/{incident_id}/transition", response_model=IncidentResponse)
def transition_incident_endpoint(
    incident_id: str,
    req: IncidentTransitionRequest,
    actor: AuthenticatedActor = Depends(get_current_actor),
    db: Session = Depends(get_db_session),
):
    """Transition an incident state machine with role-based checks."""
    target_status = req.new_status.upper().strip()

    # Closing or cancelling requires INCIDENT_CLOSE permission
    if target_status in ("CLOSED", "CANCELLED"):
        if not actor.has_permission(Permission.INCIDENT_CLOSE):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Permission '{Permission.INCIDENT_CLOSE.value}' required to close or cancel an incident",
            )
    else:
        if not actor.has_permission(Permission.INCIDENT_TRIAGE):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"Permission '{Permission.INCIDENT_TRIAGE.value}' required to transition incident status",
            )

    return incident_service.transition_incident(
        db=db,
        incident_id=incident_id,
        new_status=target_status,
        actor_id=actor.actor_id,
        notes=req.notes,
        organization_id=actor.organization_id,
    )


@router.post("/{incident_id}/links")
def link_incidents_endpoint(
    incident_id: str,
    req: IncidentLinkRequest,
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_TRIAGE)),
    db: Session = Depends(get_db_session),
):
    """Manually link related or duplicate incidents within the same organization."""
    inc1 = db.query(IncidentModel).filter(IncidentModel.id == incident_id, IncidentModel.organization_id == actor.organization_id).first()
    inc2 = db.query(IncidentModel).filter(IncidentModel.id == req.target_incident_id, IncidentModel.organization_id == actor.organization_id).first()
    if not inc1 or not inc2:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Incident not found in your organization")

    link = incident_service.link_incidents(
        db=db,
        incident_id=incident_id,
        target_incident_id=req.target_incident_id,
        link_type=req.link_type,
        actor_id=actor.actor_id,
        confidence=req.confidence,
        reason=req.reason,
    )
    return {
        "status": "SUCCESS",
        "link_id": link.id,
        "incident_id": link.incident_id,
        "target_incident_id": link.target_incident_id,
        "link_type": link.link_type,
    }


@router.get("/{incident_id}/timeline")
def get_incident_timeline_endpoint(
    incident_id: str,
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_READ)),
    db: Session = Depends(get_db_session),
):
    """Retrieve full append-only audit timeline for an incident within the organization."""
    inc = db.query(IncidentModel).filter(IncidentModel.id == incident_id, IncidentModel.organization_id == actor.organization_id).first()
    if not inc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Incident not found")

    events = (
        db.query(IncidentEventModel)
        .filter(IncidentEventModel.incident_id == incident_id)
        .order_by(IncidentEventModel.created_at.asc())
        .all()
    )
    return [
        {
            "id": e.id,
            "event_type": e.event_type,
            "actor_id": e.actor_id,
            "from_status": e.from_status,
            "to_status": e.to_status,
            "reason": e.reason,
            "payload": e.payload,
            "created_at": e.created_at,
        }
        for e in events
    ]
