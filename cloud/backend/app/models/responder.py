from sqlalchemy import Column, BigInteger, String, Integer, Float, ForeignKey, JSON, Index
from app.core.database import Base

class ResponderModel(Base):
    __tablename__ = "responders"

    id = Column(String(128), primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    callsign = Column(String(64), nullable=False)
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)
    responder_type = Column(String(64), nullable=False, default="SAR")
    skills = Column(JSON, default=list, nullable=False)
    team_id = Column(String(128), ForeignKey("teams.id", ondelete="SET NULL"), nullable=True, index=True)
    availability = Column(String(32), nullable=False, default="OFFLINE", index=True)
    last_latitude = Column(Float, nullable=True)
    last_longitude = Column(Float, nullable=True)
    location_accuracy_m = Column(Float, nullable=True)
    last_location_at = Column(BigInteger().with_variant(Integer, "sqlite"), nullable=True)
    device_id = Column(String(128), nullable=True, index=True)
    created_at = Column(BigInteger().with_variant(Integer, "sqlite"), nullable=False)

    __table_args__ = (
        Index("ix_responders_availability_type", availability, responder_type),
    )
