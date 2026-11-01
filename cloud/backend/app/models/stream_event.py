from sqlalchemy import Column, BigInteger, String, Integer, JSON, Index
from app.core.database import Base

class StreamEventModel(Base):
    __tablename__ = "stream_events"

    stream_seq = Column(BigInteger().with_variant(Integer, "sqlite"), primary_key=True, autoincrement=True)
    event_id = Column(String(128), unique=True, nullable=False, index=True)
    aggregate_type = Column(String(64), nullable=False, index=True)
    aggregate_id = Column(String(128), nullable=False, index=True)
    aggregate_version = Column(Integer, nullable=False, default=1)
    event_type = Column(String(64), nullable=False, index=True)
    payload = Column(JSON, nullable=False)
    occurred_at = Column(Integer, nullable=False, index=True)
    published_at = Column(Integer, nullable=False, index=True)
    actor_id = Column(String(128), nullable=True)

    __table_args__ = (
        Index("ix_stream_events_cursor", stream_seq),
    )
