"""Real-Time Event Stream Replay and Gapless Sequence Querying."""

from typing import List, Optional

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.core.database import get_db_session
from app.core.rbac import (
    AuthenticatedActor,
    require_permission,
    Permission,
)
from app.models.stream_event import StreamEventModel

router = APIRouter(prefix="/events", tags=["Events & Replay"])


class StreamEventResponse(BaseModel):
    stream_seq: int
    event_id: str
    aggregate_type: str
    aggregate_id: str
    aggregate_version: int
    event_type: str
    payload: dict
    occurred_at: int
    published_at: int
    actor_id: Optional[str] = None


class EventReplayResponse(BaseModel):
    events: List[StreamEventResponse]
    last_seq: int
    has_more: bool


@router.get("/replay", response_model=EventReplayResponse)
def replay_events_endpoint(
    since_seq: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
    aggregate_type: Optional[str] = None,
    actor: AuthenticatedActor = Depends(require_permission(Permission.INCIDENT_READ)),
    db: Session = Depends(get_db_session),
):
    """
    Cursor-based gapless event replay query.
    Allows clients reconnecting after network blackouts to backfill missed domain events in exact sequence.
    """
    query = (
        db.query(StreamEventModel)
        .filter(StreamEventModel.stream_seq > since_seq)
    )
    if aggregate_type:
        query = query.filter(StreamEventModel.aggregate_type == aggregate_type.upper().strip())

    items = query.order_by(StreamEventModel.stream_seq.asc()).limit(limit + 1).all()

    has_more = len(items) > limit
    returned_events = items[:limit]
    max_seq = returned_events[-1].stream_seq if returned_events else since_seq

    return EventReplayResponse(
        events=[
            StreamEventResponse(
                stream_seq=e.stream_seq,
                event_id=e.event_id,
                aggregate_type=e.aggregate_type,
                aggregate_id=e.aggregate_id,
                aggregate_version=e.aggregate_version,
                event_type=e.event_type,
                payload=e.payload,
                occurred_at=e.occurred_at,
                published_at=e.published_at,
                actor_id=e.actor_id,
            )
            for e in returned_events
        ],
        last_seq=max_seq,
        has_more=has_more,
    )
