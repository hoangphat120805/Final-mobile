"""Test utilities and helper functions."""

import uuid
from typing import Dict, Any
from app.models import User, UserRole
from app.core.security import get_password_hash, create_access_token
from app.core.config import settings
from datetime import timedelta


def create_test_user_data() -> Dict[str, Any]:
    """Create test user data."""
    return {
        "full_name": "Test User",
        "phone_number": "0123456789",
        "password": "testpassword",
        "role": UserRole.USER
    }


def create_user_in_db(session, **kwargs) -> User:
    """Create a user in the database."""
    user_data = create_test_user_data()
    user_data.update(kwargs)
    
    user = User(
        id=user_data.get("id", uuid.uuid4()),
        full_name=user_data["full_name"],
        phone_number=user_data["phone_number"],
        hashed_password=get_password_hash(user_data["password"]),
        role=user_data["role"]
    )
    
    session.add(user)
    session.commit()
    session.refresh(user)
    return user


def create_access_token_for_user(user: User) -> str:
    """Create an access token for a user."""
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return create_access_token(
        subject=str(user.id),
        expires_delta=access_token_expires
    )


def get_auth_headers(token: str) -> Dict[str, str]:
    """Get authorization headers with token."""
    return {"Authorization": f"Bearer {token}"}


class AssertionHelpers:
    """Helper class for common test assertions."""
    
    @staticmethod
    def assert_user_response(response_data: Dict[str, Any], expected_user: User):
        """Assert user response data matches expected user."""
        assert response_data["id"] == str(expected_user.id)
        assert response_data["username"] == expected_user.full_name
        assert response_data["phone"] == expected_user.phone_number
        # Ensure sensitive data is not exposed
        assert "password" not in response_data
        assert "hashed_password" not in response_data
    
    @staticmethod
    def assert_error_response(response_data: Dict[str, Any], expected_detail: str):
        """Assert error response contains expected detail."""
        assert "detail" in response_data
        assert expected_detail in response_data["detail"]
    
    @staticmethod
    def assert_success_message(response_data: Dict[str, Any], expected_message: str):
        """Assert success response contains expected message."""
        assert response_data["message"] == expected_message


class MockData:
    """Mock data for testing."""
    
    VALID_UPDATE_DATA = {
        "phone_number": "0111111111",
        "email": "updated@example.com"
    }
    
    VALID_PASSWORD_DATA = {
        "old_password": "testpassword",
        "new_password": "newpassword123"
    }
    
    INVALID_EMAIL_FORMATS = [
        "invalid-email",
        "@domain.com",
        "user@",
        "user@domain",
        "",
    ]
    
    INVALID_PHONE_NUMBERS = [
        "123",  # Too short
        "abcdefghij",  # Non-numeric
        "",  # Empty
        " ",  # Whitespace only
        "+" * 100,  # Too long
    ]
    
    WEAK_PASSWORDS = [
        "",  # Empty
        "123",  # Too short
        " ",  # Whitespace
        "a",  # Single character
    ]
