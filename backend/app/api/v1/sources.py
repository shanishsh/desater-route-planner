"""Sources and data attribution endpoint."""
from fastapi import APIRouter

router = APIRouter(prefix="/sources", tags=["Sources & Attribution"])


@router.get("", response_model=dict)
async def get_sources_attribution():
    return {
        "providers": [
            {
                "name": "OpenFreeMap",
                "purpose": "Vector tile basemap rendering (Liberty style)",
                "terms": "Free & Open-Source vector tiles powered by OpenStreetMap data",
                "attribution": "© OpenStreetMap contributors, OpenFreeMap",
                "url": "https://openfreemap.org",
            },
            {
                "name": "Open-Meteo",
                "purpose": "Global weather forecast data and atmospheric variables",
                "terms": "Free for Non-Commercial use under Creative Commons Attribution 4.0 International (CC BY 4.0)",
                "attribution": "Weather data by Open-Meteo.com",
                "url": "https://open-meteo.com",
            },
            {
                "name": "National Emergency Response (112)",
                "purpose": "Official public safety emergency assistance dialing standard",
                "terms": "Public Safety Infrastructure",
                "attribution": "Ministry of Home Affairs / Emergency Response Support System (ERSS)",
                "url": "https://112.gov.in",
            },
        ],
        "disclaimer": "Safety information, hazards, and simulated alerts are demonstration records. Always consult official emergency personnel in distress.",
    }

