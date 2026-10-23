"""Schemas for Route Planning, Corridor Safety Analysis, and Active Trip Tracking."""
from datetime import datetime
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field


class PlaceSearchResult(BaseModel):
    name: str
    latitude: float
    longitude: float
    country: Optional[str] = ""
    admin1: Optional[str] = ""
    display_name: str


class PlanRouteRequest(BaseModel):
    origin_lat: float = Field(..., description="Origin latitude in WGS84")
    origin_lon: float = Field(..., description="Origin longitude in WGS84")
    origin_name: Optional[str] = Field("Current Location", description="Display name for origin")
    destination_lat: float = Field(..., description="Destination latitude in WGS84")
    destination_lon: float = Field(..., description="Destination longitude in WGS84")
    destination_name: str = Field(..., description="Display name for destination")
    travel_mode: Optional[str] = Field("driving", description="'driving' or 'walking' or 'hiking'")
    departure_time: Optional[datetime] = Field(None, description="Planned departure time in UTC")


class RouteWeatherCondition(BaseModel):
    temperature_c: float
    relative_humidity_pct: float
    precipitation_probability_pct: float
    precipitation_mm: float
    wind_speed_kmh: float
    weather_code: int
    weather_description: str
    warning_level: str  # NORMAL, CAUTION, SEVERE
    forecast_valid_at: str
    source: str
    is_simulated: bool = False


class RouteForestInfo(BaseModel):
    in_forest: bool = False
    forest_name: Optional[str] = None
    forest_type: Optional[str] = None
    distance_to_edge_m: Optional[float] = None


class RouteWildfireInfo(BaseModel):
    has_fire_within_5km: bool = False
    nearest_fire_distance_km: Optional[float] = None
    fire_radiative_power: Optional[float] = None
    confidence: Optional[str] = None
    acquisition_time: Optional[str] = None


class RouteDisasterAlertInfo(BaseModel):
    alert_id: str
    title: str
    hazard_category: str
    severity: str
    distance_to_alert_km: float
    required_protective_instructions: str
    is_simulated: bool = False


class RouteWaypointCondition(BaseModel):
    index: int
    name: str
    latitude: float
    longitude: float
    distance_from_start_km: float
    estimated_arrival_at: str
    hazard_level: str  # LOW, MEDIUM, HIGH, CRITICAL
    weather: RouteWeatherCondition
    forest: Optional[RouteForestInfo] = None
    wildfire: Optional[RouteWildfireInfo] = None
    alerts: List[RouteDisasterAlertInfo] = Field(default_factory=list)


class RouteAlternativeSchema(BaseModel):
    id: str
    title: str
    description: str
    distance_km: float
    duration_minutes: float
    geometry_geojson: Dict[str, Any]
    hazard_score: float = 0.0
    safety_label: str = "Safe"


class PlannedRouteResponse(BaseModel):
    route_id: str
    title: str
    origin_name: str
    origin_lat: float
    origin_lon: float
    destination_name: str
    dest_lat: float
    dest_lon: float
    travel_mode: str
    distance_km: float
    duration_minutes: float
    geometry_geojson: Dict[str, Any]
    overall_hazard_score: float  # 0.0 (Safe) to 100.0 (Extreme Hazard)
    overall_safety_status: str  # "SAFE", "CAUTION", "HIGH_RISK"
    high_risk_segments_count: int = 0
    conditions: List[RouteWaypointCondition] = Field(default_factory=list)
    alternatives: List[RouteAlternativeSchema] = Field(default_factory=list)
    data_freshness: Dict[str, Any] = Field(default_factory=dict)
    created_at: str


class StartTripRequest(BaseModel):
    route_id: str
    device_id: Optional[str] = None
    user_id: Optional[str] = None


class StartTripResponse(BaseModel):
    trip_id: str
    route_id: str
    status: str
    started_at: str
    message: str


class TripProgressRequest(BaseModel):
    latitude: float
    longitude: float
    speed_kmh: Optional[float] = 0.0
    timestamp: Optional[datetime] = None


class TripProgressResponse(BaseModel):
    trip_id: str
    status: str  # ACTIVE, PAUSED, COMPLETED
    current_segment_index: int
    progress_pct: float
    distance_remaining_km: float
    distance_to_nearest_hazard_km: Optional[float] = None
    nearest_hazard_title: Optional[str] = None
    hazard_warning_level: str = "LOW"  # LOW, MEDIUM, HIGH, CRITICAL
    is_off_route: bool = False
    reroute_suggested: bool = False
    suggested_alternative_id: Optional[str] = None
    next_waypoint: Optional[RouteWaypointCondition] = None
    message: str


class TripStatusResponse(BaseModel):
    trip_id: str
    route_id: str
    status: str
    started_at: str
    last_latitude: Optional[float] = None
    last_longitude: Optional[float] = None
    progress_pct: float = 0.0
    current_segment_index: int = 0
    overall_safety_status: str = "SAFE"
    completed_at: Optional[str] = None

