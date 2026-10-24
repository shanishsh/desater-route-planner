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
            cat_lower = category.lower()
            if cat_lower == "medical":
                filters.append(MapPoint.category.in_(["medical", "hospital", "clinic"]))
            elif cat_lower == "hospital":
                filters.append(MapPoint.category.in_(["hospital", "medical"]))
            elif cat_lower == "clinic":
                filters.append(MapPoint.category.in_(["clinic", "medical"]))
            elif cat_lower in ("fuel", "petrol_pump", "fuel_station"):
                filters.append(MapPoint.category.in_(["fuel", "petrol_pump", "fuel_station"]))
            elif cat_lower in ("fire_station", "fire"):
                filters.append(MapPoint.category.in_(["fire_station", "fire"]))
            elif cat_lower in ("shelter",):
                filters.append(MapPoint.category.in_(["shelter"]))
            else:
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

    async def count_all(self) -> int:
        result = await self.session.execute(select(func.count(MapPoint.id)))
        return result.scalar_one()

    async def create_point(self, point: MapPoint) -> MapPoint:
        self.session.add(point)
        await self.session.commit()
        await self.session.refresh(point)
        return point

    async def upsert_points(self, points: List[MapPoint]) -> int:
        if not points:
            return 0
        inserted = 0
        for pt in points:
            # Check by osm_id or by close coordinates + name
            existing = None
            if pt.osm_id:
                stmt = select(MapPoint).where(MapPoint.osm_id == pt.osm_id)
                res = await self.session.execute(stmt)
                existing = res.scalar_one_or_none()
            if not existing:
                stmt = select(MapPoint).where(
                    and_(
                        MapPoint.category == pt.category,
                        MapPoint.latitude.between(pt.latitude - 0.0005, pt.latitude + 0.0005),
                        MapPoint.longitude.between(pt.longitude - 0.0005, pt.longitude + 0.0005),
                    )
                )
                res = await self.session.execute(stmt)
                existing = res.scalar_one_or_none()

            if existing:
                existing.name = pt.name
                existing.category = pt.category
                existing.address = pt.address or existing.address
                existing.phone = pt.phone or existing.phone
                existing.description = pt.description or existing.description
                existing.verification_status = pt.verification_status
                existing.verification_timestamp = pt.verification_timestamp
                existing.provenance = pt.provenance
            else:
                self.session.add(pt)
                inserted += 1

        await self.session.commit()
        return inserted

