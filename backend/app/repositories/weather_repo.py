"""Repository for Weather snapshot persistence and last-known-good fallback."""
from typing import Optional
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.weather import WeatherSnapshot


class WeatherRepository:
    """Async repository for WeatherSnapshots."""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_latest_snapshot(self, latitude: float, longitude: float, tolerance: float = 0.05) -> Optional[WeatherSnapshot]:
        """Finds the most recent weather snapshot within geographic tolerance."""
        stmt = (
            select(WeatherSnapshot)
            .where(
                and_(
                    WeatherSnapshot.latitude.between(latitude - tolerance, latitude + tolerance),
                    WeatherSnapshot.longitude.between(longitude - tolerance, longitude + tolerance),
                )
            )
            .order_by(WeatherSnapshot.retrieved_at.desc())
            .limit(1)
        )
        result = await self.session.execute(stmt)
        return result.scalar_one_or_none()

    async def save_snapshot(self, snapshot: WeatherSnapshot) -> WeatherSnapshot:
        self.session.add(snapshot)
        await self.session.commit()
        await self.session.refresh(snapshot)
        return snapshot

