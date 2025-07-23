# Backend Mobile API

Dự án backend API sử dụng FastAPI, PostgreSQL và SQLModel để phục vụ ứng dụng mobile.

## 🚀 Công nghệ sử dụng

- **FastAPI** - Web framework hiện đại và nhanh chóng
- **SQLModel** - ORM dựa trên SQLAlchemy và Pydantic
- **PostgreSQL** - Cơ sở dữ liệu quan hệ
- **Alembic** - Database migration tool
- **JWT** - Authentication và authorization
- **Bcrypt** - Mã hóa password

## 📋 Yêu cầu hệ thống

- Python 3.12+
- PostgreSQL 12+
- UV (Package manager) hoặc pip

## ⚙️ Cài đặt và Chạy dự án

### 1. Clone repository

```bash
git clone <repository-url>
cd backend-mobile
```

### 2. Cài đặt dependencies

```bash
# Nếu sử dụng UV (khuyến nghị)
uv sync
```

### 3. Chạy database migrations

```bash
# Áp dụng migrations
alembic upgrade head
```

### 7. Chạy ứng dụng

#### Development mode
```bash
# Sử dụng FastAPI development server
fastapi run app/main.py --reload

Ứng dụng sẽ chạy tại: `http://localhost:8000`

## 📚 API Documentation

Sau khi chạy ứng dụng, bạn có thể truy cập:

- **Swagger UI**: `http://localhost:8000/docs`
- **ReDoc**: `http://localhost:8000/redoc`
- **OpenAPI JSON**: `http://localhost:8000/openapi.json`

## 🔧 Các lệnh hữu ích

### Database Operations
```bash
# Tạo migration mới
alembic revision --autogenerate -m "Description"

# Áp dụng migrations
alembic upgrade head

# Rollback migration
alembic downgrade -1

# Xem lịch sử migrations
alembic history
```