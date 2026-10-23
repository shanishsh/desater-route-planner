"""Smoke test script for testing all external data provider adapters."""
import asyncio
import time
from typing import Dict, Any
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.nasa_firms import nasa_firms_client
from app.clients.opencellid import opencellid_client
from app.services.provider_registry import provider_registry


async def run_smoke_tests():
    print("=" * 80)
    print("      TOURIST SAFETY & ASSISTANCE PLATFORM - PROVIDER SMOKE TESTS      ")
    print("=" * 80)

    # 1. Open-Meteo Weather
    print("\n[1/5] Testing Open-Meteo Weather Provider...")
    start_t = time.perf_counter()
    try:
        w_data = await open_meteo_client.fetch_current_weather(32.2396, 77.1887)
        duration_ms = (time.perf_counter() - start_t) * 1000
        if w_data:
            print(f"  Status:       SUCCESS")
            print(f"  Category:     Weather / Forecast")
            print(f"  Records:      1 (Temp: {w_data['temperature_c']}°C, Condition: {w_data['weather_description']})")
            print(f"  Duration:     {duration_ms:.1f} ms")
            print(f"  Freshness:    Retrieved at {w_data['retrieved_at']}")
        else:
            print(f"  Status:       FAILED (No data returned / Circuit open)")
    except Exception as e:
        print(f"  Status:       EXCEPTION: {e}")

    # 2. OpenStreetMap Overpass (Forest Cover)
    print("\n[2/5] Testing OpenStreetMap Overpass Forest Cover Provider...")
    start_t = time.perf_counter()
    try:
        f_data = await osm_forest_client.fetch_forest_features(32.20, 77.15, 32.28, 77.22)
        duration_ms = (time.perf_counter() - start_t) * 1000
        count = f_data.get("record_count", len(f_data.get("features", [])))
        print(f"  Status:       {f_data.get('provider_status', 'UNKNOWN')}")
        print(f"  Category:     Forest & Woodland Geometry (GeoJSON)")
        print(f"  Records:      {count} geometry features")
        print(f"  Duration:     {duration_ms:.1f} ms")
        if f_data.get("error"):
            print(f"  Error Detail: {f_data.get('error')}")
    except Exception as e:
        print(f"  Status:       EXCEPTION: {e}")

    # 3. NASA FIRMS (Active Wildfires)
    print("\n[3/5] Testing NASA FIRMS Active Wildfires Provider...")
    start_t = time.perf_counter()
    try:
        fire_data = await nasa_firms_client.fetch_active_fires(32.0, 77.0, 32.5, 77.5, days=1)
        duration_ms = (time.perf_counter() - start_t) * 1000
        print(f"  Status:       {fire_data.get('provider_status', 'UNKNOWN')}")
        print(f"  Category:     Satellite Active Fire Hotspots")
        print(f"  Configured:   {fire_data.get('is_configured')}")
        print(f"  Records:      {fire_data.get('record_count', 0)} thermal detections")
        print(f"  Duration:     {duration_ms:.1f} ms")
        print(f"  Diagnostics:  {fire_data.get('diagnostic_message')}")
    except Exception as e:
        print(f"  Status:       EXCEPTION: {e}")

    # 4. OpenCelliD (Cell Towers)
    print("\n[4/5] Testing OpenCelliD Cell Tower Provider...")
    start_t = time.perf_counter()
    try:
        cell_data = await opencellid_client.fetch_nearby_cells(32.2396, 77.1887)
        duration_ms = (time.perf_counter() - start_t) * 1000
        print(f"  Status:       {cell_data.get('provider_status', 'UNKNOWN')}")
        print(f"  Category:     Telecom Base Stations")
        print(f"  Configured:   {cell_data.get('is_configured')}")
        print(f"  Records:      {cell_data.get('record_count', 0)} cell records")
        print(f"  Duration:     {duration_ms:.1f} ms")
        print(f"  Diagnostics:  {cell_data.get('diagnostic_message')}")
    except Exception as e:
        print(f"  Status:       EXCEPTION: {e}")

    # 5. Full Provider Registry Summary
    print("\n[5/5] Querying Central Provider Registry Summary...")
    statuses = await provider_registry.get_all_provider_statuses()
    print(f"\n{'PROVIDER':<32} | {'STATUS':<15} | {'CONFIGURED':<10} | {'DATA STATE':<10}")
    print("-" * 75)
    for p in statuses:
        print(f"{p['name']:<32} | {p['status']:<15} | {str(p['is_configured']):<10} | {p['data_state']:<10}")

    print("\n" + "=" * 80)
    print("Smoke tests completed.")

    # Cleanup clients
    await open_meteo_client.close()
    await osm_forest_client.close()
    await nasa_firms_client.close()
    await opencellid_client.close()


if __name__ == "__main__":
    asyncio.run(run_smoke_tests())

