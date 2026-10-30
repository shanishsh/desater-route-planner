"""Schemas for AI-Powered Live Trek Safety Assistant."""
from typing import List, Optional
from pydantic import BaseModel, Field


class LocationDto(BaseModel):
    latitude: float
    longitude: float
    altitude_meters: Optional[float] = None
    accuracy_meters: Optional[float] = None
    speed_kmh: Optional[float] = None


class RouteProgressDto(BaseModel):
    traveled_km: float = 0.0
    chainage_km: float = 0.0
    remaining_km: float = 0.0
    progress_pct: float = 0.0
    is_off_route: bool = False
    cross_track_m: float = 0.0


class HazardContextDto(BaseModel):
    id: str
    title: str
    category: str
    severity: str
    distance_ahead_m: Optional[float] = None
    distance_current_m: Optional[float] = None
    sequence_number: Optional[int] = None
    is_direct_intersection: bool = False
    source_name: Optional[str] = None
    valid_until: Optional[str] = None
    instructions: Optional[str] = None


class WeatherContextDto(BaseModel):
    temperature_c: Optional[float] = None
    weather_description: Optional[str] = None
    precipitation_probability_pct: Optional[int] = None
    wind_speed_kmh: Optional[float] = None
    thunderstorm_probability_pct: Optional[int] = None
    retrieved_at: Optional[str] = None
    observation_age_minutes: Optional[int] = None
    valid_until: Optional[str] = None
    is_stale: bool = False


class FacilityContextDto(BaseModel):
    id: str
    name: str
    category: str
    distance_km: float
    latitude: float
    longitude: float


class VerifiedAlternativeDto(BaseModel):
    route_id: str
    title: str
    distance_km: float
    duration_minutes: float
    safety_advantage: Optional[str] = None


class SafetyEventDto(BaseModel):
    event_id: str
    event_type: str  # CRITICAL_HAZARD_WARNING, HIGH_PRIORITY_HAZARD_WARNING, etc.
    hazard_id: Optional[str] = None
    hazard_title: str
    severity: str
    distance_ahead_m: Optional[float] = None
    distance_current_m: float = 0.0
    is_direct_route_obstruction: bool = False
    has_verified_alternative: bool = False
    deterministic_spoken_text: Optional[str] = None
    deterministic_action_text: Optional[str] = None


class LiveSafetyAssistantRequest(BaseModel):
    trip_id: str
    route_id: str
    context_generation_id: int = 1
    trigger_event: Optional[SafetyEventDto] = None
    current_location: LocationDto
    route_progress: RouteProgressDto
    upcoming_hazards: List[HazardContextDto] = Field(default_factory=list)
    nearby_hazards: List[HazardContextDto] = Field(default_factory=list)
    current_weather: Optional[WeatherContextDto] = None
    nearby_facilities: List[FacilityContextDto] = Field(default_factory=list)
    verified_alternatives: List[VerifiedAlternativeDto] = Field(default_factory=list)
    connectivity_status: str = "ONLINE"  # ONLINE, OFFLINE, MARGINAL, DEAD_ZONE_LIKELY
    stale_sources: List[str] = Field(default_factory=list)


class AssistantInteractiveQueryRequest(BaseModel):
    trip_id: str
    route_id: str
    context_generation_id: int = 1
    query: str
    current_location: LocationDto
    route_progress: RouteProgressDto
    upcoming_hazards: List[HazardContextDto] = Field(default_factory=list)
    nearby_hazards: List[HazardContextDto] = Field(default_factory=list)
    current_weather: Optional[WeatherContextDto] = None
    nearby_facilities: List[FacilityContextDto] = Field(default_factory=list)
    verified_alternatives: List[VerifiedAlternativeDto] = Field(default_factory=list)
    connectivity_status: str = "ONLINE"


class LiveSafetyAssistantResponse(BaseModel):
    event_id: Optional[str] = None
    context_generation_id: int = 1
    summary: str
    what_happened: str
    why_relevant: str
    recommended_action: str
    what_to_avoid: str
    verified_alternatives: List[str] = Field(default_factory=list)
    referenced_hazard_ids: List[str] = Field(default_factory=list)
    referenced_weather_ids: List[str] = Field(default_factory=list)
    referenced_facility_ids: List[str] = Field(default_factory=list)
    data_limitations: List[str] = Field(default_factory=list)
    spoken_message: str
    additional_explanation: Optional[str] = None
    is_ai_generated: bool = False
    fallback_used: bool = False
    disclaimer: str = (
        "Advisory safety guidance grounded strictly in verified trail context. "
        "Always evaluate visible on-ground mountain terrain."
    )

