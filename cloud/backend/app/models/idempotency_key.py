from sqlalchemy import Column, Integer, String, Text, UniqueConstraint, Index
from app.core.database import Base

class IdempotencyKeyModel(Base):
    __tablename__ = "idempotency_keys"

    id = Column(Integer, primary_key=True, autoincrement=True)
    scope = Column(String(64), nullable=False, index=True)
    key = Column(String(128), nullable=False, index=True)
    request_hash = Column(String(64), nullable=False)
    response_json = Column(Text, nullable=False)
    status_code = Column(Integer, nullable=False)
    created_at = Column(Integer, nullable=False)
    expires_at = Column(Integer, nullable=False, index=True)

    __table_args__ = (
        UniqueConstraint("scope", "key", name="uq_idempotency_scope_key"),
    )
