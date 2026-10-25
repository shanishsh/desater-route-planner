"""API endpoints for Community Trail Condition Reporting."""
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any

from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.database.session import get_db
from app.schemas.trail_report import (
    TrailReportCreate,
    TrailReportInteractionCreate,
    TrailReportResponse,
    TrailReportListResponse,
    TrailReportUploadResponse,
)
from app.services.trail_report_service import TrailReportService

router = APIRouter()


def _ensure_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


def _to_response_dto(report, now: datetime) -> TrailReportResponse:
    exp = _ensure_utc(report.expires_at)
    is_exp = exp is not None and exp <= now
    status_val = "EXPIRED" if is_exp else report.verification_status

    return TrailReportResponse(
        id=report.id,
        category=report.category,
        severity=report.severity,
        passability=report.passability,
        details_json=report.details_json,
        latitude=report.latitude,
        longitude=report.longitude,
        location_accuracy_meters=report.location_accuracy_meters,
        location_source=report.location_source,
        observation_time=_ensure_utc(report.observation_time),
        submission_time=_ensure_utc(report.submission_time),
        description=report.description,
        photo_url=report.photo_url,
        route_id=report.route_id,
        route_version=report.route_version,
        verification_status=status_val,
        confirmations_count=report.confirmations_count,
        disputes_count=report.disputes_count,
        last_confirmed_at=_ensure_utc(report.last_confirmed_at),
        expires_at=exp,
        idempotency_key=report.idempotency_key,
        is_expired=is_exp,
        created_at=_ensure_utc(getattr(report, "created_at", None)),
        updated_at=_ensure_utc(getattr(report, "updated_at", None)),
    )


@router.post("", response_model=TrailReportResponse, status_code=status.HTTP_201_CREATED)
@router.post("/", response_model=TrailReportResponse, status_code=status.HTTP_201_CREATED)
async def create_trail_report(
    payload: TrailReportCreate,
    db: AsyncSession = Depends(get_db)
):
    """
    Submits a new trail condition report with idempotency support, location validation,
    and automatic matching against active trek corridors to notify approaching trekkers.
    """
    service = TrailReportService(db)
    report, meta = await service.create_report(payload)
    now = datetime.now(timezone.utc)
    return _to_response_dto(report, now)


@router.get("", response_model=TrailReportListResponse)
@router.get("/", response_model=TrailReportListResponse)
async def list_trail_reports(
    bbox: Optional[str] = Query(None, description="min_lat,min_lon,max_lat,max_lon"),
    category: Optional[str] = Query(None),
    verification_status: Optional[str] = Query(None, alias="status"),
    route_id: Optional[str] = Query(None),
    include_expired: bool = Query(False),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    """Retrieves verified and active community trail condition reports with spatial bounding box filter."""
    service = TrailReportService(db)
    reports, total, active_cnt, corroborated_cnt = await service.list_reports(
        bbox=bbox,
        category=category,
        verification_status=verification_status,
        route_id=route_id,
        include_expired=include_expired,
        limit=limit,
        offset=offset,
    )
    now = datetime.now(timezone.utc)
    items = [_to_response_dto(r, now) for r in reports]
    return TrailReportListResponse(
        items=items,
        total=total,
        active_count=active_cnt,
        corroborated_count=corroborated_cnt,
    )


@router.post("/upload-photo", response_model=TrailReportUploadResponse)
async def upload_trail_photo(
    file: UploadFile = File(...)
):
    """
    Validates image format, strips all EXIF/GPS metadata,
    and returns a clean public storage URL path.
    """
    service = TrailReportService(None)
    url_path, filename, size_bytes = await service.save_uploaded_photo(file)
    return TrailReportUploadResponse(
        success=True,
        photo_url=url_path,
        filename=filename,
        size_bytes=size_bytes,
        content_type="image/jpeg",
    )


@router.get("/{report_id}", response_model=TrailReportResponse)
async def get_trail_report(
    report_id: str,
    db: AsyncSession = Depends(get_db)
):
    service = TrailReportService(db)
    report = await service.get_report_by_id(report_id)
    if not report:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Trail report '{report_id}' not found."
        )
    now = datetime.now(timezone.utc)
    return _to_response_dto(report, now)


@router.post("/{report_id}/interact", response_model=TrailReportResponse)
@router.post("/{report_id}/interactions", response_model=TrailReportResponse)
async def interact_trail_report(
    report_id: str,
    payload: TrailReportInteractionCreate,
    db: AsyncSession = Depends(get_db)
):
    """Confirms, disputes, or resolves an active trail condition report."""
    service = TrailReportService(db)
    updated_report = await service.record_interaction(report_id, payload)
    now = datetime.now(timezone.utc)
    return _to_response_dto(updated_report, now)

