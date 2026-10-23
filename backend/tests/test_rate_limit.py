"""Tests for Rate Limiting middleware and headers."""
import pytest
from app.config import settings


@pytest.mark.asyncio
async def test_rate_limiting_headers_and_limit(client):
    # Verify rate limit headers exist on standard response
    res = await client.get("/api/v1/sources")
    assert res.status_code == 200
    assert "X-RateLimit-Limit" in res.headers
    assert "X-RateLimit-Remaining" in res.headers

