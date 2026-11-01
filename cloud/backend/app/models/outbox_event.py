from sqlalchemy import Column, Integer, String, JSON, Text, Index
from app.core.database import Base

class OutboxEventModel(Base):
    __tablename__ = "outbox_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    event_id = Column(String(128), unique=True, nullable=False, index=True)
    aggregate_type = Column(String(64), nullable=False, index=True)
    aggregate_id = Column(String(128), nullable=False, index=True)
    aggregate_version = Column(Integer, nullable=False, default=1)
    event_type = Column(String(64), nullable=False, index=True)
    payload = Column(JSON, nullable=False)
    created_at = Column(Integer, nullable=False, index=True)
    published_at = Column(Integer, nullable=True, index=True)
    retry_count = Column(Integer, nullable=False, default=0)
    last_error = Column(Text, nullable=True)

    __table_args__ = (
        Index("ix_outbox_unprocessed", published_at, retry_count, created_at),
    )
