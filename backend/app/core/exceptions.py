"""Custom application exceptions."""
from typing import Any, Optional


class AppException(Exception):
    """Base application exception."""

    def __init__(self, message: str, status_code: int = 400, details: Optional[Any] = None):
        self.message = message
        self.status_code = status_code
        self.details = details
        super().__init__(message)


class NotFoundException(AppException):
    def __init__(self, message: str = "Resource not found"):
        super().__init__(message=message, status_code=404)


class UnauthorizedException(AppException):
    def __init__(self, message: str = "Authentication credentials required"):
        super().__init__(message=message, status_code=401)


class ForbiddenException(AppException):
    def __init__(self, message: str = "Insufficient permissions"):
        super().__init__(message=message, status_code=403)


class RateLimitExceededException(AppException):
    def __init__(self, message: str = "Rate limit exceeded. Please try again later."):
        super().__init__(message=message, status_code=429)


class UpstreamServiceException(AppException):
    def __init__(self, message: str = "External upstream service failure", details: Optional[Any] = None):
        super().__init__(message=message, status_code=502, details=details)

