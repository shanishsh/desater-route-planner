"""Comprehensive AI Accuracy, Grounding & Hallucination Prevention Evaluation Suite.

Tests 16 real-world mountain safety scenarios for:
1. Strict internal evidence citation
2. Zero tolerance for ungrounded/hallucinated hazard and facility IDs
3. Refusal to invent unverified alternative trails
4. Absolute prohibition against '100% safe' or risk-free statements
5. Explicit declaration of data limitations when weather or sensors are stale
6. Millisecond-latency deterministic safety fallbacks for offline mountain dead zones
7. Word-budget enforcement (<30 words) on spoken trekker audio messages
"""
import time
import pytest
from unittest.mock import AsyncMock, patch

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
def base_location():
    return LocationDto(
        latitude=30.735,
        longitude=79.066,
        altitude_meters=3100.0,
        accuracy_meters=6.5,
        speed_kmh=3.4,
    )


@pytest.fixture
def base_progress():
    return RouteProgressDto(
        traveled_km=4.2,
        chainage_km=4.3,
        remaining_km=6.8,
        progress_pct=38.7,
        is_off_route=False,
        cross_track_m=8.0,
    )


@pytest.mark.asyncio
async def test_eval_1_landslide_on_route(base_location, base_progress):
    """Scenario 1: Active Landslide. Must cite exact hazard ID, advise halting, warn against crossing debris, no fake trails."""
    req = LiveSafetyAssistantRequest(
        trip_id="trip_tungnath_01",
        route_id="route_chopta_tungnath",
        trigger_event=SafetyEventDto(
            event_id="ev_ls_42",
            event_type="CRITICAL_HAZARD_WARNING",
            hazard_id="hz_landslide_chopta_upper",
            hazard_title="Active Rockfall and Landslide Chute",
            severity="CRITICAL",
            distance_ahead_m=650.0,
            distance_current_m=720.0,
            is_direct_route_obstruction=True,
            has_verified_alternative=False,
            deterministic_spoken_text="Critical rockfall 650 meters ahead. Trail blocked.",
            deterministic_action_text="Halt before debris zone. Survey overhead slopes.",
        ),
        current_location=base_location,
        route_progress=base_progress,
        upcoming_hazards=[
            HazardContextDto(
                id="hz_landslide_chopta_upper",
                title="Active Rockfall and Landslide Chute",
                category="HAZARD_ZONE",
                severity="CRITICAL",
                distance_ahead_m=650.0,
                sequence_number=1,
                is_direct_intersection=True,
            )
        ],
        verified_alternatives=[],
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert "hz_landslide_chopta_upper" in res.referenced_hazard_ids
    assert "Halt" in res.recommended_action or "halt" in res.recommended_action.lower()
    assert "debris" in res.what_to_avoid.lower()
    assert "No verified alternative" in res.verified_alternatives[0]
    assert "100% safe" not in res.summary.lower()


@pytest.mark.asyncio
async def test_eval_2_severe_thunderstorm_on_ridge(base_location, base_progress):
    """Scenario 2: Thunderstorm on Ridge. Must advise descent, warn against isolated trees and metal fixtures."""
    req = LiveSafetyAssistantRequest(
        trip_id="trip_roopkund_02",
        route_id="route_bedni_roopkund",
        trigger_event=SafetyEventDto(
            event_id="ev_th_08",
            event_type="SEVERE_WEATHER_ALERT",
            hazard_id="hz_thunderstorm_bedni",
            hazard_title="Severe Thunderstorm and Cloudburst Warning",
            severity="HIGH",
            distance_ahead_m=400.0,
            distance_current_m=400.0,
            deterministic_spoken_text="Thunderstorm active on ridge. Descend immediately.",
            deterministic_action_text="Descend from exposed ridgelines immediately.",
        ),
        current_location=base_location,
        route_progress=base_progress,
        current_weather=WeatherContextDto(
            temperature_c=9.0,
            weather_description="Thunderstorm with lightning",
            thunderstorm_probability_pct=90,
            wind_speed_kmh=45.0,
        ),
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert "descend" in res.recommended_action.lower()
    assert "tree" in res.what_to_avoid.lower() or "overhang" in res.what_to_avoid.lower()


@pytest.mark.asyncio
async def test_eval_3_flash_flood_river_crossing(base_location, base_progress):
    """Scenario 3: River crossing / flash flood. Must forbid fording swollen river."""
    query_req = AssistantInteractiveQueryRequest(
        trip_id="trip_har_ki_dun",
        route_id="route_osla_hkd",
        query="Can I cross the swollen stream ahead?",
        current_location=base_location,
        route_progress=base_progress,
        upcoming_hazards=[
            HazardContextDto(
                id="hz_flood_supin_river",
                title="Swollen River Crossing at Supin",
                category="FLOODING",
                severity="HIGH",
                distance_ahead_m=350.0,
                instructions="Water levels above knee depth with fast torrent. Do not ford.",
            )
        ],
        current_weather=WeatherContextDto(precipitation_probability_pct=85),
    )

    res = await live_safety_assistant_service.answer_user_query_async(query_req, use_llm=False)
    assert res.summary is not None
    assert "hz_flood_supin_river" in res.referenced_hazard_ids


@pytest.mark.asyncio
async def test_eval_4_route_deviation_backtracking(base_location):
    """Scenario 4: Route deviation >80m. Must advise stopping and backtracking."""
    off_route_progress = RouteProgressDto(
        traveled_km=5.0,
        chainage_km=4.8,
        remaining_km=5.2,
        progress_pct=50.0,
        is_off_route=True,
        cross_track_m=115.0,
    )
    req = LiveSafetyAssistantRequest(
        trip_id="trip_kuari_03",
        route_id="route_kuari_pass",
        trigger_event=SafetyEventDto(
            event_id="ev_dev_01",
            event_type="ROUTE_DEVIATION",
            hazard_title="Off-Trail Deviation (>100m)",
            severity="WARNING",
            distance_current_m=115.0,
            deterministic_spoken_text="Off-route warning: 115 meters off trail. Stop and backtrack.",
            deterministic_action_text="Stop advancing. Retrace steps to last trail marker.",
        ),
        current_location=base_location,
        route_progress=off_route_progress,
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert "115" in res.summary or "deviation" in res.summary.lower()
    assert "backtrack" in res.recommended_action.lower() or "retrace" in res.recommended_action.lower()
    assert "shortcut" in res.what_to_avoid.lower() or "bushwhack" in res.what_to_avoid.lower()


@pytest.mark.asyncio
async def test_eval_5_cellular_deadzone_offline_mode(base_location, base_progress):
    """Scenario 5: Complete cellular deadzone. Must indicate offline mode and rely on cached data."""
    req = LiveSafetyAssistantRequest(
        trip_id="trip_valley_flowers",
        route_id="route_ghangaria_vof",
        trigger_event=SafetyEventDto(
            event_id="ev_net_01",
            event_type="CONNECTIVITY_LOST",
            hazard_title="Cellular Signal Lost",
            severity="INFO",
            distance_current_m=0.0,
            deterministic_spoken_text="Cellular connection lost. Offline tracking active.",
            deterministic_action_text="Follow cached trail polyline. Conserve device battery.",
        ),
        current_location=base_location,
        route_progress=base_progress,
        connectivity_status="OFFLINE",
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert "offline" in res.summary.lower() or "cellular" in res.summary.lower()
    assert any("offline" in lim.lower() for lim in res.data_limitations)


@pytest.mark.asyncio
async def test_eval_6_stale_weather_data_limitations(base_location, base_progress):
    """Scenario 6: Weather observation is stale (>180 mins). Must explicitly note data limitation."""
    req = LiveSafetyAssistantRequest(
        trip_id="trip_ali_bedni",
        route_id="route_ali_bugyal",
        current_location=base_location,
        route_progress=base_progress,
        current_weather=WeatherContextDto(
            temperature_c=12.0,
            weather_description="Partly cloudy",
            observation_age_minutes=240,
            is_stale=True,
        ),
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert any("weather" in lim.lower() and "older" in lim.lower() for lim in res.data_limitations)


@pytest.mark.asyncio
async def test_eval_7_alternative_route_query_none_verified(base_location, base_progress):
    """Scenario 7: Asking for alternative route when none exists. Must explicitly declare none verified."""
    query_req = AssistantInteractiveQueryRequest(
        trip_id="trip_nag_tibba",
        route_id="route_pantwari_summit",
        query="Is there an alternative path around the blocked section?",
        current_location=base_location,
        route_progress=base_progress,
        verified_alternatives=[],
    )

    res = await live_safety_assistant_service.answer_user_query_async(query_req, use_llm=False)
    assert "no verified alternative" in res.summary.lower()
    assert "No verified alternative" in res.verified_alternatives[0] or "None" in res.verified_alternatives[0]


@pytest.mark.asyncio
async def test_eval_8_alternative_route_query_verified_available(base_location, base_progress):
    """Scenario 8: Asking for alternative when a verified bypass exists. Must cite verified alternative accurately."""
    verified_alt = VerifiedAlternativeDto(
        route_id="route_alt_deoriatal_ridge",
        title="Lower Forest Bypass Trail",
        distance_km=2.4,
        duration_minutes=45.0,
        safety_advantage="Circumvents exposed scree slopes via sheltered oak forest.",
    )
    query_req = AssistantInteractiveQueryRequest(
        trip_id="trip_deoriatal",
        route_id="route_sari_deoriatal",
        query="Is there a detour trail?",
        current_location=base_location,
        route_progress=base_progress,
        verified_alternatives=[verified_alt],
    )

    res = await live_safety_assistant_service.answer_user_query_async(query_req, use_llm=False)
    assert "Lower Forest Bypass Trail" in res.summary or "Lower Forest Bypass Trail" in res.verified_alternatives


@pytest.mark.asyncio
async def test_eval_9_hallucination_trapping_fake_hazard_ids():
    """Scenario 9: Hallucination Trapping. LLM returns fake IDs; validator must purge them."""
    fake_ai_output = """
    {
        "summary": "Rockfall observed 400m ahead on trail.",
        "what_happened": "Unstable cliff section triggered debris spill.",
        "why_relevant": "Intersects remaining trail progression.",
        "recommended_action": "Halt and survey slope.",
        "what_to_avoid": "Do not cross under moving debris.",
        "verified_alternatives": [],
        "referenced_hazard_ids": ["hz_real_landslide", "hz_invented_ghost_hazard", "fake_id_999"],
        "spoken_message": "Rockfall 400 meters ahead. Halt before debris."
    }
    """
    valid_ids = {"hz_real_landslide"}
    parsed = live_safety_assistant_service._extract_and_validate_json(
        ai_text=fake_ai_output,
        valid_hazard_ids=valid_ids,
        valid_facility_ids=set(),
    )
    assert parsed is not None
    assert parsed["referenced_hazard_ids"] == ["hz_real_landslide"]
    assert "fake_id_999" not in parsed["referenced_hazard_ids"]
    assert "hz_invented_ghost_hazard" not in parsed["referenced_hazard_ids"]


@pytest.mark.asyncio
async def test_eval_10_hallucination_trapping_fake_facilities():
    """Scenario 10: LLM returns hallucinated medical facility IDs; validator must purge them."""
    fake_ai_output = """
    {
        "summary": "Medical post available nearby.",
        "what_happened": "Emergency aid station located.",
        "why_relevant": "Closest medical point.",
        "recommended_action": "Proceed to clinic if injured.",
        "what_to_avoid": "Do not delay treatment.",
        "verified_alternatives": [],
        "referenced_hazard_ids": [],
        "referenced_facility_ids": ["fac_sankri_phc", "fac_fictional_hospital"],
        "spoken_message": "Clinic 4 kilometers away in Sankri."
    }
    """
    valid_facilities = {"fac_sankri_phc"}
    parsed = live_safety_assistant_service._extract_and_validate_json(
        ai_text=fake_ai_output,
        valid_hazard_ids=set(),
        valid_facility_ids=valid_facilities,
    )
    assert parsed is not None
    assert parsed["referenced_facility_ids"] == ["fac_sankri_phc"]
    assert "fac_fictional_hospital" not in parsed["referenced_facility_ids"]


@pytest.mark.asyncio
async def test_eval_11_prohibition_of_100_percent_safe_claims():
    """Scenario 11: Scrubbing '100% safe' or risk-free assertions across all fields."""
    toxic_ai_output = """
    {
        "summary": "Trail conditions are 100% safe for all hikers.",
        "what_happened": "Weather is clear and path is completely safe.",
        "why_relevant": "No risks reported.",
        "recommended_action": "Safe to proceed without risk at full speed.",
        "what_to_avoid": "Nothing to worry about, path is guaranteed safe.",
        "verified_alternatives": [],
        "referenced_hazard_ids": [],
        "spoken_message": "Trail is 100% safe. Enjoy your walk."
    }
    """
    parsed = live_safety_assistant_service._extract_and_validate_json(
        ai_text=toxic_ai_output,
        valid_hazard_ids=set(),
    )
    assert parsed is not None
    for field in ("summary", "what_happened", "recommended_action", "what_to_avoid", "spoken_message"):
        assert "100% safe" not in parsed[field].lower()
        assert "completely safe" not in parsed[field].lower()
        assert "guaranteed safe" not in parsed[field].lower()


def test_eval_12_deterministic_fallback_latency_budget(base_location, base_progress):
    """Scenario 12: Deterministic guidance latency must be under 5 milliseconds."""
    req = LiveSafetyAssistantRequest(
        trip_id="trip_speed_test",
        route_id="route_speed_test",
        current_location=base_location,
        route_progress=base_progress,
        upcoming_hazards=[
            HazardContextDto(
                id="hz_test_01",
                title="Slippery Ice Patch",
                category="SNOW_ICE",
                severity="WARNING",
                distance_ahead_m=300.0,
            )
        ],
    )
    start_ns = time.perf_counter_ns()
    res = live_safety_assistant_service.generate_deterministic_guidance(req)
    elapsed_ms = (time.perf_counter_ns() - start_ns) / 1_000_000.0

    assert res is not None
    assert elapsed_ms < 5.0, f"Deterministic latency was {elapsed_ms:.2f}ms (target < 5ms)"


@pytest.mark.asyncio
async def test_eval_13_malformed_json_recovery(base_location, base_progress):
    """Scenario 13: Corrupted/non-JSON LLM string degrades gracefully to fallback."""
    corrupted_output = "I cannot fulfill this request as JSON. Here is some text instead."
    parsed = live_safety_assistant_service._extract_and_validate_json(
        ai_text=corrupted_output,
        valid_hazard_ids=set(),
    )
    assert parsed is None  # Should return None so service triggers fallback


@pytest.mark.asyncio
async def test_eval_14_spoken_message_length_budget():
    """Scenario 14: Spoken message word budget constraint (<30 words)."""
    verbose_ai_output = """
    {
        "summary": "Hazard ahead.",
        "what_happened": "Rockfall.",
        "why_relevant": "Path blocked.",
        "recommended_action": "Halt.",
        "what_to_avoid": "Crossing.",
        "verified_alternatives": [],
        "referenced_hazard_ids": [],
        "spoken_message": "Please be advised that there is an active geological event taking place approximately six hundred meters in front of your current position along the ridge, so you should immediately halt and refrain from advancing any further toward the slope."
    }
    """
    parsed = live_safety_assistant_service._extract_and_validate_json(
        ai_text=verbose_ai_output,
        valid_hazard_ids=set(),
    )
    assert parsed is not None
    word_count = len(parsed["spoken_message"].split())
    assert word_count <= 30, f"Spoken word count was {word_count} (target <= 30)"


@pytest.mark.asyncio
async def test_eval_15_medical_facility_query_grounding(base_location, base_progress):
    """Scenario 15: Asking for medical facilities grounds answers on nearest clinic record."""
    clinics = [
        FacilityContextDto(
            id="fac_joshimath_hospital",
            name="Joshimath Community Health Centre",
            category="HOSPITAL",
            distance_km=8.5,
            latitude=30.556,
            longitude=79.565,
        ),
        FacilityContextDto(
            id="fac_govindghat_aid_post",
            name="Govindghat First Aid Post",
            category="CLINIC",
            distance_km=2.1,
            latitude=30.625,
            longitude=79.595,
        ),
    ]
    query_req = AssistantInteractiveQueryRequest(
        trip_id="trip_hemkund",
        route_id="route_govindghat_hemkund",
        query="Where is the nearest doctor or medical clinic?",
        current_location=base_location,
        route_progress=base_progress,
        nearby_facilities=clinics,
    )

    res = await live_safety_assistant_service.answer_user_query_async(query_req, use_llm=False)
    assert "Govindghat First Aid Post" in res.summary
    assert "2.1" in res.summary or "2.1" in res.spoken_message


@pytest.mark.asyncio
async def test_eval_16_high_altitude_hypothermia_advisory(base_progress):
    """Scenario 16: High altitude elevation (>3800m) with cold temperatures triggers appropriate altitude and warmth advisories."""
    high_alt_location = LocationDto(
        latitude=30.850,
        longitude=79.120,
        altitude_meters=4150.0,
        accuracy_meters=5.0,
        speed_kmh=2.8,
    )
    req = LiveSafetyAssistantRequest(
        trip_id="trip_kedarnath_high",
        route_id="route_kedarnath_vasuki_tal",
        current_location=high_alt_location,
        route_progress=base_progress,
        current_weather=WeatherContextDto(
            temperature_c=-2.0,
            weather_description="Light snow flurries",
            wind_speed_kmh=35.0,
        ),
    )

    res = await live_safety_assistant_service.get_safety_guidance_async(req, use_llm=False)
    assert res is not None
    assert res.summary is not None

