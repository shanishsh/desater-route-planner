"""Alert schemas with dynamic freshness computation and clock-skew validation."""
from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel, Field, field_validator, computed_field, ConfigDict


class AlertBase(BaseModel):
    title: str = Field(..., min_length=3, max_length=200)
    description: str = Field(..., min_length=5)
    severity: str = Field(..., pattern="^(low|medium|high|critical)$")
    category: str = Field(..., pattern="^(weather|hazard|transport|crowd)$")
    affected_itinerary_id: Optional[str] = None
    affected_segment_id: Optional[str] = None
    source_name: str = Field(..., min_length=2, max_length=100)
    source_url: Optional[str] = None
    source_published_at: datetime
    freshness_threshold_seconds: int = Field(default=3600, gt=0)
    is_simulation: bool = True

    @field_validator("source_published_at")
    @classmethod
    def validate_timestamp(cls, v: datetime) -> datetime:
        # Enforce timezone awareness
        if v.tzinfo is None:
            v = v.replace(tzinfo=timezone.utc)
        
        # Check for clock skew / future timestamp (allow 30 seconds buffer for minor clock drift)
        now = datetime.now(timezone.utc)
        if (v - now).total_seconds() > 30:
            raise ValueError("source_published_at cannot be in the future (clock skew detected)")
        return v


class AlertCreate(AlertBase):
    pass


class AlertResponse(AlertBase):
    id: str
    retrieved_at: datetime
    created_at: datetime
    updated_at: datetime

    @computed_field
    @property
    def is_stale(self) -> bool:
        """Dynamically computes whether the alert has exceeded its freshness limit."""
        now = datetime.now(timezone.utc)
        source_ts = self.source_published_at
        if source_ts.tzinfo is None:
            source_ts = source_ts.replace(tzinfo=timezone.utc)
        age_seconds = (now - source_ts).total_seconds()
        return age_seconds > self.freshness_threshold_seconds

    @computed_field
    @property
    def age_minutes(self) -> int:
        """Returns age in minutes from source publication time."""
        now = datetime.now(timezone.utc)
        source_ts = self.source_published_at
        if source_ts.tzinfo is None:
            source_ts = source_ts.replace(tzinfo=timezone.utc)
        age_seconds = max(0.0, (now - source_ts).total_seconds())
        return int(age_seconds // 60)

    model_config = ConfigDict(from_attributes=True)

