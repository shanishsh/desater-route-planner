"""SQLAlchemy PostGIS models for Community Trail Condition Reports and Trekker Interactions."""
import uuid
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any

from sqlalchemy import (
    String,
    Float,
    Integer,
    Boolean,
    DateTime,
    ForeignKey,
    Text,
    JSON,
    Index,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base, TimestampMixin


class TrailConditionReport(Base, TimestampMixin):
    """
    Trekker-submitted mountain obstacle or trail condition report.
    Tracks GPS coordinates, passability, photographic evidence, and community corroboration.
    """
    __tablename__ = "trail_condition_reports"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4())
    )
    category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    severity: Mapped[str] = mapped_column(String(20), nullable=False, default="MEDIUM")
    passability: Mapped[str] = mapped_column(String(40), nullable=False, default="DIFFICULT_REQUIRES_CAUTION")
    details_json: Mapped[Optional[Dict[str, Any]]] = mapped_column(JSON, nullable=True)

    latitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    longitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    location_accuracy_meters: Mapped[float] = mapped_column(Float, nullable=False, default=10.0)
    location_source: Mapped[str] = mapped_column(String(20), nullable=False, default="gps")

    observation_time: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc)
    )
    submission_time: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc)
    )

    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    photo_url: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)

    route_id: Mapped[Optional[str]] = mapped_column(String(36), nullable=True, index=True)
    route_version: Mapped[Optional[int]] = mapped_column(Integer, nullable=True, default=1)
    idempotency_key: Mapped[Optional[str]] = mapped_column(String(100), unique=True, nullable=True, index=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(100), nullable=True, index=True)

    verification_status: Mapped[str] = mapped_column(String(30), nullable=False, default="UNVERIFIED", index=True)
    confirmations_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    disputes_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_confirmed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, index=True)

    interactions: Mapped[List["TrailReportInteraction"]] = relationship(
        "TrailReportInteraction",
        back_populates="report",
        cascade="all, delete-orphan"
    )

    __table_args__ = (
        Index("ix_trail_reports_spatial", "latitude", "longitude"),
        Index("ix_trail_reports_status_expiry", "verification_status", "expires_at"),
    )


class TrailReportInteraction(Base):
    """
    Community peer corroboration or dispute of a trail condition report.
    Enforces anti-abuse deduplication per device/user.
    """
    __tablename__ = "trail_report_interactions"

    id: Mapped[str] = mapped_column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4())
    )
    report_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("trail_condition_reports.id", ondelete="CASCADE"),
        nullable=False,
        index=True
    )
    interaction_type: Mapped[str] = mapped_column(String(20), nullable=False) # CONFIRM, DISPUTE, RESOLVE
    comment: Mapped[Optional[str]] = mapped_column(String(1000), nullable=True)
    device_id: Mapped[Optional[str]] = mapped_column(String(100), nullable=True, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc)
    )

    report: Mapped["TrailConditionReport"] = relationship("TrailConditionReport", back_populates="interactions")

    __table_args__ = (
        Index("ix_trail_interactions_report_device", "report_id", "device_id"),
    )
