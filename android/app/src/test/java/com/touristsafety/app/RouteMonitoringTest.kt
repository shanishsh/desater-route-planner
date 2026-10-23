package com.touristsafety.app

import com.touristsafety.app.core.network.dto.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RouteMonitoringTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRoutePlanDtoSerializationAndParsing() {
        val weather = RouteWeatherConditionDto(
            temperature_c = 18.5,
            relative_humidity_pct = 55.0,
            precipitation_probability_pct = 20.0,
            precipitation_mm = 0.0,
            wind_speed_kmh = 14.0,
            weather_code = 2,
            weather_description = "Partly cloudy",
            warning_level = "NORMAL",
            forecast_valid_at = "2026-10-23T14:00",
            source = "Open-Meteo Hourly API"
        )

        val wp = RouteWaypointConditionDto(
            index = 0,
            name = "Start: Shimla Mall Road",
            latitude = 31.1048,
            longitude = 77.1734,
            distance_from_start_km = 0.0,
            estimated_arrival_at = "2026-10-23T14:00:00Z",
            hazard_level = "LOW",
            weather = weather,
            forest = RouteForestInfoDto(in_forest = true, forest_name = "Shimla Pine Reserve"),
            wildfire = RouteWildfireInfoDto(has_fire_within_5km = false),
            alerts = emptyList()
        )

        val routeDto = PlannedRouteResponseDto(
            route_id = "test-route-123",
            title = "Shimla to Kufri",
            origin_name = "Shimla",
            origin_lat = 31.1048,
            origin_lon = 77.1734,
            destination_name = "Kufri",
            dest_lat = 31.0979,
            dest_lon = 77.2674,
            travel_mode = "driving",
            distance_km = 16.2,
            duration_minutes = 38.0,
            overall_hazard_score = 15.0,
            overall_safety_status = "SAFE",
            high_risk_segments_count = 0,
            conditions = listOf(wp),
            alternatives = listOf(
                RouteAlternativeDto(
                    id = "alt-1",
                    title = "Valley Detour",
                    description = "Scenic valley route",
                    distance_km = 18.0,
                    duration_minutes = 44.0,
                    hazard_score = 10.0,
                    safety_label = "Safe"
                )
            ),
            created_at = "2026-10-23T12:00:00Z"
        )

        val serialized = json.encodeToString(routeDto)
        assertNotNull(serialized)
        assertTrue(serialized.contains("Shimla to Kufri"))
        assertTrue(serialized.contains("Partly cloudy"))

        val deserialized = json.decodeFromString<PlannedRouteResponseDto>(serialized)
        assertEquals("test-route-123", deserialized.route_id)
        assertEquals(16.2, deserialized.distance_km, 0.01)
        assertEquals("SAFE", deserialized.overall_safety_status)
        assertEquals(1, deserialized.conditions.size)
        assertEquals("Shimla Pine Reserve", deserialized.conditions[0].forest?.forest_name)
    }

    @Test
    fun testCorridorHazardScoringLogic() {
        val criticalAlert = RouteDisasterAlertInfoDto(
            alert_id = "alert-landslide-1",
            title = "Flash Flood & Landslide Warning",
            hazard_category = "landslide",
            severity = "CRITICAL",
            distance_to_alert_km = 1.2,
            required_protective_instructions = "Avoid low-lying gorge passes."
        )

        val dangerousWp = RouteWaypointConditionDto(
            index = 3,
            name = "Kufri Ridge Gorge",
            latitude = 31.0990,
            longitude = 77.2550,
            distance_from_start_km = 12.0,
            estimated_arrival_at = "2026-10-23T14:30:00Z",
            hazard_level = "CRITICAL",
            weather = RouteWeatherConditionDto(
                temperature_c = 11.0,
                relative_humidity_pct = 95.0,
                precipitation_probability_pct = 90.0,
                precipitation_mm = 25.0,
                wind_speed_kmh = 60.0,
                weather_code = 95,
                weather_description = "Thunderstorm with heavy rain",
                warning_level = "SEVERE",
                forecast_valid_at = "2026-10-23T14:30",
                source = "Open-Meteo"
            ),
            wildfire = RouteWildfireInfoDto(has_fire_within_5km = true, nearest_fire_distance_km = 1.8),
            alerts = listOf(criticalAlert)
        )

        // Verify high severity triggers
        assertEquals("CRITICAL", dangerousWp.hazard_level)
        assertTrue(dangerousWp.wildfire?.has_fire_within_5km == true)
        assertEquals(1, dangerousWp.alerts.size)
        assertEquals("CRITICAL", dangerousWp.alerts[0].severity)
        assertEquals("SEVERE", dangerousWp.weather.warning_level)
    }

    @Test
    fun testRouteAnalysisResponseSerialization() {
        val analysis = RouteAnalysisResponseDto(
            route_id = "route-abc-123",
            route_version = 2,
            analysis_timestamp = "2026-10-24T01:00:00Z",
            overall_hazard_score = 45.0,
            overall_safety_status = "CAUTION",
            decision_explanation = "High gust weather along corridor",
            provider_statuses = mapOf(
                "open_meteo" to ProviderStatusItemDto(
                    provider_name = "Open-Meteo",
                    status = "LOADED",
                    record_count = 5
                )
            ),
            weather_by_waypoint = emptyList(),
            disaster_alerts = emptyList(),
            fire_detections = emptyList(),
            forest_features = emptyList(),
            assistance_points = listOf(
                SafeHavenInfoDto(
                    name = "Alpine Mountain Shelter",
                    category = "shelter",
                    latitude = 31.10,
                    longitude = 77.20,
                    distance_km = 1.5,
                    is_egress_point = true
                )
            )
        )

        val jsonStr = json.encodeToString(analysis)
        assertTrue(jsonStr.contains("route-abc-123"))
        assertTrue(jsonStr.contains("Alpine Mountain Shelter"))

        val decoded = json.decodeFromString<RouteAnalysisResponseDto>(jsonStr)
        assertEquals(2, decoded.route_version)
        assertEquals(1, decoded.assistance_points.size)
        assertEquals("Alpine Mountain Shelter", decoded.assistance_points[0].name)
    }
}

