"""Crowd API endpoints (Simulated)."""
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database.session import get_db
from app.models.crowd import CrowdSnapshot
from app.schemas.crowd import CrowdResponse

router = APIRouter(prefix="/crowd", tags=["Crowd"])


@router.get("", response_model=List[CrowdResponse])
async def get_crowd_indicators(
    itinerary_id: Optional[str] = Query(None, description="Filter by itinerary ID"),
    db: AsyncSession = Depends(get_db),
):
    query = select(CrowdSnapshot)
    if itinerary_id:
        query = query.where(CrowdSnapshot.itinerary_id == itinerary_id)

    result = await db.execute(query.order_by(CrowdSnapshot.observed_at.desc()).limit(10))
    snapshots = result.scalars().all()

    if not snapshots:
        return []

    return [CrowdResponse.model_validate(s) for s in snapshots]

