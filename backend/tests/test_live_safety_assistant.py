"""Tests for Live Trek Safety Assistant, grounded citations, schema validation, and endpoints."""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.config import settings
from app.clients.groq_client import groq_client
from app.schemas.assistant import (
    LiveSafetyAssistantRequest,
    AssistantInteractiveQueryRequest,
    LocationDto,
    RouteProgressDto,
    HazardContextDto,
    WeatherContextDto,
    FacilityContextDto,
    VerifiedAlternativeDto,
    SafetyEventDto,
)
from app.services.ai.live_safety_assistant import live_safety_assistant_service


@pytest.fixture
def sample_trek_request():
    return LiveSafetyAssistantRequest(
        trip_id="trip_kedarkantha_001",
        route_id="route_kedarkantha_summit",
        context_generation_id=5,
        trigger_event=SafetyEventDto(
            event_id="ev_ls_99",
            event_type="CRITICAL_HAZARD_WARNING",
            hazard_id="hz_landslide_juda_ka_talab",
            hazard_title="Active Landslide above Juda Ka Talab",
            severity="CRITICAL",
            distance_ahead_m=1200.0,
            distance_current_m=1400.0,
            is_direct_route_obstruction=True,
            has_verified_alternative=False,
            deterministic_spoken_text="Critical landslide 1.2 kilometers ahead. Trail blocked.",
            deterministic_action_text="Halt at Juda Ka Talab. Do not advance toward active debris.",
        ),
        current_location=LocationDto(
            latitude=31.025,
            longitude=78.185,
            altitude_meters=2700.0,
            accuracy_meters=8.0,
            speed_kmh=3.2,
        ),
        route_progress=RouteProgressDto(
            traveled_km=3.4,
            chainage_km=3.5,
            remaining_km=4.5,
            progress_pct=43.7,
            is_off_route=False,
            cross_track_m=12.0,
        ),
        upcoming_hazards=[
            HazardContextDto(
                id="hz_landslide_juda_ka_talab",
                title="Active Landslide above Juda Ka Talab",
                category="HAZARD_ZONE",
                severity="CRITICAL",
                distance_ahead_m=1200.0,
                sequence_number=1,
                is_direct_intersection=True,
                source_name="Uttarakhand SDMA",
            )
        ],
        nearby_hazards=[],
        current_weather=WeatherContextDto(
            temperature_c=14.0,
            weather_description="Overcast with mist",
            precipitation_probability_pct=60,
            wind_speed_kmh=18.0,
            thunderstorm_probability_pct=25,
            is_stale=False,
        ),
        nearby_facilities=[
            FacilityContextDto(
                id="fac_sankri_phc",
                name="Sankri Primary Health Center",
                category="CLINIC",
                distance_km=4.2,
                latitude=31.015,
                longitude=78.175,
            )
        ],
        verified_alternatives=[],
        connectivity_status="MARGINAL",
        stale_sources=[],
    )


@pytest.mark.asyncio
async def test_deterministic_guidance_generation(sample_trek_request):
    """Verifies that deterministic guidance is 100% grounded and never claims 100% safety."""
    res = live_safety_assistant_service.generate_deterministic_guidance(sample_trek_request)
    assert res.event_id == "ev_ls_99"
    assert "Active Landslide" in res.summary or "CRITICAL" in res.summary
    assert "hz_landslide_juda_ka_talab" in res.referenced_hazard_ids
    assert "No verified alternative" in res.verified_alternatives[0]
    assert "100% safe" not in res.summary
    assert res.is_ai_generated is False
    assert res.fallback_used is True
    assert len(res.spoken_message.split()) <= 35


@pytest.mark.asyncio
async def test_citation_validation_filters_hallucinations(sample_trek_request):
    """Verifies that _extract_and_validate_json filters out non-existent hazard IDs."""
    fake_ai_output = """
    {
        "summary": "Severe landslide danger on summit trail.",
        "what_happened": "Rockslide chute active.",
        "why_relevant": "Crosses the path.",
        "recommended_action": "Wait in camp.",
        "what_to_avoid": "Do not enter chute.",
        "verified_alternatives": [],
        "referenced_hazard_ids": ["hz_landslide_juda_ka_talab", "FAKE_HAZARD_9999"],
        "data_limitations": [],
        "spoken_message": "Landslide ahead. Stay in camp."
    }
    """
    valid_ids = {"hz_landslide_juda_ka_talab"}
    parsed = live_safety_assistant_service._extract_and_validate_json(fake_ai_output, valid_ids)
    assert parsed is not None
    assert "hz_landslide_juda_ka_talab" in parsed["referenced_hazard_ids"]
    assert "FAKE_HAZARD_9999" not in parsed["referenced_hazard_ids"]


@pytest.mark.asyncio
async def test_interactive_query_fallback(sample_trek_request):
    """Verifies user queries for weather, next hazards, facilities, and alternatives."""
    # 1. Weather query
    q_weather = AssistantInteractiveQueryRequest(
        trip_id="trip_1",
        route_id="route_1",
        query="What is the weather ahead?",
        current_location=sample_trek_request.current_location,
        route_progress=sample_trek_request.route_progress,
        current_weather=sample_trek_request.current_weather,
    )
    ans_w = live_safety_assistant_service._generate_query_fallback(q_weather)
    assert "14°C" in ans_w.summary
    assert "mist" in ans_w.summary.lower()

    # 2. Alternative query when none exist
    q_alt = AssistantInteractiveQueryRequest(
        trip_id="trip_1",
        route_id="route_1",
        query="Is there an alternative trail?",
        current_location=sample_trek_request.current_location,
        route_progress=sample_trek_request.route_progress,
        verified_alternatives=[],
    )
    ans_alt = live_safety_assistant_service._generate_query_fallback(q_alt)
    assert "No verified alternative" in ans_alt.summary

    # 3. Medical facility query
    q_med = AssistantInteractiveQueryRequest(
        trip_id="trip_1",
        route_id="route_1",
        query="Where is the nearest medical clinic?",
        current_location=sample_trek_request.current_location,
        route_progress=sample_trek_request.route_progress,
        nearby_facilities=sample_trek_request.nearby_facilities,
    )
    ans_med = live_safety_assistant_service._generate_query_fallback(q_med)
    assert "Sankri Primary Health Center" in ans_med.summary


def test_api_endpoints_safety_guidance(sample_trek_request):
    """Tests POST /api/v1/assistant/safety-guidance via TestClient."""
    client = TestClient(app)
    resp = client.post("/api/v1/assistant/safety-guidance", json=sample_trek_request.model_dump())
    assert resp.status_code == 200
    data = resp.json()
    assert data["context_generation_id"] == 5
    assert "hz_landslide_juda_ka_talab" in data["referenced_hazard_ids"]
    assert len(data["spoken_message"]) > 10


def test_api_endpoints_ask():
    """Tests POST /api/v1/assistant/ask via TestClient."""
    client = TestClient(app)
    payload = {
        "trip_id": "trip_1",
        "route_id": "route_1",
        "context_generation_id": 2,
        "query": "What's the weather ahead?",
        "current_location": {"latitude": 30.1, "longitude": 78.2},
        "route_progress": {"traveled_km": 1.0, "chainage_km": 1.0, "remaining_km": 5.0, "progress_pct": 20.0},
        "current_weather": {
            "temperature_c": 19.0,
            "weather_description": "Clear skies",
            "precipitation_probability_pct": 10,
        },
    }
    resp = client.post("/api/v1/assistant/ask", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert "19°C" in data["summary"]
    assert "Clear skies" in data["summary"]

