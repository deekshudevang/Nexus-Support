from pydantic import BaseModel, Field

class NodeStatus(BaseModel):
    deviceId: str = Field(..., min_length=1, max_length=128)
    displayName: str = Field(..., min_length=1, max_length=128)
    role: str = Field(..., min_length=1, max_length=64)
    batteryLevel: int = Field(..., ge=0, le=100)
    needsHelp: bool
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    timestamp: int

class NodeSyncResponse(BaseModel):
    status: str = "success"
    synced_count: int

