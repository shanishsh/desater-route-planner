"""Tests for auth schemas and email validation."""
import pytest
from pydantic import ValidationError
from app.schemas.auth import UserLogin, UserResponse, Token


def test_user_login_valid_email():
    """Verify UserLogin schema accepts valid email."""
    login = UserLogin(email="tourist.safety@example.com", password="securepassword123")
    assert login.email == "tourist.safety@example.com"
    assert login.password == "securepassword123"


def test_user_login_invalid_email_raises_validation_error():
    """Verify UserLogin schema rejects malformed email strings."""
    with pytest.raises(ValidationError):
        UserLogin(email="not-an-email-address", password="securepassword123")


def test_token_schema():
    """Verify Token schema fields."""
    token = Token(access_token="sample-jwt-token", token_type="bearer", role="tourist", expires_in_minutes=60)
    assert token.access_token == "sample-jwt-token"
    assert token.role == "tourist"

