"""Disaster Alerts API endpoints."""
import json
import logging
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Header, Query, status
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.database.session import get_db
from app.core.security import verify_admin_api_key, decode_access_token
from app.models.disaster_alert import DisasterAlert
from app.models.outbox import OutboxEvent
from app.models.notification_delivery import NotificationDelivery
from app.clients.ndma_sachet import ndma_sachet_client
from app.schemas.disaster import (
    DisasterAlertCreate,
    DisasterAlertUpdate,
    DisasterAlertResponse,
    NotificationDeliveryResponse,
    TestAlarmRequest,
)
from app.jobs.outbox_processor import process_outbox_events

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/disaster-alerts", tags=["Disaster Alerts"])


async def require_admin(
    x_admin_key: Optional[str] = Header(None, alias="X-Admin-Key"),
    authorization: Optional[str] = Header(None),
):
    """Enforces admin authentication via API key or JWT bearer token."""
    if verify_admin_api_key(x_admin_key):
        return {"role": "admin", "method": "api_key"}

    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ")[1]
        payload = decode_access_token(token)
        if payload and payload.get("role") == "admin":
            return payload

    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail="Unauthorized. Valid X-Admin-Key header or admin Bearer token required.",
    )


@router.get("", response_model=List[DisasterAlertResponse])
async def list_disaster_alerts(
    hazard_category: Optional[str] = Query(None),
    severity: Optional[str] = Query(None),
    include_cancelled: bool = Query(False),
    include_simulated: bool = Query(False, description="Only return genuine non-simulated alerts unless explicitly requested"),
    include_expired: bool = Query(False, description="Exclude expired alerts by default"),
    db: AsyncSession = Depends(get_db),
):
    """List genuine active and updated disaster alerts, optionally filtered by category and severity."""
    now = datetime.now(timezone.utc)
    stmt = select(DisasterAlert)
    if not include_cancelled:
        stmt = stmt.where(DisasterAlert.status.in_(["active", "updated"]))
    if not include_simulated:
        stmt = stmt.where(DisasterAlert.is_simulated.is_(False))
    if not include_expired:
        stmt = stmt.where(or_(DisasterAlert.expires_at.is_(None), DisasterAlert.expires_at > now))
    if hazard_category:
        stmt = stmt.where(DisasterAlert.hazard_category == hazard_category)
    if severity:
        stmt = stmt.where(DisasterAlert.severity == severity.upper())

    stmt = stmt.order_by(desc(DisasterAlert.published_at))
    result = await db.execute(stmt)
    return result.scalars().all()


@router.get("/{alert_id}", response_model=DisasterAlertResponse)
async def get_disaster_alert(alert_id: str, db: AsyncSession = Depends(get_db)):
    """Fetch details of a single disaster alert."""
    stmt = select(DisasterAlert).where(DisasterAlert.id == alert_id)
    result = await db.execute(stmt)
    alert = result.scalar_one_or_none()
    if not alert:
        raise HTTPException(status_code=404, detail="Disaster alert not found")
    return alert


