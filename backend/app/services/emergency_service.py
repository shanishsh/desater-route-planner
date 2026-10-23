"""Emergency service managing map points, spatial queries, and assistance directories."""
from typing import List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.map_point_repo import MapPointRepository
from app.models.map_point import MapPoint
from app.schemas.map_point import MapPointResponse, MapPointCreate
from app.core.exceptions import NotFoundException


class EmergencyService:
    """Manages emergency assistance locations and spatial lookups."""

    def __init__(self, session: AsyncSession):
        self.session = session
        self.point_repo = MapPointRepository(session)

    async def list_points(
        self,
        category: Optional[str] = None,
        min_lat: Optional[float] = None,
        max_lat: Optional[float] = None,
        min_lon: Optional[float] = None,
        max_lon: Optional[float] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[MapPointResponse], int]:
        items, total = await self.point_repo.list_points(
            category=category,
            min_lat=min_lat,
            max_lat=max_lat,
            min_lon=min_lon,
            max_lon=max_lon,
            limit=limit,
            offset=offset,
        )
        return [MapPointResponse.model_validate(p) for p in items], total

    async def get_point(self, point_id: str) -> MapPointResponse:
        item = await self.point_repo.get_by_id(point_id)
        if not item:
            raise NotFoundException(f"MapPoint with id {point_id} not found")
        return MapPointResponse.model_validate(item)

    async def create_point(self, data: MapPointCreate) -> MapPointResponse:
        point = MapPoint(
            name=data.name,
            category=data.category,
            latitude=data.latitude,
            longitude=data.longitude,
            address=data.address,
            phone=data.phone,
            description=data.description,
            verification_status=data.verification_status,
            verification_timestamp=data.verification_timestamp,
            provenance=data.provenance,
        )
        created = await self.point_repo.create_point(point)
        return MapPointResponse.model_validate(created)

