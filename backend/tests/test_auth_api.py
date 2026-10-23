"""Integration tests for Authentication API endpoints (/api/v1/auth)."""
import uuid
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_register_and_login_flow():
    """Verify registration, duplicate rejection, and login with issued credentials."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        unique_id = uuid.uuid4().hex[:8]
        test_email = f"trekker_{unique_id}@example.com"
        test_password = "SecurePassword456"

        # 1. Register new account
        register_resp = await client.post(
            "/api/v1/auth/register",
            json={"email": test_email, "password": test_password},
        )
        assert register_resp.status_code == 201
        data = register_resp.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"
        assert data["role"] == "tourist"
        assert data["email"] == test_email
        token = data["access_token"]

        # 2. Duplicate registration should return 400
        duplicate_resp = await client.post(
            "/api/v1/auth/register",
            json={"email": test_email, "password": "AnotherPassword"},
        )
        assert duplicate_resp.status_code == 400
        assert "already exists" in duplicate_resp.json()["detail"]

        # 3. Successful login
        login_resp = await client.post(
            "/api/v1/auth/login",
            json={"email": test_email, "password": test_password},
        )
        assert login_resp.status_code == 200
        login_data = login_resp.json()
        assert "access_token" in login_data
        assert login_data["role"] == "tourist"

        # 4. Failed login with wrong password
        bad_login_resp = await client.post(
            "/api/v1/auth/login",
            json={"email": test_email, "password": "WrongPassword"},
        )
        assert bad_login_resp.status_code == 401

        # 5. Access /me endpoint with Bearer token
        me_resp = await client.get(
            "/api/v1/auth/me",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert me_resp.status_code == 200
        me_data = me_resp.json()
        assert me_data["email"] == test_email
        assert me_data["role"] == "tourist"
        assert me_data["is_active"] is True
