"""Crowd indicator schemas."""
from datetime import datetime
from pydantic import BaseModel, ConfigDict


class CrowdResponse(BaseModel):
    id: str
    itinerary_id: str
    location_name: str
    crowd_level: str  # low, moderate, high, congested
    density_description: str
    is_simulation: bool = True
    observed_at: datetime
    simulation_notice: str = "SIMULATED: Not real-time crowd sensor measurements."

    model_config = ConfigDict(from_attributes=True)

