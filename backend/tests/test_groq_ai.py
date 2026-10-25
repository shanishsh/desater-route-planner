"""Tests for Groq AI client, grounded route safety briefing, and bulletin extraction."""
import pytest
from app.config import settings
from app.clients.groq_client import groq_client
from app.services.ai.route_summary_assistant import route_summary_assistant
from app.services.ai.bulletin_extractor import bulletin_extractor_service


def test_groq_client_configuration_toggle(monkeypatch):
    """Verifies that is_configured accurately reflects presence of key."""
    monkeypatch.setattr(settings, "GROQ_API_KEY", "")
    assert groq_client.is_configured() is False

    monkeypatch.setattr(settings, "GROQ_API_KEY", "gsk_test_mock_key_12345")
    assert groq_client.is_configured() is True


@pytest.mark.asyncio
async def test_route_summary_assistant_deterministic_fallback():
    """Verifies that route_summary_assistant produces valid briefings with zero hallucinations."""
    dummy_route = {
        "route_id": "route_dehradun_mussoorie_test",
        "route_version": 1,
        "title": "Dehradun to Mussoorie Trek",
        "distance_km": 14.5,
        "duration_minutes": 240.0,
        "disaster_alerts": [
            {
                "id": "alert_rf_01",
                "title": "Rockfall Warning near Rajpur",
                "severity": "HIGH",
                "distance_to_alert_km": 0.0,
            }
        ],
        "assistance_points": [
            {
                "name": "Rajpur Primary Health Center",
                "category": "clinic",
                "distance_km": 0.8,
            }
        ],
        "provider_statuses": {
            "imd": {"status": "UNCONFIGURED", "details": "Key missing"}
        },
    }

    # Test sync deterministic
    sync_res = route_summary_assistant.generate_grounded_summary(dummy_route)
    assert sync_res.route_id == "route_dehradun_mussoorie_test"
    assert len(sync_res.direct_intersections) == 1
    assert "Rockfall Warning" in sync_res.direct_intersections[0]
    assert len(sync_res.nearby_facilities) == 1
    assert "Rajpur Primary Health Center" in sync_res.nearby_facilities[0]
    assert "100% safe" not in sync_res.summary_text
    assert sync_res.is_ai_generated is False

    # Test async (with Groq if configured, or deterministic fallback)
    async_res = await route_summary_assistant.generate_grounded_summary_async(dummy_route)
    assert async_res.route_id == "route_dehradun_mussoorie_test"
    assert len(async_res.direct_intersections) == 1
    assert "100% safe" not in async_res.summary_text


@pytest.mark.asyncio
async def test_bulletin_extractor_rule_and_async():
    """Verifies bulletin extractor categorizes hazard and extracts districts."""
    sample_bulletin = (
        "Uttarakhand State Disaster Management Authority Notice: Heavy rainfall triggered a major "
        "landslide blocking NH-58 near Joshimath in Chamoli district. Traffic suspended until clearance."
    )
    draft = bulletin_extractor_service.extract_structured_advisory(sample_bulletin, "doc_pwd_001")
    assert "Chamoli" in draft.affected_districts
    assert draft.hazard_type in ("landslide", "road_closure")
    assert draft.confidence_score >= 0.75
    assert "NH-58" in draft.raw_text

    async_draft = await bulletin_extractor_service.extract_structured_advisory_async(
        sample_bulletin, "doc_pwd_002"
    )
    assert "Chamoli" in async_draft.affected_districts
    assert async_draft.hazard_type in ("landslide", "road_closure")
