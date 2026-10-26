"""Tests for MapPoint spatial queries and categories."""
from datetime import datetime, timezone
import pytest
from app.models.map_point import MapPoint


@pytest.mark.asyncio
async def test_map_point_spatial_and_category_queries(client, test_db):
    now = datetime.now(timezone.utc)
    # Point 1: Inside target Kedarnath valley (lat: 30.7339, lon: 79.0669)
    p1 = MapPoint(
        name="Kedarnath Medical Clinic",
        category="medical",
        latitude=30.7339,
        longitude=79.0669,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    # Point 2: Inside target valley (lat: 30.6534, lon: 79.0268)
    p2 = MapPoint(
        name="Gaurikund Police Post",
        category="police",
        latitude=30.6534,
        longitude=79.0268,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    # Point 3: Outside target valley (lat: 28.00, lon: 75.00)
    p3 = MapPoint(
        name="Distant Station",
        category="police",
        latitude=28.00,
        longitude=75.00,
        verification_status="verified",
        verification_timestamp=now,
        provenance="Registry",
    )
    test_db.add_all([p1, p2, p3])
    await test_db.commit()

    # 1. Bounding box query covering only the valley: bbox = min_lon,min_lat,max_lon,max_lat
    # 79.00, 30.60, 79.10, 30.80
    res_bbox = await client.get("/api/v1/map-points?bbox=79.00,30.60,79.10,30.80")
    assert res_bbox.status_code == 200
    data_bbox = res_bbox.json()
    assert data_bbox["total"] == 2
    names = [p["name"] for p in data_bbox["items"]]
    assert "Kedarnath Medical Clinic" in names
    assert "Gaurikund Police Post" in names
    assert "Distant Station" not in names

    # 2. Category filter: only 'medical'
    res_cat = await client.get("/api/v1/map-points?category=medical")
    assert res_cat.status_code == 200
    data_cat = res_cat.json()
    assert data_cat["total"] >= 1
    assert any(p["name"] == "Kedarnath Medical Clinic" for p in data_cat["items"])


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

