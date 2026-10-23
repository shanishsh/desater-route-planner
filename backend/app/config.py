"""Application configuration settings."""
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field, field_validator


class Settings(BaseSettings):
    ENVIRONMENT: str = "development"
    LOG_LEVEL: str = "INFO"

    # API Configuration
    API_HOST: str = "0.0.0.0"
    API_PORT: int = 8000
    API_V1_PREFIX: str = "/api/v1"
    PROJECT_NAME: str = "Tourist Safety & Assistance Platform"
    CORS_ORIGINS: List[str] = [
        "http://localhost:3000",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
        "http://10.0.2.2:8000",  # Android emulator host IP
        "https://desater-route-planner.onrender.com",
    ]

    # Database
    DATABASE_URL: str = Field(
        default="postgresql+asyncpg://postgres.whoagpvxrfihaukhijoo:QJZ3ehbYhNe60TFK@aws-0-ap-south-1.pooler.supabase.com:5432/postgres",
        description="Async PostgreSQL database URL. Can be overridden with sqlite+aiosqlite for testing.",
    )
    DATABASE_ECHO: bool = False

    @field_validator("DATABASE_URL", mode="before")
    @classmethod
    def assemble_db_url(cls, v: str) -> str:
        if isinstance(v, str):
            if v.startswith("postgres://"):
                return v.replace("postgres://", "postgresql+asyncpg://", 1)
            elif v.startswith("postgresql://") and not v.startswith("postgresql+asyncpg://"):
                return v.replace("postgresql://", "postgresql+asyncpg://", 1)
        return v

    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_TIMEOUT_SECONDS: float = 2.0

    # Security
    SECRET_KEY: str = "tourist-safety-platform-secure-token-key-for-jwt-signing-minimum-32"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    ADMIN_EMAIL: str = "admin@touristsafety.local"
    ADMIN_PASSWORD: str = "Admin@Safety2026!"

    # Rate Limiting
    RATE_LIMIT_PER_MINUTE: int = 60
    WEATHER_RATE_LIMIT_PER_MINUTE: int = 20

    # External APIs
    MAP_STYLE_URL: str = "https://tiles.openfreemap.org/styles/liberty"
    OPEN_METEO_BASE_URL: str = "https://api.open-meteo.com/v1/forecast"
    WEATHER_CACHE_TTL_SECONDS: int = 900  # 15 minutes
    MAP_POINTS_CACHE_TTL_SECONDS: int = 3600  # 1 hour
    ITINERARY_CACHE_TTL_SECONDS: int = 7200  # 2 hours

    # Environmental & Hazard Providers
    NASA_FIRMS_MAP_KEY: str = ""
    NASA_FIRMS_BASE_URL: str = "https://firms.modaps.eosdis.nasa.gov/api"
    OVERPASS_API_URL: str = "https://overpass-api.de/api/interpreter"
    OPENCELLID_API_KEY: str = ""
    OPENCELLID_BASE_URL: str = "https://opencellid.org/cell/get"
    FOREST_CACHE_TTL_SECONDS: int = 86400  # 24 hours
    FIRE_CACHE_TTL_SECONDS: int = 1800  # 30 minutes
    PROVIDER_STATUS_CACHE_TTL_SECONDS: int = 60

    # Alert Freshness
    DEFAULT_ALERT_FRESHNESS_SECONDS: int = 3600  # 1 hour

    # Disaster Alert & Notification Module
    ADMIN_API_KEY: str = "dev-disaster-admin-key-2026"
    SMS_PROVIDER: str = "mock"  # "mock" or "twilio"
    TWILIO_ACCOUNT_SID: str = ""
    TWILIO_AUTH_TOKEN: str = ""
    TWILIO_FROM_NUMBER: str = ""
    MOCK_OTP_CODE: str = "123456"  # Mock OTP for development
    OTP_EXPIRE_MINUTES: int = 10
    FCM_PROVIDER: str = "mock"  # "mock" or "firebase"
    FIREBASE_CREDENTIALS_PATH: str = ""
    OUTBOX_POLL_INTERVAL_SECONDS: float = 2.0
    OUTBOX_MAX_RETRIES: int = 5

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()

