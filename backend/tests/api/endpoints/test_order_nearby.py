from fastapi.testclient import TestClient
from sqlmodel import Session
from app.models import Order, OrderStatus, User, UserRole
from shapely.geometry import Point
from app.core.config import settings
import uuid
import pytest
from shapely.geometry import Point
from geoalchemy2.shape import from_shape

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
        # Create referenced collector user with unique phone number
        collector = User(
            full_name="Collector",
            phone_number="01234567891",
            hashed_password="hashedpassword",
            role="USER"
        )
        session.add(collector)
        session.commit()
        session.refresh(collector)
        # Create orders
        order1 = Order(
            owner_id=test_user.id,
            pickup_address="A",
            location=from_shape(Point(106.0005, 10.0005), srid=4326),
            status=OrderStatus.PENDING
        )
        order2 = Order(
            owner_id=test_user.id,
            pickup_address="B",
            location=from_shape(Point(107, 11), srid=4326),
            status=OrderStatus.PENDING
        )
        order3 = Order(
            owner_id=test_user.id,
            pickup_address="C",
            location=from_shape(Point(106.001, 10.001), srid=4326),
            status=OrderStatus.ACCEPTED,
            collector_id=collector.id
        )
        session.add_all([order1, order2, order3])
        session.commit()
        # Define center coordinates for nearby search
        center_lat = 10.0005
        center_lng = 106.0005
        resp = collector_client_nearby.get(f"{settings.API_STR}/orders/nearby", params={"lat": center_lat, "lng": center_lng, "radius_km": 5})
        assert resp.status_code == 200, resp.text
        data = resp.json()
        ids = [d["id"] for d in data]
        assert str(order1.id) in ids
        assert str(order2.id) not in ids
        assert str(order3.id) not in ids
        assert all("distance_km" in d for d in data)
        # Check location field is GeoJSON and correct for order1
        order1_resp = next(d for d in data if d["id"] == str(order1.id))
        assert order1_resp["location"]["type"] == "Point"
        assert order1_resp["location"]["coordinates"] == [106.0005, 10.0005]

    def test_nearby_requires_collector(self, authenticated_client: TestClient, session: Session, test_user):
        o = Order(owner_id=test_user.id, pickup_address="A", location=from_shape(Point(106.0, 10.0), srid=4326), status=OrderStatus.PENDING, collector_id=None)
        session.add(o); session.commit()
        resp = authenticated_client.get(f"{settings.API_STR}/orders/nearby", params={"lat": 10.0, "lng": 106.0, "radius_km": 5})
        assert resp.status_code == 403
