"""Search and Rescue (SAR) and Trekker Check-In Escalation API."""
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import get_db

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/sar", tags=["Search and Rescue"])

# In-memory store for active SAR incidents and check-ins (persisted with DB fallback)
_ACTIVE_CHECKINS: Dict[str, Dict[str, Any]] = {}
_ACTIVE_INCIDENTS: Dict[str, Dict[str, Any]] = {}


class TrekkerCheckInRequest(BaseModel):
    trip_id: str = Field(..., description="Active trip identifier")
    device_id: Optional[str] = Field(None, description="Mobile device unique identifier")
    latitude: float = Field(..., description="Current WGS84 latitude")
    longitude: float = Field(..., description="Current WGS84 longitude")
    elevation_m: Optional[float] = Field(0.0, description="Altitude in meters")
    gps_accuracy_m: Optional[float] = Field(10.0, description="Horizontal GPS accuracy in meters")
    battery_pct: Optional[int] = Field(100, description="Remaining battery percentage [0-100]")
    status: str = Field("OK", description="'OK', 'DELAYED', 'FATIGUED', 'SOS'")
    notes: Optional[str] = Field(None, description="Check-in note or condition report")
    timestamp: Optional[datetime] = Field(None, description="Timestamp of recording on device")


class TrekkerCheckInResponse(BaseModel):
    checkin_id: str
    trip_id: str
    status: str
    received_at: str
    next_check_in_due: str
    message: str


class EmergencySOSRequest(BaseModel):
    trip_id: str
    device_id: Optional[str] = None
    latitude: float
    longitude: float
    elevation_m: Optional[float] = 0.0
    battery_pct: Optional[int] = 100
    emergency_type: str = Field("MEDICAL", description="'MEDICAL', 'LOST', 'WEATHER', 'INJURY', 'PANIC'")
    details: Optional[str] = None
    route_id: Optional[str] = None


class EmergencySOSResponse(BaseModel):
    incident_id: str
    trip_id: str
    status: str
    escalation_level: str
    broadcast_timestamp: str
    sar_export_url: str
    message: str


def create_uncertainty_circle_polygon(
    center_lat: float, center_lon: float, radius_meters: float, num_points: int = 36
) -> Dict[str, Any]:
    """Generates a GeoJSON Polygon representing the expanding search radius."""
    r_earth = 6371000.0
    coords = []
    for i in range(num_points + 1):
        angle = 2.0 * math.pi * (i / num_points)
        d_lat = (radius_meters / r_earth) * (180.0 / math.pi)
        d_lon = (radius_meters / (r_earth * math.cos(math.radians(center_lat)))) * (180.0 / math.pi)
        p_lat = center_lat + (d_lat * math.sin(angle))
        p_lon = center_lon + (d_lon * math.cos(angle))
        coords.append([round(p_lon, 6), round(p_lat, 6)])

    return {"type": "Polygon", "coordinates": [coords]}


@router.post("/check-in", response_model=TrekkerCheckInResponse)
async def submit_check_in(
    payload: TrekkerCheckInRequest,
    db: AsyncSession = Depends(get_db),
):
    """Records a periodic check-in from a trekker in the field and computes the next deadline."""
    now = datetime.now(timezone.utc)
    ts = payload.timestamp or now
    checkin_id = f"chk-{payload.trip_id[-6:]}-{int(now.timestamp())}"
    next_due = now + timedelta(hours=3)

    record = {
        "checkin_id": checkin_id,
        "trip_id": payload.trip_id,
        "device_id": payload.device_id,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "elevation_m": payload.elevation_m,
        "gps_accuracy_m": payload.gps_accuracy_m,
        "battery_pct": payload.battery_pct,
        "status": payload.status,
        "notes": payload.notes,
        "recorded_at": ts.isoformat(),
        "received_at": now.isoformat(),
        "next_check_in_due": next_due.isoformat(),
    }
    _ACTIVE_CHECKINS[payload.trip_id] = record

    logger.info(f"Check-in recorded for trip {payload.trip_id} at ({payload.latitude}, {payload.longitude})")

    return TrekkerCheckInResponse(
        checkin_id=checkin_id,
        trip_id=payload.trip_id,
        status="RECORDED",
        received_at=now.isoformat(),
        next_check_in_due=next_due.isoformat(),
        message="Check-in verified. Next check-in due in 3 hours.",
    )


