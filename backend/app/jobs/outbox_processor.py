"""Transactional Outbox processor for disaster alerts and notifications."""
import json
import logging
from typing import Optional
from datetime import datetime, timezone, timedelta
from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession
from app.database.session import AsyncSessionLocal
from app.models.outbox import OutboxEvent
from app.services.notification_dispatcher import NotificationDispatcher

logger = logging.getLogger(__name__)


async def process_outbox_events(session: Optional[AsyncSession] = None, batch_size: int = 10) -> int:
    """Processes pending outbox events in a resilient transactional loop."""
    if session is not None:
        return await _process_events_with_session(session, batch_size)
    async with AsyncSessionLocal() as new_session:
        return await _process_events_with_session(new_session, batch_size)


async def _process_events_with_session(session: AsyncSession, batch_size: int = 10) -> int:
    now = datetime.now(timezone.utc)
    processed_count = 0

    stmt = (
        select(OutboxEvent)
        .where(
            or_(
                OutboxEvent.status == "PENDING",
                (
                    (OutboxEvent.status == "FAILED")
                    & (OutboxEvent.retry_count < OutboxEvent.max_retries)
                    & (OutboxEvent.next_retry_at <= now)
                ),
            )
        )
        .order_by(OutboxEvent.created_at.asc())
        .limit(batch_size)
    )
    result = await session.execute(stmt)
    events = result.scalars().all()

    if not events:
        return 0

    dispatcher = NotificationDispatcher(session)

    for event in events:
        event.status = "PROCESSING"
        await session.commit()

        try:
            payload = json.loads(event.payload_json)
            alert_id = payload.get("alert_id")

            if event.event_type in ("DISASTER_ALERT_CREATED", "DISASTER_ALERT_UPDATED"):
                if alert_id:
                    await dispatcher.dispatch_for_alert(alert_id)
            elif event.event_type == "DISASTER_ALERT_CANCELLED":
                logger.info("Outbox processing cancellation notice for alert %s", alert_id)
                if alert_id:
                    await dispatcher.dispatch_for_alert(alert_id)

            event.status = "COMPLETED"
            event.processed_at = datetime.now(timezone.utc)
            event.error_message = None
            processed_count += 1
            logger.info("Successfully processed outbox event %s (%s)", event.id, event.event_type)

        except Exception as ex:
            event.retry_count += 1
            backoff_seconds = min(300, (2 ** event.retry_count) * 5)
            event.next_retry_at = datetime.now(timezone.utc) + timedelta(seconds=backoff_seconds)
            event.error_message = str(ex)
            if event.retry_count >= event.max_retries:
                event.status = "FAILED"
                logger.error("Outbox event %s permanently failed after %d retries: %s", event.id, event.retry_count, ex)
            else:
                event.status = "FAILED"
                logger.warning("Outbox event %s failed (retry %d in %ds): %s", event.id, event.retry_count, backoff_seconds, ex)

        await session.commit()

    return processed_count
