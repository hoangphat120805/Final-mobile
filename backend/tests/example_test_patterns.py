"""
Example: Cách viết test cho endpoint mới

File này demo cách viết test cho các endpoint khác trong project.
"""

import uuid
import pytest
from fastapi.testclient import TestClient
from sqlmodel import Session

from app.models import User, ScrapCategory
from app.core.security import get_password_hash


class TestCategoryEndpoints:
    """
    Example: Test suite cho category endpoints
    Bạn có thể copy pattern này cho các endpoint khác
    """

    def test_get_categories_success(self, authenticated_client: TestClient, session: Session):
        """Test lấy danh sách categories thành công."""
        
        # Arrange: Tạo test data
        category = ScrapCategory(
            name="Test Category",
            description="Test description", 
            unit="kg",
            estimated_price_per_unit=1000,
            created_by=uuid.uuid4(),
            last_updated_by=uuid.uuid4()
        )
        session.add(category)
        session.commit()
        
        # Act: Gọi API
        response = authenticated_client.get("/api/v1/category/categories")
        
        # Assert: Kiểm tra kết quả
        assert response.status_code == 200
        data = response.json()
        assert len(data) >= 1
        assert any(cat["name"] == "Test Category" for cat in data)

    def test_create_category_success(self, authenticated_client: TestClient, test_user: User):
        """Test tạo category thành công."""
        
        # Arrange
        category_data = {
            "name": "New Category",
            "description": "New description",
            "unit": "kg", 
            "estimated_price_per_unit": 2000
        }
        
        # Act
        response = authenticated_client.post("/api/v1/category/categories", json=category_data)
        
        # Assert
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "New Category"
        assert data["estimated_price_per_unit"] == 2000

    def test_create_category_unauthorized(self, client: TestClient):
        """Test tạo category khi chưa login."""
        
        category_data = {
            "name": "Unauthorized Category", 
            "unit": "kg",
            "estimated_price_per_unit": 1000
        }
        
        response = client.post("/api/v1/category/categories", json=category_data)
        
        assert response.status_code == 401


class TestAuthEndpoints:
    """
    Example: Test suite cho auth endpoints  
    """

    def test_signup_success(self, client: TestClient, session: Session):
        """Test đăng ký thành công."""
        
        signup_data = {
            "phone_number": "0999888777",
            "password": "strongpassword123"
        }
        
        response = client.post("/api/v1/auth/signup", json=signup_data)
        
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "User created successfully"
        
        # Verify user was created in database
        from app import crud
        user = crud.get_user_by_phone_number(session=session, phone_number="0999888777")
        assert user is not None

    def test_signup_duplicate_phone(self, client: TestClient, test_user: User):
        """Test đăng ký với số điện thoại đã tồn tại."""
        
        signup_data = {
            "phone_number": test_user.phone_number,  # Phone đã tồn tại
            "password": "password123"
        }
        
        response = client.post("/api/v1/auth/signup", json=signup_data)
        
        assert response.status_code == 400
        assert "Phone number already exists" in response.json()["detail"]

    def test_login_success(self, client: TestClient, session: Session):
        """Test login thành công."""
        
        # Create user first
        user = User(
            phone_number="0888777666",
            hashed_password=get_password_hash("loginpassword"),
            role="USER"
        )
        session.add(user)
        session.commit()
        
        login_data = {
            "phone_number": "0888777666",
            "password": "loginpassword"
        }
        
        response = client.post("/api/v1/auth/login", json=login_data)
        
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    def test_login_wrong_credentials(self, client: TestClient, test_user: User):
        """Test login với credentials sai."""
        
        login_data = {
            "phone_number": test_user.phone_number,
            "password": "wrongpassword"
        }
        
        response = client.post("/api/v1/auth/login", json=login_data)
        
        assert response.status_code == 401
        assert "Incorrect phone number or password" in response.json()["detail"]


class TestValidationExamples:
    """
    Example: Test validation cho các endpoint
    """

    def test_invalid_phone_format(self, client: TestClient):
        """Test validation số điện thoại không hợp lệ."""
        
        invalid_phones = [
            "123",           # Quá ngắn  
            "abcdefghij",    # Không phải số
            "",              # Rỗng
            " ",             # Chỉ có space
        ]
        
        for phone in invalid_phones:
            signup_data = {
                "phone_number": phone,
                "password": "password123"
            }
            
            response = client.post("/api/v1/auth/signup", json=signup_data)
            # Should fail validation
            assert response.status_code in [422, 400]

    def test_missing_required_fields(self, client: TestClient):
        """Test khi thiếu field bắt buộc."""
        
        # Missing password
        response = client.post("/api/v1/auth/signup", json={"phone_number": "0123456789"})
        assert response.status_code == 422
        
        # Missing phone_number  
        response = client.post("/api/v1/auth/signup", json={"password": "password123"})
        assert response.status_code == 422

    def test_empty_request_body(self, client: TestClient):
        """Test với request body rỗng."""
        
        response = client.post("/api/v1/auth/signup", json={})
        assert response.status_code == 422


class TestErrorHandlingExamples:
    """
    Example: Test error handling
    """

    def test_404_endpoint(self, client: TestClient):
        """Test endpoint không tồn tại."""
        
        response = client.get("/api/v1/nonexistent-endpoint")
        assert response.status_code == 404

    def test_method_not_allowed(self, client: TestClient):
        """Test method không được phép."""
        
        # GET on POST-only endpoint
        response = client.get("/api/v1/auth/signup")
        assert response.status_code == 405

    def test_malformed_json(self, client: TestClient):
        """Test JSON không hợp lệ."""
        
        # This would be handled by FastAPI automatically
        response = client.post(
            "/api/v1/auth/signup",
            data="invalid json", 
            headers={"Content-Type": "application/json"}
        )
        assert response.status_code == 422


"""
PATTERN CHUNG CHO VIỆC VIẾT TEST:

1. ARRANGE (Chuẩn bị):
   - Tạo test data cần thiết
   - Setup database state
   - Prepare request data

2. ACT (Thực hiện):
   - Gọi API endpoint
   - Execute the function being tested

3. ASSERT (Kiểm tra):
   - Verify response status code
   - Check response data
   - Verify database changes
   - Check side effects

4. NAMING CONVENTION:
   - test_[action]_[scenario]
   - Ví dụ: test_create_user_success, test_login_invalid_credentials

5. TEST CATEGORIES:
   - Happy path: Test cases thành công
   - Edge cases: Test các trường hợp biên
   - Error cases: Test error handling
   - Security: Test authentication/authorization
   - Validation: Test input validation

6. FIXTURES SỬ DỤNG:
   - client: TestClient không authentication
   - authenticated_client: TestClient đã login
   - session: Database session
   - test_user: User mẫu
   - test_user_token: Token cho authentication

7. ASSERTION HELPERS:
   - Sử dụng từ tests.utils.AssertionHelpers
   - assert response.status_code == expected_code
   - assert "expected_text" in response.json()["detail"]
"""
