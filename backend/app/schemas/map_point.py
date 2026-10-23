"""MapPoint schemas."""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, ConfigDict


class MapPointBase(BaseModel):
    name: str = Field(..., min_length=2, max_length=200)
    category: str = Field(
        ...,
        pattern="^(emergency_service|medical|police|visitor_info|transport|verified_public)$",
    )
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    address: Optional[str] = None
    phone: Optional[str] = None
    description: Optional[str] = None
    verification_status: str = Field(default="verified", pattern="^(verified|unverified)$")
    verification_timestamp: datetime
    provenance: str = Field(default="Official Emergency Registry", min_length=2)


class MapPointCreate(MapPointBase):
    pass


class MapPointResponse(MapPointBase):
    id: str
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

