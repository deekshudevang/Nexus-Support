from typing import List, Optional
from pydantic import BaseModel, Field

class SosPacket(BaseModel):
    uuid: str = Field(..., min_length=1, max_length=128)
    senderId: str = Field(..., min_length=1, max_length=128)
    senderName: str = Field(..., min_length=1, max_length=128)
    emergencyType: str = Field(..., min_length=1, max_length=64)
    severity: int = Field(..., ge=1, le=10)
    latitude: Optional[float] = Field(None, ge=-90.0, le=90.0)
    longitude: Optional[float] = Field(None, ge=-180.0, le=180.0)
    gpsFixAvailable: Optional[bool] = Field(None)
    message: str = Field(..., max_length=4096)
    timestamp: int
    status: str = Field(..., min_length=1, max_length=32)
    hopCount: Optional[int] = Field(0, ge=0, le=255)

class SosReceipt(BaseModel):
    uuid: str
    status: str = "ACKNOWLEDGED"
    synced: bool = True

class SosSyncResponse(BaseModel):
    status: str = "success"
    synced_count: int
    receipts: List[SosReceipt] = []