@router.post("", response_model=DisasterAlertResponse, status_code=status.HTTP_201_CREATED)
async def create_disaster_alert(
    payload: DisasterAlertCreate,
    admin: dict = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Create a new authorized disaster alert with transactional outbox delivery event."""
    now = datetime.now(timezone.utc)
    published_at = payload.published_at or now

    alert = DisasterAlert(
        title=payload.title,
        description=payload.description,
        hazard_category=payload.hazard_category,
        severity=payload.severity,
        status="active",
        version=1,
        is_simulated=payload.is_simulated,
        issuing_authority=payload.issuing_authority,
        source_url=payload.source_url,
        source_verification_status=payload.source_verification_status,
        required_protective_instructions=payload.required_protective_instructions,
        center_latitude=payload.center_latitude,
        center_longitude=payload.center_longitude,
        affected_radius_meters=payload.affected_radius_meters,
        polygon_geojson=payload.polygon_geojson,
        affected_route_id=payload.affected_route_id,
        published_at=published_at,
        received_at=now,
        effective_at=payload.effective_at or now,
        expires_at=payload.expires_at,
    )
    db.add(alert)
    await db.flush()  # populate alert.id

    # Create transactional Outbox Event
    outbox_event = OutboxEvent(
        event_type="DISASTER_ALERT_CREATED",
        payload_json=json.dumps({"alert_id": alert.id, "version": alert.version}),
        status="PENDING",
    )
    db.add(outbox_event)
    await db.commit()
    await db.refresh(alert)

    # Trigger immediate outbox processing for fast dispatch
    try:
        await process_outbox_events(session=db)
    except Exception as ex:
        logger.warning("Immediate outbox dispatch error (will be retried by worker): %s", ex)

    return alert


@router.put("/{alert_id}", response_model=DisasterAlertResponse)
async def update_disaster_alert(
    alert_id: str,
    payload: DisasterAlertUpdate,
    admin: dict = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Update an existing disaster alert, incrementing version and generating outbox event."""
    stmt = select(DisasterAlert).where(DisasterAlert.id == alert_id)
    result = await db.execute(stmt)
    alert = result.scalar_one_or_none()
    if not alert:
        raise HTTPException(status_code=404, detail="Disaster alert not found")

    if payload.title is not None:
        alert.title = payload.title
    if payload.description is not None:
        alert.description = payload.description
    if payload.severity is not None:
        alert.severity = payload.severity.upper()
    if payload.required_protective_instructions is not None:
        alert.required_protective_instructions = payload.required_protective_instructions
    if payload.affected_radius_meters is not None:
        alert.affected_radius_meters = payload.affected_radius_meters
    if payload.polygon_geojson is not None:
        alert.polygon_geojson = payload.polygon_geojson
    if payload.expires_at is not None:
        alert.expires_at = payload.expires_at

    alert.version += 1
    alert.status = "updated"

    # Transactional Outbox Event
    outbox_event = OutboxEvent(
        event_type="DISASTER_ALERT_UPDATED",
        payload_json=json.dumps({"alert_id": alert.id, "version": alert.version}),
        status="PENDING",
    )
    db.add(outbox_event)
    await db.commit()
    await db.refresh(alert)

    try:
        await process_outbox_events(session=db)
    except Exception as ex:
        logger.warning("Immediate outbox update dispatch error: %s", ex)

    return alert


@router.post("/{alert_id}/cancel", response_model=DisasterAlertResponse)
async def cancel_disaster_alert(
    alert_id: str,
    admin: dict = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Cancel an active disaster alert, marking status cancelled and emitting outbox event."""
    stmt = select(DisasterAlert).where(DisasterAlert.id == alert_id)
    result = await db.execute(stmt)
    alert = result.scalar_one_or_none()
    if not alert:
        raise HTTPException(status_code=404, detail="Disaster alert not found")

    alert.status = "cancelled"
    outbox_event = OutboxEvent(
        event_type="DISASTER_ALERT_CANCELLED",
        payload_json=json.dumps({"alert_id": alert.id, "version": alert.version}),
        status="PENDING",
    )
    db.add(outbox_event)
    await db.commit()
    await db.refresh(alert)

    try:
        await process_outbox_events(session=db)
    except Exception as ex:
        logger.warning("Immediate outbox cancel dispatch error: %s", ex)

    return alert


@router.get("/{alert_id}/deliveries", response_model=List[NotificationDeliveryResponse])
async def list_alert_deliveries(alert_id: str, db: AsyncSession = Depends(get_db)):
    """Inspect delivery status and audit records for an alert."""
    stmt = (
        select(NotificationDelivery)
        .where(NotificationDelivery.alert_id == alert_id)
        .order_by(desc(NotificationDelivery.created_at))
    )
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("/test-alarm", response_model=DisasterAlertResponse)
async def test_alarm(
    payload: TestAlarmRequest,
    db: AsyncSession = Depends(get_db),
):
    """Generates an explicit test alarm for verifying alarms, sounds, and channels."""
    now = datetime.now(timezone.utc)
    alert = DisasterAlert(
        title="[TEST ALARM] Safety Siren & Broadcast Test",
        description="This is an automated test of the emergency warning system. Test alarm; no real disaster.",
        hazard_category=payload.hazard_category,
        severity=payload.severity,
        status="active",
        version=1,
        is_simulated=True,
        issuing_authority="Tourist Safety Platform (Diagnostic Test)",
        source_verification_status="SIMULATED",
        required_protective_instructions="No action required. Test alarm; no real disaster. Confirm sound and notification display.",
        center_latitude=32.3700,
        center_longitude=77.2400,
        affected_radius_meters=10000.0,
        published_at=now,
        received_at=now,
        effective_at=now,
    )
    db.add(alert)
    await db.flush()

    outbox_event = OutboxEvent(
        event_type="DISASTER_ALERT_CREATED",
        payload_json=json.dumps({"alert_id": alert.id, "version": alert.version}),
        status="PENDING",
    )
    db.add(outbox_event)
    await db.commit()
    await db.refresh(alert)

    try:
        await process_outbox_events(session=db)
    except Exception as ex:
        logger.warning("Test alarm dispatch exception: %s", ex)

    return alert


@router.post("/sync-sachet", status_code=status.HTTP_200_OK)
async def sync_sachet_disaster_alerts(db: AsyncSession = Depends(get_db)):
    """Triggers an authorized on-demand synchronization of official NDMA SACHET disaster alert CAP feeds."""
    try:
        count = await ndma_sachet_client.sync_sachet_alerts(db, max_fetch_details=10)
        return {"status": "SUCCESS", "synced_count": count, "provider": "NDMA SACHET CAP 1.2"}
    except Exception as e:
        logger.error(f"Failed to sync NDMA SACHET alerts: {e}")
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=f"NDMA SACHET sync failed: {str(e)}")
