"""Service managing Community Trail Condition Reports, photo validation, EXIF stripping,
corroboration logic, and matching reports to approaching active trek corridors.
"""
import io
import os
import uuid
import logging
from pathlib import Path
from datetime import datetime, timezone, timedelta
from typing import Optional, List, Dict, Any, Tuple
try:
    from PIL import Image
except ImportError:
    Image = None

from sqlalchemy import select, and_, or_, func
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import UploadFile, HTTPException, status

from app.models.trail_report import TrailConditionReport, TrailReportInteraction
from app.models.planned_route import PlannedRoute, ActiveTrip
from app.schemas.trail_report import TrailReportCreate, TrailReportInteractionCreate
from app.services.spatial.route_corridor_service import RouteCorridorService

logger = logging.getLogger(__name__)

CATEGORY_EXPIRY_HOURS: Dict[str, int] = {
    "flash_flood": 12,
    "dangerous_weather": 12,
    "wildfire_smoke": 24,
    "wildlife": 24,
    "fallen_tree": 72,
    "blocked_path": 72,
    "landslide": 168,
    "damaged_bridge": 336,
    "trail_emergency": 24,
    "other": 48,
}

MAX_IMAGE_BYTES: int = 10 * 1024 * 1024 # 10 MB
ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
UPLOAD_SUBDIR: str = "uploads/trail_reports"


