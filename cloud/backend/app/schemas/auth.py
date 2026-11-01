from pydantic import BaseModel, Field

class OperatorLoginRequest(BaseModel):
    username: str = Field(..., min_length=1, max_length=64)
    password: str = Field(..., min_length=1, max_length=128)

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    role: str = "operator"

class WsTicketResponse(BaseModel):
    ticket: str
    expires_in: int = 30

