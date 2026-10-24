"""Root entrypoint to run the backend server easily."""
import sys
from pathlib import Path

# Add backend directory to sys.path
backend_dir = Path(__file__).resolve().parent / "backend"
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from app.config import settings
import uvicorn

if __name__ == "__main__":
    server_port = getattr(settings, "PORT", getattr(settings, "API_PORT", 8000))
    server_host = getattr(settings, "API_HOST", "0.0.0.0")
    is_debug = getattr(settings, "DEBUG", settings.ENVIRONMENT == "development")
    print(f"Starting {settings.PROJECT_NAME} on http://{server_host}:{server_port}")
    uvicorn.run("app.main:app", host=server_host, port=server_port, reload=is_debug)

