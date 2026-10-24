"""Pydantic schemas for Disaster Alerts, Subscribers, Devices, and Notifications."""
from datetime import datetime, timezone
from typing import Optional, List
from pydantic import BaseModel, Field, field_validator, ConfigDict
import re


class DisasterAlertCreate(BaseModel):
    title: str = Field(..., min_length=3, max_length=200)
    description: str = Field(..., min_length=5)
    hazard_category: str = Field(
        ...,
        description="Category: flash_flood, heavy_rainfall, landslide, severe_weather, trail_closure, transport_disruption, other",
    )
    severity: str = Field(..., description="INFO, WARNING, SEVERE, CRITICAL")
    issuing_authority: str = Field(..., max_length=150)
    source_url: Optional[str] = None
    source_verification_status: str = Field(default="VERIFIED", description="VERIFIED, UNVERIFIED, SIMULATED")
    required_protective_instructions: str = Field(..., min_length=5)
    
    center_latitude: Optional[float] = Field(None, ge=-90.0, le=90.0)
    center_longitude: Optional[float] = Field(None, ge=-180.0, le=180.0)
    affected_radius_meters: Optional[float] = Field(default=5000.0, ge=100.0)
    polygon_geojson: Optional[str] = None
    area_description: Optional[str] = None
    geocode_info: Optional[str] = None
    external_identifier: Optional[str] = None
    affected_route_id: Optional[str] = None
    
    is_simulated: bool = Field(default=False, description="Flag indicating synthetic or test alert")
    published_at: Optional[datetime] = None
    effective_at: Optional[datetime] = None
    expires_at: Optional[datetime] = None

    @field_validator("severity")
    @classmethod
    def validate_severity(cls, v: str) -> str:
        valid = {"INFO", "WARNING", "SEVERE", "CRITICAL"}
        upper_v = v.upper()
        if upper_v not in valid:
            raise ValueError(f"Severity must be one of: {', '.join(valid)}")
        return upper_v


class DisasterAlertUpdate(BaseModel):
    title: Optional[str] = Field(None, min_length=3, max_length=200)
    description: Optional[str] = None
    severity: Optional[str] = None
    required_protective_instructions: Optional[str] = None
    affected_radius_meters: Optional[float] = None
    polygon_geojson: Optional[str] = None
    expires_at: Optional[datetime] = None


class DisasterAlertResponse(BaseModel):
    id: str
    title: str
    description: str
    hazard_category: str
    severity: str
    status: str
    version: int
    is_simulated: bool
    issuing_authority: str
    source_url: Optional[str] = None
    source_verification_status: str
    required_protective_instructions: str
    center_latitude: Optional[float] = None
    center_longitude: Optional[float] = None
    affected_radius_meters: Optional[float] = None
    polygon_geojson: Optional[str] = None
    area_description: Optional[str] = None
    geocode_info: Optional[str] = None
    external_identifier: Optional[str] = None
    affected_route_id: Optional[str] = None
    published_at: datetime
    received_at: datetime
    effective_at: Optional[datetime] = None
    expires_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class SubscriberRegisterRequest(BaseModel):
    phone_e164: str = Field(..., description="E.164 phone number, e.g. +919876543210")

    @field_validator("phone_e164")
    @classmethod
    def validate_phone(cls, v: str) -> str:
        clean = v.strip().replace(" ", "").replace("-", "")
        if not re.match(r"^\+[1-9]\d{6,14}$", clean):
            raise ValueError("Phone number must be a valid E.164 string starting with '+' and country code")
        return clean


class SubscriberVerifyOTPRequest(BaseModel):
    phone_e164: str
    otp: str = Field(..., min_length=4, max_length=10)
    consent_given: bool = Field(..., description="Must explicitly consent to emergency disaster warnings")
    consent_version: str = "v1.0"
    subscribed_areas: Optional[List[str]] = Field(default_factory=list)
    subscribed_itinerary_id: Optional[str] = None

    @field_validator("phone_e164")
    @classmethod
    def validate_phone(cls, v: str) -> str:
        clean = v.strip().replace(" ", "").replace("-", "")
        if not re.match(r"^\+[1-9]\d{6,14}$", clean):
            raise ValueError("Invalid phone format")
        return clean


class SubscriberConsentUpdateRequest(BaseModel):
    phone_e164: str
    consent_given: bool
    subscribed_areas: Optional[List[str]] = None
    subscribed_itinerary_id: Optional[str] = None


class SubscriberResponse(BaseModel):
    id: str
    phone_masked: str
    status: str
    consent_given: bool
    consent_timestamp: Optional[datetime] = None
    subscribed_areas: List[str]
    subscribed_itinerary_id: Optional[str] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class DeviceRegisterRequest(BaseModel):
    installation_id: str = Field(..., min_length=5, max_length=100)
    fcm_token: Optional[str] = Field(None, max_length=255)
    platform: str = "android"
    app_version: str = "1.0.0"


class DeviceResponse(BaseModel):
    installation_id: str
    fcm_token_registered: bool
    platform: str
    is_active: bool


class NotificationDeliveryResponse(BaseModel):
    id: str
    alert_id: str
    channel: str
    recipient_address: str
    provider_message_id: Optional[str] = None
    status: str
    is_simulated: bool
    error_details: Optional[str] = None
    created_at: datetime
    delivered_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class TestAlarmRequest(BaseModel):
    hazard_category: str = "severe_weather"
    severity: str = "WARNING"
    target_phone: Optional[str] = None
    installation_id: Optional[str] = None
