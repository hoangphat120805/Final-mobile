from datetime import date
from app.models import UserRole
from sqlmodel import create_engine, Session
from app.core.config import settings
from app.schemas.user import UserCreate
from app import crud

engine = create_engine(
    str(settings.POSTGRES_URL), 
    echo=True, 
    echo_pool=True,
    pool_pre_ping=True,
    pool_size=20,
    max_overflow=10,
    pool_timeout=30,
    pool_recycle=1800,
    )

def init_db(session: Session) -> None:
    admin_in = UserCreate(
        full_name="Admin",
        phone_number="1234567890",
        hashed_password="admin@123",
        email="admin@example.com",
        role=UserRole.ADMIN
    )
    crud.create_user(session, admin_in)
