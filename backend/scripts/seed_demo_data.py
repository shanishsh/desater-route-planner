"""Seed database with sample itinerary, verified map points, admin user, and the 3 simulated alerts."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import asyncio
from datetime import datetime, timezone, timedelta
from app.database.session import async_session_maker
from app.database.base import Base
from app.database.session import engine
from app.models.user import User
from app.models.itinerary import Itinerary, RouteSegment
from app.models.map_point import MapPoint
from app.models.alert import Alert
from app.models.crowd import CrowdSnapshot
from app.models.disaster_alert import DisasterAlert
from app.models.subscriber import Subscriber
from app.core.security import get_password_hash
from app.config import settings


async def seed():
    print("Initializing database tables...")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with async_session_maker() as session:
        from sqlalchemy import select
        admin_check = await session.execute(select(User).where(User.email == settings.ADMIN_EMAIL))
        if not admin_check.scalar_one_or_none():
            print("Seeding Administrator user...")
            admin = User(
                email=settings.ADMIN_EMAIL,
                hashed_password=get_password_hash(settings.ADMIN_PASSWORD),
                role="admin",
                is_active=True,
            )
            session.add(admin)

        print("Seeding Sample Itinerary: Rohtang Valley Ridge Trail...")
        # Coordinates in the scenic Himalayan valley near Manali (32.2396 N, 77.1887 E)
        route_geojson = {
            "type": "FeatureCollection",
            "features": [
                {
                    "type": "Feature",
                    "properties": {"name": "Rohtang Valley Ridge Trail", "color": "#E53935"},
                    "geometry": {
                        "type": "LineString",
                        "coordinates": [
                            [77.1887, 32.2396],
                            [77.1950, 32.2450],
                            [77.2020, 32.2530],
                            [77.2110, 32.2610],
                            [77.2200, 32.2700],
                            [77.2290, 32.2790],
                            [77.2380, 32.2880],
                            [77.2470, 32.2970],
                        ],
                    },
                }
            ],
        }

        itin_check = await session.execute(
            select(Itinerary).where(Itinerary.title == "Rohtang Valley Ridge Trail")
        )
        existing_itin = itin_check.scalar_one_or_none()
        if existing_itin:
            itinerary = existing_itin
            print("Itinerary already exists. Using existing itinerary ID:", itinerary.id)
            # Fetch segments
            seg_res = await session.execute(
                select(RouteSegment).where(RouteSegment.itinerary_id == itinerary.id).order_by(RouteSegment.sequence_order)
            )
            segments = seg_res.scalars().all()
            segment1 = segments[0] if len(segments) > 0 else None
            segment2 = segments[1] if len(segments) > 1 else None
            segment3 = segments[2] if len(segments) > 2 else None
        else:
            itinerary = Itinerary(
                title="Rohtang Valley Ridge Trail",
                description="A high-altitude scenic alpine ridge trail offering panoramic views of glaciated peaks, sub-alpine meadows, and crystal-clear mountain streams.",
                region="Himachal Himalayas",
                difficulty="moderate",
                distance_km=14.5,
                estimated_duration_hours=5.5,
                start_latitude=32.2396,
                start_longitude=77.1887,
                end_latitude=32.2970,
                end_longitude=77.2470,
                route_geojson=route_geojson,
                is_active=True,
            )
            session.add(itinerary)
            await session.flush()

            print("Seeding Route Segments...")
            segment1 = RouteSegment(
                itinerary_id=itinerary.id,
                sequence_order=1,
                name="Lower Pine Forest Ascent",
                elevation_gain_m=320.0,
                hazard_level="low",
                segment_geojson={
                    "type": "LineString",
                    "coordinates": [[77.1887, 32.2396], [77.1950, 32.2450], [77.2020, 32.2530]],
                },
            )
            segment2 = RouteSegment(
                itinerary_id=itinerary.id,
                sequence_order=2,
                name="Upper Wind Ridge Passage",
                elevation_gain_m=480.0,
                hazard_level="medium",
                segment_geojson={
                    "type": "LineString",
                    "coordinates": [[77.2020, 32.2530], [77.2110, 32.2610], [77.2200, 32.2700]],
                },
            )
            segment3 = RouteSegment(
                itinerary_id=itinerary.id,
                sequence_order=3,
                name="Glacier View Crest & Descent",
                elevation_gain_m=150.0,
                hazard_level="low",
                segment_geojson={
                    "type": "LineString",
                    "coordinates": [[77.2200, 32.2700], [77.2290, 32.2790], [77.2470, 32.2970]],
                },
            )
            session.add_all([segment1, segment2, segment3])
            await session.flush()

        if "--include-demo-alerts" in sys.argv:
            print("Seeding the Exactly 3 Required Simulated Alerts relative to T0 for manual demo testing...")
            now = datetime.now(timezone.utc)
            freshness_limit = 3600  # 60 minutes
            # Alert 1: Rain-risk (5m old -> Fresh)
            alert1 = Alert(
                title="[SIMULATED] High Rain-Risk Advisory on Upper Ridge",
                description="SIMULATED: Unseasonal cloudburst and heavy rain shower expected along the northern ridge segment. Wet rocks and slippery terrain.",
                severity="medium",
                category="weather",
                affected_itinerary_id=itinerary.id,
                affected_segment_id=segment2.id if segment2 else None,
                source_name="Regional Alpine Meteorological Center (Simulated)",
                source_url="https://meteo.simulated.local/advisories/ridge-rain",
                source_published_at=now - timedelta(minutes=5),
                retrieved_at=now,
                freshness_threshold_seconds=freshness_limit,
                is_simulation=True,
            )

            # Alert 2: Trail obstruction (75m old -> Stale)
            alert2 = Alert(
                title="[SIMULATED] Temporary Trail Obstruction (Fallen Timber)",
                description="SIMULATED: Trail crew reported fallen pine timber blocking main path at km 4.2. Detour markers deployed via eastern goat path.",
                severity="high",
                category="hazard",
                affected_itinerary_id=itinerary.id,
                affected_segment_id=segment1.id if segment1 else None,
                source_name="Forestry & Trail Ranger Post 4 (Simulated)",
                source_url="https://parks.simulated.local/trail-notices/timber-42",
                source_published_at=now - timedelta(minutes=75),
                retrieved_at=now,
                freshness_threshold_seconds=freshness_limit,
                is_simulation=True,
            )

            # Alert 3: Transport disruption (180m old -> Stale)
            alert3 = Alert(
                title="[SIMULATED] Shuttle Bus Service Delay Near Trailhead Exit",
                description="SIMULATED: Mountain feeder bus line 402 experiencing mechanical delay at southern exit junction. Hourly frequency temporarily halved.",
                severity="low",
                category="transport",
                affected_itinerary_id=itinerary.id,
                affected_segment_id=None,
                source_name="Valley Transit Authority (Simulated)",
                source_url="https://transit.simulated.local/alerts/line-402",
                source_published_at=now - timedelta(minutes=180),
                retrieved_at=now,
                freshness_threshold_seconds=freshness_limit,
                is_simulation=True,
            )
            session.add_all([alert1, alert2, alert3])
        else:
            print("Production Mode: Zero simulated alerts inserted. Only live NDMA SACHET / external feeds are used.")

        sub_check = await session.execute(select(Subscriber).where(Subscriber.phone_e164 == "+919876543210"))
        if not sub_check.scalar_one_or_none():
            print("Seeding Verified Test Subscriber for SMS Demonstration...")
            test_subscriber = Subscriber(
                phone_e164="+919876543210",
                status="ACTIVE",
                consent_given=True,
                consent_timestamp=now,
                consent_version="v1.0",
                consent_ip="127.0.0.1",
                subscribed_areas_json='["Kullu", "Manali", "Rohtang", "all"]',
                subscribed_itinerary_id=itinerary.id,
            )
            session.add(test_subscriber)

        await session.commit()
        print("Database seeded successfully with all demonstration records!")


if __name__ == "__main__":
    asyncio.run(seed())
