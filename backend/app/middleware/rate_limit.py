"""Rate limiting middleware with Redis and degraded in-memory sliding window."""
import time
import logging
from typing import Dict, Tuple
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from app.config import settings
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(self, app):
        super().__init__(app)
        self._in_memory_limits: Dict[str, Tuple[int, float]] = {}

    async def dispatch(self, request: Request, call_next):
        # Exclude docs, health, metrics, and static assets from rate limiting
        path = request.url.path
        if (
            path.startswith("/docs")
            or path.startswith("/openapi.json")
            or path.startswith("/api/v1/health")
            or path.startswith("/metrics")
        ):
            return await call_next(request)

        # Identify client by IP (or forwarded header)
        client_ip = request.client.host if request.client else "unknown"
        if "x-forwarded-for" in request.headers:
            client_ip = request.headers["x-forwarded-for"].split(",")[0].strip()

        # Determine rate limit based on endpoint
        limit = settings.RATE_LIMIT_PER_MINUTE
        if "/weather" in path:
            limit = settings.WEATHER_RATE_LIMIT_PER_MINUTE

        key = f"rate_limit:{client_ip}:{path.split('/')[-1]}"
        now = time.time()

        # Check rate limit via Redis
        client = await cache_service.get_client()
        exceeded = False
        remaining = limit

        if client:
            try:
                current = await client.incr(key)
                if current == 1:
                    await client.expire(key, 60)
                remaining = max(0, limit - current)
                if current > limit:
                    exceeded = True
            except Exception as e:
                logger.warning(f"Redis rate limiting failed: {e}")
        else:
            # Degraded in-memory rate limiting
            count, reset_at = self._in_memory_limits.get(key, (0, now + 60))
            if now > reset_at:
                count = 0
                reset_at = now + 60
            count += 1
            self._in_memory_limits[key] = (count, reset_at)
            remaining = max(0, limit - count)
            if count > limit:
                exceeded = True

        if exceeded:
            return JSONResponse(
                status_code=429,
                content={
                    "error": "RATE_LIMIT_EXCEEDED",
                    "message": "Too many requests. Please slow down.",
                    "details": {"limit_per_minute": limit},
                },
                headers={"Retry-After": "60", "X-RateLimit-Remaining": "0"},
            )

        response = await call_next(request)
        response.headers["X-RateLimit-Limit"] = str(limit)
        response.headers["X-RateLimit-Remaining"] = str(remaining)
        return response

