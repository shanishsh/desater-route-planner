"""Repository for MapPoint (emergency, medical, police) spatial queries."""
from typing import List, Optional, Tuple
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.map_point import MapPoint


class MapPointRepository:
    """Async repository for MapPoints with spatial bounding box queries."""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_id(self, point_id: str) -> Optional[MapPoint]:
        stmt = select(MapPoint).where(MapPoint.id == point_id)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def list_points(
        self,
        category: Optional[str] = None,
        min_lat: Optional[float] = None,
        max_lat: Optional[float] = None,
        min_lon: Optional[float] = None,
        max_lon: Optional[float] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[MapPoint], int]:
        query = select(MapPoint)
        count_query = select(func.count(MapPoint.id))

        filters = []
        if category:
            filters.append(MapPoint.category == category)

        # Spatial bounding box query
        if min_lat is not None and max_lat is not None:
            filters.append(MapPoint.latitude.between(min_lat, max_lat))
        if min_lon is not None and max_lon is not None:
            filters.append(MapPoint.longitude.between(min_lon, max_lon))

        if filters:
            query = query.where(and_(*filters))
            count_query = count_query.where(and_(*filters))

        total_result = await self.session.execute(count_query)
        total = total_result.scalar_one()

        query = query.order_by(MapPoint.name.asc()).offset(offset).limit(limit)
        result = await self.session.execute(query)
        return list(result.scalars().all()), total

    async def create_point(self, point: MapPoint) -> MapPoint:
        self.session.add(point)
        await self.session.commit()
        await self.session.refresh(point)
        return point

