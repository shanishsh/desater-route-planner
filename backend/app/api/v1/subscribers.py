"""Subscriber management API for SMS alert registration, OTP verification, and consent."""
import hashlib
import json
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database.session import get_db
from app.models.subscriber import Subscriber
from app.models.audit import AuditEvent
from app.schemas.disaster import (
    SubscriberRegisterRequest,
    SubscriberVerifyOTPRequest,
    SubscriberConsentUpdateRequest,
    SubscriberResponse,
)
from app.services.sms.factory import get_sms_provider

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/subscribers", tags=["SMS Alert Subscribers"])


def hash_otp(otp: str, phone: str) -> str:
    """Securely hash OTP using SHA-256 with phone as salt (Never stored in plaintext)."""
    return hashlib.sha256(f"{phone}:{otp}:{settings.SECRET_KEY}".encode("utf-8")).hexdigest()


def mask_phone(phone: str) -> str:
    if len(phone) > 7:
        return phone[:3] + "****" + phone[-4:]
    return "***"


@router.post("/register", status_code=status.HTTP_200_OK)
async def register_subscriber(
    payload: SubscriberRegisterRequest,
    db: AsyncSession = Depends(get_db),
):
    """Register a mobile phone number and send a verification OTP via SMS."""
    phone = payload.phone_e164
    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    result = await db.execute(stmt)
    subscriber = result.scalar_one_or_none()

    if not subscriber:
        subscriber = Subscriber(phone_e164=phone, status="PENDING_VERIFICATION")
        db.add(subscriber)

    # Use configured mock OTP in dev or test mode
    otp = settings.MOCK_OTP_CODE
    subscriber.hashed_otp = hash_otp(otp, phone)
    subscriber.otp_expires_at = datetime.now(timezone.utc) + timedelta(minutes=settings.OTP_EXPIRE_MINUTES)
    subscriber.otp_attempts = 0

    await db.commit()

    # Dispatch OTP via SMS provider
    sms_provider = get_sms_provider()
    msg = f"Your Tourist Safety verification OTP is: {otp}. Valid for {settings.OTP_EXPIRE_MINUTES} minutes. Never share this code."
    await sms_provider.send_sms(
        to_phone=phone,
        message=msg,
        alert_id="otp-verification",
        idempotency_key=f"otp_{phone}_{datetime.now().strftime('%Y%m%d%H%M')}",
    )

    return {
        "message": "Verification OTP dispatched successfully.",
        "phone_masked": mask_phone(phone),
        "expires_in_minutes": settings.OTP_EXPIRE_MINUTES,
        "is_mock": settings.SMS_PROVIDER == "mock",
    }


@router.post("/verify-otp", response_model=SubscriberResponse)
async def verify_otp(
    payload: SubscriberVerifyOTPRequest,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """Verify OTP, record explicit consent, and activate SMS alert subscription."""
    phone = payload.phone_e164
    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    result = await db.execute(stmt)
    subscriber = result.scalar_one_or_none()

    if not subscriber:
        raise HTTPException(status_code=404, detail="Phone number not registered. Please register first.")

    now = datetime.now(timezone.utc)
    if not subscriber.otp_expires_at:
        raise HTTPException(status_code=400, detail="No active OTP found. Please request a new code.")

    expires_at = subscriber.otp_expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)

    if expires_at < now:
        raise HTTPException(status_code=400, detail="OTP has expired. Please request a new code.")

    # Validate OTP hash
    expected_hash = hash_otp(payload.otp.strip(), phone)
    if subscriber.hashed_otp != expected_hash:
        subscriber.otp_attempts += 1
        await db.commit()
        raise HTTPException(status_code=400, detail="Invalid OTP code.")

    # Validate explicit consent requirement
    if not payload.consent_given:
        raise HTTPException(
            status_code=400,
            detail="Explicit consent is required to receive emergency disaster warnings.",
        )

    # Activate subscriber and record consent
    subscriber.status = "ACTIVE"
    subscriber.hashed_otp = None  # Clear OTP after successful verification
    subscriber.consent_given = True
    subscriber.consent_timestamp = now
    subscriber.consent_version = payload.consent_version
    subscriber.consent_ip = request.client.host if request.client else "unknown"
    if payload.subscribed_areas is not None:
        subscriber.subscribed_areas_json = json.dumps(payload.subscribed_areas)
    if payload.subscribed_itinerary_id is not None:
        subscriber.subscribed_itinerary_id = payload.subscribed_itinerary_id

    # Record Audit Event
    audit = AuditEvent(
        action="SUBSCRIBER_CONSENT_GRANTED",
        resource_type="Subscriber",
        resource_id=subscriber.id,
        details={
            "consent_version": payload.consent_version,
            "areas": payload.subscribed_areas or [],
            "phone_masked": mask_phone(phone),
        },
        ip_address=subscriber.consent_ip,
    )
    db.add(audit)
    await db.commit()
    await db.refresh(subscriber)

    areas_list = []
    if subscriber.subscribed_areas_json:
        try:
            areas_list = json.loads(subscriber.subscribed_areas_json)
        except Exception:
            pass

    return SubscriberResponse(
        id=subscriber.id,
        phone_masked=mask_phone(subscriber.phone_e164),
        status=subscriber.status,
        consent_given=subscriber.consent_given,
        consent_timestamp=subscriber.consent_timestamp,
        subscribed_areas=areas_list,
        subscribed_itinerary_id=subscriber.subscribed_itinerary_id,
        created_at=subscriber.created_at,
    )


