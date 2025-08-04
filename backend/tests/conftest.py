import os
import uuid
from typing import Generator

import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session, SQLModel, create_engine
from sqlmodel.pool import StaticPool

from app.main import app
from app.api.deps import get_db, get_current_user
from app.models import User, UserRole, ScrapCategory
from app.core.security import get_password_hash, create_access_token
from app.core.config import settings
from datetime import timedelta


# Test database URL using in-memory SQLite
TEST_DATABASE_URL = "sqlite:///:memory:"

# Create test engine
engine = create_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)


@pytest.fixture(scope="function")
def session() -> Generator[Session, None, None]:
    """Create a test database session."""
    SQLModel.metadata.create_all(engine)
    with Session(engine) as session:
        yield session
    SQLModel.metadata.drop_all(engine)


@pytest.fixture(scope="function")
def client(session: Session) -> Generator[TestClient, None, None]:
    """Create a test client."""
    def get_session_override():
        return session

    app.dependency_overrides[get_db] = get_session_override
    
    with TestClient(app) as c:
        yield c
    
    app.dependency_overrides.clear()


@pytest.fixture
def test_user(session: Session) -> User:
    """Create a test user."""
    user = User(
        id=uuid.uuid4(),
        full_name="Test User",
        phone_number="0123456789",
        hashed_password=get_password_hash("testpassword"),
        role=UserRole.USER
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return user


@pytest.fixture
def test_user_token(test_user: User) -> str:
    """Create an access token for test user."""
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return create_access_token(
        subject=str(test_user.id),
        expires_delta=access_token_expires
    )


@pytest.fixture
def authenticated_client(client: TestClient, test_user: User, test_user_token: str, session: Session) -> TestClient:
    """Create an authenticated test client."""
    def get_current_user_override():
        return test_user

    app.dependency_overrides[get_current_user] = get_current_user_override
    
    # Set authorization header
    client.headers.update({"Authorization": f"Bearer {test_user_token}"})
    
    return client


@pytest.fixture
def another_test_user(session: Session) -> User:
    """Create another test user for conflict testing."""
    user = User(
        id=uuid.uuid4(),
        full_name="Another User",
        phone_number="0987654321",
        hashed_password=get_password_hash("anotherpassword"),
        role=UserRole.USER
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return user

@pytest.fixture
def test_scrap_category(session: Session) -> list[ScrapCategory]:
    """Create a test scrap category."""
    category = ScrapCategory(
        id=uuid.uuid4(),
        name="Test Category",
        slug="test-category",
        description="A category for testing purposes",
        unit="kg",
        estimated_price_per_unit=10.0,
        icon_url=None,
        created_by=uuid.uuid4(),
        last_updated_by=uuid.uuid4()
    )
    session.add(category)
    session.commit()
    session.refresh(category)
    return [category]