"""Alert API endpoints."""
from typing import Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.services.alert_service import AlertService
from app.schemas.alert import AlertResponse
from app.schemas.common import PaginatedResponse

router = APIRouter(prefix="/alerts", tags=["Alerts"])


@router.get("", response_model=PaginatedResponse[AlertResponse])
async def list_alerts(
    itinerary_id: Optional[str] = Query(None, description="Filter by affected itinerary"),
    severity: Optional[str] = Query(None, description="Filter by severity (low, medium, high, critical)"),
    category: Optional[str] = Query(None, description="Filter by category (weather, hazard, transport, crowd)"),
    is_stale: Optional[bool] = Query(None, description="Filter by freshness status"),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    service = AlertService(db)
    items, total = await service.list_alerts(
        itinerary_id=itinerary_id,
        severity=severity,
        category=category,
        limit=limit,
        offset=offset,
    )

    # In-memory post-filter for computed is_stale if requested
    if is_stale is not None:
        items = [a for a in items if a.is_stale == is_stale]
        total = len(items)

    return PaginatedResponse(
        items=items,
        total=total,
        limit=limit,
        offset=offset,
        has_more=(offset + limit) < total,
    )


@router.get("/{alert_id}", response_model=AlertResponse)
async def get_alert(alert_id: str, db: AsyncSession = Depends(get_db)):
    service = AlertService(db)
    return await service.get_alert(alert_id)