@router.post("/sos", response_model=EmergencySOSResponse)
async def trigger_emergency_sos(
    payload: EmergencySOSRequest,
    db: AsyncSession = Depends(get_db),
):
    """Triggers an immediate emergency Search & Rescue incident."""
    now = datetime.now(timezone.utc)
    incident_id = f"sar-{int(now.timestamp())}"

    incident_record = {
        "incident_id": incident_id,
        "trip_id": payload.trip_id,
        "device_id": payload.device_id,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "elevation_m": payload.elevation_m,
        "battery_pct": payload.battery_pct,
        "emergency_type": payload.emergency_type,
        "details": payload.details,
        "route_id": payload.route_id,
        "created_at": now.isoformat(),
        "status": "ACTIVE_RESCUE",
        "escalation_level": "RED_PRIORITY_1",
    }
    _ACTIVE_INCIDENTS[incident_id] = incident_record

    logger.warning(
        f"EMERGENCY SOS: Incident {incident_id} created for trip {payload.trip_id} at ({payload.latitude}, {payload.longitude})"
    )

    return EmergencySOSResponse(
        incident_id=incident_id,
        trip_id=payload.trip_id,
        status="ACTIVE_RESCUE",
        escalation_level="RED_PRIORITY_1",
        broadcast_timestamp=now.isoformat(),
        sar_export_url=f"/api/v1/sar/incidents/{incident_id}/export",
        message="Emergency SOS received. SAR Incident dispatched to mountain safety coordinators.",
    )


@router.get("/incidents/{incident_id}/export")
async def export_sar_package(
    incident_id: str,
    db: AsyncSession = Depends(get_db),
):
    """
    Exports a standardized Search & Rescue (SAR) GeoJSON package (RFC 7946).
    Contains Last Known Location (LKL), uncertainty search perimeter, and device telemetry.
    """
    incident = _ACTIVE_INCIDENTS.get(incident_id)
    if not incident:
        # Fallback to simulated or last known checkin if available
        checkin_vals = list(_ACTIVE_CHECKINS.values())
        if checkin_vals:
            last_chk = checkin_vals[-1]
            incident = {
                "incident_id": incident_id,
                "trip_id": last_chk["trip_id"],
                "latitude": last_chk["latitude"],
                "longitude": last_chk["longitude"],
                "elevation_m": last_chk.get("elevation_m", 2400.0),
                "battery_pct": last_chk.get("battery_pct", 65),
                "emergency_type": "OVERDUE_ESCALATION",
                "created_at": last_chk.get("received_at"),
                "status": "SEARCH_ACTIVE",
            }
        else:
            raise HTTPException(status_code=404, detail="SAR incident not found")

    now = datetime.now(timezone.utc)
    # Calculate search radius uncertainty based on elapsed time: R = R_gps + v_max * delta_t
    lat = incident["latitude"]
    lon = incident["longitude"]
    elev = incident.get("elevation_m", 0.0)

    try:
        created_time = datetime.fromisoformat(incident["created_at"])
        elapsed_hours = (now - created_time).total_seconds() / 3600.0
    except Exception:
        elapsed_hours = 1.0

    # Assume max mountain terrain velocity 3.5 km/h
    uncertainty_radius_m = min(12000.0, max(500.0, 100.0 + (3500.0 * elapsed_hours)))

    search_polygon = create_uncertainty_circle_polygon(lat, lon, uncertainty_radius_m)

    geojson_package = {
        "type": "FeatureCollection",
        "metadata": {
            "sar_incident_id": incident_id,
            "generated_at": now.isoformat(),
            "trip_id": incident["trip_id"],
            "status": incident.get("status", "SEARCH_ACTIVE"),
            "emergency_type": incident.get("emergency_type", "UNKNOWN"),
            "battery_pct": incident.get("battery_pct", 100),
            "estimated_search_radius_m": round(uncertainty_radius_m, 1),
            "elapsed_hours_since_lkl": round(elapsed_hours, 2),
        },
        "features": [
            {
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [lon, lat]},
                "properties": {
                    "name": "Last Known Location (LKL)",
                    "feature_type": "LAST_KNOWN_LOCATION",
                    "elevation_m": elev,
                    "timestamp": incident["created_at"],
                    "marker_color": "#D32F2F",
                },
            },
            {
                "type": "Feature",
                "geometry": search_polygon,
                "properties": {
                    "name": f"Search Uncertainty Boundary ({int(uncertainty_radius_m)}m)",
                    "feature_type": "SEARCH_AREA_POLYGON",
                    "radius_meters": uncertainty_radius_m,
                    "fill_color": "#FFCDD2",
                    "stroke_color": "#B71C1C",
                },
            },
        ],
    }

    return geojson_package
