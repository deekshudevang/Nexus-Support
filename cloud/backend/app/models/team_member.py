from sqlalchemy import Column, Integer, String, ForeignKey, UniqueConstraint
from app.core.database import Base

class TeamMemberModel(Base):
    __tablename__ = "team_members"

    id = Column(Integer, primary_key=True, autoincrement=True)
    team_id = Column(String(128), ForeignKey("teams.id", ondelete="CASCADE"), nullable=False, index=True)
    responder_id = Column(String(128), ForeignKey("responders.id", ondelete="CASCADE"), nullable=False, index=True)
    joined_at = Column(Integer, nullable=False)

    __table_args__ = (
        UniqueConstraint("team_id", "responder_id", name="uq_team_members_team_responder"),
    )
