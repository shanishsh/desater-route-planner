"""MapPoint schemas."""
from datetime import datetime, timezone
from typing import Optional, Dict, Any
from pydantic import BaseModel, Field, ConfigDict


class MapPointBase(BaseModel):
    name: str = Field(..., min_length=2, max_length=200)
    category: str = Field(
        ...,
        pattern="^(emergency_service|medical|hospital|clinic|police|fire_station|fuel|petrol_pump|fuel_station|shelter|visitor_info|transport|verified_public)$",
    )
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)
    address: Optional[str] = None
    phone: Optional[str] = None
    description: Optional[str] = None
    verification_status: str = Field(default="verified", pattern="^(verified|unverified)$")
    verification_timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    provenance: str = Field(default="Official Emergency Registry", min_length=2)


class MapPointCreate(MapPointBase):
    pass


class MapPointResponse(MapPointBase):
    id: str
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
    provider_status: Optional[str] = "success"  # success, empty, partial, stale, provider_error, not_configured
    is_stale: bool = False
    details: Optional[Dict[str, Any]] = None

    model_config = ConfigDict(from_attributes=True)

