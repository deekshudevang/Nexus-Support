"""Transactional Outbox Processor & Gapless Stream Event Dispatcher."""

import asyncio
import logging
import time
from typing import List, Optional
from sqlalchemy.orm import Session

from app.models.outbox_event import OutboxEventModel
from app.models.stream_event import StreamEventModel
from app.services.alert_manager import alert_manager

logger = logging.getLogger("rescuemesh.outbox")


def process_outbox_batch(db: Session, limit: int = 50) -> List[dict]:
    """
    Publish a batch of pending outbox events into the gapless monotonic stream_events log.
    Dual-dialect compatible (works across SQLite and PostgreSQL).
    Returns list of published event dictionaries.
    """
    # Query pending outbox entries
    query = (
        db.query(OutboxEventModel)
        .filter(OutboxEventModel.published_at.is_(None))
        .order_by(OutboxEventModel.id.asc())
        .limit(limit)
    )

    # In PostgreSQL we can use with_for_update(skip_locked=True)
    if db.bind and db.bind.dialect.name == "postgresql":
        query = query.with_for_update(skip_locked=True)

    unprocessed = query.all()
    if not unprocessed:
        return []

    now = int(time.time())
    published_events = []

    for item in unprocessed:
        stream_event = StreamEventModel(
            event_id=item.event_id,
            aggregate_type=item.aggregate_type,
            aggregate_id=item.aggregate_id,
            aggregate_version=item.aggregate_version,
            event_type=item.event_type,
            payload=item.payload,
            occurred_at=item.created_at,
            published_at=now,
            actor_id=item.payload.get("actor_id"),
        )
        db.add(stream_event)
        db.flush()  # Allocates monotonic stream_seq

        item.published_at = now
        
        event_dict = {
            "type": "event",
            "stream_seq": stream_event.stream_seq,
            "event_id": stream_event.event_id,
            "event_type": stream_event.event_type,
            "aggregate_type": stream_event.aggregate_type,
            "aggregate_id": stream_event.aggregate_id,
            "payload": stream_event.payload,
            "occurred_at": stream_event.occurred_at,
        }
        published_events.append(event_dict)

    db.commit()

    # Schedule non-blocking async broadcast to WebSocket subscribers
    for evt in published_events:
        try:
            loop = asyncio.get_running_loop()
            loop.create_task(alert_manager.broadcast_event(evt))
        except RuntimeError:
            # Fallback if running synchronously outside an active event loop
            try:
                asyncio.run(alert_manager.broadcast_event(evt))
            except Exception as e:
                logger.warning(f"Failed synchronous broadcast of event {evt['event_id']}: {e}")

    return published_events
