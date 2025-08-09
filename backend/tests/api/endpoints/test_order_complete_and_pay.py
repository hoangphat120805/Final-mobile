import uuid
from fastapi.testclient import TestClient
from sqlmodel import Session
from app.models import TransactionMethod, Order, OrderItem, OrderStatus
from app.core.config import settings


import pytest
from app.models import UserRole

@pytest.fixture
def collector_user(session: Session):
    from app.models import User
    import uuid
    user = User(
        id=uuid.uuid4(),
        full_name="Collector User",
        phone_number="0999999999",
        hashed_password="$2b$12$testhashforcollector",
        role=UserRole.COLLECTOR
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return user


# Create a token for collector_user
@pytest.fixture
def collector_token(collector_user):
    from app.core.security import create_access_token
    from app.core.config import settings
    from datetime import timedelta
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return create_access_token(
        subject=str(collector_user.id),
        expires_delta=access_token_expires
    )

# Collector client uses collector_user and its token
@pytest.fixture
def collector_client(client: TestClient, collector_user, collector_token):
    from app.api.deps import get_current_user
    def get_current_user_override():
        return collector_user
    client.app.dependency_overrides[get_current_user] = get_current_user_override
    client.headers.update({"Authorization": f"Bearer {collector_token}"})
    return client

class TestOrderCompleteAndPay:
    def test_complete_order_and_pay_success(self, collector_client, session: Session, collector_user, test_user):
        # Arrange: create order and order item for the test user
        # Owner is the scrap provider (test_user), collector_user is assigned as collector
        order = Order(
            owner_id=test_user.id,
            collector_id=collector_user.id,
            pickup_address="123 Test St",
            pickup_latitude=10.0,
            pickup_longitude=106.0,
            status=OrderStatus.ACCEPTED
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        order_item = OrderItem(
            order_id=order.id,
            category_id=uuid.uuid4(),
            quantity=2.0,
            price_per_unit=10.0
        )
        session.add(order_item)
        session.commit()
        session.refresh(order_item)

        payload = {
            "payment_method": TransactionMethod.CASH,
            "items": [
                {
                    "order_item_id": str(order_item.id),
                    "actual_quantity": 2.0
                }
            ]
        }

        # Act
        response = collector_client.post(f"{settings.API_STR}/orders/{order.id}/complete", json=payload)

        # Assert
        assert response.status_code == 201
        data = response.json()
        assert data["order_id"] == str(order.id)
        assert data["status"] in ("successful", "pending")
        assert "payer" in data and "payee" in data

    def test_complete_order_and_pay_unauthorized(self, client: TestClient, session: Session, test_user, collector_user):
        # Arrange: create order and order item for the test user
        # Create order owned by test_user with assigned collector (collector_user) but unauthenticated client performs call
        order = Order(
            owner_id=test_user.id,
            collector_id=collector_user.id,
            pickup_address="123 Test St",
            pickup_latitude=10.0,
            pickup_longitude=106.0,
            status=OrderStatus.ACCEPTED  # ensure passes service precondition
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        order_item = OrderItem(
            order_id=order.id,
            category_id=uuid.uuid4(),
            quantity=2.0,
            price_per_unit=10.0
        )
        session.add(order_item)
        session.commit()
        session.refresh(order_item)

        payload = {
            "payment_method": TransactionMethod.CASH,
            "items": [
                {
                    "order_item_id": str(order_item.id),
                    "actual_quantity": 2.0
                }
            ]
        }

        # Act
        response = client.post(f"{settings.API_STR}/orders/{order.id}/complete", json=payload)

        # Assert
        assert response.status_code in (401, 403)

class TestOrderAccept:
    def test_accept_order_success(self, collector_client: TestClient, session: Session, test_user, collector_user):
        # Create pending order (no collector assigned yet)
        order = Order(
            owner_id=test_user.id,
            pickup_address="Addr",
            pickup_latitude=1.0,
            pickup_longitude=2.0,
            status=OrderStatus.PENDING,
            collector_id=None
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        resp = collector_client.post(f"{settings.API_STR}/orders/{order.id}/accept", json={"note": "on the way"})
        assert resp.status_code == 200, resp.text
        data = resp.json()
        assert data["id"] == str(order.id)
        assert data["status"] == OrderStatus.ACCEPTED
        assert data["collector_id"] == str(collector_user.id)

    def test_accept_order_unauthenticated(self, client: TestClient, session: Session, test_user):
        order = Order(
            owner_id=test_user.id,
            pickup_address="Addr",
            pickup_latitude=1.0,
            pickup_longitude=2.0,
            status=OrderStatus.PENDING,
            collector_id=None
        )
        session.add(order)
        session.commit()
        session.refresh(order)
        resp = client.post(f"{settings.API_STR}/orders/{order.id}/accept", json={"note": None})
        assert resp.status_code in (401, 403)

    def test_accept_order_wrong_status(self, collector_client: TestClient, session: Session, test_user, collector_user):
        # Already accepted order cannot be accepted again (status not PENDING)
        order = Order(
            owner_id=test_user.id,
            pickup_address="Addr",
            pickup_latitude=1.0,
            pickup_longitude=2.0,
            status=OrderStatus.ACCEPTED,
            collector_id=collector_user.id
        )
        session.add(order)
        session.commit()
        session.refresh(order)
        resp = collector_client.post(f"{settings.API_STR}/orders/{order.id}/accept", json={"note": "retry"})
        assert resp.status_code == 400

    def test_accept_order_already_assigned_other(self, collector_client: TestClient, session: Session, test_user, collector_user):
        # Simulate different collector already assigned (create a second collector)
        from app.models import User, UserRole
        other_collector = User(
            full_name="Other",
            phone_number="0888888888",
            hashed_password="hash",
            role=UserRole.COLLECTOR
        )
        session.add(other_collector)
        session.commit()
        session.refresh(other_collector)

        order = Order(
            owner_id=test_user.id,
            pickup_address="Addr",
            pickup_latitude=1.0,
            pickup_longitude=2.0,
            status=OrderStatus.PENDING,
            collector_id=other_collector.id
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        resp = collector_client.post(f"{settings.API_STR}/orders/{order.id}/accept", json={"note": None})
        assert resp.status_code in (400, 409)
