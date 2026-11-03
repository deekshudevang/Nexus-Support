from sqlalchemy import Column, String, Integer, Boolean, Index
from app.core.database import Base

class UserModel(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    username = Column(String(64), unique=True, nullable=False, index=True)
    email = Column(String(128), unique=True, nullable=True, index=True)
    password_hash = Column(String(256), nullable=False)
    role = Column(String(32), nullable=False, default="operator")
    status = Column(String(32), nullable=False, default="active")
    organization_id = Column(String(64), nullable=False, default="org-default", index=True)
    is_active = Column(Boolean, default=True)
    last_login = Column(Integer, nullable=True)
    token_version = Column(Integer, default=1, nullable=False)
    created_at = Column(Integer, nullable=False)

    __table_args__ = (
        Index("ix_users_username_lower", username),
    )
