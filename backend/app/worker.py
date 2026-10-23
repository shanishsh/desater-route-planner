"""Background worker entrypoint using ARQ or standalone async periodic runner."""
import asyncio
import logging
from arq.connections import RedisSettings
from app.config import settings
from app.jobs.scheduled_tasks import refresh_active_weather, evaluate_alert_freshness
from app.core.logging import setup_logging

logger = logging.getLogger(__name__)


async def startup(ctx):
    setup_logging(settings.LOG_LEVEL)
    logger.info("Worker process initialized and connected to job queue")


async def shutdown(ctx):
    logger.info("Worker process shutting down gracefully")


class WorkerSettings:
    """ARQ Worker configuration."""
    functions = [refresh_active_weather, evaluate_alert_freshness]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.REDIS_URL)
    max_jobs = 10
    job_timeout = 60


async def run_standalone_loop():
    """Runs a standalone periodic background loop when ARQ CLI is not used."""
    setup_logging(settings.LOG_LEVEL)
    logger.info("Starting standalone periodic worker loop...")
    ctx = {}
    await startup(ctx)
    try:
        while True:
            try:
                await refresh_active_weather(ctx)
                await evaluate_alert_freshness(ctx)
            except Exception as e:
                logger.error(f"Error in background task cycle: {e}")
            await asyncio.sleep(600)  # run every 10 minutes
    except (asyncio.CancelledError, KeyboardInterrupt):
        await shutdown(ctx)


if __name__ == "__main__":
    asyncio.run(run_standalone_loop())

