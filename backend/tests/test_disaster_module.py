"""Comprehensive unit and integration tests for Disaster Alert, SMS Fallback, Outbox, and Subscriptions."""
import json
import pytest
from datetime import datetime, timezone
from sqlalchemy import select

from app.models.disaster_alert import DisasterAlert
from app.models.subscriber import Subscriber
from app.models.device_installation import DeviceInstallation
from app.models.outbox import OutboxEvent
from app.models.notification_delivery import NotificationDelivery
from app.services.sms.mock_provider import mock_sms_provider
from app.services.push.mock_fcm_provider import mock_fcm_provider
from app.services.notification_dispatcher import NotificationDispatcher
from app.config import settings


@pytest.mark.asyncio
async def test_subscriber_registration_and_otp(client, test_db):
    """Test mobile number registration, OTP hashing, and SMS dispatch."""
    mock_sms_provider.clear()
    phone = "+919876543210"

    response = await client.post("/api/v1/subscribers/register", json={"phone_e164": phone})
    assert response.status_code == 200
    data = response.json()
    assert data["phone_masked"] == "+91****3210"

    # Verify Subscriber record in DB
    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    res = await test_db.execute(stmt)
    sub = res.scalar_one_or_none()
    assert sub is not None
    assert sub.status == "PENDING_VERIFICATION"
    assert sub.hashed_otp is not None
    assert sub.hashed_otp != "123456"  # MUST NOT be plaintext!

    # Verify mock SMS was recorded
    assert len(mock_sms_provider.dispatched_messages) == 1
    assert "123456" in mock_sms_provider.dispatched_messages[0]["message"]


@pytest.mark.asyncio
async def test_subscriber_otp_verification_consent_required(client, test_db):
    """Test OTP verification enforces explicit consent."""
    phone = "+919876543210"
    await client.post("/api/v1/subscribers/register", json={"phone_e164": phone})

    # Attempt to verify WITHOUT consent -> must be rejected
    res_no_consent = await client.post(
        "/api/v1/subscribers/verify-otp",
        json={
            "phone_e164": phone,
            "otp": "123456",
            "consent_given": False,
        },
    )
    assert res_no_consent.status_code == 400
    assert "consent is required" in res_no_consent.json()["detail"].lower()

    # Verify WITH explicit consent -> succeeds
    res_with_consent = await client.post(
        "/api/v1/subscribers/verify-otp",
        json={
            "phone_e164": phone,
            "otp": "123456",
            "consent_given": True,
            "consent_version": "v1.0",
            "subscribed_areas": ["Kullu", "Rohtang Pass"],
        },
    )
    assert res_with_consent.status_code == 200
    sub_data = res_with_consent.json()
    assert sub_data["status"] == "ACTIVE"
    assert sub_data["consent_given"] is True
    assert "Kullu" in sub_data["subscribed_areas"]


@pytest.mark.asyncio
async def test_subscriber_unsubscribe(client, test_db):
    """Test subscriber opt-out and unsubscription."""
    phone = "+919876543210"
    await client.post("/api/v1/subscribers/register", json={"phone_e164": phone})
    await client.post(
        "/api/v1/subscribers/verify-otp",
        json={"phone_e164": phone, "otp": "123456", "consent_given": True},
    )

    unsub_res = await client.post("/api/v1/subscribers/unsubscribe", json={"phone_e164": phone})
    assert unsub_res.status_code == 200

    stmt = select(Subscriber).where(Subscriber.phone_e164 == phone)
    res = await test_db.execute(stmt)
    sub = res.scalar_one_or_none()
    assert sub.status == "UNSUBSCRIBED"
    assert sub.consent_given is False


@pytest.mark.asyncio
async def test_device_push_registration(client, test_db):
    """Test device installation registration and FCM token updates."""
    inst_id = "test-device-uuid-12345"
    fcm_token = "fcm_mock_token_abc123xyz456"

    reg_res = await client.post(
        "/api/v1/devices/register",
        json={
            "installation_id": inst_id,
            "fcm_token": fcm_token,
            "platform": "android",
            "app_version": "1.0.0",
        },
    )
    assert reg_res.status_code == 200
    assert reg_res.json()["fcm_token_registered"] is True

    # Verify DB
    stmt = select(DeviceInstallation).where(DeviceInstallation.installation_id == inst_id)
    res = await test_db.execute(stmt)
    dev = res.scalar_one_or_none()
    assert dev is not None
    assert dev.fcm_token == fcm_token
    assert dev.is_active is True


