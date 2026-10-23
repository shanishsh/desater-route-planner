# TrailGuard: Deployment & Operations Checklist

This checklist documents the exact procedures, environment variables, secret management practices, and post-deployment validation steps required to safely deploy the TrailGuard backend on cloud platforms (e.g. Render, AWS, GCP, Fly.io, or Docker Compose).

---

## 1. Required Deployment Environment Variables

Configure these variables in your platform's secure Environment Settings (never commit raw values to git):

```bash
# Core Runtime
ENVIRONMENT=production
LOG_LEVEL=INFO
API_HOST=0.0.0.0
API_PORT=8000
API_V1_PREFIX=/api/v1
CORS_ORIGINS=["https://desater-route-planner.onrender.com","https://touristsafety.app"]

# PostgreSQL / PostGIS Database (Supabase Session Pooler or Cloud RDS)
DATABASE_URL=postgresql+asyncpg://postgres:[DB_PASSWORD]@aws-0-ap-south-1.pooler.supabase.com:5432/postgres
DATABASE_ECHO=false

# Redis / Upstash Redis TLS (Cache-Aside & Job Coordination)
REDIS_URL=rediss://default:[REDIS_TOKEN]@viable-bluegill-237463.upstash.io:6379
UPSTASH_REDIS_REST_URL=https://viable-bluegill-237463.upstash.io
UPSTASH_REDIS_REST_TOKEN=[REDIS_TOKEN]

# Security & Admin Authentication
SECRET_KEY=[SECURE_RANDOM_32_BYTE_HEX_KEY]
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=60
REFRESH_TOKEN_EXPIRE_DAYS=7
ADMIN_API_KEY=[SECURE_ADMIN_API_KEY]
ADMIN_EMAIL=admin@touristsafety.local
ADMIN_PASSWORD=[SECURE_ADMIN_PASSWORD]

# External Data Providers
NASA_FIRMS_MAP_KEY=f1876d284ab49332f77d0ac5ea52522c
NASA_FIRMS_BASE_URL=https://firms.modaps.eosdis.nasa.gov/api
OPEN_METEO_BASE_URL=https://api.open-meteo.com/v1/forecast
OVERPASS_API_URL=https://overpass-api.de/api/interpreter

# Notification Gateways (Optional Production Keys)
SMS_PROVIDER=mock    # Change to 'twilio' in production once credentials are set
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_FROM_NUMBER=
FCM_PROVIDER=mock    # Change to 'fcm' when google-services.json is configured
```

---

## 2. Docker & Render Configuration

### Render Web Service Settings:
- **Build Command**:
  ```bash
  cd backend && pip install --no-cache-dir -r requirements.txt
  ```
- **Start Command**:
  ```bash
  cd backend && uvicorn app.main:app --host 0.0.0.0 --port $PORT
  ```
- **Health Check Path**:
  ```
  /api/v1/health/live
  ```
- **Docker Build Context** (if deploying via Dockerfile):
  - Path: `backend`
  - Dockerfile: `backend/Dockerfile`

---

## 3. Database Migration Procedure

Alembic migrations run asynchronously via Python:
```bash
cd backend
python -m alembic upgrade head
```
*Note*: `app/main.py` lifespan calls `init_db()` which automatically creates missing tables and falls back to local SQLite if the remote database is momentarily unreachable during cold boots.

---

## 4. Post-Deployment Verification Steps

Execute these smoke checks immediately following any deployment:

1. **Verify Liveness**:
   ```bash
   curl -f https://desater-route-planner.onrender.com/api/v1/health/live
   # Expect: {"status":"alive", ...}
   ```

2. **Verify Database and Redis Readiness**:
   ```bash
   curl -f https://desater-route-planner.onrender.com/api/v1/health/ready
   # Expect: {"status":"ok","db_status":"ok","redis_status":"ok", ...}
   ```

3. **Verify Provider Health**:
   ```bash
   curl -f https://desater-route-planner.onrender.com/api/v1/providers/status
   # Expect: List of 7 providers with live statuses and timestamps
   ```

4. **Verify TrailGuard Route Planning**:
   ```bash
   curl -X POST https://desater-route-planner.onrender.com/api/v1/routes/plan \
     -H "Content-Type: application/json" \
     -d '{"origin_lat": 32.2396, "origin_lon": 77.1887, "origin_name": "Manali", "destination_lat": 32.3166, "destination_lon": 77.1722, "destination_name": "Solang Valley", "travel_mode": "hiking"}'
   # Expect: HTTP 201 with elevation_profile, hazard score, and decision_explanation
   ```
