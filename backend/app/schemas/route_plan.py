"""Schemas for Route Planning, Corridor Safety Analysis, and Active Trip Tracking."""
from datetime import datetime
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field, model_validator, field_validator


class CoordinatePoint(BaseModel):
    latitude: float = Field(..., description="Latitude in WGS84")
    longitude: float = Field(..., description="Longitude in WGS84")
    name: Optional[str] = Field(None, description="Optional location or waypoint label")


class PlaceSearchResult(BaseModel):
    name: str
    latitude: float
    longitude: float
    country: Optional[str] = ""
    admin1: Optional[str] = ""
    display_name: str


class PlanRouteRequest(BaseModel):
    origin: Optional[CoordinatePoint] = None
    destination: Optional[CoordinatePoint] = None
    waypoints: List[CoordinatePoint] = Field(default_factory=list)
    origin_lat: Optional[float] = Field(None, description="Origin latitude in WGS84")
    origin_lon: Optional[float] = Field(None, description="Origin longitude in WGS84")
    origin_name: Optional[str] = Field("Current Location", description="Display name for origin")
    destination_lat: Optional[float] = Field(None, description="Destination latitude in WGS84")
    destination_lon: Optional[float] = Field(None, description="Destination longitude in WGS84")
    destination_name: Optional[str] = Field(None, description="Display name for destination")
    travel_mode: Optional[str] = Field("walking", description="'driving', 'walking', or 'hiking'")
    departure_time: Optional[datetime] = Field(None, description="Planned departure time in UTC")
    corridor_distance_km: Optional[float] = Field(5.0, description="Corridor scan distance on each side in km (1, 2, 5, 10)")
    corridor_distance_meters: Optional[float] = Field(None, description="Corridor scan distance on each side in meters")

    @model_validator(mode="before")
    @classmethod
    def resolve_coordinates(cls, data: Any) -> Any:
        if isinstance(data, dict):
            # Handle structured origin object
            orig = data.get("origin")
            if isinstance(orig, dict):
                if data.get("origin_lat") is None:
                    data["origin_lat"] = orig.get("latitude")
                if data.get("origin_lon") is None:
                    data["origin_lon"] = orig.get("longitude")
                if not data.get("origin_name"):
                    data["origin_name"] = orig.get("name", "Origin")

            # Handle structured destination object
            dest = data.get("destination")
            if isinstance(dest, dict):
                if data.get("destination_lat") is None:
                    data["destination_lat"] = dest.get("latitude")
                if data.get("destination_lon") is None:
                    data["destination_lon"] = dest.get("longitude")
                if not data.get("destination_name"):
                    data["destination_name"] = dest.get("name", "Destination")

            # Fallback names
            if not data.get("origin_name"):
                data["origin_name"] = "Current Location"
            if not data.get("destination_name"):
                data["destination_name"] = "Selected Destination"

            # Populate nested origin / destination if flat ones were passed
            if not orig and data.get("origin_lat") is not None and data.get("origin_lon") is not None:
                data["origin"] = {
                    "latitude": data["origin_lat"],
                    "longitude": data["origin_lon"],
                    "name": data.get("origin_name", "Current Location"),
                }
            if not dest and data.get("destination_lat") is not None and data.get("destination_lon") is not None:
                data["destination"] = {
                    "latitude": data["destination_lat"],
                    "longitude": data["destination_lon"],
                    "name": data.get("destination_name", "Selected Destination"),
                }
        return data


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
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    brightness: Optional[float] = None


