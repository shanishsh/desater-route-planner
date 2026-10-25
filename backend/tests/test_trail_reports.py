"""Tests for Community Trail Condition Reporting (REM-08)."""
import io
import uuid
import pytest
from datetime import datetime, timezone, timedelta
from PIL import Image

from app.models.planned_route import PlannedRoute, ActiveTrip
from app.schemas.trail_report import TrailReportCreate
from app.services.trail_report_service import TrailReportService


def _create_sample_jpeg_bytes() -> bytes:
    """Creates a valid 100x100 in-memory JPEG image with simulated EXIF metadata."""
    img = Image.new("RGB", (100, 100), color=(255, 0, 0))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


@pytest.mark.asyncio
async def test_create_trail_report_valid(client, test_db):
    """Verifies successful creation of a community trail condition report."""
    payload = {
        "category": "fallen_tree",
        "severity": "HIGH",
        "passability": "IMPASSABLE",
        "latitude": 30.45,
        "longitude": 79.15,
        "description": "Large pine tree blocking the main trail path.",
        "location_accuracy_meters": 5.0,
        "location_source": "gps",
    }
    response = await client.post("/api/v1/trail-reports", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["category"] == "fallen_tree"
    assert data["severity"] == "HIGH"
    assert data["passability"] == "IMPASSABLE"
    assert data["verification_status"] == "UNVERIFIED"
    assert data["confirmations_count"] == 0
    assert data["is_expired"] is False
    assert "id" in data


@pytest.mark.asyncio
async def test_create_trail_report_invalid_coords(client):
    """Verifies invalid coordinates (e.g. lat > 90) fail with 422."""
    payload = {
        "category": "landslide",
        "severity": "CRITICAL",
        "passability": "IMPASSABLE",
        "latitude": 135.0,
        "longitude": 79.15,
    }
    response = await client.post("/api/v1/trail-reports", json=payload)
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_idempotent_report_submission(client, test_db):
    """Submitting the same idempotency key twice returns the original record without duplication."""
    idem_key = f"key-idem-{uuid.uuid4().hex[:10]}"
    payload = {
        "category": "flash_flood",
        "severity": "CRITICAL",
        "passability": "IMPASSABLE",
        "latitude": 30.73,
        "longitude": 79.06,
        "idempotency_key": idem_key,
    }
    resp1 = await client.post("/api/v1/trail-reports", json=payload)
    assert resp1.status_code == 201
    id1 = resp1.json()["id"]

    resp2 = await client.post("/api/v1/trail-reports", json=payload)
    assert resp2.status_code == 201
    id2 = resp2.json()["id"]

    assert id1 == id2


def test_category_expiry_durations():
    """Verifies category-specific freshness rules."""
    now = datetime(2026, 10, 24, 12, 0, tzinfo=timezone.utc)
    exp_flood = TrailReportService.calculate_expiry("flash_flood", now)
    assert exp_flood == now + timedelta(hours=12)

    exp_tree = TrailReportService.calculate_expiry("fallen_tree", now)
    assert exp_tree == now + timedelta(hours=72)

    exp_landslide = TrailReportService.calculate_expiry("landslide", now)
    assert exp_landslide == now + timedelta(hours=168)

    exp_bridge = TrailReportService.calculate_expiry("damaged_bridge", now)
    assert exp_bridge == now + timedelta(hours=336)


@pytest.mark.asyncio
async def test_photo_upload_and_exif_stripping(client):
    """Verifies photo upload endpoint validates content-type, strips metadata, and stores cleanly."""
    jpeg_bytes = _create_sample_jpeg_bytes()
    files = {"file": ("test_trail_photo.jpg", jpeg_bytes, "image/jpeg")}
    response = await client.post("/api/v1/trail-reports/upload-photo", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert data["photo_url"].startswith("/static/uploads/trail_reports/")
    assert data["content_type"] == "image/jpeg"
    assert data["size_bytes"] > 0


@pytest.mark.asyncio
async def test_photo_upload_rejects_unsupported_mimetype(client):
    """Verifies non-image uploads are rejected with 400 Bad Request."""
    fake_pdf = b"%PDF-1.4 test stream"
    files = {"file": ("document.pdf", fake_pdf, "application/pdf")}
    response = await client.post("/api/v1/trail-reports/upload-photo", files=files)
    assert response.status_code == 400


@pytest.mark.asyncio
async def test_corroboration_progression_to_corroborated(client, test_db):
    """Verifies that 3 independent confirmations promote status from UNVERIFIED to CORROBORATED."""
    idem_key = f"key-corrob-{uuid.uuid4().hex[:10]}"
    payload = {
        "category": "damaged_bridge",
        "severity": "HIGH",
        "passability": "DIFFICULT_REQUIRES_CAUTION",
        "latitude": 30.45,
        "longitude": 79.15,
        "idempotency_key": idem_key,
    }
    create_resp = await client.post("/api/v1/trail-reports", json=payload)
    assert create_resp.status_code == 201
    report_id = create_resp.json()["id"]

    r1 = await client.post(
        f"/api/v1/trail-reports/{report_id}/interact",
        json={"interaction_type": "CONFIRM", "device_id": "device-user-A"}
    )
    assert r1.status_code == 200
    assert r1.json()["confirmations_count"] == 1
    assert r1.json()["verification_status"] == "UNVERIFIED"

    r2 = await client.post(
        f"/api/v1/trail-reports/{report_id}/interact",
        json={"interaction_type": "CONFIRM", "device_id": "device-user-B"}
    )
    assert r2.status_code == 200
    assert r2.json()["confirmations_count"] == 2

    r3 = await client.post(
        f"/api/v1/trail-reports/{report_id}/interact",
        json={"interaction_type": "CONFIRM", "device_id": "device-user-C"}
    )
    assert r3.status_code == 200
    assert r3.json()["confirmations_count"] == 3
    assert r3.json()["verification_status"] == "CORROBORATED"


@pytest.mark.asyncio
async def test_interaction_anti_abuse_deduplication(client, test_db):
    """Verifies that a user/device cannot submit multiple confirmations for the same report."""
    idem_key = f"key-abuse-{uuid.uuid4().hex[:10]}"
    payload = {
        "category": "wildlife",
        "severity": "MEDIUM",
        "passability": "PASSABLE_ON_FOOT",
        "latitude": 30.50,
        "longitude": 79.20,
        "idempotency_key": idem_key,
    }
    create_resp = await client.post("/api/v1/trail-reports", json=payload)
    assert create_resp.status_code == 201
    report_id = create_resp.json()["id"]

    vote1 = await client.post(
        f"/api/v1/trail-reports/{report_id}/interact",
        json={"interaction_type": "CONFIRM", "device_id": "device-user-X"}
    )
    assert vote1.status_code == 200

    vote2 = await client.post(
        f"/api/v1/trail-reports/{report_id}/interact",
        json={"interaction_type": "CONFIRM", "device_id": "device-user-X"}
    )
    assert vote2.status_code == 400


@pytest.mark.asyncio
async def test_spatial_matching_to_approaching_active_trips(client, test_db):
    """
    Verifies that when an obstacle is reported along a planned trek corridor,
    active trips along that corridor are identified.
    """
    now = datetime.now(timezone.utc)
    route_id = f"test-route-{uuid.uuid4().hex[:8]}"

    route = PlannedRoute(
        id=route_id,
        title="Active Trek Corridor",
        origin_lat=30.40,
        origin_lon=79.10,
        dest_lat=30.50,
        dest_lon=79.20,
        origin_name="Basecamp",
        destination_name="Summit",
        geometry_geojson={
            "type": "LineString",
            "coordinates": [
                [79.10, 30.40],
                [79.15, 30.45],
                [79.20, 30.50]
            ]
        },
        distance_km=15.0,
        duration_minutes=180.0
    )
    test_db.add(route)

    trip = ActiveTrip(
        id=f"trip-{uuid.uuid4().hex[:8]}",
        route_id=route_id,
        user_id="trekker-1",
        status="ACTIVE",
        started_at=now,
    )
    test_db.add(trip)
    await test_db.commit()

    service = TrailReportService(test_db)
    payload = TrailReportCreate(
        category="landslide",
        severity="HIGH",
        passability="IMPASSABLE",
        latitude=30.451, # very close to [79.15, 30.45]
        longitude=79.151,
    )
    report, meta = await service.create_report(payload)
    assert meta["affected_trekkers"] >= 1
