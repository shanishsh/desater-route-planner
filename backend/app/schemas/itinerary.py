"""Itinerary and route segment schemas."""
from typing import List, Optional, Dict, Any
from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict


class RouteSegmentBase(BaseModel):
    sequence_order: int = Field(..., ge=0)
    name: str = Field(..., min_length=2, max_length=150)
    elevation_gain_m: float = 0.0
    hazard_level: str = Field(default="low", pattern="^(low|medium|high)$")
    segment_geojson: Optional[Dict[str, Any]] = None


class RouteSegmentResponse(RouteSegmentBase):
    id: str
    itinerary_id: str
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class ItineraryBase(BaseModel):
    title: str = Field(..., min_length=3, max_length=200)
    description: str
    region: str = Field(..., min_length=2, max_length=100)
    difficulty: str = Field(..., pattern="^(easy|moderate|hard|extreme)$")
    distance_km: float = Field(..., gt=0)
    estimated_duration_hours: float = Field(..., gt=0)
    start_latitude: float = Field(..., ge=-90.0, le=90.0)
    start_longitude: float = Field(..., ge=-180.0, le=180.0)
    end_latitude: float = Field(..., ge=-90.0, le=90.0)
    end_longitude: float = Field(..., ge=-180.0, le=180.0)
    route_geojson: Dict[str, Any]
    is_active: bool = True


class ItineraryCreate(ItineraryBase):
    segments: Optional[List[RouteSegmentBase]] = None


class ItineraryResponse(ItineraryBase):
    id: str
    created_at: datetime
    updated_at: datetime
    segments: List[RouteSegmentResponse] = []

    model_config = ConfigDict(from_attributes=True)


class ItineraryRouteGeoJSONResponse(BaseModel):
    itinerary_id: str
    title: str
    route_geojson: Dict[str, Any]

