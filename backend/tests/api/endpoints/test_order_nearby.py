from fastapi.testclient import TestClient
from sqlmodel import Session
from app.models import Order, OrderStatus, User, UserRole
from app.core.config import settings
import uuid
import pytest

@pytest.fixture
def collector_user_nearby(session: Session):
    user = User(
        full_name="Collector Nearby",
        phone_number="0777777777",
        hashed_password="hash",
        role=UserRole.COLLECTOR
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return user

@pytest.fixture
def collector_client_nearby(client: TestClient, collector_user_nearby):
    from app.api.deps import get_current_user
    from app.core.security import create_access_token
    from datetime import timedelta
    from app.core.config import settings as cfg
    token = create_access_token(subject=str(collector_user_nearby.id), expires_delta=timedelta(minutes=cfg.ACCESS_TOKEN_EXPIRE_MINUTES))
    def override():
        return collector_user_nearby
    client.app.dependency_overrides[get_current_user] = override
    client.headers.update({"Authorization": f"Bearer {token}"})
    return client

class TestNearbyOrders:
    def test_nearby_orders_success(self, collector_client_nearby: TestClient, session: Session, test_user):
        center_lat = 10.0
        center_lng = 106.0
        o1 = Order(owner_id=test_user.id, pickup_address="A", pickup_latitude=10.0005, pickup_longitude=106.0005, status=OrderStatus.PENDING, collector_id=None)
        o2 = Order(owner_id=test_user.id, pickup_address="B", pickup_latitude=11.0, pickup_longitude=107.0, status=OrderStatus.PENDING, collector_id=None)
        o3 = Order(owner_id=test_user.id, pickup_address="C", pickup_latitude=10.001, pickup_longitude=106.001, status=OrderStatus.ACCEPTED, collector_id=uuid.uuid4())
        session.add(o1); session.add(o2); session.add(o3)
        session.commit(); session.refresh(o1)
        resp = collector_client_nearby.get(f"{settings.API_STR}/orders/nearby", params={"lat": center_lat, "lng": center_lng, "radius_km": 5})
        assert resp.status_code == 200, resp.text
        data = resp.json()
        ids = [d["id"] for d in data]
        assert str(o1.id) in ids
        assert str(o2.id) not in ids
        assert str(o3.id) not in ids
        assert all("distance_km" in d for d in data)

    def test_nearby_requires_collector(self, authenticated_client: TestClient, session: Session, test_user):
        o = Order(owner_id=test_user.id, pickup_address="A", pickup_latitude=10.0, pickup_longitude=106.0, status=OrderStatus.PENDING, collector_id=None)
        session.add(o); session.commit()
        resp = authenticated_client.get(f"{settings.API_STR}/orders/nearby", params={"lat": 10.0, "lng": 106.0, "radius_km": 5})
        assert resp.status_code == 403
