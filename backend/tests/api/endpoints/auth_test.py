from fastapi.testclient import TestClient
from app.models import User
from app.core.config import settings

class TestAuthEndpoints:
    """Test suite for authentication endpoints."""

    def test_login_success(self, client: TestClient, test_user: User):
        """Test successful login."""
        login_data = {
            "phone_number": test_user.phone_number,
            "password": "testpassword"
        }

        response = client.post(f"{settings.API_STR}/auth/login", json=login_data)

        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    def test_login_invalid_credentials(self, client: TestClient):
        """Test login with invalid credentials."""
        login_data = {
            "phone_number": "nonexistent",
            "password": "wrongpassword"
        }

        response = client.post(f"{settings.API_STR}/auth/login", json=login_data)

        assert response.status_code == 401
        assert "detail" in response.json()

    def test_signup_success(self, client: TestClient):
        """Test successful user signup."""
        signup_data = {
            "phone_number": "0987654321",
            "password": "hdhoH!@##212"
        }

        response = client.post(f"{settings.API_STR}/auth/signup", json=signup_data)

        assert response.status_code == 201
        assert response.json()["phone_number"] == signup_data["phone_number"]

    def test_signup_phone_conflict(self, client: TestClient, test_user: User):
        """Test signup with existing phone number."""
        signup_data = {
            "full_name": "Another User",
            "phone_number": test_user.phone_number,
            "password": "anotherpas@##3Sword"
        }

        response = client.post(f"{settings.API_STR}/auth/signup", json=signup_data)

        assert response.status_code == 400
        assert "Phone number already exists" in response.json()["detail"]

    def test_keep_alive(self, client: TestClient):
        """Test keep-alive endpoint."""
        response = client.get(f"{settings.API_STR}/auth/keep-alive")

        assert response.status_code == 200
        assert response.json() == {"message": "I'm alive!"}

    def test_signin_access_token(self, client: TestClient, test_user: User):
        """Test access token signin."""
        form_data = {
            "username": test_user.phone_number,
            "password": "testpassword"
        }

        response = client.post(f"{settings.API_STR}/auth/login/access-token", data=form_data)

        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"
    
    def test_signin_access_token_invalid_credentials(self, client: TestClient):
        """Test access token signin with invalid credentials."""
        form_data = {
            "username": "nonexistent",
            "password": "wrongpassword"
        }

        response = client.post(f"{settings.API_STR}/auth/login/access-token", data=form_data)

        assert response.status_code == 401
        assert "detail" in response.json()