@router.post("/consent", response_model=SubscriberResponse)
async def update_consent(
    payload: SubscriberConsentUpdateRequest,
    db: AsyncSession = Depends(get_db),
):
    """Update subscription consent and targeted geographical areas."""
    phone = payload.phone_e164
    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    result = await db.execute(stmt)
    subscriber = result.scalar_one_or_none()

    if not subscriber:
        raise HTTPException(status_code=404, detail="Subscriber not found")

    subscriber.consent_given = payload.consent_given
    if not payload.consent_given:
        subscriber.status = "UNSUBSCRIBED"
    else:
        subscriber.status = "ACTIVE"
        subscriber.consent_timestamp = datetime.now(timezone.utc)

    if payload.subscribed_areas is not None:
        subscriber.subscribed_areas_json = json.dumps(payload.subscribed_areas)
    if payload.subscribed_itinerary_id is not None:
        subscriber.subscribed_itinerary_id = payload.subscribed_itinerary_id

    # Audit log
    audit = AuditEvent(
        action="SUBSCRIBER_CONSENT_UPDATED",
        resource_type="Subscriber",
        resource_id=subscriber.id,
        details={"consent_given": subscriber.consent_given},
    )
    db.add(audit)
    await db.commit()
    await db.refresh(subscriber)

    areas_list = []
    if subscriber.subscribed_areas_json:
        try:
            areas_list = json.loads(subscriber.subscribed_areas_json)
        except Exception:
            pass

    return SubscriberResponse(
        id=subscriber.id,
        phone_masked=mask_phone(subscriber.phone_e164),
        status=subscriber.status,
        consent_given=subscriber.consent_given,
        consent_timestamp=subscriber.consent_timestamp,
        subscribed_areas=areas_list,
        subscribed_itinerary_id=subscriber.subscribed_itinerary_id,
        created_at=subscriber.created_at,
    )


@router.post("/unsubscribe")
async def unsubscribe_subscriber(
    payload: SubscriberRegisterRequest,
    db: AsyncSession = Depends(get_db),
):
    """Opt-out and unsubscribe mobile number from all disaster SMS warnings."""
    phone = payload.phone_e164
    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    result = await db.execute(stmt)
    subscriber = result.scalar_one_or_none()

    if not subscriber:
        raise HTTPException(status_code=404, detail="Subscriber not found")

    subscriber.status = "UNSUBSCRIBED"
    subscriber.consent_given = False

    audit = AuditEvent(
        action="SUBSCRIBER_UNSUBSCRIBED",
        resource_type="Subscriber",
        resource_id=subscriber.id,
        details={"action": "unsubscribed", "phone_masked": mask_phone(phone)},
    )
    db.add(audit)
    await db.commit()

    return {
        "message": "Successfully unsubscribed from emergency disaster SMS alerts.",
        "phone_masked": mask_phone(phone),
    }
