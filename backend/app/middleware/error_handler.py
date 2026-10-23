"""Global structured error handling middleware."""
import logging
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from pydantic import ValidationError
from app.core.exceptions import AppException

logger = logging.getLogger(__name__)


class ErrorHandlingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        try:
            return await call_next(request)
        except AppException as exc:
            logger.warning(f"Application error: {exc.message} (status: {exc.status_code})")
            return JSONResponse(
                status_code=exc.status_code,
                content={
                    "error": exc.__class__.__name__,
                    "message": exc.message,
                    "details": exc.details,
                },
            )
        except ValidationError as exc:
            logger.warning(f"Schema validation error: {exc.errors()}")
            return JSONResponse(
                status_code=422,
                content={
                    "error": "VALIDATION_ERROR",
                    "message": "Input data failed schema validation",
                    "details": exc.errors(),
                },
            )
        except Exception as exc:
            logger.exception(f"Unhandled server error: {exc}")
            return JSONResponse(
                status_code=500,
                content={
                    "error": "INTERNAL_SERVER_ERROR",
                    "message": "An unexpected internal server error occurred.",
                },
            )

