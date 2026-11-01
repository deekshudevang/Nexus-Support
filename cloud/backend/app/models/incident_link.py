from sqlalchemy import Column, String, Integer, ForeignKey, Index
from app.core.database import Base

class IncidentLinkModel(Base):
    __tablename__ = "incident_links"

    id = Column(String(128), primary_key=True, index=True)
    incident_id = Column(String(128), ForeignKey("incidents.id", ondelete="CASCADE"), nullable=False, index=True)
    target_incident_id = Column(String(128), ForeignKey("incidents.id", ondelete="CASCADE"), nullable=False, index=True)
    link_type = Column(String(32), nullable=False, default="SUGGESTED_DUPLICATE")
    reason = Column(String(512), nullable=False, default="")
    reviewer_id = Column(String(128), nullable=True)
    created_at = Column(Integer, nullable=False)

    __table_args__ = (
        Index("ix_incident_links_pair", incident_id, target_incident_id),
    )
