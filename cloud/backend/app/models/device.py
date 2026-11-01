from sqlalchemy import Column, String, Integer, Text, Index
from app.core.database import Base

class DeviceModel(Base):
    __tablename__ = "devices"

    id = Column(String(128), primary_key=True, index=True)
    device_id = Column(String(128), unique=True, nullable=False, index=True)
    public_key_pem = Column(Text, nullable=False)
    enrollment_token_id = Column(String(128), nullable=True)
    role = Column(String(32), nullable=False, default="gateway")
    status = Column(String(32), nullable=False, default="active")
    last_seen_at = Column(Integer, nullable=True)
    token_version = Column(Integer, nullable=False, default=1)
    created_at = Column(Integer, nullable=False)
