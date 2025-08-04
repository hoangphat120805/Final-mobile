# Testing Guide for Backend Mobile API

Hướng dẫn này giúp bạn hiểu và chạy test cho các endpoint trong project backend mobile API.

## 🧪 Test Structure

```
tests/
├── __init__.py
├── conftest.py              # Test configuration và fixtures
├── utils.py                 # Test utilities và helper functions
├── api/
│   └── v1/
│       └── endpoints/
│           ├── test_user.py              # Unit tests cho user endpoints
│           └── test_user_integration.py  # Integration tests cho user endpoints
```

## 📋 Prerequisites

### Cài đặt dependencies:

```powershell
# Cài đặt test dependencies
uv sync --extra test
```

### Test dependencies bao gồm:

-   **pytest**: Test framework
-   **pytest-asyncio**: Support cho async testing
-   **httpx**: HTTP client cho testing FastAPI
-   **pytest-mock**: Mocking utilities

## 🚀 Chạy Tests

### Cách 1: Sử dụng PowerShell script (Recommended cho Windows)

```powershell
# Chạy tất cả tests
.\run-tests.ps1

# Chạy với coverage
.\run-tests.ps1 -Coverage

# Chạy với verbose output
.\run-tests.ps1 -Verbose

# Chạy chỉ user tests
.\run-tests.ps1 -TestType user

# Chạy unit tests
.\run-tests.ps1 -TestType unit

# Chạy integration tests
.\run-tests.ps1 -TestType integration
```

### Cách 2: Sử dụng UV trực tiếp

```powershell
# Chạy tất cả tests
uv run pytest

# Chạy với coverage
uv run pytest --cov=app --cov-report=html --cov-report=term

# Chạy verbose
uv run pytest -v

# Chạy specific test file
uv run pytest tests/api/v1/endpoints/test_user.py -v

# Chạy specific test method
uv run pytest tests/api/v1/endpoints/test_user.py::TestUserEndpoints::test_get_me_success -v
```

### Cách 3: Sử dụng Makefile (nếu có make trên Windows)

```powershell
# Cài đặt test dependencies
make install-test

# Chạy tất cả tests
make test

# Chạy với coverage
make test-cov

# Chạy user tests
make test-user
```

## 📊 Test Coverage cho User Endpoints

### GET /user/me

✅ **test_get_me_success**: Test lấy thông tin user thành công  
✅ **test_get_me_unauthorized**: Test truy cập không có authentication

### PATCH /user/me

✅ **test_update_me_success**: Test cập nhật profile thành công  
✅ **test_update_me_email_conflict**: Test conflict khi email đã tồn tại  
✅ **test_update_me_phone_conflict**: Test conflict khi phone đã tồn tại  
✅ **test_update_me_unauthorized**: Test cập nhật không có authentication  
✅ **test_update_me_partial_update**: Test cập nhật một phần  
✅ **test_update_me_empty_data**: Test cập nhật với data rỗng

### DELETE /user/me

✅ **test_delete_me_success**: Test xóa user thành công  
✅ **test_delete_me_unauthorized**: Test xóa không có authentication

### PATCH /user/me/password

✅ **test_update_password_success**: Test đổi password thành công  
✅ **test_update_password_wrong_old_password**: Test đổi password với old password sai  
✅ **test_update_password_unauthorized**: Test đổi password không có authentication  
✅ **test_update_password_invalid_data**: Test với data không hợp lệ

## 🔍 Integration Tests

### TestUserIntegration

✅ **test_full_user_workflow**: Test workflow đầy đủ (get → update → change password → delete)  
✅ **test_concurrent_user_updates**: Test cập nhật đồng thời của nhiều user

### TestUserValidation

✅ **test_update_me_phone_number_format**: Test validation format số điện thoại  
✅ **test_password_requirements**: Test yêu cầu password  
✅ **test_large_payload_handling**: Test xử lý payload lớn

### TestUserSecurity

✅ **test_password_not_returned_in_response**: Test đảm bảo password không được trả về  
✅ **test_user_cannot_access_others_data**: Test phân quyền truy cập

## 🛠️ Test Fixtures

### Fixtures chính trong `conftest.py`:

-   **session**: Database session cho testing (in-memory SQLite)
-   **client**: FastAPI test client
-   **test_user**: User mẫu cho testing
-   **test_user_token**: Access token cho test user
-   **authenticated_client**: Client đã được authenticate
-   **another_test_user**: User thứ 2 cho conflict testing

## 🎯 Test Utilities

### File `utils.py` cung cấp:

-   **create_test_user_data()**: Tạo data user mẫu
-   **create_user_in_db()**: Tạo user trong database
-   **create_access_token_for_user()**: Tạo token cho user
-   **AssertionHelpers**: Helper class cho assertions
-   **MockData**: Data mẫu cho testing

## 📈 Expected Test Results

Khi chạy test thành công, bạn sẽ thấy:

```
================================ test session starts ================================
platform win32 -- Python 3.12.x, pytest-7.4.x, pluggy-1.x.x
testpaths: tests
collected 20 items

tests/api/v1/endpoints/test_user.py ................            [ 80%]
tests/api/v1/endpoints/test_user_integration.py ....            [100%]

================================ 20 passed in 2.50s ================================
```

## 🐛 Troubleshooting

### Lỗi thường gặp:

1. **Import errors**: Đảm bảo đã cài đặt test dependencies
2. **Database errors**: Test sử dụng in-memory SQLite, không cần setup database
3. **Authentication errors**: Fixtures tự động handle authentication
4. **Dependency injection errors**: App dependency overrides được quản lý tự động

### Debug tips:

```powershell
# Chạy test với output chi tiết hơn
uv run pytest -v -s

# Chạy specific test để debug
uv run pytest tests/api/v1/endpoints/test_user.py::TestUserEndpoints::test_get_me_success -v -s

# Xem coverage report
uv run pytest --cov=app --cov-report=html
# Mở htmlcov/index.html để xem chi tiết
```

## ✨ Best Practices

1. **Isolation**: Mỗi test độc lập, không phụ thuộc vào test khác
2. **Fixtures**: Sử dụng fixtures để setup data common
3. **Assertions**: Test cả happy path và edge cases
4. **Security**: Test authentication và authorization
5. **Coverage**: Đảm bảo coverage cao cho business logic
6. **Performance**: Test với data lớn và concurrent access

## 🔄 CI/CD Integration

Test có thể được tích hợp vào CI/CD pipeline:

```yaml
# GitHub Actions example
- name: Run Tests
  run: |
      uv sync --extra test
      uv run pytest --cov=app --cov-report=xml
```

Việc test đầy đủ giúp đảm bảo chất lượng code và phát hiện lỗi sớm trong quá trình phát triển!
