"""Disaster Alert model for high-severity hazards, severe weather, and emergency warnings."""
import uuid
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import String, Integer, Float, Boolean, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class DisasterAlert(Base, TimestampMixin):
    __tablename__ = "disaster_alerts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    
    # Hazard category: flash_flood, heavy_rainfall, landslide, severe_weather, trail_closure, transport_disruption, other
    hazard_category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    # Severity: INFO, WARNING, SEVERE, CRITICAL
    severity: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    # State: active, updated, cancelled, expired
    status: Mapped[str] = mapped_column(String(50), default="active", nullable=False, index=True)
    
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    is_simulated: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False, index=True)
    
    issuing_authority: Mapped[str] = mapped_column(String(150), nullable=False)
    source_url: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    source_verification_status: Mapped[str] = mapped_column(String(50), default="VERIFIED", nullable=False)
    
    required_protective_instructions: Mapped[str] = mapped_column(Text, nullable=False)
    
    # Geographic scope
    center_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    center_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    affected_radius_meters: Mapped[float] = mapped_column(Float, default=5000.0, nullable=False)
    polygon_geojson: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    affected_route_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("itineraries.id", ondelete="SET NULL"), nullable=True, index=True
    )
    
    published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False
    )
    effective_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    deliveries: Mapped[List["NotificationDelivery"]] = relationship(
        "NotificationDelivery", back_populates="disaster_alert", cascade="all, delete-orphan"
    )

    __table_args__ = (
        Index("ix_disaster_alerts_status_severity", "status", "severity"),
    )

