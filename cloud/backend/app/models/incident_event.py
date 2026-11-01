from sqlalchemy import Column, Integer, String, JSON, ForeignKey, Index
from app.core.database import Base

class IncidentEventModel(Base):
    __tablename__ = "incident_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    incident_id = Column(String(128), ForeignKey("incidents.id", ondelete="CASCADE"), nullable=False, index=True)
    event_type = Column(String(64), nullable=False, index=True)
    from_status = Column(String(32), nullable=True)
    to_status = Column(String(32), nullable=True)
    actor_id = Column(String(128), nullable=True)
    reason = Column(String(512), nullable=True)
    payload = Column(JSON, nullable=False, default=dict)
    created_at = Column(Integer, nullable=False, index=True)

    __table_args__ = (
        Index("ix_incident_events_incident_created", incident_id, created_at.desc()),
    )
