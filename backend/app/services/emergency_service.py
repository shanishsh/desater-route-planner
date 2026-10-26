"""Emergency service managing map points, spatial queries, and assistance directories."""
import logging
from datetime import datetime, timezone
from typing import List, Optional, Tuple, Dict, Any
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.map_point_repo import MapPointRepository
from app.models.map_point import MapPoint
from app.schemas.map_point import MapPointResponse, MapPointCreate
from app.core.exceptions import NotFoundException
from app.clients.osm_amenities import osm_amenities_client
from app.data.regional_facilities import UTTARAKHAND_REGIONAL_FACILITIES
from app.services.spatial.route_corridor_service import route_corridor_service
from app.services.spatial.region_boundary_service import UTTARAKHAND_BBOX, region_boundary_service

logger = logging.getLogger(__name__)


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
                logger.info(f"Successfully seeded {len(seed_models)} verified baseline regional facilities.")
        except Exception as e:
            logger.warning(f"Could not seed baseline facilities: {e}")

    async def list_points(
        self,
        category: Optional[str] = None,
        min_lat: Optional[float] = None,
        max_lat: Optional[float] = None,
        min_lon: Optional[float] = None,
        max_lon: Optional[float] = None,
        route_id: Optional[str] = None,
        corridor_km: float = 5.0,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[MapPointResponse], int, Dict[str, Any]]:
        now = datetime.now(timezone.utc)
        await self.ensure_baseline_seeded()

        metadata: Dict[str, Any] = {
            "provider_status": "success",
            "is_stale": False,
            "retrieved_at": now,
            "provenance": "OpenStreetMap Contributors (ODbL) & Official Emergency Registry",
            "diagnostics": osm_amenities_client.get_diagnostics(),
        }

        # Clamp queries strictly to Uttarakhand administrative boundary
        uk_min_lat, uk_min_lon, uk_max_lat, uk_max_lon = UTTARAKHAND_BBOX
        if min_lat is not None:
            min_lat = max(min_lat, uk_min_lat)
        if max_lat is not None:
            max_lat = min(max_lat, uk_max_lat)
        if min_lon is not None:
            min_lon = max(min_lon, uk_min_lon)
        if max_lon is not None:
            max_lon = min(max_lon, uk_max_lon)

        # 1. Route-Corridor Filtered Query (if route_id is specified)
        if route_id:
            from app.models.planned_route import PlannedRoute
            stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
            res = await self.session.execute(stmt)
            db_route = res.scalar_one_or_none()
            if db_route and db_route.geometry_geojson:
                geom = db_route.geometry_geojson
                coords = geom.get("coordinates", [])
                if coords:
                    route_coords_lat_lon = [(float(c[1]), float(c[0])) for c in coords]
                    lats = [pt[0] for pt in route_coords_lat_lon]
                    lons = [pt[1] for pt in route_coords_lat_lon]
                    buffer_deg = (corridor_km / 111.0) * 1.5
                    r_min_lat = min(lats) - buffer_deg
                    r_max_lat = max(lats) + buffer_deg
                    r_min_lon = min(lons) - buffer_deg
                    r_max_lon = max(lons) + buffer_deg

                    candidates, _ = await self.point_repo.list_points(
                        category=category,
                        min_lat=r_min_lat,
                        max_lat=r_max_lat,
                        min_lon=r_min_lon,
                        max_lon=r_max_lon,
                        limit=500,
                        offset=0,
                    )

                    matching = []
                    for pt in candidates:
                        dist_m, along_m, _ = route_corridor_service.distance_point_to_route(
                            pt.latitude, pt.longitude, route_coords_lat_lon
                        )
                        if dist_m <= (corridor_km * 1000.0):
                            matching.append((along_m, pt))

                    matching.sort(key=lambda x: x[0])
                    total_route_points = len(matching)
                    sliced = matching[offset : offset + limit]
                    responses = [MapPointResponse.model_validate(item[1]) for item in sliced]
                    return responses, total_route_points, metadata

        # 2. General Query (Bounded or Unbounded)
        items, total = await self.point_repo.list_points(
            category=category,
            min_lat=min_lat,
            max_lat=max_lat,
            min_lon=min_lon,
            max_lon=max_lon,
            limit=limit,
            offset=offset,
        )

        # 2a. Unbounded query (e.g. GET /api/v1/map-points?limit=100)
        if min_lat is None or min_lon is None or max_lat is None or max_lon is None:
            if total > 0:
                responses = [MapPointResponse.model_validate(p) for p in items]
                return responses, total, metadata

            # Table is empty and seeding didn't complete: fallback to in-memory verified baseline
            fallback_items = []
            for f in UTTARAKHAND_REGIONAL_FACILITIES:
                f_cat = f["category"].lower()
                cat_match = (
                    not category
                    or f_cat == category.lower()
                    or (category.lower() == "medical" and f_cat in ("hospital", "clinic"))
                    or (category.lower() in ("fuel", "petrol_pump") and f_cat == "fuel")
                    or (category.lower() in ("fire_station", "fire") and f_cat == "fire_station")
                )
                if cat_match:
                    fallback_items.append(
                        MapPointResponse(
                            id=f.get("osm_id", f["name"]),
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
                            provider_status="stale",
                            is_stale=True,
                        )
                    )
            if fallback_items:
                sliced = fallback_items[offset : offset + limit]
                return sliced, len(fallback_items), {
                    **metadata,
                    "provider_status": "stale",
                    "is_stale": True,
                }
            return [], 0, {**metadata, "provider_status": "empty"}

        # 2b. Bounded spatial query with existing local database hits
        if total > 0:
            responses = [MapPointResponse.model_validate(p) for p in items]
            return responses, total, metadata

        # 2c. Bounded spatial query with NO local database hits: query live Overpass
        try:
            actual_min_lat = min(min_lat, max_lat)
            actual_max_lat = max(min_lat, max_lat)
            actual_min_lon = min(min_lon, max_lon)
            actual_max_lon = max(min_lon, max_lon)

            osm_records = await osm_amenities_client.fetch_amenities(
                min_lat=actual_min_lat,
                min_lon=actual_min_lon,
                max_lat=actual_max_lat,
                max_lon=actual_max_lon,
                category=category,
            )
            if osm_records:
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

                db_items, db_total = await self.point_repo.list_points(
                    category=category,
                    min_lat=actual_min_lat,
                    max_lat=actual_max_lat,
                    min_lon=actual_min_lon,
                    max_lon=actual_max_lon,
                    limit=limit,
                    offset=offset,
                )
                responses = [MapPointResponse.model_validate(p) for p in db_items]
                metadata["diagnostics"] = osm_amenities_client.get_diagnostics()
                return responses, db_total, metadata

            # Overpass returned empty: distinguish clean empty vs provider error
            metadata["diagnostics"] = osm_amenities_client.get_diagnostics()
            if osm_amenities_client.last_error:
                # Upstream error/timeout. Check if any cached records exist regionally
                cached_items, cached_total = await self.point_repo.list_points(
                    category=category, limit=limit, offset=offset
                )
                if cached_total > 0:
                    responses = [MapPointResponse.model_validate(p) for p in cached_items]
                    metadata["provider_status"] = "stale"
                    metadata["is_stale"] = True
                    metadata["last_error"] = osm_amenities_client.last_error
                    return responses, cached_total, metadata

                metadata["provider_status"] = "provider_error"
                metadata["last_error"] = osm_amenities_client.last_error
            else:
                metadata["provider_status"] = "empty"

        except Exception as e:
            metadata["provider_status"] = "provider_error"
            metadata["last_error"] = str(e)
            metadata["diagnostics"] = osm_amenities_client.get_diagnostics()

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


