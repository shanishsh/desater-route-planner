"""Common schemas for pagination, standard responses, and health."""
from datetime import datetime
from typing import Generic, List, Optional, TypeVar
from pydantic import BaseModel, Field

T = TypeVar("T")


class PaginatedResponse(BaseModel, Generic[T]):
    items: List[T]
    total: int
    limit: int
    offset: int
    has_more: bool
    provider_status: Optional[str] = "success"  # success, empty, partial, stale, provider_error, not_configured
    retrieved_at: Optional[datetime] = None
    is_stale: bool = False
    source_provenance: Optional[str] = None


class ErrorResponse(BaseModel):
    error: str
    message: str
    details: Optional[dict] = None


class HealthResponse(BaseModel):
    status: str = "ok"
    version: str = "0.1.0"
    db_status: str
    redis_status: str
    timestamp: str

