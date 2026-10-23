"""MapPoint API endpoints."""
from typing import Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.services.emergency_service import EmergencyService
from app.schemas.map_point import MapPointResponse
from app.schemas.common import PaginatedResponse

router = APIRouter(prefix="/map-points", tags=["Map Points"])


@router.get("", response_model=PaginatedResponse[MapPointResponse])
async def list_map_points(
    category: Optional[str] = Query(None, description="Category filter (medical, police, emergency_service, etc.)"),
    bbox: Optional[str] = Query(None, description="Bounding box formatted as 'min_lon,min_lat,max_lon,max_lat'"),
    min_lat: Optional[float] = Query(None, ge=-90.0, le=90.0),
    max_lat: Optional[float] = Query(None, ge=-90.0, le=90.0),
    min_lon: Optional[float] = Query(None, ge=-180.0, le=180.0),
    max_lon: Optional[float] = Query(None, ge=-180.0, le=180.0),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    # Parse bbox parameter if provided
    if bbox:
        parts = [float(p.strip()) for p in bbox.split(",")]
        if len(parts) == 4:
            min_lon, min_lat, max_lon, max_lat = parts

    service = EmergencyService(db)
    items, total = await service.list_points(
        category=category,
        min_lat=min_lat,
        max_lat=max_lat,
        min_lon=min_lon,
        max_lon=max_lon,
        limit=limit,
        offset=offset,
    )
    return PaginatedResponse(
        items=items,
        total=total,
        limit=limit,
        offset=offset,
        has_more=(offset + limit) < total,
    )


@router.get("/{point_id}", response_model=MapPointResponse)
async def get_map_point(point_id: str, db: AsyncSession = Depends(get_db)):
    service = EmergencyService(db)
    return await service.get_point(point_id)

