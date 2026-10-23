# System Architecture: Tourist Safety & Assistance Platform

## 1. System Overview

The **Tourist Safety & Assistance Platform** is an offline-first mobile safety and navigation platform designed for alpine and wilderness tourists. It features a native Android frontend built with Kotlin and Jetpack Compose Material 3, and a modular monolith backend built with FastAPI, PostgreSQL/PostGIS, and Redis.

```mermaid
graph TD
    subgraph "Android Mobile Client (Kotlin)"
        UI[Jetpack Compose Material 3 UI\nHome | Map | Alerts | Emergency | Saved]
        VM[ViewModels\nStateFlow State Management]
        Repo[Offline-First Repositories\nItinerary | Alert | Weather | Emergency]
        Room[(Room Database\nLocal Single Source of Truth)]
        MapLibre[MapLibre Native Android SDK\nVector Map Renderer]
        WM[WorkManager\nConstraint-Aware Periodic Sync]
    end

    subgraph "Backend API (FastAPI + Python 3.11)"
        API[FastAPI Endpoints /api/v1/*]
        Middleware[Correlation ID | Rate Limiter | Structured Error Handler]
        Services[Application Services\nAlert | Weather | Itinerary | Emergency]
        Cache[Redis Cache-Aside & Stampede Lock]
        Worker[ARQ Background Worker]
    end

    subgraph "Durable Storage & Infrastructure"
        PG[(PostgreSQL + PostGIS\nSpatial Indexes)]
        Redis[(Redis 7\nCache & Job Queue)]
    end

    subgraph "External Providers (Free & Open-Source)"
        OFM[OpenFreeMap Vector Tiles\nLiberty Style]
        OpenMeteo[Open-Meteo Weather API\nCC BY 4.0]
        ERSS[National Emergency Registry 112]
    end

    UI --> VM
    VM --> Repo
    Repo --> Room
    Repo --> API
    WM --> Repo
    MapLibre --> OFM

    API --> Middleware
    Middleware --> Services
    Services --> Cache
    Cache --> Redis
    Services --> PG
    Services --> OpenMeteo
    Worker --> Redis
    Worker --> PG
    Worker --> OpenMeteo
```

---

## 2. Data Flow Architecture

### 2.1 Weather Forecast Retrieval & Circuit Breaker Flow

```mermaid
sequenceDiagram
    autonumber
    actor Tourist as Mobile Client
    participant API as FastAPI Service
    participant Redis as Redis Cache
    participant CB as Circuit Breaker
    participant OM as Open-Meteo API
    participant DB as PostgreSQL Snapshots

    Tourist->>API: GET /api/v1/weather?lat=32.24&lon=77.19
    API->>Redis: Check cache "weather:32.24:77.19"
    alt Cache Hit
        Redis-->>API: Return cached JSON (TTL valid)
        API-->>Tourist: WeatherResponse (is_cached=true)
    else Cache Miss
        API->>Redis: Acquire Stampede Lock
        API->>CB: Can execute request?
        alt Circuit CLOSED
            CB->>OM: GET /v1/forecast (timeout: 5s)
            alt Upstream Success
                OM-->>CB: 200 OK + Live Forecast
                CB-->>API: Parsed Forecast
                API->>DB: Save durable WeatherSnapshot
                API->>Redis: Set cache (TTL: 15 min)
                API-->>Tourist: WeatherResponse (is_cached=false, is_fallback=false)
            else Upstream Failure / Timeout
                CB->>CB: Record failure (trips to OPEN if threshold met)
                API->>DB: Query latest snapshot within geographic tolerance
                DB-->>API: Last-known-good snapshot
                API-->>Tourist: WeatherResponse (is_fallback=true, is_stale=true)
            end
        else Circuit OPEN
            API->>DB: Query latest snapshot (bypass external request)
            DB-->>API: Last-known-good snapshot
            API-->>Tourist: WeatherResponse (is_fallback=true, is_stale=true)
        end
    end
```

---

### 2.2 Alert Dynamic Freshness Engine Flow

