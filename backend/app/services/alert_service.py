"""Alert service managing alerts, queries, and repeatable demo seeding."""
from datetime import datetime, timezone, timedelta
from typing import List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from app.repositories.alert_repo import AlertRepository
from app.models.alert import Alert
from app.schemas.alert import AlertResponse, AlertCreate
from app.core.exceptions import NotFoundException


class AlertService:
    """Manages alerts, validation, and demo scenario seeding."""

    def __init__(self, session: AsyncSession):
        self.session = session
        self.alert_repo = AlertRepository(session)

    async def list_alerts(
        self,
        itinerary_id: Optional[str] = None,
        severity: Optional[str] = None,
        category: Optional[str] = None,
        include_simulated: bool = False,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[AlertResponse], int]:
        alerts, total = await self.alert_repo.list_alerts(
            itinerary_id=itinerary_id,
            severity=severity,
            category=category,
            include_simulated=include_simulated,
            limit=limit,
            offset=offset,
        )
        return [AlertResponse.model_validate(a) for a in alerts], total

    async def get_alert(self, alert_id: str) -> AlertResponse:
        alert = await self.alert_repo.get_by_id(alert_id)
        if not alert:
            raise NotFoundException(f"Alert with id {alert_id} not found")
        return AlertResponse.model_validate(alert)

    async def create_alert(self, alert_in: AlertCreate) -> AlertResponse:
        alert = Alert(
            title=alert_in.title,
            description=alert_in.description,
            severity=alert_in.severity,
            category=alert_in.category,
            affected_itinerary_id=alert_in.affected_itinerary_id,
            affected_segment_id=alert_in.affected_segment_id,
            source_name=alert_in.source_name,
            source_url=alert_in.source_url,
            source_published_at=alert_in.source_published_at,
            freshness_threshold_seconds=alert_in.freshness_threshold_seconds,
            is_simulation=alert_in.is_simulation,
        )
        created = await self.alert_repo.create_alert(alert)
        return AlertResponse.model_validate(created)

    async def seed_demo_alerts(self, itinerary_id: Optional[str] = None, segment_id: Optional[str] = None) -> List[AlertResponse]:
        """Seeds the exactly 3 required simulated alerts relative to current reset time."""
        # 1. Clear previous simulation alerts
        await self.alert_repo.delete_all_simulation_alerts()

        now = datetime.now(timezone.utc)
        freshness_limit = 3600  # 60 minutes threshold

        # Alert 1: Rain-risk advisory (5 min old -> Fresh)
        alert1 = Alert(
            title="[SIMULATED] High Rain-Risk Advisory on Upper Ridge",
            description="SIMULATED: Unseasonal cloudburst and heavy rain shower expected along the northern ridge segment. Wet rocks and slippery terrain.",
            severity="medium",
            category="weather",
            affected_itinerary_id=itinerary_id,
            affected_segment_id=segment_id,
            source_name="Regional Alpine Meteorological Center (Simulated)",
            source_url="https://meteo.simulated.local/advisories/ridge-rain",
            source_published_at=now - timedelta(minutes=5),
            retrieved_at=now,
            freshness_threshold_seconds=freshness_limit,
            is_simulation=True,
        )

        # Alert 2: Temporary trail obstruction (75 min old -> Stale)
        alert2 = Alert(
            title="[SIMULATED] Temporary Trail Obstruction (Fallen Timber)",
            description="SIMULATED: Trail crew reported fallen pine timber blocking main path at km 4.2. Detour markers deployed via eastern goat path.",
            severity="high",
            category="hazard",
            affected_itinerary_id=itinerary_id,
            affected_segment_id=segment_id,
            source_name="Forestry & Trail Ranger Post 4 (Simulated)",
            source_url="https://parks.simulated.local/trail-notices/timber-42",
            source_published_at=now - timedelta(minutes=75),
            retrieved_at=now,
            freshness_threshold_seconds=freshness_limit,
            is_simulation=True,
        )

        # Alert 3: Transport disruption near exit (180 min old -> Stale)
        alert3 = Alert(
            title="[SIMULATED] Shuttle Bus Service Delay Near Trailhead Exit",
            description="SIMULATED: Mountain feeder bus line 402 experiencing mechanical delay at southern exit junction. Hourly frequency temporarily halved.",
            severity="low",
            category="transport",
            affected_itinerary_id=itinerary_id,
            affected_segment_id=None,
            source_name="Valley Transit Authority (Simulated)",
            source_url="https://transit.simulated.local/alerts/line-402",
            source_published_at=now - timedelta(minutes=180),
            retrieved_at=now,
            freshness_threshold_seconds=freshness_limit,
            is_simulation=True,
        )

        saved_alerts = []
        for a in [alert1, alert2, alert3]:
            saved = await self.alert_repo.create_alert(a)
            saved_alerts.append(AlertResponse.model_validate(saved))

        return saved_alerts

