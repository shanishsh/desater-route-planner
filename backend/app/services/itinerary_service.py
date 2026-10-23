"""Itinerary service managing routes, waypoints, and GeoJSON."""
from typing import List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.itinerary_repo import ItineraryRepository
from app.models.itinerary import Itinerary, RouteSegment
from app.schemas.itinerary import ItineraryResponse, ItineraryCreate, ItineraryRouteGeoJSONResponse
from app.core.exceptions import NotFoundException


class ItineraryService:
    """Manages itineraries and route geometries."""

    def __init__(self, session: AsyncSession):
        self.session = session
        self.itinerary_repo = ItineraryRepository(session)

    async def list_itineraries(
        self,
        region: Optional[str] = None,
        difficulty: Optional[str] = None,
        limit: int = 20,
        offset: int = 0,
    ) -> Tuple[List[ItineraryResponse], int]:
        items, total = await self.itinerary_repo.list_itineraries(
            region=region, difficulty=difficulty, limit=limit, offset=offset
        )
        return [ItineraryResponse.model_validate(item) for item in items], total

    async def get_itinerary(self, itinerary_id: str) -> ItineraryResponse:
        item = await self.itinerary_repo.get_by_id(itinerary_id)
        if not item:
            raise NotFoundException(f"Itinerary with id {itinerary_id} not found")
        return ItineraryResponse.model_validate(item)

    async def get_itinerary_route(self, itinerary_id: str) -> ItineraryRouteGeoJSONResponse:
        item = await self.itinerary_repo.get_by_id(itinerary_id)
        if not item:
            raise NotFoundException(f"Itinerary with id {itinerary_id} not found")
        return ItineraryRouteGeoJSONResponse(
            itinerary_id=item.id,
            title=item.title,
            route_geojson=item.route_geojson,
        )

    async def create_itinerary(self, data: ItineraryCreate) -> ItineraryResponse:
        itinerary = Itinerary(
            title=data.title,
            description=data.description,
            region=data.region,
            difficulty=data.difficulty,
            distance_km=data.distance_km,
            estimated_duration_hours=data.estimated_duration_hours,
            start_latitude=data.start_latitude,
            start_longitude=data.start_longitude,
            end_latitude=data.end_latitude,
            end_longitude=data.end_longitude,
            route_geojson=data.route_geojson,
            is_active=data.is_active,
        )
        if data.segments:
            for s in data.segments:
                seg = RouteSegment(
                    sequence_order=s.sequence_order,
                    name=s.name,
                    elevation_gain_m=s.elevation_gain_m,
                    hazard_level=s.hazard_level,
                    segment_geojson=s.segment_geojson,
                )
                itinerary.segments.append(seg)

        created = await self.itinerary_repo.create_itinerary(itinerary)
        return ItineraryResponse.model_validate(created)

