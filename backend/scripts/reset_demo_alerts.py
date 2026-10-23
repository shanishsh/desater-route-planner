"""Reset simulated alerts relative to current execution time (T0)."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import asyncio
from sqlalchemy import select
from app.database.session import async_session_maker
from app.models.itinerary import Itinerary, RouteSegment
from app.services.alert_service import AlertService


async def reset():
    print("Resetting simulated demonstration alerts...")
    async with async_session_maker() as session:
        itin_result = await session.execute(select(Itinerary).where(Itinerary.is_active.is_(True)).limit(1))
        active_itin = itin_result.scalar_one_or_none()

        itinerary_id = active_itin.id if active_itin else None
        segment_id = None
        if active_itin:
            seg_result = await session.execute(
                select(RouteSegment).where(RouteSegment.itinerary_id == active_itin.id).limit(1)
            )
            seg = seg_result.scalar_one_or_none()
            segment_id = seg.id if seg else None

        service = AlertService(session)
        alerts = await service.seed_demo_alerts(itinerary_id=itinerary_id, segment_id=segment_id)

        print(f"Successfully reset {len(alerts)} simulated alerts at current timestamp:")
        for a in alerts:
            status = "STALE" if a.is_stale else "FRESH"
            print(f" - [{status}] {a.title} (Age: {a.age_minutes}m, Threshold: {a.freshness_threshold_seconds//60}m)")


if __name__ == "__main__":
    asyncio.run(reset())
