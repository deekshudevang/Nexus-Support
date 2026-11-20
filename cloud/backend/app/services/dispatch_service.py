"""Dispatch Operations, Responder Presence, and Concurrency-Safe Assignments."""

import time
import uuid
import logging
from typing import List, Optional

logger = logging.getLogger("rescuemesh.dispatch")

from fastapi import HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.rbac import AuthenticatedActor, Permission
from app.models.assignment import AssignmentModel
from app.models.incident import IncidentModel
from app.models.incident_event import IncidentEventModel
from app.models.outbox_event import OutboxEventModel
from app.models.responder import ResponderModel
from app.models.team import TeamModel
from app.models.team_member import TeamMemberModel
from app.services.outbox_publisher import process_outbox_batch

ACTIVE_ASSIGNMENT_STATUSES = {"DISPATCHED", "ACCEPTED", "EN_ROUTE", "ON_SCENE"}
TERMINAL_ASSIGNMENT_STATUSES = {"COMPLETED", "DECLINED", "CANCELLED", "EXPIRED"}

ALLOWED_ASSIGNMENT_TRANSITIONS = {
    "PROPOSED": {"DISPATCHED", "CANCELLED"},
    "DISPATCHED": {"ACCEPTED", "DECLINED", "CANCELLED"},
    "ACCEPTED": {"EN_ROUTE", "CANCELLED"},
    "EN_ROUTE": {"ON_SCENE", "CANCELLED"},
    "ON_SCENE": {"COMPLETED", "CANCELLED"},
    "COMPLETED": set(),
    "DECLINED": set(),
    "CANCELLED": set(),
    "EXPIRED": set(),
}


def create_responder(
    db: Session,
    callsign: str,
    responder_type: str = "SAR",
    skills: Optional[List[str]] = None,
    user_id: Optional[int] = None,
    device_id: Optional[str] = None,
    organization_id: str = "org-default",
) -> ResponderModel:
    now = int(time.time())
    resp_id = f"resp_{uuid.uuid4().hex[:12]}"
    responder = ResponderModel(
        id=resp_id,
        callsign=callsign,
        responder_type=responder_type,
        skills=skills or [],
        user_id=user_id,
        device_id=device_id,
        organization_id=organization_id,
        availability="AVAILABLE",
        created_at=now,
    )
    db.add(responder)
    db.commit()
    db.refresh(responder)
    return responder


def create_team(
    db: Session,
    name: str,
    callsign: str,
    leader_id: Optional[str] = None,
    organization_id: str = "org-default",
) -> TeamModel:
    now = int(time.time())
    team = TeamModel(
        id=f"team_{uuid.uuid4().hex[:12]}",
        name=name,
        callsign=callsign,
        leader_id=leader_id,
        organization_id=organization_id,
        status="active",
        created_at=now,
    )
    db.add(team)
    db.commit()
    db.refresh(team)
    return team


def add_team_member(
    db: Session,
    team_id: str,
    responder_id: str,
) -> TeamMemberModel:
    now = int(time.time())
    member = TeamMemberModel(
        team_id=team_id,
        responder_id=responder_id,
        joined_at=now,
    )
    db.add(member)
    db.commit()
    db.refresh(member)
    return member


