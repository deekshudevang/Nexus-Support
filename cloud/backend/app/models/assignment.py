from sqlalchemy import Column, String, Integer, ForeignKey, Index, text
from app.core.database import Base

class AssignmentModel(Base):
    __tablename__ = "assignments"

    id = Column(String(128), primary_key=True, index=True)
    incident_id = Column(String(128), ForeignKey("incidents.id", ondelete="CASCADE"), nullable=False, index=True)
    responder_id = Column(String(128), ForeignKey("responders.id", ondelete="CASCADE"), nullable=False, index=True)
    team_id = Column(String(128), ForeignKey("teams.id", ondelete="SET NULL"), nullable=True, index=True)
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)
    status = Column(String(32), nullable=False, default="PROPOSED", index=True)
    offered_at = Column(Integer, nullable=False)
    responded_at = Column(Integer, nullable=True)
    completed_at = Column(Integer, nullable=True)

    __table_args__ = (
        # Partial unique index: At most one active assignment per responder across the system
        Index(
            "uq_assignments_active_responder",
            responder_id,
            unique=True,
            postgresql_where=text("status IN ('DISPATCHED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE')"),
            sqlite_where=text("status IN ('DISPATCHED', 'ACCEPTED', 'EN_ROUTE', 'ON_SCENE')")
        ),
        # Partial unique index: Unique non-terminal assignment per incident/responder pair
        Index(
            "uq_assignments_active_incident_responder",
            incident_id,
            responder_id,
            unique=True,
            postgresql_where=text("status NOT IN ('COMPLETED', 'DECLINED', 'CANCELLED', 'EXPIRED')"),
            sqlite_where=text("status NOT IN ('COMPLETED', 'DECLINED', 'CANCELLED', 'EXPIRED')")
        ),
    )
