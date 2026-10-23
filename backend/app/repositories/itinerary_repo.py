"""Repository for Itinerary and RouteSegment operations."""
from typing import List, Optional, Tuple
from sqlalchemy import select, func
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.itinerary import Itinerary, RouteSegment


class ItineraryRepository:
    """Async repository for Itineraries."""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_id(self, itinerary_id: str) -> Optional[Itinerary]:
        stmt = (
            select(Itinerary)
            .options(selectinload(Itinerary.segments))
            .where(Itinerary.id == itinerary_id)
        )
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def list_itineraries(
        self,
        region: Optional[str] = None,
        difficulty: Optional[str] = None,
        limit: int = 20,
        offset: int = 0,
    ) -> Tuple[List[Itinerary], int]:
        query = select(Itinerary).options(selectinload(Itinerary.segments)).where(Itinerary.is_active.is_(True))
        count_query = select(func.count(Itinerary.id)).where(Itinerary.is_active.is_(True))

        if region:
            query = query.where(Itinerary.region.ilike(f"%{region}%"))
            count_query = count_query.where(Itinerary.region.ilike(f"%{region}%"))

        if difficulty:
            query = query.where(Itinerary.difficulty == difficulty)
            count_query = count_query.where(Itinerary.difficulty == difficulty)

        total_result = await self.session.execute(count_query)
        total = total_result.scalar_one()

        query = query.order_by(Itinerary.created_at.desc()).offset(offset).limit(limit)
        result = await self.session.execute(query)
        return list(result.scalars().all()), total

    async def create_itinerary(self, itinerary: Itinerary) -> Itinerary:
        self.session.add(itinerary)
        await self.session.commit()
        await self.session.refresh(itinerary)
        return itinerary

