"""Crowd indicator model."""
import uuid
from datetime import datetime, timezone
from sqlalchemy import String, Boolean, DateTime, ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column
from app.database.base import Base, TimestampMixin


class CrowdSnapshot(Base, TimestampMixin):
    __tablename__ = "crowd_snapshots"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    itinerary_id: Mapped[str] = mapped_column(String(36), ForeignKey("itineraries.id", ondelete="CASCADE"), nullable=False, index=True)
    location_name: Mapped[str] = mapped_column(String(150), nullable=False)
    crowd_level: Mapped[str] = mapped_column(String(50), nullable=False)  # low, moderate, high, congested
    density_description: Mapped[str] = mapped_column(String(200), default="Normal flow", nullable=False)
    is_simulation: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

    __table_args__ = (
        Index("ix_crowd_itinerary_observed", "itinerary_id", "observed_at"),
    )

