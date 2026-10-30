"""Background worker entrypoint using ARQ or standalone async periodic runner."""
import asyncio
import logging
import time
from arq.connections import RedisSettings
from app.config import settings
from app.jobs.scheduled_tasks import refresh_active_weather, evaluate_alert_freshness, ingest_disaster_alerts
from app.jobs.outbox_processor import process_outbox_events
from app.core.logging import setup_logging

logger = logging.getLogger(__name__)


async def startup(ctx):
    setup_logging(settings.LOG_LEVEL)
    logger.info("Worker process initialized and connected to job queue")


async def shutdown(ctx):
    logger.info("Worker process shutting down gracefully")


class WorkerSettings:
    """ARQ Worker configuration."""
    functions = [refresh_active_weather, evaluate_alert_freshness, process_outbox_events, ingest_disaster_alerts]
    on_startup = startup
    on_shutdown = shutdown
    redis_settings = RedisSettings.from_dsn(settings.REDIS_URL)
    max_jobs = 10
    job_timeout = 60


async def run_standalone_loop():
    """Runs a standalone periodic background loop when ARQ CLI is not used, with decoupled task cadences."""
    setup_logging(settings.LOG_LEVEL)
    logger.info("Starting standalone periodic worker loop with decoupled cadences...")
    ctx = {}
    await startup(ctx)

    # Cadence intervals in seconds
    outbox_interval = getattr(settings, "OUTBOX_POLL_INTERVAL_SECONDS", 2.0)
    weather_interval = 900.0   # 15 minutes
    freshness_interval = 300.0  # 5 minutes
    ingest_interval = 600.0    # 10 minutes

    last_weather_run = 0.0
    last_freshness_run = 0.0
    last_ingest_run = 0.0

    try:
        while True:
            now = time.time()
            try:
                # 1. High-frequency transactional outbox processor
                await process_outbox_events()
            except Exception as e:
                logger.error("Error processing transactional outbox: %s", e)

            # 2. Moderate-frequency alert freshness evaluator
            if now - last_freshness_run >= freshness_interval:
                try:
                    await evaluate_alert_freshness(ctx)
                    last_freshness_run = now
                except Exception as e:
                    logger.error("Error evaluating alert freshness: %s", e)

            # 3. Scheduled disaster alerts ingestion
            if now - last_ingest_run >= ingest_interval:
                try:
                    await ingest_disaster_alerts(ctx)
                    last_ingest_run = now
                except Exception as e:
                    logger.error("Error ingesting disaster alerts: %s", e)

            # 4. Scheduled active route weather refresh
            if now - last_weather_run >= weather_interval:
                try:
                    await refresh_active_weather(ctx)
                    last_weather_run = now
                except Exception as e:
                    logger.error("Error refreshing active weather: %s", e)

            await asyncio.sleep(outbox_interval)
    except (asyncio.CancelledError, KeyboardInterrupt):
        await shutdown(ctx)


if __name__ == "__main__":
    asyncio.run(run_standalone_loop())
