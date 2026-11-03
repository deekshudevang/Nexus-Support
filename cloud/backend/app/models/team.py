from sqlalchemy import Column, String, Integer, Index
from app.core.database import Base

class TeamModel(Base):
    __tablename__ = "teams"

    id = Column(String(128), primary_key=True, index=True)
    name = Column(String(128), unique=True, nullable=False)
    callsign = Column(String(64), nullable=False)
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)
    leader_id = Column(String(128), nullable=True)
    status = Column(String(32), nullable=False, default="active")
    created_at = Column(Integer, nullable=False)
