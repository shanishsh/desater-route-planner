"""Repository for Alert queries and persistence."""
from typing import List, Optional, Tuple
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.alert import Alert


class AlertRepository:
    """Async repository for Alerts."""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_id(self, alert_id: str) -> Optional[Alert]:
        stmt = select(Alert).where(Alert.id == alert_id)
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def list_alerts(
        self,
        itinerary_id: Optional[str] = None,
        severity: Optional[str] = None,
        category: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> Tuple[List[Alert], int]:
        query = select(Alert)
        count_query = select(func.count(Alert.id))

        filters = []
        if itinerary_id:
            filters.append(Alert.affected_itinerary_id == itinerary_id)
        if severity:
            filters.append(Alert.severity == severity)
        if category:
            filters.append(Alert.category == category)

        if filters:
            query = query.where(and_(*filters))
            count_query = count_query.where(and_(*filters))

        total_result = await self.session.execute(count_query)
        total = total_result.scalar_one()

        query = query.order_by(Alert.source_published_at.desc()).offset(offset).limit(limit)
        result = await self.session.execute(query)
        return list(result.scalars().all()), total

    async def create_alert(self, alert: Alert) -> Alert:
        self.session.add(alert)
        await self.session.commit()
        await self.session.refresh(alert)
        return alert

    async def delete_all_simulation_alerts(self) -> int:
        """Deletes all simulated alerts (used during demo reset)."""
        stmt = select(Alert).where(Alert.is_simulation.is_(True))
        result = await self.session.execute(stmt)
        alerts = result.scalars().all()
        count = len(alerts)
        for a in alerts:
            await self.session.delete(a)
        await self.session.commit()
        return count

