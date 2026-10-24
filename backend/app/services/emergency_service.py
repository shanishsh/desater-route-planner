"""Emergency service managing map points, spatial queries, and assistance directories."""
from datetime import datetime, timezone
from typing import List, Optional, Tuple, Dict, Any
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.map_point_repo import MapPointRepository
from app.models.map_point import MapPoint
from app.schemas.map_point import MapPointResponse, MapPointCreate
from app.core.exceptions import NotFoundException
from app.clients.osm_amenities import osm_amenities_client
from app.data.regional_facilities import UTTARAKHAND_REGIONAL_FACILITIES


class EmergencyService:
    """Manages emergency assistance locations and spatial lookups."""

    def __init__(self, session: AsyncSession):
        self.session = session
        self.point_repo = MapPointRepository(session)

    async def ensure_baseline_seeded(self) -> None:
        """Seeds initial verified Uttarakhand regional OpenStreetMap facilities if table is empty."""
        try:
            cnt = await self.point_repo.count_all()
            if cnt == 0:
                now = datetime.now(timezone.utc)
                seed_models = []
                for f in UTTARAKHAND_REGIONAL_FACILITIES:
                    seed_models.append(
                        MapPoint(
                            name=f["name"],
                            category=f["category"],
                            latitude=f["latitude"],
                            longitude=f["longitude"],
                            address=f.get("address"),
                            phone=f.get("phone"),
                            description=f.get("description"),
                            verification_status="verified",
                            verification_timestamp=now,
                            provenance=f.get("provenance", "OpenStreetMap Contributors"),
                            osm_id=f.get("osm_id"),
                            osm_type=f.get("osm_type"),
                        )
                    )
                await self.point_repo.upsert_points(seed_models)
        except Exception as e:
            # Table might be locked or concurrently seeded
            pass

    async def list_points(
        self,
        category: Optional[str] = None,
        min_lat: Optional[float] = None,
        max_lat: Optional[float] = None,
        min_lon: Optional[float] = None,
        max_lon: Optional[float] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[MapPointResponse], int, Dict[str, Any]]:
        now = datetime.now(timezone.utc)
        await self.ensure_baseline_seeded()

        items, total = await self.point_repo.list_points(
            category=category,
            min_lat=min_lat,
            max_lat=max_lat,
            min_lon=min_lon,
            max_lon=max_lon,
            limit=limit,
            offset=offset,
        )

        metadata: Dict[str, Any] = {
            "provider_status": "success",
            "is_stale": False,
            "retrieved_at": now,
            "provenance": "OpenStreetMap Contributors (ODbL) & Official Emergency Registry",
        }

        # 1. Unbounded global query (e.g. GET /api/v1/map-points?limit=100)
        if min_lat is None or min_lon is None or max_lat is None or max_lon is None:
            if total > 0:
                responses = [MapPointResponse.model_validate(p) for p in items]
                return responses, total, metadata
            return [], 0, {**metadata, "provider_status": "empty"}

        # 2. Bounded spatial query with existing local cache/database hits
        if total > 0:
            responses = [MapPointResponse.model_validate(p) for p in items]
            return responses, total, metadata

        # 3. Database has no points in this specific bbox: query live Overpass
        try:
            osm_records = await osm_amenities_client.fetch_amenities(
                min_lat=min_lat,
                min_lon=min_lon,
                max_lat=max_lat,
                max_lon=max_lon,
                category=category,
            )
            if osm_records:
                # Durably persist the fetched records into PostgreSQL
                new_points = []
                for r in osm_records:
                    new_points.append(
                        MapPoint(
                            name=r["name"],
                            category=r["category"],
                            latitude=r["latitude"],
                            longitude=r["longitude"],
                            address=r.get("address"),
                            phone=r.get("phone"),
                            description=r.get("description"),
                            verification_status="verified",
                            verification_timestamp=now,
                            provenance=r.get("provenance", "OpenStreetMap Contributors"),
                            osm_id=r.get("osm_id"),
                            osm_type=r.get("osm_type"),
                        )
                    )
                await self.point_repo.upsert_points(new_points)

                # Re-query from database to guarantee consistent IDs and timestamps
                db_items, db_total = await self.point_repo.list_points(
                    category=category,
                    min_lat=min_lat,
                    max_lat=max_lat,
                    min_lon=min_lon,
                    max_lon=max_lon,
                    limit=limit,
                    offset=offset,
                )
                responses = [MapPointResponse.model_validate(p) for p in db_items]
                return responses, db_total, metadata

            # Overpass returned empty: distinguish clean empty vs provider error
            if osm_amenities_client.last_error:
                metadata["provider_status"] = "provider_error"
                metadata["last_error"] = osm_amenities_client.last_error
            else:
                metadata["provider_status"] = "empty"

        except Exception as e:
            metadata["provider_status"] = "provider_error"
            metadata["last_error"] = str(e)

        return [], 0, metadata

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


