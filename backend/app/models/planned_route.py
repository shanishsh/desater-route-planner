"""ORM models for Planned Routes, Corridor Waypoint Conditions, and Active Trips."""
import uuid
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any
from sqlalchemy import String, Float, Integer, Boolean, DateTime, ForeignKey, Text, JSON
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class PlannedRoute(Base, TimestampMixin):
    __tablename__ = "planned_routes"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title: Mapped[str] = mapped_column(String(250), nullable=False)
    origin_name: Mapped[str] = mapped_column(String(200), nullable=False)
    origin_lat: Mapped[float] = mapped_column(Float, nullable=False)
    origin_lon: Mapped[float] = mapped_column(Float, nullable=False)
    destination_name: Mapped[str] = mapped_column(String(200), nullable=False)
    dest_lat: Mapped[float] = mapped_column(Float, nullable=False)
    dest_lon: Mapped[float] = mapped_column(Float, nullable=False)
    travel_mode: Mapped[str] = mapped_column(String(50), default="driving", nullable=False)
    distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    duration_minutes: Mapped[float] = mapped_column(Float, nullable=False)
    geometry_geojson: Mapped[Dict[str, Any]] = mapped_column(JSON, nullable=False)
    overall_hazard_score: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    overall_safety_status: Mapped[str] = mapped_column(String(50), default="SAFE", nullable=False)
    route_version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    routing_provider: Mapped[str] = mapped_column(String(50), default="OSRM", nullable=False)
    conditions_data: Mapped[Optional[List[Dict[str, Any]]]] = mapped_column(JSON, nullable=True)
    alternatives_data: Mapped[Optional[List[Dict[str, Any]]]] = mapped_column(JSON, nullable=True)
    data_freshness_data: Mapped[Optional[Dict[str, Any]]] = mapped_column(JSON, nullable=True)
    analysis_data: Mapped[Optional[Dict[str, Any]]] = mapped_column(JSON, nullable=True)
    last_analyzed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    trips: Mapped[List["ActiveTrip"]] = relationship(
        "ActiveTrip", back_populates="route", cascade="all, delete-orphan"
    )


class ActiveTrip(Base, TimestampMixin):
    __tablename__ = "active_trips"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    route_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("planned_routes.id", ondelete="CASCADE"), nullable=False, index=True
    )
    user_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    status: Mapped[str] = mapped_column(String(50), default="ACTIVE", nullable=False, index=True)
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False
    )
    last_latitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    last_longitude: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    current_segment_index: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    progress_pct: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    deviation_detected: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    last_synced_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False
    )
    reroute_suggested: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    route: Mapped["PlannedRoute"] = relationship("PlannedRoute", back_populates="trips")

