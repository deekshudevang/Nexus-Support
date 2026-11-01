from sqlalchemy import Column, String, Integer, Float, Boolean, Index
from app.core.database import Base

class NodeStatusModel(Base):
    __tablename__ = "node_status"

    deviceId = Column(String(128), primary_key=True, index=True)
    displayName = Column(String(128), nullable=False)
    role = Column(String(64), nullable=False)
    batteryLevel = Column(Integer, nullable=False)
    needsHelp = Column(Boolean, nullable=False, default=False)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    timestamp = Column(Integer, nullable=False, index=True)

    __table_args__ = (
        Index("idx_node_status_timestamp_desc", timestamp.desc()),
    )

