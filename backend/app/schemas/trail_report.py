"""Pydantic schemas for Community Trail Condition Reporting."""
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field, field_validator

VALID_CATEGORIES = {
    "fallen_tree",
    "landslide",
    "flash_flood",
    "damaged_bridge",
    "blocked_path",
    "wildfire_smoke",
    "wildlife",
    "dangerous_weather",
    "trail_emergency",
    "other",
}

VALID_PASSABILITIES = {
    "PASSABLE_ON_FOOT",
    "PASSABLE_WITH_EQUIPMENT",
    "DIFFICULT_REQUIRES_CAUTION",
    "IMPASSABLE",
    "UNKNOWN",
}

VALID_SEVERITIES = {
    "LOW",
    "MEDIUM",
    "HIGH",
    "CRITICAL",
}

VALID_VERIFICATION_STATUSES = {
    "UNVERIFIED",
    "CORROBORATED",
    "OFFICIALLY_CONFIRMED",
    "DISPUTED",
    "RESOLVED",
    "EXPIRED",
}

VALID_INTERACTION_TYPES = {
    "CONFIRM",
    "DISPUTE",
    "RESOLVE",
}


class TrailReportCreate(BaseModel):
    category: str = Field(..., description="Observed trail condition category")
    severity: str = Field(default="MEDIUM", description="Assessed obstacle severity")
    passability: str = Field(default="DIFFICULT_REQUIRES_CAUTION", description="Effect on passage")
    details_json: Optional[Dict[str, Any]] = Field(default=None, description="Category-specific questions")
    latitude: float = Field(..., ge=-90.0, le=90.0, description="Obstacle latitude")
    longitude: float = Field(..., ge=-180.0, le=180.0, description="Obstacle longitude")
    location_accuracy_meters: float = Field(default=10.0, ge=0.0, description="Report location accuracy in meters")
    location_source: str = Field(default="gps", description="'gps' or 'manual_map'")
    observation_time: Optional[datetime] = Field(default=None, description="Time trekker observed the condition")
    description: Optional[str] = Field(default=None, max_length=2000, description="Optional brief notes")
    photo_url: Optional[str] = Field(default=None, max_length=255, description="Uploaded photo path")
    route_id: Optional[str] = Field(default=None, description="Associated planned or active route")
    route_version: Optional[int] = Field(default=1, description="Route version")
    idempotency_key: Optional[str] = Field(default=None, min_length=8, max_length=100, description="Client idempotency key")
    device_id: Optional[str] = Field(default=None, description="Submitting device identifier")

    @field_validator("category")
    @classmethod
    def validate_category(cls, v: str) -> str:
        if v not in VALID_CATEGORIES:
            raise ValueError(f"Invalid category '{v}'. Allowed: {sorted(VALID_CATEGORIES)}")
        return v

    @field_validator("passability")
    @classmethod
    def validate_passability(cls, v: str) -> str:
        if v not in VALID_PASSABILITIES:
            raise ValueError(f"Invalid passability '{v}'. Allowed: {sorted(VALID_PASSABILITIES)}")
        return v

    @field_validator("severity")
    @classmethod
    def validate_severity(cls, v: str) -> str:
        if v not in VALID_SEVERITIES:
            raise ValueError(f"Invalid severity '{v}'. Allowed: {sorted(VALID_SEVERITIES)}")
        return v


class TrailReportInteractionCreate(BaseModel):
    interaction_type: str = Field(..., description="'CONFIRM', 'DISPUTE', or 'RESOLVE'")
    comment: Optional[str] = Field(default=None, max_length=1000, description="Optional explanation")
    device_id: Optional[str] = Field(default=None, max_length=100, description="Device identifier")

    @field_validator("interaction_type")
    @classmethod
    def validate_type(cls, v: str) -> str:
        if v not in VALID_INTERACTION_TYPES:
            raise ValueError(f"Invalid interaction type '{v}'. Allowed: {sorted(VALID_INTERACTION_TYPES)}")
        return v


class TrailReportResponse(BaseModel):
    id: str
    category: str
    severity: str
    passability: str
    details_json: Optional[Dict[str, Any]] = None
    latitude: float
    longitude: float
    location_accuracy_meters: float
    location_source: str
    observation_time: datetime
    submission_time: datetime
    description: Optional[str] = None
    photo_url: Optional[str] = None
    route_id: Optional[str] = None
    route_version: Optional[int] = None
    verification_status: str
    confirmations_count: int
    disputes_count: int
    last_confirmed_at: Optional[datetime] = None
    expires_at: datetime
    idempotency_key: Optional[str] = None
    is_expired: bool = False
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class TrailReportListResponse(BaseModel):
    items: List[TrailReportResponse]
    total: int
    active_count: int
    corroborated_count: int


class TrailReportUploadResponse(BaseModel):
    success: bool
    photo_url: str
    filename: str
    size_bytes: int
    content_type: str
