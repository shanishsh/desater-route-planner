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

        if not existing_itin:
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
                MapPoint(
                    name="High Altitude Emergency First-Aid Clinic",
                    category="clinic",
                    latitude=32.2512,
                    longitude=77.1955,
                    phone="+91-1902-253112",
                    address="Solang Approach Road, Sector 4",
                    description="Walk-in primary care clinic, frostbite & acute mountain sickness triage, oxygen refills.",
                    verification_status="verified",
                    verification_timestamp=now,
                    provenance="State Health Department Registry",
                ),
                MapPoint(
                    name="Valley Municipal Fire & Rescue Station (Station 3)",
                    category="fire_station",
                    latitude=32.2380,
                    longitude=77.1860,
                    phone="+91-1902-252101",
                    address="Hadimba Link Road, Municipal Fire Compound",
                    description="Municipal fire service equipped with wildfire suppression pumps, structural engines, and high-angle rope rescue equipment.",
                    verification_status="verified",
                    verification_timestamp=now,
                    provenance="Municipal Fire & Emergency Services Bureau",
                ),
                MapPoint(
                    name="Beas Valley Municipal Relief Shelter",
                    category="shelter",
                    latitude=32.2450,
                    longitude=77.1880,
                    phone="+91-1902-254100",
                    address="Community Hall, Aleo Sector",
                    description="Designated civil defense evacuation shelter with backup power generators, clean water, and thermal emergency bedding.",
                    verification_status="verified",
                    verification_timestamp=now,
                    provenance="District Disaster Management Authority (DDMA)",
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

        now = datetime.now(timezone.utc)
        disaster_check = await session.execute(select(DisasterAlert))
        if not disaster_check.scalars().first():
            print("Seeding Demonstration Disaster Alerts...")
            disaster1 = DisasterAlert(
                title="[SIMULATED] High-Risk Flash Flood & Heavy Rainfall Emergency",
                description="SIMULATED: Extreme precipitation in alpine catchment causing rapid surge in river discharge. High flood risk across stream crossings and canyon beds.",
                hazard_category="flash_flood",
                severity="CRITICAL",
                status="active",
                version=1,
                is_simulated=True,
                issuing_authority="Himachal Pradesh State Disaster Management Authority (HPSDMA)",
                source_url="https://hpsdma.nic.in/alerts/simulated-flashflood-01",
                source_verification_status="SIMULATED",
                required_protective_instructions="Evacuate low-lying riverbeds, streams, and bridges immediately. Climb to higher ridges above 2,500m.",
                center_latitude=32.2610,
                center_longitude=77.2110,
                affected_radius_meters=5000.0,
                affected_route_id=itinerary.id,
                published_at=now - timedelta(minutes=10),
                received_at=now,
                effective_at=now - timedelta(minutes=10),
                expires_at=now + timedelta(hours=12),
            )

            disaster2 = DisasterAlert(
                title="[SIMULATED] Active Landslide Watch on Rohtang Ridge Highway",
                description="SIMULATED: Rockfall and continuous debris slide reported at hairpin curve 9. Road closed for vehicular transit.",
                hazard_category="landslide",
                severity="SEVERE",
                status="active",
                version=1,
                is_simulated=True,
                issuing_authority="Border Roads Organisation & District Disaster Emergency Operation Centre",
                source_url="https://bro.gov.in/notices/simulated-landslide-02",
                source_verification_status="SIMULATED",
                required_protective_instructions="Halt all vehicle and trekking movement towards pass summit. Take shelter at nearest mountain police post.",
                center_latitude=32.2790,
                center_longitude=77.2290,
                affected_radius_meters=3500.0,
                affected_route_id=itinerary.id,
                published_at=now - timedelta(minutes=45),
                received_at=now,
                effective_at=now - timedelta(minutes=45),
                expires_at=now + timedelta(hours=6),
            )
            session.add_all([disaster1, disaster2])

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
