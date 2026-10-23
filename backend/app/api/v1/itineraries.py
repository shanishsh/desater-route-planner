"""Itinerary API endpoints."""
from typing import Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db
from app.services.itinerary_service import ItineraryService
from app.schemas.itinerary import ItineraryResponse, ItineraryRouteGeoJSONResponse
from app.schemas.common import PaginatedResponse

router = APIRouter(prefix="/itineraries", tags=["Itineraries"])


@router.get("", response_model=PaginatedResponse[ItineraryResponse])
async def list_itineraries(
    region: Optional[str] = Query(None, description="Filter by geographic region"),
    difficulty: Optional[str] = Query(None, description="Filter by difficulty level"),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    service = ItineraryService(db)
    items, total = await service.list_itineraries(
        region=region, difficulty=difficulty, limit=limit, offset=offset
    )
    return PaginatedResponse(
        items=items,
        total=total,
        limit=limit,
        offset=offset,
        has_more=(offset + limit) < total,
    )


@router.get("/{itinerary_id}", response_model=ItineraryResponse)
async def get_itinerary(itinerary_id: str, db: AsyncSession = Depends(get_db)):
    service = ItineraryService(db)
    return await service.get_itinerary(itinerary_id)


@router.get("/{itinerary_id}/route", response_model=ItineraryRouteGeoJSONResponse)
async def get_itinerary_route(itinerary_id: str, db: AsyncSession = Depends(get_db)):
    service = ItineraryService(db)
    return await service.get_itinerary_route(itinerary_id)

