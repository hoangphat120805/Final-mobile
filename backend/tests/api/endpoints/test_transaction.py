import uuid
import uuid
import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session
from app.models import Transaction, Order, TransactionMethod, TransactionStatus, OrderStatus
from app.core.config import settings
from shapely.geometry import Point
from geoalchemy2.shape import from_shape

class TestTransactionEndpoints:
    def test_get_transactions_by_user_success(self, authenticated_client: TestClient, session: Session, test_user, another_test_user):
        # Arrange: create order, transaction for test_user
        
        order = Order(
            owner_id=test_user.id,
            pickup_address="123 Test St",
            location=from_shape(Point(106.0, 10.0), srid=4326),
            status=OrderStatus.PENDING
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        transaction = Transaction(
            order_id=order.id,
            payer_id=test_user.id,
            payee_id=another_test_user.id,
            amount=100.0,
            method=TransactionMethod.CASH,
            status=TransactionStatus.SUCCESSFUL
        )
        session.add(transaction)
        session.commit()
        session.refresh(transaction)

        # Act
        response = authenticated_client.get(f"{settings.API_STR}/transactions/user/{test_user.id}")

        # Assert
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert any(tx["id"] == str(transaction.id) for tx in data)

    def test_get_transactions_by_user_unauthorized(self, client: TestClient, test_user):
        # Act
        response = client.get(f"{settings.API_STR}/transactions/user/{test_user.id}")
        # Assert
        assert response.status_code in (401, 403)

    def test_get_transactions_by_order_success(self, authenticated_client: TestClient, session: Session, test_user, another_test_user):
        # Arrange: create order, transaction for test_user
        order = Order(
            owner_id=test_user.id,
            pickup_address="123 Test St",
            pickup_latitude=10.0,
            pickup_longitude=106.0,
            status=OrderStatus.PENDING
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        transaction = Transaction(
            order_id=order.id,
            payer_id=test_user.id,
            payee_id=another_test_user.id,
            amount=100.0,
            method=TransactionMethod.CASH,
            status=TransactionStatus.SUCCESSFUL
        )
        session.add(transaction)
        session.commit()
        session.refresh(transaction)

        # Act
        response = authenticated_client.get(f"{settings.API_STR}/transactions/order/{order.id}")

        # Assert
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert any(tx["id"] == str(transaction.id) for tx in data)

    def test_get_transactions_by_order_unauthorized(self, client: TestClient, session: Session, test_user, another_test_user):
        # Arrange: create order, transaction for test_user
        order = Order(
            owner_id=test_user.id,
            pickup_address="123 Test St",
            pickup_latitude=10.0,
            pickup_longitude=106.0,
            status=OrderStatus.PENDING
        )
        session.add(order)
        session.commit()
        session.refresh(order)

        transaction = Transaction(
            order_id=order.id,
            payer_id=test_user.id,
            payee_id=another_test_user.id,
            amount=100.0,
            method=TransactionMethod.CASH,
            status=TransactionStatus.SUCCESSFUL
        )
        session.add(transaction)
        session.commit()
        session.refresh(transaction)

        # Act
        response = client.get(f"{settings.API_STR}/transactions/order/{order.id}")
    # Assert
        assert response.status_code in (401, 403)
