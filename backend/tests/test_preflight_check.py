"""Unit tests for the preflight check script."""
import pytest
from scripts.preflight_check import PreflightChecker


@pytest.mark.asyncio
async def test_preflight_checker_run():
    from app.config import settings
    checker = PreflightChecker(strict=False)
    checker.check_env_variables(settings)
    
    assert len(checker.report["checks"]) > 0
    assert checker.report["overall_status"] in ("PASS", "WARN", "FAIL")
    assert "timestamp_utc" in checker.report
    assert "summary" in checker.report

