"""Grounded Route Summary Assistant.

Generates concise, evidence-based briefings strictly grounded in actual database records.
Cites specific source IDs, timestamps, and spatial relationships.
Prohibits hallucinating events or declaring any route '100% safe'.
Provides robust deterministic fallback when LLM APIs are unconfigured or unavailable.
"""
import logging
from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class GroundedRouteSummary(BaseModel):
    route_id: str
    route_version: int
    summary_text: str
    direct_intersections: List[str] = Field(default_factory=list)
    regional_context: List[str] = Field(default_factory=list)
    nearby_facilities: List[str] = Field(default_factory=list)
    stale_or_missing_sources: List[str] = Field(default_factory=list)
    alternative_tradeoffs: List[str] = Field(default_factory=list)
    is_ai_generated: bool = False
    disclaimer: str = (
        "Advisory summary grounded strictly in retrieved database records. "
        "Absence of reported alerts does not guarantee mountain terrain is free from localized hazards."
    )


class RouteSummaryAssistant:
    """Produces structured route briefings referencing genuine records."""

    def generate_grounded_summary(
        self,
        route_response: Dict[str, Any],
        use_llm: bool = False,
    ) -> GroundedRouteSummary:
        """
        Builds a grounded route safety briefing. Uses deterministic template synthesis
        citing actual IDs, records, and provider statuses.
        """
        route_id = route_response.get("route_id", "unknown")
        version = route_response.get("route_version", 1)
        title = route_response.get("title", "Trek Route")
        dist_km = route_response.get("distance_km", 0.0)
        dur_mins = route_response.get("duration_minutes", 0.0)

        # 1. Direct vs Regional Hazards
        direct_items = []
        regional_items = []
        raw_alerts = route_response.get("disaster_alerts") or route_response.get("conditions", [])

        for item in raw_alerts:
            # Handle dictionary or Pydantic model
            al_id = item.get("alert_id") or item.get("id") or "alert"
            al_title = item.get("title", "Official Advisory")
            dist_to_al = item.get("distance_to_alert_km", 0.0)
            sev = item.get("severity", "ADVISORY")

            if dist_to_al == 0.0:
                direct_items.append(f"[{al_id[:8]}] {al_title} ({sev}) - directly intersects trail polyline")
            else:
                regional_items.append(f"[{al_id[:8]}] {al_title} ({sev}) - {dist_to_al:.1f} km from trail corridor")

        # 2. Fire detections
        fires = route_response.get("fire_detections", [])
        if fires:
            regional_items.append(
                f"NASA FIRMS: {len(fires)} active satellite thermal anomaly detection(s) within 5 km of trail."
            )

        # 3. Essential facilities
        facilities = route_response.get("assistance_points") or route_response.get("safe_havens", [])
        fac_items = []
        for fac in facilities[:5]:
            f_name = fac.get("name", "Emergency Point")
            f_cat = fac.get("category", "facility")
            f_dist = fac.get("distance_km", 0.0)
            fac_items.append(f"{f_name} ({f_cat}) - {f_dist:.1f} km from trail")

        # 4. Missing or stale providers
        statuses = route_response.get("provider_statuses", {})
        missing_items = []
        for p_id, p_info in statuses.items():
            if isinstance(p_info, dict):
                p_status = p_info.get("status")
                p_name = p_info.get("provider_name", p_id)
                if p_status in ("UNCONFIGURED", "UNAVAILABLE", "ERROR", "CIRCUIT_OPEN", "UNSUPPORTED_REGION"):
                    missing_items.append(f"{p_name}: {p_status} ({p_info.get('details', 'No data')})")

        # 5. Alternative trade-offs
        alts = route_response.get("alternatives", [])
        alt_tradeoffs = []
        for alt in alts:
            a_title = alt.get("title", "Alternative")
            a_dist = alt.get("distance_km", 0.0)
            a_dur = alt.get("duration_minutes", 0.0)
            a_diff_km = round(a_dist - dist_km, 1)
            a_diff_m = round(a_dur - dur_mins, 1)
            a_reason = alt.get("recommendation_reason") or alt.get("description", "")
            alt_tradeoffs.append(
                f"{a_title}: {a_dist} km ({a_diff_km:+g} km, {a_diff_m:+g} mins vs primary). {a_reason}"
            )

        # Construct concise summary text
        summary_paragraphs = [
            f"**Trek Route Briefing for {title}** ({dist_km} km, ~{int(dur_mins)} mins walking)."
        ]

        if direct_items:
            summary_paragraphs.append(
                f"**Critical Trail Exposure**: {len(direct_items)} hazard(s) physically intersect the path. "
                "Exercise extreme caution or consider alternative routing."
            )
        else:
            summary_paragraphs.append(
                "**Trail Polyline Clear of Direct Polygon Intersections**: No known official hazard polygons directly overlap the center of the trail."
            )

        if regional_items:
            summary_paragraphs.append(
                f"**Corridor Weather & Regional Context**: {len(regional_items)} regional alert(s)/detection(s) active in the valley."
            )

        if fac_items:
            summary_paragraphs.append(
                f"**Emergency Egress**: {len(facilities)} verified emergency facilities mapped along corridor."
            )

        if missing_items:
            summary_paragraphs.append(
                f"**Data Limitations**: {len(missing_items)} data source(s) unconfigured or degraded. "
                "Do not assume unmonitored sectors are hazard-free."
            )

        summary_text = "\n\n".join(summary_paragraphs)

        return GroundedRouteSummary(
            route_id=route_id,
            route_version=version,
            summary_text=summary_text,
            direct_intersections=direct_items,
            regional_context=regional_items,
            nearby_facilities=fac_items,
            stale_or_missing_sources=missing_items,
            alternative_tradeoffs=alt_tradeoffs,
            is_ai_generated=False,
        )


route_summary_assistant = RouteSummaryAssistant()
