"""
Terrain and Historical Exposure Index Calculator.

Provides a transparent, auditable 0-100 exposure score based on physical GIS factors:
1. Slope steepness & precipitous segment exposure (0-35 points)
2. River proximity & gorge confinement (0-25 points)
3. Historical landslide proximity & chronic slide zones (0-25 points)
4. High altitude / extreme elevation exposure (0-15 points)

NO black-box AI model or fabricated probability: all calculations are derived
directly from verifiable GIS geometry, DEM slopes, waterways, and GSI/USDMA records.
"""

from typing import Any, Dict, List, Optional


class TerrainExposureCalculator:
    """Calculates defensible terrain and historical exposure scores."""

    def compute_exposure(
        self,
        elevation_profile: List[Dict[str, Any]],
        waterway_summary: Dict[str, Any],
        landslide_summary: Dict[str, Any],
        max_elevation_m: Optional[float] = None,
    ) -> Dict[str, Any]:
        """
        Computes composite exposure score (0-100) and explicit factor breakdowns.
        """
        factors = []

        # 1. Slope & Terrain Steepness (0-35 points)
        slope_score = 0.0
        steep_count = 0
        precipitous_count = 0
        total_pts = len(elevation_profile)

        for pt in elevation_profile:
            slope = abs(float(pt.get("slope_deg", 0.0)))
            if slope > 45.0:
                precipitous_count += 1
            elif slope >= 30.0:
                steep_count += 1

        if total_pts > 0:
            steep_ratio = steep_count / total_pts
            precipitous_ratio = precipitous_count / total_pts
            slope_score = min(35.0, (precipitous_ratio * 35.0 * 2.5) + (steep_ratio * 25.0))
        slope_score = round(slope_score, 1)

        factors.append({
            "factor_name": "Slope Steepness",
            "score": slope_score,
            "max_score": 35.0,
            "description": f"{round(precipitous_count / max(total_pts, 1) * 100, 1)}% precipitous (>45°), {round(steep_count / max(total_pts, 1) * 100, 1)}% steep (30°-45°)",
        })

        # 2. River Proximity & Gorge Confinement (0-25 points)
        river_score = 0.0
        nearest_river_m = waterway_summary.get("nearest_distance_m")
        river_name = waterway_summary.get("nearest_river_name")

        if nearest_river_m is not None:
            if nearest_river_m <= 250.0:
                river_score = 25.0
                desc = f"Direct riverbed / narrow gorge proximity ({round(nearest_river_m)}m to {river_name})"
            elif nearest_river_m <= 500.0:
                river_score = 18.0
                desc = f"Active river gorge corridor ({round(nearest_river_m)}m to {river_name})"
            elif nearest_river_m <= 1000.0:
                river_score = 10.0
                desc = f"Moderate river channel proximity ({round(nearest_river_m)}m to {river_name})"
            elif nearest_river_m <= 2000.0:
                river_score = 5.0
                desc = f"Distant river corridor ({round(nearest_river_m)}m to {river_name})"
            else:
                river_score = 0.0
                desc = "Upland ridge / safe distance from major riverbeds"
        else:
            desc = "No major glacial torrents detected within search buffer"

        factors.append({
            "factor_name": "Hydrological Exposure",
            "score": river_score,
            "max_score": 25.0,
            "description": desc,
        })

        # 3. Historical Landslides & Chronic Slide Zones (0-25 points)
        landslide_score = 0.0
        ls_count = landslide_summary.get("count", 0)
        high_risk_ls = landslide_summary.get("high_risk_zones_intersecting", 0)
        nearest_ls_m = landslide_summary.get("nearest_distance_m")
        nearest_ls_name = landslide_summary.get("nearest_landslide_name")

        if high_risk_ls > 0:
            landslide_score = min(25.0, 15.0 + (high_risk_ls * 5.0))
            ls_desc = f"Direct proximity to {high_risk_ls} chronic/catastrophic landslide zone(s) ({nearest_ls_name})"
        elif ls_count > 0 and nearest_ls_m is not None:
            if nearest_ls_m <= 1500.0:
                landslide_score = 12.0
                ls_desc = f"Near historical landslide zone ({round(nearest_ls_m)}m to {nearest_ls_name})"
            else:
                landslide_score = 6.0
                ls_desc = f"Historical slide records in corridor buffer ({round(nearest_ls_m)}m)"
        else:
            ls_desc = "No documented chronic landslide sectors in immediate corridor"

        factors.append({
            "factor_name": "Historical Slope Instability",
            "score": landslide_score,
            "max_score": 25.0,
            "description": ls_desc,
        })

        # 4. Extreme Altitude Exposure (0-15 points)
        altitude_score = 0.0
        alt = max_elevation_m or 0.0
        if alt > 4000.0:
            altitude_score = 15.0
            alt_desc = f"Extreme alpine altitude ({round(alt)}m) - high hypothermia/AMS hazard"
        elif alt > 3200.0:
            altitude_score = 10.0
            alt_desc = f"High alpine altitude ({round(alt)}m) - rapid weather changes"
        elif alt > 2400.0:
            altitude_score = 5.0
            alt_desc = f"Sub-alpine mountain altitude ({round(alt)}m)"
        else:
            altitude_score = 0.0
            alt_desc = f"Low to moderate elevation ({round(alt)}m)"

        factors.append({
            "factor_name": "High Altitude Factor",
            "score": altitude_score,
            "max_score": 15.0,
            "description": alt_desc,
        })

        total_score = round(min(100.0, slope_score + river_score + landslide_score + altitude_score), 1)

        if total_score >= 75.0:
            rating = "SEVERE_EXPOSURE"
            label = "Severe Terrain & Hazard Exposure"
        elif total_score >= 50.0:
            rating = "HIGH_EXPOSURE"
            label = "High Terrain & Hazard Exposure"
        elif total_score >= 25.0:
            rating = "MODERATE_EXPOSURE"
            label = "Moderate Terrain Exposure"
        else:
            rating = "LOW_EXPOSURE"
            label = "Low Terrain Exposure"

        return {
            "score": total_score,
            "rating": rating,
            "label": label,
            "factors": factors,
            "disclaimer": (
                "Terrain and Historical Exposure index is derived from slope gradients, "
                "river proximity, and official historical landslide registries. It provides "
                "baseline geographical context and should be evaluated alongside live weather warnings."
            ),
        }


terrain_exposure_calculator = TerrainExposureCalculator()
