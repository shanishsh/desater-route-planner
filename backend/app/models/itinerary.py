"""Itinerary and RouteSegment models."""
import uuid
from typing import List, Optional
from sqlalchemy import String, Float, Integer, Boolean, ForeignKey, JSON, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class Itinerary(Base, TimestampMixin):
    __tablename__ = "itineraries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    region: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    difficulty: Mapped[str] = mapped_column(String(50), nullable=False)  # easy, moderate, hard, extreme
    distance_km: Mapped[float] = mapped_column(Float, nullable=False)
    estimated_duration_hours: Mapped[float] = mapped_column(Float, nullable=False)
    start_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    start_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    end_latitude: Mapped[float] = mapped_column(Float, nullable=False)
    end_longitude: Mapped[float] = mapped_column(Float, nullable=False)
    route_geojson: Mapped[dict] = mapped_column(JSON, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    segments: Mapped[List["RouteSegment"]] = relationship(
        "RouteSegment",
        back_populates="itinerary",
        cascade="all, delete-orphan",
        order_by="RouteSegment.sequence_order",
    )
    alerts: Mapped[List["Alert"]] = relationship(  # noqa: F821
        "Alert",
        back_populates="itinerary",
    )


class RouteSegment(Base, TimestampMixin):
    __tablename__ = "route_segments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    itinerary_id: Mapped[str] = mapped_column(String(36), ForeignKey("itineraries.id", ondelete="CASCADE"), nullable=False, index=True)
    sequence_order: Mapped[int] = mapped_column(Integer, nullable=False)
    name: Mapped[str] = mapped_column(String(150), nullable=False)
    elevation_gain_m: Mapped[float] = mapped_column(Float, default=0.0)
    hazard_level: Mapped[str] = mapped_column(String(50), default="low")  # low, medium, high
    segment_geojson: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)

    itinerary: Mapped["Itinerary"] = relationship("Itinerary", back_populates="segments")
    alerts: Mapped[List["Alert"]] = relationship(  # noqa: F821
        "Alert",
        back_populates="segment",
    )

