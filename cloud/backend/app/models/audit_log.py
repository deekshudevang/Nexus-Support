from sqlalchemy import Column, Integer, String, JSON, Index
from app.core.database import Base

class AuditLogModel(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    event_type = Column(String(64), nullable=False, index=True)
    actor_id = Column(String(128), nullable=True, index=True)
    target_type = Column(String(64), nullable=True)
    target_id = Column(String(128), nullable=True)
    details = Column(JSON, nullable=False, default=dict)
    ip_address = Column(String(64), nullable=True)
    created_at = Column(Integer, nullable=False, index=True)

    __table_args__ = (
        Index("ix_audit_logs_actor_created", actor_id, created_at.desc()),
    )
