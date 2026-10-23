"""Notification Delivery record tracking message dispatch, status, and audit trail."""
import uuid
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy import String, Boolean, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class NotificationDelivery(Base, TimestampMixin):
    __tablename__ = "notification_deliveries"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    alert_id: Mapped[str] = mapped_column(
        String(36), ForeignKey("disaster_alerts.id", ondelete="CASCADE"), nullable=False, index=True
    )
    # Channel: SMS, FCM_PUSH, LOCAL
    channel: Mapped[str] = mapped_column(String(50), nullable=False, index=True)
    
    subscriber_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("subscribers.id", ondelete="SET NULL"), nullable=True, index=True
    )
    device_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("device_installations.id", ondelete="SET NULL"), nullable=True, index=True
    )
    
    # Masked recipient address (e.g. +91 98****3210 or fcm_token_prefix)
    recipient_address: Mapped[str] = mapped_column(String(100), nullable=False)
    provider_message_id: Mapped[Optional[str]] = mapped_column(String(150), nullable=True, index=True)
    
    # Status: QUEUED, ACCEPTED, SENT, DELIVERED, FAILED, UNKNOWN
    status: Mapped[str] = mapped_column(String(50), default="QUEUED", nullable=False, index=True)
    is_simulated: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    error_details: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    delivered_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    disaster_alert: Mapped["DisasterAlert"] = relationship("DisasterAlert", back_populates="deliveries")  # noqa: F821
    subscriber: Mapped[Optional["Subscriber"]] = relationship("Subscriber", back_populates="deliveries")  # noqa: F821
    device: Mapped[Optional["DeviceInstallation"]] = relationship("DeviceInstallation", back_populates="deliveries")  # noqa: F821

    __table_args__ = (
        Index("ix_notification_deliveries_alert_channel", "alert_id", "channel"),
        Index("ix_notification_deliveries_status_channel", "status", "channel"),
    )

