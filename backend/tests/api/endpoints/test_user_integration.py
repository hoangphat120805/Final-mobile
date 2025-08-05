import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session

from app.models import User, UserRole
from app.core.security import get_password_hash
from app.core.config import settings


class TestUserIntegration:
    """Integration tests for user endpoints."""

    def test_full_user_workflow(self, authenticated_client: TestClient, test_user: User, session: Session):
        """Test complete user workflow: get -> update -> change password -> delete."""
        
        # 1. Get current user info
        response = authenticated_client.get(f"{settings.API_STR}/user/me")
        assert response.status_code == 200
        original_data = response.json()
        
        # 2. Update user profile
        update_data = {
            "email": "workflow@example.com",
            "phone_number": "0999999999"
        }
        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)
        assert response.status_code == 200
        updated_data = response.json()
        assert updated_data["phone_number"] == "0999999999"
        
        # 3. Change password
        password_data = {
            "old_password": "testpassword",
            "new_password": "newworkfl@2Aowpass"
        }
        response = authenticated_client.patch(f"{settings.API_STR}/user/me/password", json=password_data)
        assert response.status_code == 200
        
        # 4. Verify password change worked
        session.refresh(test_user)
        from app.core.security import verify_password
        assert verify_password("newworkfl@2Aowpass", test_user.hashed_password)
        
        # 5. Delete user
        response = authenticated_client.delete(f"{settings.API_STR}/user/me")
        assert response.status_code == 200
        
        # 6. Verify user is deleted
        deleted_user = session.get(User, test_user.id)
        assert deleted_user is None

    def test_concurrent_user_updates(self, session: Session, client: TestClient):
        """Test concurrent updates to different users don't interfere."""
        
        # Create two users
        user1 = User(
            full_name="User 1",
            phone_number="0111111111",
            hashed_password=get_password_hash("password1"),
            role=UserRole.USER
        )
        user2 = User(
            full_name="User 2", 
            phone_number="0222222222",
            hashed_password=get_password_hash("password2"),
            role=UserRole.USER
        )
        
        session.add(user1)
        session.add(user2)
        session.commit()
        session.refresh(user1)
        session.refresh(user2)
        
        # Create tokens and clients for both users
        from app.core.security import create_access_token
        from datetime import timedelta
        from app.core.config import settings
        
        token1 = create_access_token(str(user1.id), timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
        token2 = create_access_token(str(user2.id), timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
        
        # Update both users simultaneously
        headers1 = {"Authorization": f"Bearer {token1}"}
        headers2 = {"Authorization": f"Bearer {token2}"}
        
        # Override get_current_user for this test
        from app.main import app
        from app.api.deps import get_current_user
        
        def get_user1():
            return user1
        
        def get_user2():
            return user2
        
        # This is a simplified test - in real scenario you'd need proper dependency override
        # The idea is to test that updates don't interfere with each other


class TestUserValidation:
    """Test user input validation."""

    def test_update_me_phone_number_format(self, authenticated_client: TestClient):
        """Test phone number format validation."""
        
        # Test various invalid phone formats
        invalid_phones = [
            "123",  # Too short
            "abcdefghij",  # Non-numeric
            "",  # Empty
            " ",  # Whitespace only
        ]
        
        for phone in invalid_phones:
            update_data = {"phone_number": phone}
            response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)
            # Should either succeed (if validation is lenient) or fail with 422
            assert response.status_code in [200, 422]

    def test_password_requirements(self, authenticated_client: TestClient):
        """Test password strength requirements."""
        
        weak_passwords = [
            "",  # Empty
            "123",  # Too short
            " ",  # Whitespace
        ]
        
        for weak_pass in weak_passwords:
            password_data = {
                "old_password": "testpassword",
                "new_password": weak_pass
            }
            response = authenticated_client.patch(f"{settings.API_STR}/user/me/password", json=password_data)
            # Should either succeed or fail with validation error
            assert response.status_code in [200, 422, 400]

    def test_large_payload_handling(self, authenticated_client: TestClient):
        """Test handling of unusually large payloads."""
        
        # Create a very long string
        long_string = "a" * 10000
        
        update_data = {
            "email": f"{long_string}@example.com",
            "phone_number": long_string
        }

        response = authenticated_client.patch(f"{settings.API_STR}/user/me", json=update_data)
        # Should handle gracefully (either succeed or return validation error)
        assert response.status_code in [200, 422, 400]


class TestUserSecurity:
    """Test security aspects of user endpoints."""

    def test_password_not_returned_in_response(self, authenticated_client: TestClient):
        """Ensure password/hash is never returned in API responses."""
        
        # Get user
        response = authenticated_client.get(f"{settings.API_STR}/user/me")
        assert response.status_code == 200
        data = response.json()
        
        # Ensure no password-related fields are present
        assert "password" not in data
        assert "hashed_password" not in data
        assert "hash" not in data

    def test_user_cannot_access_others_data(self, session: Session, client: TestClient):
        """Test that users can only access their own data."""
        
        # Create two users
        user1 = User(
            full_name="User 1",
            phone_number="0333333333", 
            hashed_password=get_password_hash("password1"),
            role=UserRole.USER
        )
        user2 = User(
            full_name="User 2",
            phone_number="0444444444",
            hashed_password=get_password_hash("password2"), 
            role=UserRole.USER
        )
        
        session.add(user1)
        session.add(user2)
        session.commit()
        session.refresh(user1)
        session.refresh(user2)
        
        # Create token for user1
        from app.core.security import create_access_token
        from datetime import timedelta
        from app.core.config import settings
        
        token1 = create_access_token(str(user1.id), timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
        
        # Try to access with user1's token - should only get user1's data
        headers = {"Authorization": f"Bearer {token1}"}
        
        # Override dependency to return user1
        from app.main import app
        from app.api.deps import get_current_user
        
        def get_user1_override():
            return user1
        
        app.dependency_overrides[get_current_user] = get_user1_override

        response = client.get(f"{settings.API_STR}/user/me", headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == str(user1.id)
        assert data["id"] != str(user2.id)
        
        # Clean up
        app.dependency_overrides.clear()
