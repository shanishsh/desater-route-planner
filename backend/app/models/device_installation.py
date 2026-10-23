"""Device Installation model for FCM push notification tokens and device tracking."""
import uuid
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import String, Boolean, DateTime, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class DeviceInstallation(Base, TimestampMixin):
    __tablename__ = "device_installations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    installation_id: Mapped[str] = mapped_column(String(100), unique=True, nullable=False, index=True)
    fcm_token: Mapped[Optional[str]] = mapped_column(String(255), nullable=True, index=True)
    platform: Mapped[str] = mapped_column(String(50), default="android", nullable=False)
    app_version: Mapped[str] = mapped_column(String(50), default="1.0.0", nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False
    )

    deliveries: Mapped[List["NotificationDelivery"]] = relationship(
        "NotificationDelivery", back_populates="device"
    )

    __table_args__ = (
        Index("ix_device_installations_active_fcm", "is_active", "fcm_token"),
    )

