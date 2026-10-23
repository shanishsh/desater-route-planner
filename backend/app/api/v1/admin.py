"""Administrative privileged API endpoints."""
from typing import List, Optional
from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database.session import get_db
from app.models.itinerary import Itinerary, RouteSegment
from app.models.audit import AuditEvent
from app.services.alert_service import AlertService
from app.schemas.alert import AlertResponse, AlertCreate
from app.core.security import decode_access_token
from app.config import settings

router = APIRouter(prefix="/admin", tags=["Admin Privileged"])


async def require_admin(authorization: Optional[str] = Header(None)) -> dict:
    """Verifies that the caller possesses a valid admin JWT token or dev master key."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Admin Bearer token required",
        )
    token = authorization.split(" ")[1]
    payload = decode_access_token(token)
    if not payload or payload.get("role") != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin privilege required",
        )
    return payload


@router.post("/alerts", response_model=AlertResponse)
async def create_alert(
    alert_in: AlertCreate,
    admin: dict = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    service = AlertService(db)
    created = await service.create_alert(alert_in)

    # Record audit log
    audit = AuditEvent(
        user_id=admin.get("sub"),
        action="CREATE_ALERT",
        resource_type="Alert",
        resource_id=created.id,
        details={"title": created.title, "severity": created.severity},
    )
    db.add(audit)
    await db.commit()

    return created


@router.post("/demo/reset", response_model=List[AlertResponse])
async def reset_demo_alerts(
    admin: dict = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
):
    """Resets the three seeded simulated alerts relative to current timestamp."""
    # Find active itinerary and first segment if available
    itin_result = await db.execute(select(Itinerary).where(Itinerary.is_active.is_(True)).limit(1))
    active_itin = itin_result.scalar_one_or_none()

    itinerary_id = active_itin.id if active_itin else None
    segment_id = None
    if active_itin:
        seg_result = await db.execute(
            select(RouteSegment).where(RouteSegment.itinerary_id == active_itin.id).limit(1)
        )
        seg = seg_result.scalar_one_or_none()
        segment_id = seg.id if seg else None

    service = AlertService(db)
    alerts = await service.seed_demo_alerts(itinerary_id=itinerary_id, segment_id=segment_id)

    # Record audit event
    audit = AuditEvent(
        user_id=admin.get("sub"),
        action="RESET_DEMO_ALERTS",
        resource_type="Alerts",
        resource_id=None,
        details={"seeded_count": len(alerts)},
    )
    db.add(audit)
    await db.commit()

    return alerts