@pytest.mark.asyncio
async def test_disaster_alert_admin_auth_and_creation(client, test_db):
    """Test disaster alert creation requires admin authorization and emits outbox events."""
    alert_payload = {
        "title": "Severe Flash Flood Warning",
        "description": "Rapid water level increase detected along Beas River and Rohtang valley slopes.",
        "hazard_category": "flash_flood",
        "severity": "CRITICAL",
        "issuing_authority": "Himachal Pradesh State Disaster Management Authority",
        "required_protective_instructions": "Evacuate low-lying riverbanks immediately. Climb to higher ridges.",
        "center_latitude": 32.3700,
        "center_longitude": 77.2400,
        "affected_radius_meters": 5000.0,
        "is_simulated": True,
    }

    # 1. Reject without admin key
    res_unauth = await client.post("/api/v1/disaster-alerts", json=alert_payload)
    assert res_unauth.status_code == 403

    # 2. Succeed with admin key
    headers = {"X-Admin-Key": settings.ADMIN_API_KEY}
    res_auth = await client.post("/api/v1/disaster-alerts", json=alert_payload, headers=headers)
    assert res_auth.status_code == 201
    created_alert = res_auth.json()
    assert created_alert["version"] == 1
    assert created_alert["status"] == "active"
    alert_id = created_alert["id"]

    # 3. Verify OutboxEvent exists in DB
    stmt = select(OutboxEvent).where(OutboxEvent.event_type == "DISASTER_ALERT_CREATED")
    outbox_res = await test_db.execute(stmt)
    event = outbox_res.scalar_one_or_none()
    assert event is not None
    payload = json.loads(event.payload_json)
    assert payload["alert_id"] == alert_id


@pytest.mark.asyncio
async def test_notification_dispatch_and_deduplication(client, test_db):
    """Test severity policies, multi-channel dispatch, and delivery deduplication."""
    mock_sms_provider.clear()
    mock_fcm_provider.clear()

    # 1. Register a verified subscriber
    phone = "+919876543210"
    await client.post("/api/v1/subscribers/register", json={"phone_e164": phone})
    await client.post(
        "/api/v1/subscribers/verify-otp",
        json={
            "phone_e164": phone,
            "otp": "123456",
            "consent_given": True,
            "subscribed_areas": ["all"],
        },
    )

    # 2. Register an active device
    await client.post(
        "/api/v1/devices/register",
        json={"installation_id": "device-1", "fcm_token": "fcm-tok-123"},
    )

    # 3. Create a SEVERE disaster alert
    headers = {"X-Admin-Key": settings.ADMIN_API_KEY}
    alert_res = await client.post(
        "/api/v1/disaster-alerts",
        json={
            "title": "Severe Landslide Warning",
            "description": "Active debris flow reported near Gulaba checkpost.",
            "hazard_category": "landslide",
            "severity": "SEVERE",
            "issuing_authority": "Himachal SDMA",
            "required_protective_instructions": "Halt vehicle movement immediately.",
            "center_latitude": 32.3200,
            "center_longitude": 77.2100,
            "affected_radius_meters": 4000.0,
            "is_simulated": True,
        },
        headers=headers,
    )
    alert_id = alert_res.json()["id"]

    # 4. Check that deliveries were dispatched automatically via outbox
    deliv_res = await client.get(f"/api/v1/disaster-alerts/{alert_id}/deliveries")
    assert deliv_res.status_code == 200
    deliveries = deliv_res.json()
    assert len(deliveries) >= 2

    # Check channels dispatched
    channels = {d["channel"] for d in deliveries}
    assert "FCM_PUSH" in channels
    assert "SMS" in channels

    # Verify simulation safeguard applied to SMS message
    assert len(mock_sms_provider.dispatched_messages) >= 1
    last_sms = mock_sms_provider.dispatched_messages[-1]
    assert "[SIMULATION TEST - NO REAL DISASTER]" in last_sms["message"]

    # 5. Check deduplication: Run dispatcher again -> should NOT create duplicate deliveries
    dispatcher = NotificationDispatcher(test_db)
    second_deliveries = await dispatcher.dispatch_for_alert(alert_id)
    assert len(second_deliveries) == 0


@pytest.mark.asyncio
async def test_alert_update_and_cancellation(client, test_db):
    """Test alert version increments on update and status becomes cancelled on cancel."""
    headers = {"X-Admin-Key": settings.ADMIN_API_KEY}
    create_res = await client.post(
        "/api/v1/disaster-alerts",
        json={
            "title": "Severe Weather Watch",
            "description": "Blizzard warning for high altitude passes.",
            "hazard_category": "severe_weather",
            "severity": "WARNING",
            "issuing_authority": "IMD",
            "required_protective_instructions": "Remain inside shelter.",
            "center_latitude": 32.3700,
            "center_longitude": 77.2400,
            "affected_radius_meters": 3000.0,
            "is_simulated": True,
        },
        headers=headers,
    )
    alert_id = create_res.json()["id"]

    # Update alert
    update_res = await client.put(
        f"/api/v1/disaster-alerts/{alert_id}",
        json={"title": "Severe Weather Watch - Upgraded", "severity": "SEVERE"},
        headers=headers,
    )
    assert update_res.status_code == 200
    updated_data = update_res.json()
    assert updated_data["version"] == 2
    assert updated_data["status"] == "updated"

    # Cancel alert
    cancel_res = await client.post(
        f"/api/v1/disaster-alerts/{alert_id}/cancel",
        headers=headers,
    )
    assert cancel_res.status_code == 200
    assert cancel_res.json()["status"] == "cancelled"