```mermaid
sequenceDiagram
    autonumber
    actor Tourist as Mobile Client
    participant API as FastAPI Service
    participant Engine as Freshness Engine
    participant DB as PostgreSQL Database

    Tourist->>API: GET /api/v1/alerts
    API->>DB: SELECT * FROM alerts ORDER BY source_published_at DESC
    DB-->>API: List[Alert ORM Records]
    API->>Engine: Evaluate Dynamic Freshness for each alert
    Note over Engine: age_seconds = now() - source_published_at<br/>is_stale = age_seconds > freshness_threshold_seconds
    Engine-->>API: List[AlertResponse] with dynamic is_stale and age_minutes
    API-->>Tourist: PaginatedResponse[AlertResponse]
    Tourist->>Tourist: Render in Room & UI with [FRESH] / [STALE] pill and [SIMULATED] badge
```

---

## 3. Database Entity Relationship Diagram

```mermaid
erDiagram
    USERS {
        string id PK
        string email UK
        string hashed_password
        string role
        boolean is_active
        datetime created_at
        datetime updated_at
    }

    ITINERARIES {
        string id PK
        string title
        text description
        string region
        string difficulty
        float distance_km
        float estimated_duration_hours
        float start_latitude
        float start_longitude
        float end_latitude
        float end_longitude
        json route_geojson
        boolean is_active
        datetime created_at
        datetime updated_at
    }

    ROUTE_SEGMENTS {
        string id PK
        string itinerary_id FK
        int sequence_order
        string name
        float elevation_gain_m
        string hazard_level
        json segment_geojson
        datetime created_at
        datetime updated_at
    }

    MAP_POINTS {
        string id PK
        string name
        string category
        float latitude
        float longitude
        string address
        string phone
        text description
        string verification_status
        datetime verification_timestamp
        string provenance
        datetime created_at
        datetime updated_at
    }

    ALERTS {
        string id PK
        string title
        text description
        string severity
        string category
        string affected_itinerary_id FK
        string affected_segment_id FK
        string source_name
        string source_url
        datetime source_published_at
        datetime retrieved_at
        int freshness_threshold_seconds
        boolean is_simulation
        datetime created_at
        datetime updated_at
    }

    WEATHER_SNAPSHOTS {
        string id PK
        float latitude
        float longitude
        float temperature_c
        float relative_humidity_pct
        float wind_speed_kmh
        float precipitation_mm
        float precipitation_probability_pct
        int weather_code
        string weather_description
        datetime forecast_valid_at
        datetime retrieved_at
        datetime expires_at
    }

    CROWD_SNAPSHOTS {
        string id PK
        string itinerary_id FK
        string location_name
        string crowd_level
        string density_description
        boolean is_simulation
        datetime observed_at
    }

    AUDIT_EVENTS {
        string id PK
        string user_id
        string action
        string resource_type
        string resource_id
        json details
        string ip_address
        datetime created_at
    }

    ITINERARIES ||--o{ ROUTE_SEGMENTS : contains
    ITINERARIES ||--o{ ALERTS : affects
    ROUTE_SEGMENTS ||--o{ ALERTS : targets
    ITINERARIES ||--o{ CROWD_SNAPSHOTS : monitors
```

---

## 4. Key Architectural Decisions (ADR)

1. **Room as Single Source of Truth for Mobile**:
   The Android UI never binds directly to volatile network requests. All network responses are transactionally written to Room, and UI components observe Room `Flow` streams. If network calls fail, the existing cached state remains seamlessly visible without crashing.

2. **Dynamic Alert Freshness vs Static Stored Booleans**:
   `is_stale` is computed at request time and UI render time (`(now - source_published_at) > threshold`). This ensures that an alert that was fresh 30 minutes ago correctly transitions to stale without requiring write locks or database cron triggers.

3. **OpenFreeMap Liberty Style & Bounded Vector Strategy**:
   MapLibre Native Android SDK renders vector tiles directly from OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`). Downloads are bounded by OpenFreeMap terms (maximum 3,000 tiles or 50 MB, <= 4 concurrent threads). Additionally, the application renders a pure vector schematic canvas offline so route geometry and waypoints remain 100% accessible even when no basemap tiles are present.

4. **Multi-Layer Cache-Aside with Degraded Mode**:
   Redis caches weather and geographic queries. If Redis crashes or is stopped, the application seamlessly operates in degraded in-memory mode without terminating active user requests.

5. **Privacy by Design**:
   Tourists' GPS coordinates are processed exclusively on-device for finding nearest emergency points. The backend does not log, persist, or track tourist locations.

