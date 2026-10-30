"""Redis caching layer with graceful degradation, bounded LRU fallback, and single-flight coalescing."""
import asyncio
import json
import logging
import time
from collections import OrderedDict
from typing import Optional, Any, Dict, Callable
from datetime import datetime
import redis.asyncio as aioredis
from app.config import settings

logger = logging.getLogger(__name__)

# Maximum number of items in the degraded in-memory cache to prevent memory leaks
MAX_IN_MEMORY_ENTRIES = 2000
REDIS_RECONNECT_INTERVAL_SECONDS = 30.0


class DateTimeEncoder(json.JSONEncoder):
    """Custom JSON encoder handling datetime objects."""
    def default(self, obj):
        if isinstance(obj, datetime):
            return obj.isoformat()
        return super().default(obj)


class SingleFlight:
    """Coalesces concurrent duplicate requests for the same key to prevent stampedes."""

    def __init__(self):
        self._flights: Dict[str, asyncio.Future] = {}
        self._lock = asyncio.Lock()

    async def do(self, key: str, coro_fn: Callable, *args, **kwargs) -> Any:
        """Executes coro_fn if not already running for key; otherwise awaits the active flight."""
        async with self._lock:
            if key in self._flights:
                return await self._flights[key]
            fut = asyncio.get_running_loop().create_future()
            self._flights[key] = fut

        try:
            val = await coro_fn(*args, **kwargs)
            if not fut.done():
                fut.set_result(val)
            return val
        except BaseException as e:
            if not fut.done():
                fut.set_exception(e)
            raise
        finally:
            async with self._lock:
                self._flights.pop(key, None)


class CacheService:
    """Async Redis cache service with bounded LRU memory fallback, auto-reconnect, and request coalescing."""

    def __init__(self):
        self._redis_client: Optional[aioredis.Redis] = None
        self._in_memory_cache: OrderedDict = OrderedDict()
        self._is_redis_available: bool = True
        self._last_redis_failure: float = 0.0
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self.single_flight = SingleFlight()

    def _record_metric(self, operation: str, result: str):
        try:
            from app.observability.metrics import CACHE_OPERATIONS
            CACHE_OPERATIONS.labels(operation=operation, result=result).inc()
        except Exception:
            pass

    async def get_client(self) -> Optional[aioredis.Redis]:
        """Initializes or returns active Redis client with automatic backoff reconnection."""
        try:
            current_loop = asyncio.get_running_loop()
        except RuntimeError:
            current_loop = None

        if self._redis_client is not None and self._loop != current_loop:
            try:
                await self._redis_client.aclose()
            except Exception:
                pass
            self._redis_client = None

        # If previously marked unavailable, check if reconnect backoff period has elapsed
        now = time.time()
        if not self._is_redis_available:
            if now - self._last_redis_failure < REDIS_RECONNECT_INTERVAL_SECONDS:
                return None
            logger.info("Attempting scheduled Redis reconnection...")

        if self._redis_client is None:
            try:
                self._redis_client = aioredis.from_url(
                    settings.REDIS_URL,
                    decode_responses=True,
                    socket_timeout=settings.REDIS_TIMEOUT_SECONDS,
                    socket_connect_timeout=settings.REDIS_TIMEOUT_SECONDS,
                )
                await self._redis_client.ping()
                self._loop = current_loop
                self._is_redis_available = True
                logger.info("Connected to Redis successfully.")
            except Exception as e:
                logger.warning("Redis unavailable, operating in bounded memory-cache mode: %s", e)
                self._is_redis_available = False
                self._last_redis_failure = now
                self._redis_client = None
                return None

        return self._redis_client

    async def get(self, key: str) -> Optional[Any]:
        """Fetches and deserializes a value from Redis or bounded LRU fallback memory."""
        try:
            client = await self.get_client()
            if client:
                data = await client.get(key)
                if data:
                    self._record_metric("get", "hit")
                    return json.loads(data)
                self._record_metric("get", "miss")
                return None
        except Exception as e:
            logger.warning("Redis get failed for key '%s', using fallback: %s", key, e)
            self._record_metric("get", "error")
            self._is_redis_available = False
            self._last_redis_failure = time.time()

        # In-memory LRU fallback
        item = self._in_memory_cache.get(key)
        if item:
            val, expiry = item
            if expiry is None or time.time() < expiry:
                self._in_memory_cache.move_to_end(key)
                self._record_metric("get", "hit_memory")
                return val
            else:
                del self._in_memory_cache[key]

        self._record_metric("get", "miss")
        return None

    async def set(self, key: str, value: Any, ttl_seconds: int = 300) -> bool:
        """Serializes and saves a value to Redis or bounded LRU fallback memory with TTL."""
        serialized = json.dumps(value, cls=DateTimeEncoder)
        try:
            client = await self.get_client()
            if client:
                await client.set(key, serialized, ex=ttl_seconds)
                self._record_metric("set", "success")
                return True
        except Exception as e:
            logger.warning("Redis set failed for key '%s', using fallback: %s", key, e)
            self._record_metric("set", "error")
            self._is_redis_available = False
            self._last_redis_failure = time.time()

        # Bounded LRU in-memory fallback
        expiry = time.time() + ttl_seconds
        if len(self._in_memory_cache) >= MAX_IN_MEMORY_ENTRIES:
            # Evict least recently used entry
            self._in_memory_cache.popitem(last=False)

        self._in_memory_cache[key] = (value, expiry)
        self._in_memory_cache.move_to_end(key)
        self._record_metric("set", "success_memory")
        return True

    async def delete(self, key: str) -> bool:
        """Deletes a key from Redis and fallback memory."""
        try:
            client = await self.get_client()
            if client:
                await client.delete(key)
                self._record_metric("delete", "success")
        except Exception as e:
            logger.warning("Redis delete failed for key '%s': %s", key, e)
            self._record_metric("delete", "error")

        self._in_memory_cache.pop(key, None)
        return True

    async def clear(self) -> None:
        """Flushes in-memory cache and Redis keys if available."""
        self._in_memory_cache.clear()
        try:
            client = await self.get_client()
            if client:
                await client.flushdb()
        except Exception as e:
            logger.warning("Redis flushdb failed: %s", e)

    async def acquire_lock(self, lock_key: str, timeout_seconds: int = 10) -> bool:
        """Acquires a distributed lock for stampede protection."""
        try:
            client = await self.get_client()
            if client:
                acquired = await client.set(f"lock:{lock_key}", "locked", nx=True, ex=timeout_seconds)
                return bool(acquired)
        except Exception as e:
            logger.warning("Lock acquire failed for '%s': %s", lock_key, e)
        return True  # Allow execution in degraded mode

    async def release_lock(self, lock_key: str) -> None:
        """Releases the distributed lock."""
        try:
            client = await self.get_client()
            if client:
                await client.delete(f"lock:{lock_key}")
        except Exception:
            pass

    async def close(self) -> None:
        """Closes Redis connections gracefully."""
        if self._redis_client is not None:
            try:
                await self._redis_client.aclose()
            except Exception:
                pass
            self._redis_client = None


cache_service = CacheService()
