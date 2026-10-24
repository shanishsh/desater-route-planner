"""Background worker scheduled jobs."""
import logging
from sqlalchemy import select
from app.database.session import async_session_maker
from app.models.itinerary import Itinerary
from app.services.weather_service import WeatherService
from app.services.alert_service import AlertService

logger = logging.getLogger(__name__)


async def refresh_active_weather(ctx: dict) -> int:
    """Refreshes weather forecasts for all active itineraries."""
    logger.info("Executing background job: refresh_active_weather")
    refreshed = 0
    async with async_session_maker() as session:
        result = await session.execute(select(Itinerary).where(Itinerary.is_active.is_(True)))
        itineraries = result.scalars().all()

        weather_service = WeatherService(session)
        for itin in itineraries:
            try:
                await weather_service.get_weather(itin.start_latitude, itin.start_longitude)
                refreshed += 1
            except Exception as e:
                logger.warning(f"Background weather refresh failed for itinerary {itin.id}: {e}")

    logger.info(f"Background weather refresh completed: {refreshed} locations updated")
    return refreshed


async def evaluate_alert_freshness(ctx: dict) -> int:
    """Scans and evaluates alert freshness metrics."""
    logger.info("Executing background job: evaluate_alert_freshness")
    stale_count = 0
    async with async_session_maker() as session:
        alert_service = AlertService(session)
        alerts, total = await alert_service.list_alerts(limit=100)
        for a in alerts:
            if a.is_stale:
                stale_count += 1

    logger.info(f"Alert freshness evaluation completed: {stale_count} stale alerts identified out of {total}")
    return stale_count


async def ingest_disaster_alerts(ctx: dict) -> int:
    """
    Ingests official disaster alerts from NDMA SACHET CAP feeds and Uttarakhand PWD.
    Parses genuine CAP XML, enforces idempotency, updates lifecycle, and persists records.
    """
    logger.info("Executing background job: ingest_disaster_alerts")
    synced_count = 0
    async with async_session_maker() as session:
        try:
            from app.clients.ndma_sachet import ndma_sachet_client
            synced_count = await ndma_sachet_client.sync_sachet_alerts(session, max_fetch_details=15)
        except Exception as e:
            logger.error("NDMA SACHET background sync failed: %s", e)

        try:
            from app.clients.uttarakhand_pwd import uttarakhand_pwd_client
            await uttarakhand_pwd_client.fetch_latest_bulletins()
        except Exception as e:
            logger.warning("Uttarakhand PWD background bulletin fetch failed: %s", e)

    logger.info("Disaster alerts ingestion completed: %d SACHET alerts synced.", synced_count)
    return synced_count


