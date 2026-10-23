"""Redis caching layer with graceful degradation and stampede lock."""
import json
import logging
from typing import Optional, Any
from datetime import datetime
import redis.asyncio as aioredis
from app.config import settings

logger = logging.getLogger(__name__)


class DateTimeEncoder(json.JSONEncoder):
    """Custom JSON encoder handling datetime objects."""
    def default(self, obj):
        if isinstance(obj, datetime):
            return obj.isoformat()
        return super().default(obj)


class CacheService:
    """Async Redis cache service with in-memory fallback and stampede protection."""

    def __init__(self):
        self._redis_client: Optional[aioredis.Redis] = None
        self._in_memory_cache: dict = {}
        self._is_redis_available: bool = True

    async def get_client(self) -> Optional[aioredis.Redis]:
        """Initializes or returns the active Redis client."""
        if not self._is_redis_available:
            return None

        if self._redis_client is None:
            try:
                self._redis_client = aioredis.from_url(
                    settings.REDIS_URL,
                    decode_responses=True,
                    socket_timeout=settings.REDIS_TIMEOUT_SECONDS,
                    socket_connect_timeout=settings.REDIS_TIMEOUT_SECONDS,
                )
                await self._redis_client.ping()
            except Exception as e:
                logger.warning(f"Redis unavailable, operating in degraded memory-cache mode: {e}")
                self._is_redis_available = False
                self._redis_client = None
                return None
        return self._redis_client

    async def get(self, key: str) -> Optional[Any]:
        """Fetches and deserializes a value from Redis or fallback memory."""
        try:
            client = await self.get_client()
            if client:
                data = await client.get(key)
                if data:
                    return json.loads(data)
                return None
        except Exception as e:
            logger.warning(f"Redis get failed for key '{key}', using fallback: {e}")
        
        # In-memory fallback
        item = self._in_memory_cache.get(key)
        if item:
            val, expiry = item
            if expiry is None or datetime.now().timestamp() < expiry:
                return val
            else:
                del self._in_memory_cache[key]
        return None

    async def set(self, key: str, value: Any, ttl_seconds: int = 300) -> bool:
        """Serializes and saves a value to Redis or fallback memory with TTL."""
        serialized = json.dumps(value, cls=DateTimeEncoder)
        try:
            client = await self.get_client()
            if client:
                await client.set(key, serialized, ex=ttl_seconds)
                return True
        except Exception as e:
            logger.warning(f"Redis set failed for key '{key}', using fallback: {e}")

        # In-memory fallback
        expiry = datetime.now().timestamp() + ttl_seconds
        self._in_memory_cache[key] = (value, expiry)
        return True

    async def delete(self, key: str) -> bool:
        """Deletes a key from Redis and fallback memory."""
        try:
            client = await self.get_client()
            if client:
                await client.delete(key)
        except Exception as e:
            logger.warning(f"Redis delete failed for key '{key}': {e}")
        
        self._in_memory_cache.pop(key, None)
        return True

    async def acquire_lock(self, lock_key: str, timeout_seconds: int = 10) -> bool:
        """Acquires a distributed lock for stampede protection."""
        try:
            client = await self.get_client()
            if client:
                acquired = await client.set(f"lock:{lock_key}", "locked", nx=True, ex=timeout_seconds)
                return bool(acquired)
        except Exception as e:
            logger.warning(f"Lock acquire failed for '{lock_key}': {e}")
        return True  # Allow execution in degraded mode

    async def release_lock(self, lock_key: str) -> None:
        """Releases the distributed lock."""
        try:
            client = await self.get_client()
            if client:
                await client.delete(f"lock:{lock_key}")
        except Exception:
            pass


cache_service = CacheService()

