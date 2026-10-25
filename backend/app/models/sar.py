"""Search and Rescue (SAR) incident and trekker check-in persistence models."""
import uuid
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy import String, Float, Integer, DateTime, Text, Index
from sqlalchemy.orm import Mapped, mapped_column
from app.database.base import Base, TimestampMixin


class SARIncident(Base, TimestampMixin):
    """Represents an emergency Search and Rescue distress beacon or incident."""
    __tablename__ = "sar_incidents"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    trip_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    latitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    longitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    elevation_m: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    battery_pct: Mapped[int] = mapped_column(Integer, default=100, nullable=False)
    emergency_type: Mapped[str] = mapped_column(String(50), default="MANUAL_PANIC", nullable=False)
    status: Mapped[str] = mapped_column(String(50), default="ACTIVE_RESCUE", nullable=False, index=True)
    escalation_level: Mapped[str] = mapped_column(String(50), default="RED_PRIORITY_1", nullable=False)
    details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    route_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True, index=True)

    __table_args__ = (
        Index("ix_sar_incidents_lat_lon", "latitude", "longitude"),
    )


class TrekkerCheckIn(Base, TimestampMixin):
    """Represents a periodic safety check-in from a trekker along a trail."""
    __tablename__ = "trekker_checkins"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    trip_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    latitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    longitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    elevation_m: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    gps_accuracy_m: Mapped[float] = mapped_column(Float, default=10.0, nullable=False)
    battery_pct: Mapped[int] = mapped_column(Integer, default=100, nullable=False)
    status: Mapped[str] = mapped_column(String(50), default="OK", nullable=False)
    notes: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    next_check_in_due: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        Index("ix_trekker_checkins_trip_received", "trip_id", "received_at"),
    )

