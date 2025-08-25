from fastapi.testclient import TestClient
from app.models import User, Order
from shapely.geometry import Point, mapping
from app.core.config import settings
from sqlalchemy.orm import Session

class TestOrderEndpoints:
    """Test suite for order endpoints."""

    def test_create_order(self, authenticated_client: TestClient, session: Session, test_user: User) -> None:
        point = Point(98.765432, 12.345678)
        # Send location as GeoJSON
        order_data = {
            "pickup_address": "123 Test St",
            "location": mapping(point),
        }
        response = authenticated_client.post(f"{settings.API_STR}/orders/", json=order_data)
        assert response.status_code == 201
        data = response.json()
        assert data["owner_id"] == str(test_user.id)
        # Verify the location was saved and returned correctly
        assert data["location"]["type"] == "Point"
        assert data["location"]["coordinates"] == [98.765432, 12.345678]

