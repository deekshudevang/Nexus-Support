from sqlalchemy import Column, String, Integer, Float, Index
from app.core.database import Base

class LocationEventModel(Base):
    __tablename__ = "location_events"

    event_id = Column(String(128), primary_key=True, index=True)
    peer_id = Column(String(128), nullable=False, index=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy = Column(Float, nullable=False, default=0.0)
    timestamp = Column(Integer, nullable=False, index=True)
    sequence_number = Column(Integer, nullable=False, default=0)
    signature = Column(String(512), nullable=True, default="")
    public_key = Column(String(512), nullable=True, default="")
    sync_status = Column(Integer, nullable=False, default=1)
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)

    __table_args__ = (
        Index("ix_location_events_peer_timestamp", peer_id, timestamp),
    )
