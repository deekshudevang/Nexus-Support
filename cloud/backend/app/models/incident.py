from sqlalchemy import Column, String, Integer, Float, Text, CheckConstraint, Index, Sequence
from app.core.database import Base

incident_number_seq = Sequence("incident_number_seq", start=1001)

class IncidentModel(Base):
    __tablename__ = "incidents"

    id = Column(String(128), primary_key=True, index=True)
    incident_number = Column(Integer, incident_number_seq, unique=True, nullable=True)
    title = Column(String(256), nullable=False)
    description = Column(Text, nullable=False, default="")
    category = Column(String(64), nullable=False, default="OTHER", index=True)
    priority = Column(Integer, nullable=False, default=3, index=True)
    severity = Column(Integer, nullable=False, default=3, index=True)
    status = Column(String(32), nullable=False, default="REPORTED", index=True)
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)
    source = Column(String(64), nullable=False, default="OPERATOR", index=True)
    reporter_name = Column(String(128), nullable=True)
    reporter_contact = Column(String(128), nullable=True)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    location_accuracy_m = Column(Float, nullable=True)
    location_description = Column(String(512), nullable=True)
    commander_id = Column(String(128), nullable=True, index=True)
    version = Column(Integer, nullable=False, default=1)
    created_at = Column(Integer, nullable=False, index=True)
    updated_at = Column(Integer, nullable=False)
    resolved_at = Column(Integer, nullable=True)

    __table_args__ = (
        CheckConstraint(
            "(latitude IS NULL AND longitude IS NULL) OR (latitude >= -90.0 AND latitude <= 90.0 AND longitude >= -180.0 AND longitude <= 180.0)",
            name="ck_incidents_coordinates_valid"
        ),
        Index("ix_incidents_status_priority_created", status, priority, created_at.desc()),
    )
