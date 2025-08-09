from fastapi.testclient import TestClient
from app.models import User
from app.core.config import settings

class TestOrderEndpoints:
    """Test suite for order endpoints."""

    def test_create_order(self, authenticated_client: TestClient, test_user: User) -> None:
        order_data = {
            "pickup_address": "123 Test St",
            "pickup_latitude": 12.345678,
            "pickup_longitude": 98.765432,
        }

        response = authenticated_client.post(f"{settings.API_STR}/orders/", json=order_data)
        assert response.status_code == 201
        data = response.json()
        assert data["owner_id"] == str(test_user.id)

