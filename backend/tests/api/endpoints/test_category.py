from fastapi.testclient import TestClient

from app.models import User
from app.core.config import settings
from app.models import ScrapCategory

class TestCategoryEndpoints:
    def test_get_categories(self, client: TestClient, test_scrap_category: list[ScrapCategory]) -> None:
        response = client.get(f"{settings.API_STR}/category/")
        assert response.status_code == 200
        assert isinstance(response.json(), list)
        assert len(response.json()) == len(test_scrap_category)

    def test_create_category(self, authenticated_client: TestClient, test_user: User) -> None:
        category_data = {
            "name": "Test Category",
            "slug": "test-category",
            "description": "A category for testing purposes",
            "unit": "kg",
            "icon_url": None,
            "estimated_price_per_unit": 10.0
        }

        response = authenticated_client.post(f"{settings.API_STR}/category/", json=category_data)
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == category_data["name"]
        assert data["slug"] == category_data["slug"]
        assert data["created_by"] == str(test_user.id)
    
    def test_create_category_conflict(self, authenticated_client: TestClient, test_scrap_category: list[ScrapCategory]) -> None:
        existing_category = test_scrap_category[0]
        category_data = {
            "name": "Existing Category",
            "slug": existing_category.slug,
            "description": "This slug already exists",
            "unit": "kg",
            "icon_url": None,
            "estimated_price_per_unit": 10.0
        }

        response = authenticated_client.post(f"{settings.API_STR}/category/", json=category_data)
        assert response.status_code == 400
        assert "Category with this slug already exists" in response.json()["detail"]

    def test_update_category(self, authenticated_client: TestClient, test_scrap_category: list[ScrapCategory]) -> None:
        category_slug = test_scrap_category[0].slug
        update_data = {
            "name": "Updated Category",
            "slug": "updated-category",
            "description": "Updated description",
            "unit": "kg",
            "icon_url": None,
            "estimated_price_per_unit": 15.0
        }

        response = authenticated_client.patch(f"{settings.API_STR}/category/{category_slug}", json=update_data)
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == update_data["name"]
        assert data["slug"] == update_data["slug"]
        assert data["estimated_price_per_unit"] == update_data["estimated_price_per_unit"]
    
    def test_update_category_not_found(self, authenticated_client: TestClient) -> None:
        update_data = {
            "name": "Nonexistent Category",
            "slug": "nonexistent-category",
            "description": "This category does not exist",
            "unit": "kg",
            "icon_url": None,
            "estimated_price_per_unit": 10.0
        }

        response = authenticated_client.patch(f"{settings.API_STR}/category/nonexistent-category", json=update_data)
        assert response.status_code == 404
        assert response.json() == {"detail": "Category not found"}

    def test_delete_category(self, authenticated_client: TestClient, test_scrap_category: list[ScrapCategory]) -> None:
        category_slug = test_scrap_category[0].slug

        response = authenticated_client.delete(f"{settings.API_STR}/category/{category_slug}")
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Category deleted successfully"
    
    def test_delete_category_not_found(self, authenticated_client: TestClient) -> None:
        response = authenticated_client.delete(f"{settings.API_STR}/category/nonexistent-category")
        assert response.status_code == 404
        assert response.json() == {"detail": "Category not found"}

    def test_get_category_by_slug(self, client: TestClient, test_scrap_category: list[ScrapCategory]) -> None:
        category_slug = test_scrap_category[0].slug

        response = client.get(f"{settings.API_STR}/category/{category_slug}")
        assert response.status_code == 200
        data = response.json()
        assert data["slug"] == category_slug
        assert data["name"] == test_scrap_category[0].name

    def test_get_category_by_slug_not_found(self, client: TestClient) -> None:
        response = client.get(f"{settings.API_STR}/category/nonexistent-category")
        assert response.status_code == 404
        assert response.json() == {"detail": "Category not found"}
