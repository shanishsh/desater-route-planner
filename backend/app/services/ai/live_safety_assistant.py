"""Live Trek Safety Assistant powered by Groq Cloud AI with deterministic grounding and fallback."""
import json
import re
import logging
from typing import Dict, Any, List, Set, Optional

from app.config import settings
from app.clients.groq_client import groq_client
from app.schemas.assistant import (
    LiveSafetyAssistantRequest,
    AssistantInteractiveQueryRequest,
    LiveSafetyAssistantResponse,
)

logger = logging.getLogger(__name__)


class LiveSafetyAssistantService:
    """Provides real-time, grounded safety guidance and conversational support for active trekkers."""

    async def get_safety_guidance_async(
        self,
        request: LiveSafetyAssistantRequest,
        use_llm: bool = True,
    ) -> LiveSafetyAssistantResponse:
        """
        Evaluates a live trek safety event or current progress state.
        Calls Groq AI for rich contextual explanation if available,
        with strict grounding checks and immediate deterministic fallback.
        """
        # Always build the deterministic baseline first
        fallback_response = self.generate_deterministic_guidance(request)

        if not use_llm or settings.AI_PROVIDER != "groq" or not groq_client.is_configured():
            return fallback_response

        valid_hazard_ids = {h.id for h in request.upcoming_hazards + request.nearby_hazards}
        if request.trigger_event and request.trigger_event.hazard_id:
            valid_hazard_ids.add(request.trigger_event.hazard_id)

        valid_facility_ids = {f.id for f in request.nearby_facilities}
        valid_alternative_titles = {a.title for a in request.verified_alternatives}
        is_weather_stale = request.current_weather.is_stale if request.current_weather else False
        weather_age_mins = request.current_weather.observation_age_minutes if request.current_weather else None

        try:
            system_prompt = (
                "You are the Uttarakhand Mountain Trek Safety Assistant. "
                "Analyze the provided live trek context and return a JSON object with strict safety advice. "
                "CRITICAL INSTRUCTIONS:\n"
                "1. NEVER state that any mountain trail is '100% safe', 'completely safe', or risk-free.\n"
                "2. Reference ONLY the exact hazard IDs provided. Do not invent hazards or facilities.\n"
                "3. If verified_alternatives is empty, explicitly state that NO verified alternative trail is available. Under NO circumstances invent trails or bypass routes.\n"
                "4. If current_weather is marked is_stale=True or observation_age_minutes > 180, explicitly note in data_limitations that weather observations are aged and conditions may have changed.\n"
                "5. Approved safety guidance rules:\n"
                "   - Landslide/Rockfall: Do not cross moving debris; seek high, stable ground away from runout chutes.\n"
                "   - Thunderstorm: Descend from exposed ridges/summits immediately; avoid isolated trees and metal fences.\n"
                "   - Flash flood: Do not attempt to ford swollen rivers; seek high ground immediately.\n"
                "   - Route deviation: If off-route (>80m), stop immediately and backtrack to the last verified marker.\n"
                "6. Spoken message MUST be concise (under 30 words), clear, and direct for a walking trekker.\n"
                "7. Output MUST be ONLY valid JSON matching this structure:\n"
                "{\n"
                '  "summary": "Short 1-2 sentence overview",\n'
                '  "what_happened": "Specific condition or event description",\n'
                '  "why_relevant": "Why this matters to the trekker right now",\n'
                '  "recommended_action": "Clear immediate action to take",\n'
                '  "what_to_avoid": "Dangerous actions to avoid",\n'
                '  "verified_alternatives": ["alternative names if provided, else empty"],\n'
                '  "referenced_hazard_ids": ["exact hazard IDs cited"],\n'
                '  "referenced_facility_ids": ["exact facility IDs cited if applicable"],\n'
                '  "data_limitations": ["any stale or missing data notes"],\n'
                '  "spoken_message": "Under 30 words spoken guidance"\n'
                "}"
            )

            context_json = request.model_dump_json()
            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": f"Live Trek Context:\n{context_json}\n\nReturn JSON safety guidance:"},
            ]

            ai_raw = await groq_client.chat_completion(
                messages=messages,
                temperature=0.1,
                max_tokens=650,
                response_format={"type": "json_object"},
            )

            if ai_raw:
                parsed = self._extract_and_validate_json(
                    ai_text=ai_raw,
                    valid_hazard_ids=valid_hazard_ids,
                    valid_facility_ids=valid_facility_ids,
                    valid_alternative_titles=valid_alternative_titles,
                    is_weather_stale=is_weather_stale,
                    weather_age_mins=weather_age_mins,
                )
                if parsed:
                    return LiveSafetyAssistantResponse(
                        event_id=request.trigger_event.event_id if request.trigger_event else None,
                        context_generation_id=request.context_generation_id,
                        summary=parsed.get("summary") or fallback_response.summary,
                        what_happened=parsed.get("what_happened") or fallback_response.what_happened,
                        why_relevant=parsed.get("why_relevant") or fallback_response.why_relevant,
                        recommended_action=parsed.get("recommended_action") or fallback_response.recommended_action,
                        what_to_avoid=parsed.get("what_to_avoid") or fallback_response.what_to_avoid,
                        verified_alternatives=parsed.get("verified_alternatives") or fallback_response.verified_alternatives,
                        referenced_hazard_ids=parsed.get("referenced_hazard_ids") or fallback_response.referenced_hazard_ids,
                        referenced_facility_ids=parsed.get("referenced_facility_ids") or [],
                        referenced_weather_ids=[],
                        data_limitations=parsed.get("data_limitations") or fallback_response.data_limitations,
                        spoken_message=parsed.get("spoken_message") or fallback_response.spoken_message,
                        additional_explanation=parsed.get("additional_explanation"),
                        is_ai_generated=True,
                        fallback_used=False,
                    )
        except Exception as e:
            logger.warning("Groq live safety assistant failed; using deterministic fallback: %s", e)

        return fallback_response

    async def answer_user_query_async(
        self,
        request: AssistantInteractiveQueryRequest,
        use_llm: bool = True,
    ) -> LiveSafetyAssistantResponse:
        """Answers contextual questions asked by the trekker during active navigation."""
        # Baseline deterministic answer
        fallback_response = self._generate_query_fallback(request)

        if not use_llm or settings.AI_PROVIDER != "groq" or not groq_client.is_configured():
            return fallback_response

        valid_hazard_ids = {h.id for h in request.upcoming_hazards + request.nearby_hazards}
        valid_facility_ids = {f.id for f in request.nearby_facilities}
        valid_alternative_titles = {a.title for a in request.verified_alternatives}
        is_weather_stale = request.current_weather.is_stale if request.current_weather else False
        weather_age_mins = request.current_weather.observation_age_minutes if request.current_weather else None

        try:
            system_prompt = (
                "You are the Uttarakhand Mountain Trek Safety Assistant. "
                "Answer the trekker's question strictly grounded in the provided live trek context. "
                "RULES:\n"
                "1. Base answers ONLY on provided hazards, route progress, weather, and facilities.\n"
                "2. If the user asks for alternatives and none exist in verified_alternatives, clearly state that NO verified alternative trail is available. Under NO circumstances invent paths.\n"
                "3. If current_weather is marked is_stale=True or observation_age_minutes > 180, explicitly note in data_limitations that weather observations are aged and conditions may have changed.\n"
                "4. Never declare the trail completely safe or 100% safe.\n"
                "5. Spoken message must be under 30 words.\n"
                "6. Output ONLY valid JSON with keys: summary, what_happened, why_relevant, recommended_action, what_to_avoid, verified_alternatives, referenced_hazard_ids, referenced_facility_ids, data_limitations, spoken_message."
            )

            user_prompt = (
                f"Trekker Question: \"{request.query}\"\n\n"
                f"Context Data:\n{request.model_dump_json()}\n\n"
                "Provide grounded JSON response:"
            )

            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ]

            ai_raw = await groq_client.chat_completion(
                messages=messages,
                temperature=0.15,
                max_tokens=650,
                response_format={"type": "json_object"},
            )

            if ai_raw:
                parsed = self._extract_and_validate_json(
                    ai_text=ai_raw,
                    valid_hazard_ids=valid_hazard_ids,
                    valid_facility_ids=valid_facility_ids,
                    valid_alternative_titles=valid_alternative_titles,
                    is_weather_stale=is_weather_stale,
                    weather_age_mins=weather_age_mins,
                )
                if parsed:
                    return LiveSafetyAssistantResponse(
                        event_id=None,
                        context_generation_id=request.context_generation_id,
                        summary=parsed.get("summary") or fallback_response.summary,
                        what_happened=parsed.get("what_happened") or fallback_response.what_happened,
                        why_relevant=parsed.get("why_relevant") or fallback_response.why_relevant,
                        recommended_action=parsed.get("recommended_action") or fallback_response.recommended_action,
                        what_to_avoid=parsed.get("what_to_avoid") or fallback_response.what_to_avoid,
                        verified_alternatives=parsed.get("verified_alternatives") or fallback_response.verified_alternatives,
                        referenced_hazard_ids=parsed.get("referenced_hazard_ids") or fallback_response.referenced_hazard_ids,
                        referenced_facility_ids=parsed.get("referenced_facility_ids") or [],
                        referenced_weather_ids=[],
                        data_limitations=parsed.get("data_limitations") or fallback_response.data_limitations,
                        spoken_message=parsed.get("spoken_message") or fallback_response.spoken_message,
                        is_ai_generated=True,
                        fallback_used=False,
                    )
        except Exception as e:
            logger.warning("Groq interactive assistant failed; using fallback: %s", e)

        return fallback_response

    def generate_deterministic_guidance(
        self,
        request: LiveSafetyAssistantRequest,
    ) -> LiveSafetyAssistantResponse:
        """Synthesizes guaranteed grounded safety guidance from deterministic rules and pre-approved templates."""
        ev = request.trigger_event
        prog = request.route_progress
        w = request.current_weather
        hazards = request.upcoming_hazards

        referenced_ids = []
        if ev and ev.hazard_id:
            referenced_ids.append(ev.hazard_id)

        # 1. Evaluate Trigger Event
        if ev:
            ev_type = ev.event_type
            title = ev.hazard_title
            sev = ev.severity.upper()
            dist_m = ev.distance_ahead_m or ev.distance_current_m
            dist_str = f"{dist_m / 1000.0:.1f} km" if dist_m >= 1000.0 else f"{int(dist_m)} m"

            if "LANDSLIDE" in title.upper() or "ROCKFALL" in title.upper() or "OBSTRUCTION" in ev_type:
                summary = f"{sev} {title} detected {dist_str} along route corridor."
                what_happened = f"An active geological hazard [{ev.hazard_id or 'HZ'}] was reported intersecting or near the trail."
                why_relevant = f"The path ahead {dist_str} has compromised terrain stability and falling rock risk."
                action = "Halt progress before entering the active debris zone. Survey overhead slopes from a protected position."
                avoid = "Do not attempt to scramble over moving debris or cross under active rockfall chutes."
                spoken = f"Warning: {title} {dist_str} ahead. Do not cross active debris. Halt in protected terrain."

            elif "THUNDER" in title.upper() or "WEATHER" in ev_type:
                summary = f"Severe thunderstorm warning active for current trail sector."
                what_happened = "Rapid atmospheric deterioration with lightning and torrential rain risk detected."
                why_relevant = f"High elevation ridge sections at {request.current_location.altitude_meters or 2200:.0f}m pose severe lightning strike exposure."
                action = "Descend immediately from exposed ridgelines and open peaks to lower forested terrain."
                avoid = "Avoid solitary tall trees, wet ropes, metal safety cables, and shallow rock overhangs."
                spoken = "Thunderstorm warning active. Descend from ridges immediately. Avoid solitary trees and metal fences."

            elif "DEVIATION" in ev_type or prog.is_off_route:
                dev_m = int(prog.cross_track_m)
                summary = f"Route deviation detected: {dev_m} meters off planned trail."
                what_happened = f"Your current GPS position has departed from the verified trail polyline."
                why_relevant = "Unmapped mountain terrain frequently leads to impassable cliffs, dense underbrush, or steep ravines."
                action = "Stop advancing. Check compass and map orientation, then carefully backtrack to the verified trail."
                avoid = "Do not bushwhack forward assuming the trail will rejoin ahead."
                spoken = f"Off-route warning: {dev_m} meters off planned trail. Stop and backtrack to verified trail."

            elif "CONNECTIVITY" in ev_type:
                summary = "Cellular signal lost. System operating in offline safety mode."
                what_happened = "Device has transitioned outside cellular tower reach into a mountain dead zone."
                why_relevant = "Live updates are paused; safety assessments are using cached hazard and weather records."
                action = "Continue following the cached route polyline. Conserve device battery."
                avoid = "Do not rely on live SOS dispatch over standard cellular network until connection returns."
                spoken = "Cellular connection lost. Offline safety guidance and GPS tracking remain active."

            else:
                summary = f"{sev} alert: {title} ({dist_str})."
                what_happened = f"Hazard [{ev.hazard_id or 'alert'}] detected in trail vicinity."
                why_relevant = "Environmental conditions require heightened vigilance."
                action = ev.deterministic_action_text or "Proceed with caution and maintain visual trail awareness."
                avoid = "Avoid traversing hazardous terrain in poor visibility."
                spoken = ev.deterministic_spoken_text or f"Alert: {title} detected {dist_str} ahead. Exercise caution."

        elif prog.is_off_route:
            dev_m = int(prog.cross_track_m)
            summary = f"Route deviation detected: {dev_m} meters off trail."
            what_happened = "GPS tracking indicates lateral departure from the selected hiking path."
            why_relevant = "Off-trail travel in Uttarakhand mountains risks cliff hazards and lost orientation."
            action = "Halt and retrace steps to the last verified trail marker."
            avoid = "Never force a shortcut down steep gullies."
            spoken = f"Off-route alert: {dev_m} meters off planned trail. Stop and backtrack."

        elif hazards:
            first_h = hazards[0]
            referenced_ids.append(first_h.id)
            dist_str = f"{(first_h.distance_ahead_m or 0.0) / 1000.0:.1f} km"
            summary = f"Upcoming: #{first_h.sequence_number or 1} {first_h.title} {dist_str} ahead."
            what_happened = f"Monitored hazard [{first_h.id}] is positioned along your planned progression."
            why_relevant = f"Expected encounter in approximately {int((first_h.distance_ahead_m or 1000) / 58)} minutes at normal walking pace."
            action = first_h.instructions or "Maintain trail vigilance as you approach the identified area."
            avoid = "Do not stop in narrow choke points beneath steep cuts."
            spoken = f"Next hazard: {first_h.title} {dist_str} ahead. Maintain trail awareness."

        else:
            summary = f"Trek active: {prog.remaining_km:.1f} km remaining to destination."
            what_happened = "No active hazard intersections currently reported along the remaining route."
            why_relevant = f"Progress: {prog.progress_pct:.0f}% completed ({prog.traveled_km:.1f} km walked)."
            action = "Continue along the marked trail path. Monitor changing mountain weather."
            avoid = "Do not assume absence of reported hazards guarantees zero localized natural risks."
            spoken = f"Trail clear of known hazards. {prog.remaining_km:.1f} kilometers remaining."

        # Verified alternatives check
        alt_names = [a.title for a in request.verified_alternatives]
        if not alt_names:
            alt_names = ["No verified alternative trail available in database."]

        # Data limitations notes
        limitations = list(request.stale_sources)
        if request.connectivity_status in ("OFFLINE", "DEAD_ZONE_LIKELY"):
            limitations.append("Cellular offline: displaying local cached hazard and weather data.")
        if w and (w.is_stale or (w.observation_age_minutes and w.observation_age_minutes > 180)):
            limitations.append("Weather forecast observations are older than 3 hours; mountain conditions may differ.")

        return LiveSafetyAssistantResponse(
            event_id=ev.event_id if ev else None,
            context_generation_id=request.context_generation_id,
            summary=summary,
            what_happened=what_happened,
            why_relevant=why_relevant,
            recommended_action=action,
            what_to_avoid=avoid,
            verified_alternatives=alt_names,
            referenced_hazard_ids=referenced_ids,
            data_limitations=limitations,
            spoken_message=spoken[:180],
            is_ai_generated=False,
            fallback_used=True,
        )

    def _generate_query_fallback(
        self,
        request: AssistantInteractiveQueryRequest,
    ) -> LiveSafetyAssistantResponse:
        """Deterministic response for user-initiated questions."""
        q = request.query.lower()
        prog = request.route_progress
        w = request.current_weather
        hazards = request.upcoming_hazards

        if "weather" in q or "rain" in q or "temp" in q:
            if w:
                temp_str = f"{w.temperature_c:.0f}°C" if w.temperature_c is not None else "Unknown"
                desc = w.weather_description or "Variable mountain conditions"
                rain_prob = f"{w.precipitation_probability_pct}%" if w.precipitation_probability_pct is not None else "N/A"
                summary = f"Current weather: {temp_str}, {desc}. Rain probability: {rain_prob}."
                action = "Check sky conditions regularly and prepare rain gear if overcast."
                spoken = f"Current weather is {temp_str} with {desc}. Rain probability {rain_prob}."
            else:
                summary = "Weather observations currently unconfigured or offline."
                action = "Observe sky and wind patterns directly from trail vantage points."
                spoken = "Live weather data currently unavailable. Observe local sky conditions."
            what_happened = "Retrieved live meteorological conditions for current trail sector."
            why_relevant = "High altitude mountain weather can change in under 30 minutes."
            avoid = "Never remain on exposed ridgelines during sudden convective cloud buildups."
            ref_ids = []

        elif any(k in q for k in ("next hazard", "hazard", "danger", "cross", "stream", "river", "flood", "water")):
            if hazards:
                first = hazards[0]
                dist_str = f"{(first.distance_ahead_m or 0.0) / 1000.0:.1f} km"
                summary = f"Next hazard is #{first.sequence_number or 1} {first.title}, {dist_str} ahead."
                what_happened = f"Identified {first.severity} {first.category} [{first.id}]."
                why_relevant = f"Located directly along your planned path."
                action = first.instructions or "Approach with caution and inspect terrain."
                avoid = "Do not disregard official barricades or trail warnings."
                spoken = f"Next hazard: {first.title}, {dist_str} ahead."
                ref_ids = [first.id]
            else:
                summary = "No reported hazards along your remaining planned route."
                what_happened = "All known official advisories and active polygons are clear of remaining path."
                why_relevant = f"{prog.remaining_km:.1f} km remaining on trail."
                action = "Stay on the designated track and keep situational awareness."
                avoid = "Do not assume unmonitored sectors are risk-free."
                spoken = "No upcoming hazards reported along your remaining route."
                ref_ids = []

        elif "alternative" in q or "bypass" in q or "detour" in q:
            if request.verified_alternatives:
                first_alt = request.verified_alternatives[0]
                summary = f"Verified alternative available: {first_alt.title} ({first_alt.distance_km:.1f} km)."
                what_happened = f"System identified {len(request.verified_alternatives)} verified bypass path(s)."
                why_relevant = first_alt.safety_advantage or "Provides bypass around monitored hazard zones."
                action = "Inspect alternative in route planner before choosing to divert."
                avoid = "Never attempt unverified goat paths or steep off-trail descents."
                spoken = f"Verified alternative: {first_alt.title}, {first_alt.distance_km:.1f} kilometers."
                ref_ids = []
            else:
                summary = "No verified alternative trails exist in the validated trail database."
                what_happened = "Network graph does not have an alternate mapped trail for this mountain section."
                why_relevant = "Terrain outside mapped paths features severe cliffs and dense forest."
                action = "If route ahead is impassable, retreat to the last safe checkpoint or shelter."
                avoid = "Do not attempt unverified cross-country detours."
                spoken = "No verified alternative trail exists. If blocked, return to last safe checkpoint."
                ref_ids = []

        elif "clinic" in q or "hospital" in q or "medical" in q or "facility" in q:
            facs = request.nearby_facilities
            if facs:
                nearest = sorted(facs, key=lambda f: f.distance_km)[0]
                summary = f"Nearest mapped facility: {nearest.name} ({nearest.category}), {nearest.distance_km:.1f} km away."
                what_happened = f"Found {len(facs)} emergency facility record(s) in corridor database."
                why_relevant = "Closest verified shelter or medical support point."
                action = f"In an emergency, head toward {nearest.name}."
                avoid = "Do not leave marked trail unless direct access route is confirmed."
                spoken = f"Nearest facility is {nearest.name}, {nearest.distance_km:.1f} kilometers away."
                ref_ids = []
            else:
                summary = "No emergency facilities mapped within 10 km corridor."
                what_happened = "Remote alpine sector with no verified medical or shelter infrastructure."
                why_relevant = "Self-reliance and early evacuation decisions are critical."
                action = "Prepare warm gear and consider turning back if illness or injury is developing."
                avoid = "Do not delay turning back if symptoms of altitude sickness or injury arise."
                spoken = "No medical facilities mapped within 10 kilometers. Evacuate early if injured."
                ref_ids = []

        else:
            summary = f"Trek assistant: {prog.remaining_km:.1f} km remaining to destination."
            what_happened = "Active monitoring is tracking your coordinates against Uttarakhand hazards."
            why_relevant = f"Currently at {prog.chainage_km:.1f} km chainage, walking pace normal."
            action = "Ask about upcoming hazards, weather ahead, facilities, or alternatives."
            avoid = "Do not hike after nightfall in high bear and leopard corridors."
            spoken = f"Monitoring active. {prog.remaining_km:.1f} kilometers remaining to destination."
            ref_ids = []

        return LiveSafetyAssistantResponse(
            event_id=None,
            context_generation_id=request.context_generation_id,
            summary=summary,
            what_happened=what_happened,
            why_relevant=why_relevant,
            recommended_action=action,
            what_to_avoid=avoid,
            verified_alternatives=[a.title for a in request.verified_alternatives] or ["None verified"],
            referenced_hazard_ids=ref_ids,
            data_limitations=[],
            spoken_message=spoken[:180],
            is_ai_generated=False,
            fallback_used=True,
        )

    def _extract_and_validate_json(
        self,
        ai_text: str,
        valid_hazard_ids: Set[str],
        valid_facility_ids: Optional[Set[str]] = None,
        valid_alternative_titles: Optional[Set[str]] = None,
        is_weather_stale: bool = False,
        weather_age_mins: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Parses LLM output, extracting JSON and enforcing citation grounding and safety rules."""
        if not ai_text or not ai_text.strip():
            return None

        data: Optional[Dict[str, Any]] = None
        try:
            # First try direct parse (works when json_object mode is on)
            data = json.loads(ai_text.strip())
        except Exception:
            # Fallback to regex object extractor
            match = re.search(r"\{.*\}", ai_text, re.DOTALL)
            if match:
                try:
                    data = json.loads(match.group(0))
                except Exception:
                    data = None

        if not isinstance(data, dict):
            return None

        # 1. Enforce citation grounding on referenced hazard IDs
        raw_hazard_cites = data.get("referenced_hazard_ids") or []
        if isinstance(raw_hazard_cites, list):
            data["referenced_hazard_ids"] = [cid for cid in raw_hazard_cites if str(cid) in valid_hazard_ids]
        else:
            data["referenced_hazard_ids"] = []

        # 2. Enforce citation grounding on referenced facility IDs
        raw_facility_cites = data.get("referenced_facility_ids") or []
        if isinstance(raw_facility_cites, list) and valid_facility_ids:
            data["referenced_facility_ids"] = [fid for fid in raw_facility_cites if str(fid) in valid_facility_ids]
        else:
            data["referenced_facility_ids"] = []

        # 3. Enforce verified alternatives grounding
        if not valid_alternative_titles:
            data["verified_alternatives"] = ["No verified alternative trail available"]
        else:
            raw_alts = data.get("verified_alternatives") or []
            if isinstance(raw_alts, list):
                valid_alts = [a for a in raw_alts if str(a) in valid_alternative_titles]
                data["verified_alternatives"] = valid_alts if valid_alts else ["No verified alternative trail available"]
            else:
                data["verified_alternatives"] = ["No verified alternative trail available"]

        # 4. Enforce weather staleness limitation
        limitations = data.get("data_limitations")
        if not isinstance(limitations, list):
            limitations = []
        if is_weather_stale or (weather_age_mins and weather_age_mins > 180):
            if not any("weather" in str(l).lower() for l in limitations):
                limitations.append("Weather forecast observations are older than 3 hours; mountain conditions may differ.")
        data["data_limitations"] = limitations

        # 5. Prohibit '100% safe' or risk-free language across all text fields
        for field in ("summary", "what_happened", "why_relevant", "recommended_action", "what_to_avoid", "spoken_message"):
            val = data.get(field, "")
            if isinstance(val, str):
                for forbidden in ("100% safe", "completely safe", "safe to proceed without risk", "guaranteed safe", "entirely safe", "zero risk"):
                    if forbidden in val.lower():
                        val = re.sub(re.escape(forbidden), "currently clear of reported warnings; maintain standard trail vigilance", val, flags=re.IGNORECASE)
                data[field] = val

        # 6. Concise spoken message constraint (under 30 words)
        spoken = data.get("spoken_message", "")
        if isinstance(spoken, str) and spoken.strip():
            words = spoken.split()
            if len(words) > 30:
                data["spoken_message"] = " ".join(words[:30])

        return data


live_safety_assistant_service = LiveSafetyAssistantService()