def dispatch_responder(
    db: Session,
    incident_id: str,
    responder_id: str,
    team_id: Optional[str] = None,
    actor_id: str = "dispatcher",
    organization_id: Optional[str] = None,
) -> AssignmentModel:
    """
    Dispatch a responder to an incident with strict partial-unique concurrency protection
    and multi-tenant organization boundary verification.
    """
    incident = db.query(IncidentModel).filter(IncidentModel.id == incident_id).first()
    if not incident or (organization_id is not None and incident.organization_id != organization_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Incident not found")
    if incident.status in ("CLOSED", "CANCELLED"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Cannot dispatch to an incident in '{incident.status}' state",
        )

    responder = db.query(ResponderModel).filter(ResponderModel.id == responder_id).first()
    if not responder or (organization_id is not None and responder.organization_id != organization_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Responder not found")

    if incident.organization_id != responder.organization_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Cross-organization dispatch forbidden: incident org '{incident.organization_id}' != responder org '{responder.organization_id}'",
        )

    # Application-level check
    active_assignment = (
        db.query(AssignmentModel)
        .filter(
            AssignmentModel.responder_id == responder_id,
            AssignmentModel.status.in_(ACTIVE_ASSIGNMENT_STATUSES),
        )
        .first()
    )
    if active_assignment:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Responder '{responder.callsign}' already has an active assignment ({active_assignment.id} in state '{active_assignment.status}')",
        )

    now = int(time.time())
    asgn_id = f"asgn_{uuid.uuid4().hex[:12]}"
    assignment = AssignmentModel(
        id=asgn_id,
        incident_id=incident_id,
        responder_id=responder_id,
        team_id=team_id,
        organization_id=incident.organization_id,
        status="DISPATCHED",
        offered_at=now,
    )
    db.add(assignment)

    responder.availability = "ASSIGNED"

    # Incident timeline event
    db.add(
        IncidentEventModel(
            incident_id=incident_id,
            event_type="ASSIGNMENT_CREATED",
            from_status=None,
            to_status="DISPATCHED",
            actor_id=actor_id,
            reason=f"Dispatched responder {responder.callsign}",
            payload={"assignment_id": asgn_id, "responder_id": responder_id, "callsign": responder.callsign},
            created_at=now,
        )
    )

    # Outbox event
    db.add(
        OutboxEventModel(
            event_id=f"evt_{uuid.uuid4().hex[:16]}",
            aggregate_type="ASSIGNMENT",
            aggregate_id=asgn_id,
            aggregate_version=1,
            event_type="assignment.dispatched",
            payload={
                "assignment_id": asgn_id,
                "incident_id": incident_id,
                "responder_id": responder_id,
                "callsign": responder.callsign,
                "status": "DISPATCHED",
                "actor_id": actor_id,
                "created_at": now,
            },
            created_at=now,
        )
    )

    try:
        db.commit()
    except IntegrityError as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Concurrent dispatch collision: responder '{responder.callsign}' was simultaneously assigned",
        )

    db.refresh(assignment)
    logger.info("DEMO FLOW: Assignment created for incident %s to responder %s (Assignment: %s)", incident_id, responder_id, asgn_id)
    process_outbox_batch(db)
    return assignment


def transition_assignment(
    db: Session,
    assignment_id: str,
    new_status: str,
    actor: AuthenticatedActor,
    reason: Optional[str] = None,
) -> AssignmentModel:
    """Transition assignment state with strict IDOR, multi-tenant boundaries, and capability checks."""
    assignment = db.query(AssignmentModel).filter(AssignmentModel.id == assignment_id).first()
    if not assignment:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assignment not found")

    if hasattr(actor, "organization_id") and actor.organization_id:
        if assignment.organization_id != actor.organization_id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Assignment not found")

    target_status = new_status.upper().strip()
    current_status = assignment.status.upper().strip()

    if target_status == current_status:
        return assignment

    valid_targets = ALLOWED_ASSIGNMENT_TRANSITIONS.get(current_status, set())
    if target_status not in valid_targets:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Illegal assignment transition from '{current_status}' to '{target_status}'",
        )

    # IDOR and permission check
    responder = db.query(ResponderModel).filter(ResponderModel.id == assignment.responder_id).first()
    if not actor.has_permission(Permission.ASSIGNMENT_UPDATE):
        # Must be the assigned responder themselves
        is_owner = False
        if actor.responder_id and actor.responder_id == assignment.responder_id:
            is_owner = True
        elif responder and actor.user_id and responder.user_id == actor.user_id:
            is_owner = True

        if not is_owner or not actor.has_permission(Permission.ASSIGNMENT_UPDATE_SELF):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="IDOR violation: you cannot update an assignment belonging to another responder",
            )

    now = int(time.time())
    assignment.status = target_status
    if target_status in ("ACCEPTED", "DECLINED"):
        assignment.responded_at = now
    if target_status in TERMINAL_ASSIGNMENT_STATUSES:
        assignment.completed_at = now
        if responder:
            responder.availability = "AVAILABLE"

    # Timeline event
    db.add(
        IncidentEventModel(
            incident_id=assignment.incident_id,
            event_type="ASSIGNMENT_STATUS_CHANGED",
            from_status=current_status,
            to_status=target_status,
            actor_id=actor.actor_id,
            reason=reason,
            payload={"assignment_id": assignment_id, "responder_id": assignment.responder_id},
            created_at=now,
        )
    )

    # Outbox event
    db.add(
        OutboxEventModel(
            event_id=f"evt_{uuid.uuid4().hex[:16]}",
            aggregate_type="ASSIGNMENT",
            aggregate_id=assignment_id,
            aggregate_version=1,
            event_type="assignment.status_changed",
            payload={
                "assignment_id": assignment_id,
                "incident_id": assignment.incident_id,
                "responder_id": assignment.responder_id,
                "from_status": current_status,
                "to_status": target_status,
                "actor_id": actor.actor_id,
                "reason": reason,
                "updated_at": now,
            },
            created_at=now,
        )
    )

    db.commit()
    db.refresh(assignment)
    logger.info("DEMO FLOW: Assignment %s transitioned to %s", assignment_id, target_status)
    process_outbox_batch(db)
    return assignment
