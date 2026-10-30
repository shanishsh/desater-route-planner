"""Repository for MapPoint (emergency, medical, police) spatial queries."""
from typing import List, Optional, Tuple
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.map_point import MapPoint


class MapPointRepository:
    """Async repository for MapPoints with spatial bounding box queries and batch operations."""

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
            actual_min_lat = min(min_lat, max_lat)
            actual_max_lat = max(min_lat, max_lat)
            filters.append(MapPoint.latitude.between(actual_min_lat, actual_max_lat))
        if min_lon is not None and max_lon is not None:
            actual_min_lon = min(min_lon, max_lon)
            actual_max_lon = max(min_lon, max_lon)
            filters.append(MapPoint.longitude.between(actual_min_lon, actual_max_lon))

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
        """
        Batch upserts points with bulk pre-fetching to eliminate N+1 roundtrips.
        """
        if not points:
            return 0

        # 1. Bulk pre-fetch by osm_id
        osm_ids = [pt.osm_id for pt in points if pt.osm_id]
        existing_by_osm = {}
        if osm_ids:
            # Query in chunks of 500 if necessary
            chunk_size = 500
            for i in range(0, len(osm_ids), chunk_size):
                chunk = osm_ids[i:i + chunk_size]
                res = await self.session.execute(
                    select(MapPoint).where(MapPoint.osm_id.in_(chunk))
                )
                for existing in res.scalars().all():
                    existing_by_osm[existing.osm_id] = existing

        # 2. Bulk pre-fetch candidate points by bounding box for spatial deduplication
        min_lat = min(pt.latitude for pt in points) - 0.001
        max_lat = max(pt.latitude for pt in points) + 0.001
        min_lon = min(pt.longitude for pt in points) - 0.001
        max_lon = max(pt.longitude for pt in points) + 0.001

        geo_res = await self.session.execute(
            select(MapPoint).where(
                MapPoint.latitude.between(min_lat, max_lat),
                MapPoint.longitude.between(min_lon, max_lon)
            )
        )
        existing_geo_candidates = list(geo_res.scalars().all())

        inserted = 0
        new_points = []

        for pt in points:
            existing = None
            if pt.osm_id and pt.osm_id in existing_by_osm:
                existing = existing_by_osm[pt.osm_id]

            if not existing:
                # Fast in-memory check against geo candidates
                for cand in existing_geo_candidates:
                    if (cand.category == pt.category and
                        abs(cand.latitude - pt.latitude) <= 0.0005 and
                        abs(cand.longitude - pt.longitude) <= 0.0005):
                        existing = cand
                        break

            if existing:
                existing.name = pt.name
                existing.category = pt.category
                if pt.address is not None:
                    existing.address = pt.address
                if pt.phone is not None:
                    existing.phone = pt.phone
                if pt.description is not None:
                    existing.description = pt.description
                if pt.verification_status is not None:
                    existing.verification_status = pt.verification_status
                if pt.verification_timestamp is not None:
                    existing.verification_timestamp = pt.verification_timestamp
                if pt.provenance is not None:
                    existing.provenance = pt.provenance
            else:
                new_points.append(pt)
                existing_geo_candidates.append(pt)
                if pt.osm_id:
                    existing_by_osm[pt.osm_id] = pt
                inserted += 1

        if new_points:
            self.session.add_all(new_points)

        await self.session.commit()
        return inserted
