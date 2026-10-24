"""Tests for MapPoint spatial queries and categories."""
from datetime import datetime, timezone
import pytest
from app.models.map_point import MapPoint


@pytest.mark.asyncio
async def test_map_point_spatial_and_category_queries(client, test_db):
    now = datetime.now(timezone.utc)
    # Point 1: Inside target valley (lat: 32.24, lon: 77.18)
    p1 = MapPoint(
        name="Valley Medical Clinic",
        category="medical",
        latitude=32.24,
        longitude=77.18,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    # Point 2: Inside target valley (lat: 32.25, lon: 77.19)
    p2 = MapPoint(
        name="Mountain Police Post",
        category="police",
        latitude=32.25,
        longitude=77.19,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    # Point 3: Outside target valley (lat: 35.00, lon: 75.00)
    p3 = MapPoint(
        name="Distant Station",
        category="police",
        latitude=35.00,
        longitude=75.00,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    test_db.add_all([p1, p2, p3])
    await test_db.commit()

    # 1. Bounding box query covering only the valley: bbox = min_lon,min_lat,max_lon,max_lat
    # 77.10, 32.20, 77.30, 32.30
    res_bbox = await client.get("/api/v1/map-points?bbox=77.10,32.20,77.30,32.30")
    assert res_bbox.status_code == 200
    data_bbox = res_bbox.json()
    assert data_bbox["total"] == 2
    names = [p["name"] for p in data_bbox["items"]]
    assert "Valley Medical Clinic" in names
    assert "Mountain Police Post" in names
    assert "Distant Station" not in names

    # 2. Category filter: only 'medical'
    res_cat = await client.get("/api/v1/map-points?category=medical")
    assert res_cat.status_code == 200
    data_cat = res_cat.json()
    assert data_cat["total"] == 1
    assert data_cat["items"][0]["name"] == "Valley Medical Clinic"


@pytest.mark.asyncio
async def test_unbounded_map_points_returns_seeded_facilities(client, test_db):
    """Verifies that an unbounded client call (GET /api/v1/map-points?limit=100) returns facilities."""
    res = await client.get("/api/v1/map-points?limit=100&offset=0")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] > 0
    assert len(data["items"]) > 0
    assert data["provider_status"] == "success"
    categories = {item["category"] for item in data["items"]}
    # Verify core emergency amenities are present in the response
    assert any(c in categories for c in ("hospital", "medical"))
    assert any(c in categories for c in ("police",))
    assert any(c in categories for c in ("fuel", "petrol_pump", "fuel_station"))
    assert any(c in categories for c in ("fire_station",))


@pytest.mark.asyncio
async def test_facility_category_filters(client, test_db):
    """Verifies querying specific category filters: hospital, clinic, police, fuel, fire_station."""
    for cat in ["hospital", "clinic", "police", "fuel", "fire_station"]:
        res = await client.get(f"/api/v1/map-points?category={cat}&limit=50")
        assert res.status_code == 200
        data = res.json()
        assert data["total"] >= 1
        for item in data["items"]:
            if cat == "hospital":
                assert item["category"] in ("hospital", "medical")
            elif cat == "fuel":
                assert item["category"] in ("fuel", "petrol_pump", "fuel_station")
            elif cat == "clinic":
                assert item["category"] in ("clinic", "medical")
            else:
                assert item["category"] == cat