class RouteDisasterAlertInfo(BaseModel):
    alert_id: str = "alert"
    title: str = "Disaster Alert"
    hazard_category: str = "general"
    severity: str = "WARNING"
    distance_to_alert_km: float = 0.0
    required_protective_instructions: str = ""
    is_simulated: bool = False
    center_latitude: Optional[float] = None
    center_longitude: Optional[float] = None
    affected_radius_meters: Optional[float] = None
    polygon_geojson: Optional[Any] = None
    area_description: Optional[str] = None
    issuing_authority: Optional[str] = None
    published_at: Optional[str] = None
    effective_at: Optional[str] = None
    expires_at: Optional[str] = None
    source_url: Optional[str] = None

    @model_validator(mode="before")
    @classmethod
    def resolve_alert_fields(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "alert_id" not in data and "id" in data:
                data["alert_id"] = str(data["id"])
            if "title" not in data and "name" in data:
                data["title"] = str(data["name"])
        return data

    @field_validator("polygon_geojson", mode="before")
    @classmethod
    def parse_polygon_geojson(cls, v: Any) -> Any:
        if isinstance(v, str):
            try:
                import json
                return json.loads(v)
            except Exception:
                return None
        return v


class ElevationProfilePoint(BaseModel):
    index: int
    latitude: float
    longitude: float
    elevation_m: float
    distance_from_start_km: float
    slope_degrees: float
    hiking_speed_kmh: float
    cumulative_minutes: float
    estimated_arrival_iso: str


class SafeHavenInfo(BaseModel):
    name: str
    category: str  # hospital, clinic, police, fuel, fire_station, shelter
    latitude: float
    longitude: float
    distance_km: float
    is_egress_point: bool = True
    opening_hours: Optional[str] = None
    phone: Optional[str] = None
    website: Optional[str] = None
    brand: Optional[str] = None
    operator: Optional[str] = None
    address: Optional[str] = None
    emergency_verified: bool = False
    source: str = "OpenStreetMap Overpass"


class TrailGuardRiskBreakdown(BaseModel):
    weather_subscore: float
    slope_subscore: float
    hazard_subscore: float
    fire_subscore: float
    composite_score: float
    risk_level: str
    contributing_factors: List[str] = Field(default_factory=list)


class RouteWaypointCondition(BaseModel):
    index: int = 0
    name: str = "Waypoint"
    latitude: float
    longitude: float
    distance_from_start_km: float = 0.0
    estimated_arrival_at: str = ""
    hazard_level: str = "LOW"  # LOW, MEDIUM, HIGH, CRITICAL
    elevation_m: float = 0.0
    slope_degrees: float = 0.0
    weather: Optional[RouteWeatherCondition] = None
    forest: Optional[RouteForestInfo] = None
    wildfire: Optional[RouteWildfireInfo] = None
    alerts: List[RouteDisasterAlertInfo] = Field(default_factory=list)
    risk_breakdown: Optional[Dict[str, float]] = None
    contributing_factors: List[str] = Field(default_factory=list)



class RouteAlternativeSchema(BaseModel):
    id: str
    title: str
    description: str
    distance_km: float
    duration_minutes: float
    geometry_geojson: Dict[str, Any]
    hazard_score: float = 0.0
    safety_label: str = "Safe"
    corridor_geojson: Optional[Dict[str, Any]] = None
    is_recommended: bool = False
    direct_hazard_intersections_count: int = 0
    corridor_hazard_count: int = 0
    fires_within_2km_count: int = 0
    recommendation_reason: Optional[str] = None
    exposure_summary: Optional[str] = None
    hospitals_count: int = 0
    clinics_count: int = 0
    police_stations_count: int = 0
    fuel_stations_count: int = 0
    fire_stations_count: int = 0
    nearest_hospital_km: Optional[float] = None
    nearest_police_km: Optional[float] = None
    nearest_fuel_km: Optional[float] = None
    facilities_geojson: Optional[Dict[str, Any]] = None
    preference_rankings: Dict[str, Any] = Field(default_factory=dict)
    data_completeness: bool = True


class ProviderStatusItem(BaseModel):
    provider_name: str
    status: str  # LOADED, EMPTY, CACHED, UNAVAILABLE, FAILED, UNSUPPORTED_REGION, UNCONFIGURED
    last_request_time: Optional[str] = None
    last_success_time: Optional[str] = None
    http_status: Optional[int] = None
    record_count: int = 0
    is_stale: bool = False
    details: Optional[str] = None


class RouteExposureMetrics(BaseModel):
    direct_hazard_intersections_count: int = 0
    corridor_advisories_count: int = 0
    active_fire_hotspots_5km_count: int = 0
    max_precipitation_mm_hr: float = 0.0
    max_wind_speed_kmh: float = 0.0
    freezing_elevation_warning: bool = False
    elevation_gain_m: float = 0.0
    elevation_loss_m: float = 0.0
    max_slope_degrees: float = 0.0
    essential_facilities_count: int = 0
    source_completeness_pct: float = 100.0
    unconfigured_or_degraded_sources: List[str] = Field(default_factory=list)
    decision_narrative: str = ""


class PlannedRouteResponse(BaseModel):
    route_id: str
    route_version: int = 1
    origin: Dict[str, Any] = Field(default_factory=dict)
    destination: Dict[str, Any] = Field(default_factory=dict)
    geometry: Dict[str, Any] = Field(default_factory=dict)
    corridor_distance_km: float = 5.0
    corridor_distance_meters: float = 5000.0
    corridor_geojson: Optional[Dict[str, Any]] = None
    hazards_geojson: Optional[Dict[str, Any]] = None
    community_hazards_geojson: Optional[Dict[str, Any]] = None
    fires_geojson: Optional[Dict[str, Any]] = None
    facilities_geojson: Optional[Dict[str, Any]] = None
    forests_geojson: Optional[Dict[str, Any]] = None
    weather_geojson: Optional[Dict[str, Any]] = None
    terrain_slope_geojson: Optional[Dict[str, Any]] = None
    cellular_sites_geojson: Optional[Dict[str, Any]] = None
    cellular_range_geojson: Optional[Dict[str, Any]] = None
    cellular_coverage_geojson: Optional[Dict[str, Any]] = None
    waterways_geojson: Optional[Dict[str, Any]] = None
    historical_landslides_geojson: Optional[Dict[str, Any]] = None
    terrain_exposure: Optional[Dict[str, Any]] = None
    cellular_summary: Optional[Dict[str, Any]] = None
    waterway_summary: Optional[Dict[str, Any]] = None
    landslide_summary: Optional[Dict[str, Any]] = None
    nasa_lhasa_status: Optional[Dict[str, Any]] = None
    community_reports: List[Dict[str, Any]] = Field(default_factory=list)
    geometry_diagnostics: Dict[str, Any] = Field(default_factory=dict)
    grounded_summary: Optional[Dict[str, Any]] = None
    distance_meters: float = 0.0
    duration_seconds: float = 0.0
    routing_provider: str = "OSRM"
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
    overall_hazard_score: float = 0.0  # 0.0 (Safe) to 100.0 (Extreme Hazard)
    overall_safety_status: str = "SAFE"  # "SAFE", "CAUTION", "HIGH_RISK"
    high_risk_segments_count: int = 0
    total_ascent_m: float = 0.0
    total_descent_m: float = 0.0
    min_elevation_m: float = 0.0
    max_elevation_m: float = 0.0
    decision_explanation: str = ""
    exposure_metrics: Optional[RouteExposureMetrics] = None
    conditions: List[RouteWaypointCondition] = Field(default_factory=list)
    elevation_profile: List[ElevationProfilePoint] = Field(default_factory=list)
    safe_havens: List[SafeHavenInfo] = Field(default_factory=list)
    community_reports: List[Dict[str, Any]] = Field(default_factory=list)
    alternatives: List[RouteAlternativeSchema] = Field(default_factory=list)
    provider_statuses: Dict[str, ProviderStatusItem] = Field(default_factory=dict)
    analysis_limitations: List[str] = Field(default_factory=list)
    data_freshness: Dict[str, Any] = Field(default_factory=dict)
    created_at: str
    verification_status: str = "MAPPED_TRAIL"
    drawing_mode: str = "AUTOMATIC"
    is_user_drawn: bool = False
    proposed_connector_geojson: Optional[Dict[str, Any]] = None
    unverified_reason: Optional[str] = None
    trail_name: Optional[str] = None

    @model_validator(mode="before")
    @classmethod
    def populate_contract_fields(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "route_version" not in data or data["route_version"] is None:
                data["route_version"] = 1
            if "routing_provider" not in data or not data["routing_provider"]:
                data["routing_provider"] = "OSRM"
            if "geometry" not in data or not data["geometry"]:
                data["geometry"] = data.get("geometry_geojson", {})
            if "distance_meters" not in data or data["distance_meters"] == 0.0:
                data["distance_meters"] = round((data.get("distance_km", 0.0) * 1000.0), 1)
            if "duration_seconds" not in data or data["duration_seconds"] == 0.0:
                data["duration_seconds"] = round((data.get("duration_minutes", 0.0) * 60.0), 1)
            if "origin" not in data or not data["origin"]:
                data["origin"] = {
                    "latitude": data.get("origin_lat", 0.0),
                    "longitude": data.get("origin_lon", 0.0),
                    "name": data.get("origin_name", "Origin"),
                }
            if "destination" not in data or not data["destination"]:
                data["destination"] = {
                    "latitude": data.get("dest_lat", 0.0),
                    "longitude": data.get("dest_lon", 0.0),
                    "name": data.get("destination_name", "Destination"),
                }
        return data


class RouteAnalysisRequest(BaseModel):
    route_version: Optional[int] = Field(None, description="Expected route version")
    corridor_distance_meters: Optional[float] = Field(None, description="Buffer distance on each side in meters (default: 5000)")
    corridor_distance_km: Optional[float] = Field(None, description="Buffer distance on each side in km (e.g. 1.0, 2.0, 5.0, 10.0)")
    weather_sample_spacing_km: Optional[float] = Field(None, description="Spacing between weather samples along route")
    include_safe_havens: bool = True
    include_wildfires: bool = True
    include_disaster_alerts: bool = True


class RouteAnalysisResponse(BaseModel):
    route_id: str
    exposure_metrics: Optional[RouteExposureMetrics] = None
    route_version: int = 1
    corridor_distance_meters: float = 5000.0
    corridor_distance_km: float = 5.0
    analysis_timestamp: str
    overall_hazard_score: float = 0.0
    overall_safety_status: str = "SAFE"
    decision_explanation: str = ""
    provider_statuses: Dict[str, ProviderStatusItem] = Field(default_factory=dict)
    weather_by_waypoint: List[RouteWeatherCondition] = Field(default_factory=list)
    disaster_alerts: List[RouteDisasterAlertInfo] = Field(default_factory=list)
    fire_detections: List[RouteWildfireInfo] = Field(default_factory=list)
    forest_features: List[RouteForestInfo] = Field(default_factory=list)
    assistance_points: List[SafeHavenInfo] = Field(default_factory=list)
    affected_segments: List[RouteWaypointCondition] = Field(default_factory=list)
    data_quality_summary: Dict[str, Any] = Field(default_factory=dict)
    overlays_geojson: Dict[str, Any] = Field(default_factory=dict)
    corridor_geojson: Optional[Dict[str, Any]] = None
    hazards_geojson: Optional[Dict[str, Any]] = None
    community_hazards_geojson: Optional[Dict[str, Any]] = None
    fires_geojson: Optional[Dict[str, Any]] = None
    facilities_geojson: Optional[Dict[str, Any]] = None
    forests_geojson: Optional[Dict[str, Any]] = None
    weather_geojson: Optional[Dict[str, Any]] = None
    terrain_slope_geojson: Optional[Dict[str, Any]] = None
    cellular_sites_geojson: Optional[Dict[str, Any]] = None
    cellular_range_geojson: Optional[Dict[str, Any]] = None
    cellular_coverage_geojson: Optional[Dict[str, Any]] = None
    waterways_geojson: Optional[Dict[str, Any]] = None
    historical_landslides_geojson: Optional[Dict[str, Any]] = None
    terrain_exposure: Optional[Dict[str, Any]] = None
    cellular_summary: Optional[Dict[str, Any]] = None
    waterway_summary: Optional[Dict[str, Any]] = None
    landslide_summary: Optional[Dict[str, Any]] = None
    nasa_lhasa_status: Optional[Dict[str, Any]] = None
    alternatives: List[RouteAlternativeSchema] = Field(default_factory=list)
    analysis_limitations: List[str] = Field(default_factory=list)
    community_reports: List[Dict[str, Any]] = Field(default_factory=list)
    geometry_diagnostics: Dict[str, Any] = Field(default_factory=dict)
    grounded_summary: Optional[Dict[str, Any]] = None
    verification_status: str = "MAPPED_TRAIL"
    drawing_mode: str = "AUTOMATIC"
    is_user_drawn: bool = False
    proposed_connector_geojson: Optional[Dict[str, Any]] = None
    unverified_reason: Optional[str] = None
    trail_name: Optional[str] = None


class CustomRouteAnalysisRequest(BaseModel):
    title: Optional[str] = None
    route_name: Optional[str] = None
    description: Optional[str] = None
    geometry_geojson: Optional[Dict[str, Any]] = None
    coordinates: Optional[List[List[float]]] = None
    drawing_mode: str = "FREEHAND"  # "FREEHAND", "TRAIL_FOLLOWING", or "RECORDED_TREK"
    corridor_distance_km: float = 2.0


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


class TrackPointBatchItem(BaseModel):
    latitude: float
    longitude: float
    altitude_m: Optional[float] = 0.0
    accuracy_m: Optional[float] = 10.0
    speed_kmh: Optional[float] = 0.0
    recorded_at: Optional[str] = None


class TrackPointsSyncRequest(BaseModel):
    points: List[TrackPointBatchItem]


class TrackPointsSyncResponse(BaseModel):
    trip_id: str
    status: str
    synced_count: int
    traveled_distance_km: float
    progress_pct: float
    distance_remaining_km: float
    distance_to_nearest_hazard_km: Optional[float] = None
    nearest_hazard_title: Optional[str] = None
    hazard_warning_level: str = "LOW"
    is_off_route: bool = False
    message: str


class TripSummaryResponse(BaseModel):
    trip_id: str
    route_id: str
    status: str
    started_at: str
    completed_at: Optional[str] = None
    route_title: Optional[str] = None
    origin_name: Optional[str] = None
    destination_name: Optional[str] = None
    travel_mode: Optional[str] = None
    distance_km: Optional[float] = None
    last_latitude: Optional[float] = None
    last_longitude: Optional[float] = None
    progress_pct: float = 0.0


