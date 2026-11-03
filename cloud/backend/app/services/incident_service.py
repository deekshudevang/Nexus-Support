"""Incident Lifecycle Domain Service with 3-State-Machine validation and duplicate clustering."""

import math
import time
import uuid
from typing import List, Optional, Tuple

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.models.incident import IncidentModel
from app.models.incident_event import IncidentEventModel
from app.models.incident_link import IncidentLinkModel
from app.models.outbox_event import OutboxEventModel
from app.services.outbox_publisher import process_outbox_batch

ALLOWED_TRANSITIONS = {
    "REPORTED": {"TRIAGED", "CANCELLED"},
    "TRIAGED": {"DISPATCHED", "REPORTED", "CANCELLED"},
    "DISPATCHED": {"ON_SCENE", "TRIAGED", "CANCELLED"},
    "ON_SCENE": {"RESOLVED", "DISPATCHED", "CANCELLED"},
    "RESOLVED": {"CLOSED", "TRIAGED"},
    "CLOSED": {"TRIAGED"},  # Reopening supported for commander review
    "CANCELLED": set(),   # Terminal state
}


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate Great Circle distance between two coordinates in meters."""
    R = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


def create_incident(
    db: Session,
    title: str,
    category: str,
    priority: int,
    severity: int,
    description: str,
    source: str,
    actor_id: str,
    organization_id: str = "org-default",
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
    location_accuracy_m: Optional[float] = None,
    location_description: Optional[str] = None,
    reporter_name: Optional[str] = None,
    reporter_contact: Optional[str] = None,
    client_incident_id: Optional[str] = None,
) -> IncidentModel:
    """Create a new emergency incident and emit outbox event within a single transaction."""
    # Coordinate validation
    if (latitude is not None and longitude is None) or (latitude is None and longitude is not None):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Latitude and longitude must both be provided or both be null",
        )
    if latitude is not None and longitude is not None:
        if not (-90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Coordinates out of bounds: lat={latitude}, lon={longitude}",
            )

    now = int(time.time())
    inc_id = client_incident_id or f"inc_{uuid.uuid4().hex[:12]}"

    incident = IncidentModel(
        id=inc_id,
        title=title,
        description=description,
        category=category.upper(),
        priority=priority,
        severity=severity,
        status="REPORTED",
        organization_id=organization_id,
        source=source,
        reporter_name=reporter_name,
        reporter_contact=reporter_contact,
        latitude=latitude,
        longitude=longitude,
        location_accuracy_m=location_accuracy_m,
        location_description=location_description,
        version=1,
        created_at=now,
        updated_at=now,
    )
    db.add(incident)

    # Append-only timeline creation event
    db.add(
        IncidentEventModel(
            incident_id=inc_id,
            event_type="CREATED",
            from_status=None,
            to_status="REPORTED",
            actor_id=actor_id,
            reason=None,
            payload={
                "title": title,
                "category": category,
                "priority": priority,
                "severity": severity,
                "source": source,
                "organization_id": organization_id,
            },
            created_at=now,
        )
    )

    # Transactional outbox event
    db.add(
        OutboxEventModel(
            event_id=f"evt_{uuid.uuid4().hex[:16]}",
            aggregate_type="INCIDENT",
            aggregate_id=inc_id,
            aggregate_version=1,
            event_type="incident.created",
            payload={
                "id": inc_id,
                "title": title,
                "category": category,
                "priority": priority,
                "severity": severity,
                "status": "REPORTED",
                "organization_id": organization_id,
                "latitude": latitude,
                "longitude": longitude,
                "actor_id": actor_id,
                "created_at": now,
            },
            created_at=now,
        )
    )

    # Duplicate detection: Find existing active incidents in same category within 500m and 1 hour in same org
    if latitude is not None and longitude is not None:
        recent_threshold = now - 3600
        candidates = (
            db.query(IncidentModel)
            .filter(
                IncidentModel.category == category.upper(),
                IncidentModel.organization_id == organization_id,
                IncidentModel.status.in_(["REPORTED", "TRIAGED", "DISPATCHED", "ON_SCENE"]),
                IncidentModel.created_at >= recent_threshold,
                IncidentModel.latitude.is_not(None),
                IncidentModel.longitude.is_not(None),
                IncidentModel.id != inc_id,
            )
            .all()
        )
        for cand in candidates:
            dist = haversine_distance_m(latitude, longitude, cand.latitude, cand.longitude)
            if dist <= 500.0:
                # Create duplicate suggestion link
                conf_val = round(max(0.5, 1.0 - (dist / 1000.0)), 2)
                db.add(
                    IncidentLinkModel(
                        id=f"link_{uuid.uuid4().hex[:12]}",
                        incident_id=inc_id,
                        target_incident_id=cand.id,
                        link_type="DUPLICATE",
                        reason=f"Proximity duplicate: {int(dist)}m away within 1h (conf {conf_val}) in category {category}",
                        reviewer_id=actor_id,
                        created_at=now,
                    )
                )

    db.commit()
    db.refresh(incident)

    # Process outbox synchronously to allocate monotonic stream_seq and broadcast immediately
    process_outbox_batch(db)

    return incident


def transition_incident(
    db: Session,
    incident_id: str,
    new_status: str,
    actor_id: str,
    notes: Optional[str] = None,
    organization_id: Optional[str] = None,
) -> IncidentModel:
    """Transition an incident through the formal state machine, verifying allowed transitions."""
    incident = db.query(IncidentModel).filter(IncidentModel.id == incident_id).first()
    if not incident or (organization_id is not None and incident.organization_id != organization_id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Incident '{incident_id}' not found",
        )

    target_status = new_status.upper().strip()
    current_status = incident.status.upper().strip()

    if target_status == current_status:
        return incident

    valid_targets = ALLOWED_TRANSITIONS.get(current_status, set())
    if target_status not in valid_targets:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                f"Illegal state transition from '{current_status}' to '{target_status}'. "
                f"Allowed transitions: {sorted(list(valid_targets)) if valid_targets else 'None (terminal state)'}"
            ),
        )

    now = int(time.time())
    incident.status = target_status
    incident.version += 1
    incident.updated_at = now
    if target_status in ("RESOLVED", "CLOSED") and not incident.resolved_at:
        incident.resolved_at = now

    # Timeline event
    db.add(
        IncidentEventModel(
            incident_id=incident.id,
            event_type="STATUS_CHANGED",
            from_status=current_status,
            to_status=target_status,
            actor_id=actor_id,
            reason=notes,
            payload={"notes": notes},
            created_at=now,
        )
    )

    # Outbox event
    db.add(
        OutboxEventModel(
            event_id=f"evt_{uuid.uuid4().hex[:16]}",
            aggregate_type="INCIDENT",
            aggregate_id=incident.id,
            aggregate_version=incident.version,
            event_type="incident.status_changed",
            payload={
                "id": incident.id,
                "from_status": current_status,
                "to_status": target_status,
                "version": incident.version,
                "actor_id": actor_id,
                "notes": notes,
                "updated_at": now,
            },
            created_at=now,
        )
    )

    db.commit()
    db.refresh(incident)

    process_outbox_batch(db)
    return incident


def link_incidents(
    db: Session,
    incident_id: str,
    target_incident_id: str,
    link_type: str,
    actor_id: str,
    confidence: float = 1.0,
    reason: Optional[str] = None,
) -> IncidentLinkModel:
    """Link two incidents (duplicate, parent/child, related) with validation."""
    if incident_id == target_incident_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot link an incident to itself",
        )

    inc1 = db.query(IncidentModel).filter(IncidentModel.id == incident_id).first()
    inc2 = db.query(IncidentModel).filter(IncidentModel.id == target_incident_id).first()
    if not inc1 or not inc2:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="One or both incidents not found")

    existing = (
        db.query(IncidentLinkModel)
        .filter(
            IncidentLinkModel.incident_id == incident_id,
            IncidentLinkModel.target_incident_id == target_incident_id,
        )
        .first()
    )
    if existing:
        return existing

    now = int(time.time())
    link = IncidentLinkModel(
        id=f"link_{uuid.uuid4().hex[:12]}",
        incident_id=incident_id,
        target_incident_id=target_incident_id,
        link_type=link_type.upper(),
        reason=reason or "",
        reviewer_id=actor_id,
        created_at=now,
    )
    db.add(link)

    db.add(
        IncidentEventModel(
            incident_id=incident_id,
            event_type="LINKED",
            from_status=None,
            to_status=None,
            actor_id=actor_id,
            reason=reason,
            payload={"target_id": target_incident_id, "link_type": link_type, "reason": reason},
            created_at=now,
        )
    )

    db.add(
        OutboxEventModel(
            event_id=f"evt_{uuid.uuid4().hex[:16]}",
            aggregate_type="INCIDENT",
            aggregate_id=incident_id,
            aggregate_version=inc1.version,
            event_type="incident.linked",
            payload={
                "incident_id": incident_id,
                "target_incident_id": target_incident_id,
                "link_type": link_type,
                "actor_id": actor_id,
                "created_at": now,
            },
            created_at=now,
        )
    )

    db.commit()
    db.refresh(link)
    process_outbox_batch(db)
    return link
