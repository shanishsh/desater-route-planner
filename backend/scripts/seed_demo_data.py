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
from app.core.security import get_password_hash
from app.config import settings


async def seed():
    print("Initializing database tables...")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with async_session_maker() as session:
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

        print("Seeding Verified Map Points (Emergency & Assistance)...")
        now = datetime.now(timezone.utc)
        points = [
            MapPoint(
                name="ERSS Central Emergency Hub (Dial 112)",
                category="emergency_service",
                latitude=32.2380,
                longitude=77.1870,
                phone="112",
                address="National Emergency Response Support System, Valley Dispatch",
                description="Official 24/7 nationwide emergency dialing response for police, fire, and medical aid.",
                verification_status="verified",
                verification_timestamp=now,
                provenance="Ministry of Home Affairs ERSS Directory",
            ),
            MapPoint(
                name="District Alpine Civil Hospital & Trauma Unit",
                category="medical",
                latitude=32.2410,
                longitude=77.1850,
                phone="+91-1902-252244",
                address="Mall Road West, Manali District Hospital",
                description="Level 2 trauma stabilization center, emergency department, altitude sickness hyperbaric chamber.",
                verification_status="verified",
                verification_timestamp=now,
                provenance="State Health Department Registry",
            ),
            MapPoint(
                name="Tourist Police & Mountain Rescue Post",
                category="police",
                latitude=32.2425,
                longitude=77.1910,
                phone="+91-1902-252322",
                address="Near Circuit House Junction",
                description="Assistance with lost property, mountain search & rescue dispatch, foreign national registration.",
                verification_status="verified",
                verification_timestamp=now,
                provenance="Himachal State Police Registry",
            ),
            MapPoint(
                name="Himachal Tourism Information Centre",
                category="visitor_info",
                latitude=32.2405,
                longitude=77.1895,
                phone="+91-1902-253531",
                address="The Mall Road, HPTDC Centre",
                description="Trail maps, certified local trekking guides, weather advisories, permit validations.",
                verification_status="verified",
                verification_timestamp=now,
                provenance="HPTDC Official Tourism Registry",
            ),
            MapPoint(
                name="Interstate Bus & Transit Terminal (ISBT)",
                category="transport",
                latitude=32.2350,
                longitude=77.1830,
                phone="+91-1902-252323",
                address="Riverside Bypass Highway",
                description="Scheduled regional transport, authorized government taxi union stand, shuttle service.",
                verification_status="verified",
                verification_timestamp=now,
                provenance="State Road Transport Corporation",
            ),
        ]
        session.add_all(points)

        print("Seeding the Exactly 3 Required Simulated Alerts relative to T0...")
        freshness_limit = 3600  # 60 minutes
        # Alert 1: Rain-risk (5m old -> Fresh)
        alert1 = Alert(
            title="[SIMULATED] High Rain-Risk Advisory on Upper Ridge",
            description="SIMULATED: Unseasonal cloudburst and heavy rain shower expected along the northern ridge segment. Wet rocks and slippery terrain.",
            severity="medium",
            category="weather",
            affected_itinerary_id=itinerary.id,
            affected_segment_id=segment2.id,
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
            affected_segment_id=segment1.id,
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

        print("Seeding Simulated Crowd Snapshot...")
        crowd1 = CrowdSnapshot(
            itinerary_id=itinerary.id,
            location_name="Trailhead Base Camp",
            crowd_level="moderate",
            density_description="Steady morning hiker departures; manageable capacity.",
            is_simulation=True,
            observed_at=now,
        )
        crowd2 = CrowdSnapshot(
            itinerary_id=itinerary.id,
            location_name="Wind Ridge Crest",
            crowd_level="low",
            density_description="Few hikers on ridge; open spacing.",
            is_simulation=True,
            observed_at=now,
        )
        session.add_all([crowd1, crowd2])

        await session.commit()
        print("Database seeded successfully with all demonstration records!")


if __name__ == "__main__":
    asyncio.run(seed())
