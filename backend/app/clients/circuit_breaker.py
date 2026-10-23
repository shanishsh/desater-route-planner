"""Circuit breaker pattern implementation for upstream HTTP clients."""
import time
import logging
from enum import Enum
from typing import Callable, Any

logger = logging.getLogger(__name__)


class CircuitState(Enum):
    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"


class CircuitBreaker:
    """Isolates failing external dependencies to prevent cascading failures."""

    def __init__(
        self,
        name: str,
        failure_threshold: int = 3,
        recovery_time_seconds: float = 30.0,
    ):
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_time_seconds = recovery_time_seconds
        self.state = CircuitState.CLOSED
        self.failure_count = 0
        self.last_failure_time = 0.0

    def can_execute(self) -> bool:
        """Determines if a request should be attempted."""
        now = time.time()
        if self.state == CircuitState.OPEN:
            if now - self.last_failure_time >= self.recovery_time_seconds:
                logger.info(f"CircuitBreaker [{self.name}] transitioning OPEN -> HALF_OPEN")
                self.state = CircuitState.HALF_OPEN
                return True
            return False
        return True

    def record_success(self) -> None:
        """Records a successful execution, resetting circuit counters."""
        if self.state != CircuitState.CLOSED:
            logger.info(f"CircuitBreaker [{self.name}] recovered -> CLOSED")
        self.state = CircuitState.CLOSED
        self.failure_count = 0

    def record_failure(self) -> None:
        """Records a failure and potentially trips the circuit."""
        self.failure_count += 1
        self.last_failure_time = time.time()
        if self.failure_count >= self.failure_threshold:
            self.state = CircuitState.OPEN
            logger.warning(
                f"CircuitBreaker [{self.name}] TRIPPED -> OPEN (failure count: {self.failure_count})"
            )

