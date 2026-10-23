"""Subscriber model for registered mobile numbers, SMS subscriptions, and consent records."""
import uuid
from datetime import datetime, timezone
from typing import Optional, List
from sqlalchemy import String, Integer, Boolean, DateTime, ForeignKey, Text, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.database.base import Base, TimestampMixin


class Subscriber(Base, TimestampMixin):
    __tablename__ = "subscribers"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    phone_e164: Mapped[str] = mapped_column(String(30), unique=True, nullable=False, index=True)
    
    # Status: PENDING_VERIFICATION, VERIFIED, ACTIVE, UNSUBSCRIBED
    status: Mapped[str] = mapped_column(String(50), default="PENDING_VERIFICATION", nullable=False, index=True)
    
    # Security: Hashed OTP and expiration (Never stored plaintext)
    hashed_otp: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    otp_expires_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    otp_attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    
    # Explicit User Consent
    consent_given: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    consent_timestamp: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    consent_version: Mapped[str] = mapped_column(String(50), default="v1.0", nullable=False)
    consent_ip: Mapped[Optional[str]] = mapped_column(String(100), nullable=True)
    
    # Targeting preferences: JSON array of districts/regions e.g. ["Kullu", "Manali", "Rohtang"]
    subscribed_areas_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    subscribed_itinerary_id: Mapped[Optional[str]] = mapped_column(
        String(36), ForeignKey("itineraries.id", ondelete="SET NULL"), nullable=True, index=True
    )

    deliveries: Mapped[List["NotificationDelivery"]] = relationship(
        "NotificationDelivery", back_populates="subscriber"
    )

    __table_args__ = (
        Index("ix_subscribers_phone_status", "phone_e164", "status"),
    )

