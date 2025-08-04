import uuid
import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session

from app.models import User
from app.core.security import get_password_hash
from app.core.config import settings


class TestUserEndpoints:
    """Test suite for user endpoints."""

    def test_get_me_success(self, authenticated_client: TestClient, test_user: User):
        """Test successful retrieval of current user."""
        response = authenticated_client.get(f"{settings.API_STR}/user/me")

        assert response.status_code == 200
        data = response.json()
        assert data["id"] == str(test_user.id)
        assert data["phone_number"] == test_user.phone_number
        assert data["full_name"] == test_user.full_name

    def test_get_me_unauthorized(self, client: TestClient):
        """Test get current user without authentication."""
        response = client.get(f"{settings.API_STR}/user/me")

        assert response.status_code == 401

    def test_update_me_success(self, authenticated_client: TestClient, test_user: User):
        """Test successful user profile update."""
        update_data = {
            "phone_number": "0111111111",
            "email": "test@example.com"
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)

        assert response.status_code == 200
        data = response.json()
        assert data["phone_number"] == "0111111111"

    def test_update_me_phone_conflict(self, authenticated_client: TestClient, another_test_user: User):
        """Test user update with existing phone number."""
        update_data = {
            "phone_number": another_test_user.phone_number
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)

        assert response.status_code == 400
        assert "Phone number already exists" in response.json()["detail"]

    def test_update_me_unauthorized(self, client: TestClient):
        """Test user update without authentication."""
        update_data = {
            "phone_number": "0111111111"
        }

        response = client.patch(f"{settings.API_STR}/user/me", json=update_data)

        assert response.status_code == 401

    def test_delete_me_success(self, authenticated_client: TestClient, session: Session, test_user: User):
        """Test successful user deletion."""
        response = authenticated_client.delete(f"{settings.API_STR}/user/me")
        
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "User deleted successfully"
        
        # Verify user is actually deleted from database
        deleted_user = session.get(User, test_user.id)
        assert deleted_user is None

    def test_delete_me_unauthorized(self, client: TestClient):
        """Test user deletion without authentication."""
        response = client.delete(f"{settings.API_STR}/user/me")

        assert response.status_code == 401

    def test_update_password_success(self, authenticated_client: TestClient, test_user: User, session: Session):
        """Test successful password update."""
        password_data = {
            "old_password": "testpassword",
            "new_password": "newpassword123"
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me/password", json=password_data)

        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Password updated successfully"
        
        # Verify password was actually changed
        session.refresh(test_user)
        from app.core.security import verify_password
        assert verify_password("newpassword123", test_user.hashed_password)

    def test_update_password_wrong_old_password(self, authenticated_client: TestClient):
        """Test password update with wrong old password."""
        password_data = {
            "old_password": "wrongpassword",
            "new_password": "newpassword123"
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me/password", json=password_data)

        assert response.status_code == 400
        assert "Old password is incorrect" in response.json()["detail"]

    def test_update_password_unauthorized(self, client: TestClient):
        """Test password update without authentication."""
        password_data = {
            "old_password": "testpassword",
            "new_password": "newpassword123"
        }

        response = client.patch(f"{settings.API_STR}/user/me/password", json=password_data)

        assert response.status_code == 401

    def test_update_password_invalid_data(self, authenticated_client: TestClient):
        """Test password update with invalid data."""
        # Missing new_password
        password_data = {
            "old_password": "testpassword"
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me/password", json=password_data)

        assert response.status_code == 422  # Validation error

    def test_update_me_partial_update(self, authenticated_client: TestClient, test_user: User):
        """Test partial user update (only one field)."""
        update_data = {
            "full_name": "New Full Name"
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)

        assert response.status_code == 200
        data = response.json()
        # Phone should remain unchanged
        assert data["phone_number"] == test_user.phone_number

    def test_update_me_empty_data(self, authenticated_client: TestClient, test_user: User):
        """Test user update with empty data."""
        update_data = {}

        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)

        assert response.status_code == 200
        data = response.json()
        # Data should remain unchanged
        assert data["id"] == str(test_user.id)
        assert data["phone_number"] == test_user.phone_number
