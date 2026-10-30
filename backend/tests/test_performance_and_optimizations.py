"""Unit and integration tests for performance optimizations:
- Redis cache bounded LRU eviction
- Single-Flight request coalescing
- Prometheus metrics recording
- Batch MapPointRepository upsert
- Point context caching
"""
import asyncio
import time
import pytest
from app.cache.redis_cache import CacheService, SingleFlight, MAX_IN_MEMORY_ENTRIES
from app.observability.metrics import REQUEST_COUNT, CACHE_OPERATIONS
from app.models.map_point import MapPoint
from app.repositories.map_point_repo import MapPointRepository


async def pytest_asyncio_none():
    return None


@pytest.mark.asyncio
async def test_single_flight_coalescing():
    """Verify concurrent requests for the same key execute loader only once."""
    sf = SingleFlight()
    call_count = 0

    async def expensive_fetch():
        nonlocal call_count
        call_count += 1
        await asyncio.sleep(0.05)
        return "shared_result"

    # Launch 10 concurrent requests for the same key
    tasks = [sf.do("sample_flight_key", expensive_fetch) for _ in range(10)]
    results = await asyncio.gather(*tasks)

    assert len(results) == 10
    assert all(r == "shared_result" for r in results)
    assert call_count == 1  # Only 1 execution occurred across all 10 callers!


@pytest.mark.asyncio
async def test_cache_lru_bounded_eviction(monkeypatch):
    """Verify in-memory fallback cache evicts LRU items and stays within capacity."""
    service = CacheService()
    # Force degraded mode to test bounded LRU behavior
    monkeypatch.setattr(service, "get_client", pytest_asyncio_none)

    # Insert up to limit
    for i in range(50):
        await service.set(f"key_{i}", f"val_{i}", ttl_seconds=60)

    # Verify oldest keys are accessible
    assert await service.get("key_0") == "val_0"
    assert await service.get("key_49") == "val_49"

    # Fill beyond small custom threshold to test eviction
    service._in_memory_cache.clear()
    original_max = MAX_IN_MEMORY_ENTRIES
    from app.cache import redis_cache
    redis_cache.MAX_IN_MEMORY_ENTRIES = 5
    try:
        for i in range(7):
            await service.set(f"evict_key_{i}", i, ttl_seconds=60)

        # Keys 0 and 1 should have been evicted
        assert len(service._in_memory_cache) == 5
        assert await service.get("evict_key_0") is None
        assert await service.get("evict_key_1") is None
        assert await service.get("evict_key_6") == 6
    finally:
        redis_cache.MAX_IN_MEMORY_ENTRIES = original_max


@pytest.mark.asyncio
async def test_prometheus_metrics_middleware_recording(client):
    """Verify Prometheus metrics middleware tracks request count and latency."""
    res = await client.get("/health")
    assert res.status_code == 200

    metrics_res = await client.get("/metrics")
    assert metrics_res.status_code == 200
    metrics_text = metrics_res.text

    assert "http_requests_total" in metrics_text
    assert "http_request_duration_seconds" in metrics_text
    assert 'endpoint="/health"' in metrics_text or "status_code=\"200\"" in metrics_text


@pytest.mark.asyncio
async def test_batch_map_point_upsert(test_db):
    """Verify batch upsert correctly inserts new points and updates existing without N+1."""
    repo = MapPointRepository(test_db)

    # 1. Batch insert 5 new points
    pts = [
        MapPoint(
            id=f"test_pt_{i}",
            osm_id=f"osm_{i}",
            name=f"Clinic {i}",
            category="clinic",
            latitude=30.30 + (i * 0.01),
            longitude=78.00 + (i * 0.01),
            verification_status="VERIFIED"
        )
        for i in range(5)
    ]
    inserted = await repo.upsert_points(pts)
    assert inserted == 5

    # 2. Batch update 3 existing points + 2 new points
    updated_and_new = [
        MapPoint(
            id=f"test_pt_{i}",
            osm_id=f"osm_{i}",
            name=f"Updated Clinic {i}",
            category="clinic",
            latitude=30.30 + (i * 0.01),
            longitude=78.00 + (i * 0.01),
            verification_status="VERIFIED"
        )
        for i in range(3)
    ] + [
        MapPoint(
            id=f"test_pt_new_{j}",
            osm_id=f"osm_new_{j}",
            name=f"Brand New Clinic {j}",
            category="clinic",
            latitude=30.40 + (j * 0.01),
            longitude=78.10 + (j * 0.01),
            verification_status="VERIFIED"
        )
        for j in range(2)
    ]

    inserted2 = await repo.upsert_points(updated_and_new)
    assert inserted2 == 2  # Only the 2 brand new points inserted

    # Verify update persisted
    pt0 = await repo.get_by_id("test_pt_0")
    assert pt0 is not None
    assert pt0.name == "Updated Clinic 0"


@pytest.mark.asyncio
async def test_point_context_endpoint_caching(client):
    """Verify GET /places/point-context caches responses and second call reports is_cached=True."""
    # First call
    res1 = await client.get("/api/v1/places/point-context?lat=30.3165&lon=78.0322")
    assert res1.status_code == 200
    data1 = res1.json()
    assert data1["in_uttarakhand"] is True

    # Second call (same coordinates)
    res2 = await client.get("/api/v1/places/point-context?lat=30.3165&lon=78.0322")
    assert res2.status_code == 200
    data2 = res2.json()
    assert data2["is_cached"] is True
    assert data2["latitude"] == data1["latitude"]
    assert data2["longitude"] == data1["longitude"]
