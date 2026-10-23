"""Tests for CircuitBreaker state transitions."""
from app.clients.circuit_breaker import CircuitBreaker, CircuitState


def test_circuit_breaker_transitions():
    cb = CircuitBreaker("TestService", failure_threshold=2, recovery_time_seconds=0.1)
    assert cb.state == CircuitState.CLOSED
    assert cb.can_execute() is True

    # Record first failure
    cb.record_failure()
    assert cb.state == CircuitState.CLOSED
    assert cb.can_execute() is True

    # Record second failure -> trips to OPEN
    cb.record_failure()
    assert cb.state == CircuitState.OPEN
    assert cb.can_execute() is False

    # Simulate success after reset
    cb.record_success()
    assert cb.state == CircuitState.CLOSED
    assert cb.can_execute() is True

