"""TrailGuard Multi-Factor Risk Assessment Engine.

Computes segment-by-segment and corridor-level mountain risk scores by fusing:
1. Hourly weather and freezing thresholds at arrival time
2. DEM terrain slope angles and landslide/rockfall compounding
3. Authoritative NDMA disaster polygons
4. NASA FIRMS satellite thermal detections
"""
import math
import logging
from typing import List, Dict, Any, Optional

logger = logging.getLogger(__name__)


class TrailGuardRiskEngine:
    """Calculates multi-factor alpine risk scores and generates explainable safety decisions."""

    # Weights for risk factors (sum = 1.0)
    W_WEATHER = 0.30
    W_SLOPE = 0.25
    W_HAZARD = 0.25
    W_FIRE = 0.20

    def compute_weather_subscore(
        self,
        precipitation_mm: float,
        precipitation_prob_pct: float,
        wind_speed_kmh: float,
        temperature_c: float,
        elevation_m: float,
        warning_level: str,
    ) -> Dict[str, Any]:
        """Calculates weather risk subscore [0.0 to 100.0] with freezing/gale escalation."""
        score = 0.0
        factors = []

        # 1. Precipitation intensity
        if precipitation_mm >= 15.0:
            score += 55.0
            factors.append(f"Torrential rain ({precipitation_mm:.1f} mm/h)")
        elif precipitation_mm >= 8.0:
            score += 40.0
            factors.append(f"Heavy rain ({precipitation_mm:.1f} mm/h)")
        elif precipitation_mm >= 2.5:
            score += 20.0
            factors.append(f"Moderate rain ({precipitation_mm:.1f} mm/h)")
        elif precipitation_prob_pct >= 70.0:
            score += 12.0
            factors.append(f"High rain probability ({precipitation_prob_pct:.0f}%)")

        # 2. Wind gusts & alpine exposure
        if wind_speed_kmh >= 65.0:
            score += 40.0
            factors.append(f"Dangerous gale gusts ({wind_speed_kmh:.0f} km/h)")
        elif wind_speed_kmh >= 45.0:
            score += 25.0
            factors.append(f"High wind exposure ({wind_speed_kmh:.0f} km/h)")
        elif wind_speed_kmh >= 30.0:
            score += 10.0

        # 3. High elevation freezing/hypothermia threshold
        if elevation_m >= 2500.0 and temperature_c <= 2.0 and precipitation_mm > 0.5:
            score += 35.0
            factors.append(f"Freezing snow/sleet threshold at {elevation_m:.0f}m ({temperature_c:.1f}°C)")
        elif temperature_c <= -5.0:
            score += 25.0
            factors.append(f"Extreme sub-zero chill ({temperature_c:.1f}°C)")

        # 4. Official provider warning level
        if warning_level == "SEVERE":
            score = max(score, 70.0)
        elif warning_level == "CAUTION":
            score = max(score, 35.0)

        clamped = min(100.0, max(0.0, score))
        return {"subscore": round(clamped, 1), "factors": factors}

    def compute_slope_subscore(
        self,
        slope_deg: float,
        precipitation_mm: float,
        elevation_m: float,
    ) -> Dict[str, Any]:
        """
        Calculates terrain slope & landslide susceptibility risk [0.0 to 100.0].
        Applies non-linear multiplier when steep slope compounds with heavy rain.
        """
        factors = []
        if slope_deg >= 38.0:
            base_score = 80.0
            factors.append(f"Extreme alpine slope ({slope_deg:.1f}°)")
        elif slope_deg >= 28.0:
            base_score = 55.0
            factors.append(f"Steep mountain grade ({slope_deg:.1f}°)")
        elif slope_deg >= 18.0:
            base_score = 28.0
            factors.append(f"Moderate incline ({slope_deg:.1f}°)")
        elif slope_deg >= 8.0:
            base_score = 10.0
        else:
            base_score = 3.0

        # Compounding hazard: Steep slopes + heavy rain = debris flow / landslide trigger
        if slope_deg >= 24.0 and precipitation_mm >= 5.0:
            compound_multiplier = 1.45
            base_score *= compound_multiplier
            factors.append("Landslide & rockfall vulnerability (steep terrain under heavy rain)")

        clamped = min(100.0, max(0.0, base_score))
        return {"subscore": round(clamped, 1), "factors": factors}

    def compute_hazard_subscore(self, alerts: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Calculates official NDMA/disaster alert subscore [0.0 to 100.0]."""
        score = 0.0
        factors = []

        for a in alerts:
            sev = str(a.get("severity", "")).upper()
            title = a.get("title", "Disaster Alert")
            if sev == "CRITICAL":
                score = max(score, 95.0)
                factors.append(f"Critical Advisory: {title}")
            elif sev == "SEVERE":
                score = max(score, 75.0)
                factors.append(f"Severe Advisory: {title}")
            elif sev in ["WARNING", "MEDIUM"]:
                score = max(score, 45.0)
                factors.append(f"Warning: {title}")
            elif sev in ["WATCH", "LOW"]:
                score = max(score, 20.0)

        return {"subscore": round(score, 1), "factors": factors}

    def compute_fire_subscore(self, wildfire_info: Dict[str, Any]) -> Dict[str, Any]:
        """Calculates wildfire proximity risk subscore [0.0 to 100.0]."""
        score = 0.0
        factors = []

        if wildfire_info.get("has_fire_within_5km"):
            dist_km = wildfire_info.get("nearest_fire_distance_km", 5.0)
            frp = wildfire_info.get("fire_radiative_power")
            conf = wildfire_info.get("confidence", "nominal")

            if dist_km <= 1.2:
                score = 90.0
                factors.append(f"Immediate active thermal hotspot ({dist_km:.1f} km, Conf: {conf})")
            elif dist_km <= 2.5:
                score = 70.0
                factors.append(f"Wildfire detection in corridor ({dist_km:.1f} km)")
            else:
                score = 35.0
                factors.append(f"Wildfire detection within 5 km ({dist_km:.1f} km)")

            if frp and frp > 25.0:
                score = min(100.0, score + 15.0)
                factors.append(f"High thermal radiative power ({frp:.1f} MW)")

        return {"subscore": round(score, 1), "factors": factors}

    def evaluate_segment_risk(
        self,
        weather: Dict[str, Any],
        slope_deg: float,
        elevation_m: float,
        alerts: List[Dict[str, Any]],
        wildfire: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Combines all 4 factors into a calibrated segment risk score [0.0 to 100.0]."""
        w_res = self.compute_weather_subscore(
            precipitation_mm=weather.get("precipitation_mm", 0.0),
            precipitation_prob_pct=weather.get("precipitation_probability_pct", 0.0),
            wind_speed_kmh=weather.get("wind_speed_kmh", 0.0),
            temperature_c=weather.get("temperature_c", 15.0),
            elevation_m=elevation_m,
            warning_level=weather.get("warning_level", "LOW"),
        )
        s_res = self.compute_slope_subscore(
            slope_deg=slope_deg,
            precipitation_mm=weather.get("precipitation_mm", 0.0),
            elevation_m=elevation_m,
        )
        h_res = self.compute_hazard_subscore(alerts)
        f_res = self.compute_fire_subscore(wildfire)

        # Weighted aggregate
        composite_score = (
            self.W_WEATHER * w_res["subscore"]
            + self.W_SLOPE * s_res["subscore"]
            + self.W_HAZARD * h_res["subscore"]
            + self.W_FIRE * f_res["subscore"]
        )
        # Dominant local hazard override: active official disaster alerts or proximate wildfires
        # must not be diluted by calm weather on that specific segment
        dominant_hazard = max(h_res["subscore"], f_res["subscore"])
        composite_score = max(composite_score, dominant_hazard)
        composite_score = min(100.0, max(0.0, composite_score))

        # Assign risk tier
        if composite_score >= 58.0 or h_res["subscore"] >= 70.0 or f_res["subscore"] >= 70.0:
            level = "CRITICAL"
        elif (
            composite_score >= 30.0
            or w_res["subscore"] >= 40.0
            or s_res["subscore"] >= 45.0
            or h_res["subscore"] >= 35.0
            or f_res["subscore"] >= 35.0
        ):
            level = "CAUTION"
        else:
            level = "SAFE"

        all_factors = w_res["factors"] + s_res["factors"] + h_res["factors"] + f_res["factors"]

        return {
            "risk_score": round(composite_score, 1),
            "risk_level": level,
            "breakdown": {
                "weather": w_res["subscore"],
                "slope": s_res["subscore"],
                "hazard": h_res["subscore"],
                "wildfire": f_res["subscore"],
            },
            "contributing_factors": all_factors,
        }

    def aggregate_route_risk(
        self, segment_evaluations: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Aggregates segment risks preserving extreme danger:
        RouteScore = 0.65 * max(segment_scores) + 0.35 * mean(segment_scores)
        """
        if not segment_evaluations:
            return {
                "overall_score": 10.0,
                "overall_status": "SAFE",
                "max_segment_score": 10.0,
                "critical_segments_count": 0,
                "caution_segments_count": 0,
            }

        scores = [s["risk_score"] for s in segment_evaluations]
        max_score = max(scores)
        mean_score = sum(scores) / len(scores)

        overall_score = (0.65 * max_score) + (0.35 * mean_score)
        if max_score >= 70.0:
            overall_score = max(overall_score, max_score * 0.80)
        overall_score = min(100.0, max(0.0, overall_score))

        critical_count = sum(1 for s in segment_evaluations if s["risk_level"] == "CRITICAL")
        caution_count = sum(1 for s in segment_evaluations if s["risk_level"] == "CAUTION")

        if overall_score >= 50.0 or critical_count >= 1:
            status = "HIGH_RISK"
        elif overall_score >= 28.0 or caution_count >= 1:
            status = "CAUTION"
        else:
            status = "SAFE"

        return {
            "overall_score": round(overall_score, 1),
            "overall_status": status,
            "max_segment_score": round(max_score, 1),
            "critical_segments_count": critical_count,
            "caution_segments_count": caution_count,
        }

    def generate_contrastive_explanation(
        self,
        primary_agg: Dict[str, Any],
        primary_segments: List[Dict[str, Any]],
        alternatives: List[Dict[str, Any]],
    ) -> str:
        """Generates a human-verifiable 'Why This Route?' contrastive safety explanation."""
        status = primary_agg["overall_status"]
        score = primary_agg["overall_score"]

        # Collect prominent factors along the route
        prominent_factors = []
        for s in primary_segments:
            for factor in s.get("contributing_factors", []):
                if factor not in prominent_factors:
                    prominent_factors.append(factor)

        factor_summary = (
            ", ".join(prominent_factors[:3])
            if prominent_factors
            else "Standard mountain trail conditions with no severe weather or active hazards."
        )

        if status == "SAFE":
            explanation = (
                f"TrailGuard assessment confirms this route is currently Safe (Risk Score: {score:.1f}/100). "
                f"Waypoints demonstrate manageable slopes and stable atmospheric conditions. "
                f"Contributing notes: {factor_summary}."
            )
        elif status == "CAUTION":
            explanation = (
                f"Caution advised along this corridor (Risk Score: {score:.1f}/100). "
                f"Identified safety considerations: {factor_summary}. "
                f"Ensure appropriate alpine clothing and monitor arrival timestamps at higher waypoints."
            )
        else:
            # High risk
            alt_comparison = ""
            safest_alt = None
            if alternatives:
                sorted_alts = sorted(alternatives, key=lambda a: a.get("hazard_score", 999.0))
                if sorted_alts and sorted_alts[0].get("hazard_score", 999.0) < score:
                    safest_alt = sorted_alts[0]
                    alt_comparison = (
                        f" Recommended action: Consider '{safest_alt.get('title')}' "
                        f"which reduces corridor hazard score to {safest_alt.get('hazard_score'):.1f}/100."
                    )

            explanation = (
                f"HIGH RISK DETECTED along primary route (Risk Score: {score:.1f}/100, "
                f"{primary_agg['critical_segments_count']} critical section(s)). "
                f"Primary hazard drivers: {factor_summary}.{alt_comparison}"
            )

        return explanation


trailguard_risk_engine = TrailGuardRiskEngine()
