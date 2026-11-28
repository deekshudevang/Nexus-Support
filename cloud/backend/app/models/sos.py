from sqlalchemy import Column, BigInteger, String, Integer, Float, Boolean, Index
from app.core.database import Base

class SosPacketModel(Base):
    __tablename__ = "sos_packets"

    uuid = Column(String(128), primary_key=True, index=True)
    senderId = Column(String(128), nullable=False, index=True)
    senderName = Column(String(128), nullable=False)
    emergencyType = Column(String(64), nullable=False, index=True)
    severity = Column(Integer, nullable=False, index=True)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    gpsFixAvailable = Column(Boolean, default=True)
    message = Column(String(4096), nullable=False)
    timestamp = Column(BigInteger().with_variant(Integer, "sqlite"), nullable=False, index=True)
    status = Column(String(32), nullable=False, default="PENDING", index=True)
    hopCount = Column(Integer, default=0)

    __table_args__ = (
        Index("idx_sos_packets_timestamp_desc", timestamp.desc()),
        Index("idx_sos_packets_severity_desc", severity.desc()),
    )

