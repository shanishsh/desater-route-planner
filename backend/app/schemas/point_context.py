"""Schemas for Point Context & Inspection API."""
from datetime import datetime
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from app.schemas.weather import WeatherResponse
from app.schemas.disaster import DisasterAlertResponse


class NearbyHazardItem(BaseModel):
    id: str
    category: str
    title: str
    distance_km: float
    severity: Optional[str] = None
    source: str = "Verified Observation"
    is_intersecting: bool = False
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    details: Dict[str, Any] = Field(default_factory=dict)


class NearbyFacilityItem(BaseModel):
    id: str
    name: str
    category: str  # hospital, clinic, police, petrol_pump, shelter
    distance_km: float
    latitude: float
    longitude: float
    phone: Optional[str] = None
    address: Optional[str] = None
    is_verified: bool = True


class PointContextResponse(BaseModel):
    latitude: float
    longitude: float
    in_uttarakhand: bool
    nearest_settlement: Optional[str] = None
    settlement_distance_km: Optional[float] = None
    elevation_m: Optional[float] = None
    weather: Optional[WeatherResponse] = None
    active_warnings: List[DisasterAlertResponse] = Field(default_factory=list)
    nearby_hazards: List[NearbyHazardItem] = Field(default_factory=list)
    nearby_facilities: List[NearbyFacilityItem] = Field(default_factory=list)
    nearest_waterway: Optional[Dict[str, Any]] = None
    cellular_estimate: Dict[str, Any] = Field(default_factory=dict)
    retrieved_at: datetime
    is_cached: bool = False
    is_stale: bool = False
    disclaimer: str = (
        "Point inspection synthesizes Open-Meteo atmospheric forecasts, NDMA Sachet/IMD official bulletins, "
        "and OSM emergency infrastructure. Forecasts are model-based and not on-site measurements."
    )
