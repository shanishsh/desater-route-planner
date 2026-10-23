"""MapPoint (emergency, medical, police, visitor info, transport) model."""
import uuid
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy import String, Float, DateTime, Text, Index
from sqlalchemy.orm import Mapped, mapped_column
from app.database.base import Base, TimestampMixin


class MapPoint(Base, TimestampMixin):
    __tablename__ = "map_points"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    category: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    # Categories: emergency_service, medical, police, visitor_info, transport, verified_public
    latitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    longitude: Mapped[float] = mapped_column(Float, nullable=False, index=True)
    address: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    phone: Mapped[Optional[str]] = mapped_column(String(50), nullable=True)
    description: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    verification_status: Mapped[str] = mapped_column(String(50), default="verified", nullable=False)  # verified, unverified
    verification_timestamp: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        nullable=False,
    )
    provenance: Mapped[str] = mapped_column(String(255), default="Official Public Emergency Registry", nullable=False)

    __table_args__ = (
        Index("ix_map_points_lat_lon", "latitude", "longitude"),
    )