class TrailReportService:
    def __init__(self, session: AsyncSession):
        self.session = session

    @staticmethod
    def calculate_expiry(category: str, base_time: Optional[datetime] = None) -> datetime:
        now = base_time or datetime.now(timezone.utc)
        if now.tzinfo is None:
            now = now.replace(tzinfo=timezone.utc)
        hours = CATEGORY_EXPIRY_HOURS.get(category, 48)
        return now + timedelta(hours=hours)

    async def save_uploaded_photo(self, file: UploadFile) -> Tuple[str, str, int]:
        content_type = file.content_type or ""
        if content_type.lower() not in ALLOWED_MIME_TYPES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Unsupported file type '{content_type}'. Allowed: image/jpeg, image/png, image/webp"
            )

        raw_bytes = await file.read()
        size_bytes = len(raw_bytes)
        if size_bytes > MAX_IMAGE_BYTES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Image size {size_bytes} bytes exceeds maximum limit of {MAX_IMAGE_BYTES} bytes."
            )

        os.makedirs(UPLOAD_SUBDIR, exist_ok=True)
        filename = f"{uuid.uuid4().hex[:16]}.jpg"
        target_path = Path(UPLOAD_SUBDIR) / filename

        if Image is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Image processing service is currently unavailable (Pillow dependency missing)."
            )

        try:
            # Strip EXIF and re-encode to JPEG for privacy & safety
            img = Image.open(io.BytesIO(raw_bytes))
            img_to_save = img.convert("RGB")
            img_to_save.save(target_path, "JPEG", quality=85)
        except Exception as e:
            logger.error("Failed to parse and sanitize image: %s", e)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid or corrupt image format."
            )

        url_path = f"/static/{UPLOAD_SUBDIR}/{filename}"
        return url_path, filename, size_bytes

    async def create_report(
        self,
        data: TrailReportCreate,
        reporter_user_id: Optional[str] = None
    ) -> Tuple[TrailConditionReport, Dict[str, Any]]:
        # 1. Idempotency check
        if data.idempotency_key:
            stmt = select(TrailConditionReport).where(
                TrailConditionReport.idempotency_key == data.idempotency_key
            )
            res = await self.session.execute(stmt)
            existing = res.scalar_one_or_none()
            if existing:
                return existing, {"idempotent": True, "affected_trekkers": 0}

        now = datetime.now(timezone.utc)
        obs_time = data.observation_time or now
        if obs_time.tzinfo is None:
            obs_time = obs_time.replace(tzinfo=timezone.utc)

        expires_at = self.calculate_expiry(data.category, obs_time)

        report = TrailConditionReport(
            category=data.category,
            severity=data.severity,
            passability=data.passability,
            details_json=data.details_json,
            latitude=data.latitude,
            longitude=data.longitude,
            location_accuracy_meters=data.location_accuracy_meters,
            location_source=data.location_source,
            observation_time=obs_time,
            submission_time=now,
            description=data.description,
            photo_url=data.photo_url,
            route_id=data.route_id,
            route_version=data.route_version,
            idempotency_key=data.idempotency_key,
            device_id=data.device_id,
            verification_status="UNVERIFIED",
            confirmations_count=0,
            disputes_count=0,
            expires_at=expires_at,
        )

        self.session.add(report)
        await self.session.commit()
        await self.session.refresh(report)

        affected = await self._find_affected_active_trips(report)
        return report, {"idempotent": False, "affected_trekkers": len(affected)}

    async def _find_affected_active_trips(
        self,
        report: TrailConditionReport,
        corridor_radius_meters: float = 2000.0
    ) -> List[ActiveTrip]:
        stmt = (
            select(ActiveTrip, PlannedRoute)
            .join(PlannedRoute, ActiveTrip.route_id == PlannedRoute.id)
            .where(ActiveTrip.status == "ACTIVE")
        )
        res = await self.session.execute(stmt)
        active_trips_and_routes = res.all()

        corridor_svc = RouteCorridorService()
        affected = []
        for trip, route in active_trips_and_routes:
            geom = route.geometry_geojson
            if not geom or not isinstance(geom, dict):
                continue
            coords = geom.get("coordinates")
            if not coords or not isinstance(coords, list):
                continue

            # Check distance from obstacle to route line
            try:
                route_coords = [(c[1], c[0]) for c in coords if len(c) >= 2]
                if len(route_coords) >= 2:
                    dist_m, along_m, seg_idx = corridor_svc.distance_point_to_route(
                        report.latitude, report.longitude, route_coords
                    )
                    if dist_m <= corridor_radius_meters:
                        affected.append(trip)
                elif len(route_coords) == 1:
                    dist_m = corridor_svc.haversine_distance_m(
                        report.latitude, report.longitude, route_coords[0][0], route_coords[0][1]
                    )
                    if dist_m <= corridor_radius_meters:
                        affected.append(trip)
            except Exception as e:
                logger.warning("Error evaluating corridor proximity for trip %s: %s", trip.id, e)

        return affected

    async def list_reports(
        self,
        bbox: Optional[str] = None,
        category: Optional[str] = None,
        verification_status: Optional[str] = None,
        route_id: Optional[str] = None,
        include_expired: bool = False,
        limit: int = 50,
        offset: int = 0
    ) -> Tuple[List[TrailConditionReport], int, int, int]:
        now = datetime.now(timezone.utc)
        query = select(TrailConditionReport)
        filters = []

        if not include_expired:
            filters.append(or_(TrailConditionReport.expires_at.is_(None), TrailConditionReport.expires_at > now))

        if category:
            filters.append(TrailConditionReport.category == category)

        if verification_status:
            filters.append(TrailConditionReport.verification_status == verification_status)

        if route_id:
            filters.append(TrailConditionReport.route_id == route_id)

        if bbox:
            try:
                parts = [float(x.strip()) for x in bbox.split(",")]
                if len(parts) == 4:
                    min_lat, min_lon, max_lat, max_lon = parts
                    filters.append(
                        and_(
                            TrailConditionReport.latitude >= min_lat,
                            TrailConditionReport.latitude <= max_lat,
                            TrailConditionReport.longitude >= min_lon,
                            TrailConditionReport.longitude <= max_lon,
                        )
                    )
            except Exception as e:
                logger.warning("Invalid bbox query parameter '%s': %s", bbox, e)

        if filters:
            query = query.where(and_(*filters))

        count_query = select(func.count()).select_from(query.subquery())
        total_res = await self.session.execute(count_query)
        total = total_res.scalar_one() or 0

        # Execute paginated query
        query = query.order_by(TrailConditionReport.submission_time.desc()).limit(limit).offset(offset)
        result = await self.session.execute(query)
        reports = list(result.scalars().all())

        # Count active vs corroborated safely handling timezones and None
        def _is_active(r):
            if r.verification_status == "RESOLVED":
                return False
            if r.expires_at is None:
                return True
            exp = r.expires_at.replace(tzinfo=timezone.utc) if r.expires_at.tzinfo is None else r.expires_at
            return exp > now

        active_cnt = sum(1 for r in reports if _is_active(r))
        corroborated_cnt = sum(1 for r in reports if r.verification_status == "CORROBORATED")

        return reports, total, active_cnt, corroborated_cnt

    async def get_report_by_id(self, report_id: str) -> Optional[TrailConditionReport]:
        stmt = select(TrailConditionReport).where(TrailConditionReport.id == report_id)
        res = await self.session.execute(stmt)
        return res.scalar_one_or_none()

    async def record_interaction(
        self,
        report_id: str,
        interaction_data: TrailReportInteractionCreate,
        user_id: Optional[str] = None
    ) -> TrailConditionReport:
        report = await self.get_report_by_id(report_id)
        if not report:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Trail report '{report_id}' not found."
            )

        now = datetime.now(timezone.utc)
        itype = interaction_data.interaction_type

        # Anti-abuse: 1 vote per device
        if interaction_data.device_id:
            dup_stmt = select(TrailReportInteraction).where(
                and_(
                    TrailReportInteraction.report_id == report_id,
                    TrailReportInteraction.device_id == interaction_data.device_id
                )
            )
            res = await self.session.execute(dup_stmt)
            if res.scalar_one_or_none():
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="This device has already registered an interaction for this report."
                )

        interaction = TrailReportInteraction(
            report_id=report_id,
            interaction_type=itype,
            comment=interaction_data.comment,
            device_id=interaction_data.device_id,
            created_at=now,
        )
        self.session.add(interaction)

        if itype == "CONFIRM":
            report.confirmations_count += 1
            report.last_confirmed_at = now
            if report.confirmations_count >= 3 and report.verification_status == "UNVERIFIED":
                report.verification_status = "CORROBORATED"
        elif itype == "DISPUTE":
            report.disputes_count += 1
            if report.disputes_count >= 3:
                report.verification_status = "DISPUTED"
        elif itype == "RESOLVE":
            report.verification_status = "RESOLVED"

        await self.session.commit()
        await self.session.refresh(report)
        return report
