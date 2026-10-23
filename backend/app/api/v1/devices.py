"""Device Installation API for Android FCM push token registration and lifecycle."""
import logging
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database.session import get_db
from app.models.device_installation import DeviceInstallation
from app.schemas.disaster import DeviceRegisterRequest, DeviceResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/devices", tags=["Device Push Registration"])


@router.post("/register", response_model=DeviceResponse, status_code=status.HTTP_200_OK)
async def register_device(
    payload: DeviceRegisterRequest,
    db: AsyncSession = Depends(get_db),
):
    """Register or update an Android device installation and its FCM token."""
    stmt = select(DeviceInstallation).where(DeviceInstallation.installation_id == payload.installation_id)
    result = await db.execute(stmt)
    device = result.scalar_one_or_none()

    now = datetime.now(timezone.utc)
    if not device:
        device = DeviceInstallation(
            installation_id=payload.installation_id,
            fcm_token=payload.fcm_token,
            platform=payload.platform,
            app_version=payload.app_version,
            is_active=True,
            last_seen_at=now,
        )
        db.add(device)
    else:
        if payload.fcm_token:
            device.fcm_token = payload.fcm_token
        device.platform = payload.platform
        device.app_version = payload.app_version
        device.is_active = True
        device.last_seen_at = now

    await db.commit()
    await db.refresh(device)

    return DeviceResponse(
        installation_id=device.installation_id,
        fcm_token_registered=bool(device.fcm_token),
        platform=device.platform,
        is_active=device.is_active,
    )


@router.delete("/{installation_id}")
async def unregister_device(
    installation_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Deactivate device and clear FCM token upon app uninstall or opt-out."""
    stmt = select(DeviceInstallation).where(DeviceInstallation.installation_id == installation_id)
    result = await db.execute(stmt)
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(status_code=404, detail="Device not found")

    device.is_active = False
    device.fcm_token = None
    await db.commit()

    return {"message": "Device successfully unregistered"}

