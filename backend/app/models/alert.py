"""Alert model."""
import uuid
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy import String, Integer, Boolean, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class Alert(Base, TimestampMixin):
    __tablename__ = "alerts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    severity: Mapped[str] = mapped_column(String(50), nullable=False, index=True)  # low, medium, high, critical
    category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)  # weather, hazard, transport, crowd
    
    affected_itinerary_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("itineraries.id", ondelete="SET NULL"), nullable=True, index=True
    )
    affected_segment_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("route_segments.id", ondelete="SET NULL"), nullable=True, index=True
    )

    source_name: Mapped[str] = mapped_column(String(100), nullable=False)
    source_url: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    source_published_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    retrieved_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False
    )
    freshness_threshold_seconds: Mapped[int] = mapped_column(Integer, default=3600, nullable=False)
    is_simulation: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)

    itinerary: Mapped[Optional["Itinerary"]] = relationship("Itinerary", back_populates="alerts")  # noqa: F821
    segment: Mapped[Optional["RouteSegment"]] = relationship("RouteSegment", back_populates="alerts")  # noqa: F821

    __table_args__ = (
        Index("ix_alerts_severity_category", "severity", "category"),
    )

