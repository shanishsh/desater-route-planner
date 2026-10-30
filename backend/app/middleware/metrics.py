"""Prometheus metrics collection middleware for HTTP request count and latency."""
import time
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response
from app.observability.metrics import REQUEST_COUNT, REQUEST_LATENCY


class PrometheusMetricsMiddleware(BaseHTTPMiddleware):
    """Middleware recording HTTP request counts and durations for Prometheus."""

    async def dispatch(self, request: Request, call_next) -> Response:
        if request.url.path == "/metrics":
            return await call_next(request)

        # Normalize endpoint path to avoid high-cardinality explosions
        endpoint = request.url.path
        route = request.scope.get("route")
        if route and hasattr(route, "path"):
            endpoint = route.path

        start_time = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            return response
        finally:
            duration = time.perf_counter() - start_time
            try:
                REQUEST_COUNT.labels(
                    method=request.method,
                    endpoint=endpoint,
                    status_code=str(status_code),
                ).inc()
                REQUEST_LATENCY.labels(endpoint=endpoint).observe(duration)
            except Exception:
                pass

