# Backend Mobile API

Backend API project using FastAPI, PostgreSQL, and SQLModel to serve mobile applications.

## 🚀 Technologies Used

- **FastAPI** - Modern and fast web framework
- **SQLModel** - ORM based on SQLAlchemy and Pydantic
- **PostgreSQL** - Relational database
- **Alembic** - Database migration tool
- **JWT** - Authentication and authorization
- **Bcrypt** - Password encryption

## 📋 System Requirements

- Python 3.12+
- PostgreSQL 12+
- UV (Package manager) or pip

## ⚙️ Installation and Running the Project

### 1. Clone repository

```bash
git clone <repository-url>
cd backend-mobile
cd backend
```

### 2. Install dependencies

```bash
# Using UV (recommended)
uv sync
```
### 3. Run database migrations

```bash
# Apply migrations
alembic upgrade head
```

### 4. Run the application

#### Development mode
```bash
# Using FastAPI development server
fastapi run app/main.py --reload

The application will run at: `http://localhost:8000`

## 📚 API Documentation

After running the application, you can access:

- **Swagger UI**: `http://localhost:8000/docs`
- **ReDoc**: `http://localhost:8000/redoc`
- **OpenAPI JSON**: `http://localhost:8000/openapi.json`

## 🔧 Useful Commands

### Database Operations
```bash
# Create new migration
alembic revision --autogenerate -m "Description"

# Apply migrations
alembic upgrade head

# Rollback migration
alembic downgrade -1

# View migration history
alembic history
```