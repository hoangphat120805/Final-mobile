from datetime import date
from app.models import UserRole
from sqlmodel import create_engine, Session
from app.core.config import settings
from app.schemas.user import UserCreate
from app.schemas.category import CategoryCreate
from app import crud

engine = create_engine(
    str(settings.POSTGRES_URL), 
    echo=False, 
    echo_pool=True,
    pool_pre_ping=True,
    pool_size=20,
    max_overflow=10,
    pool_timeout=30,
    pool_recycle=1800,
    )

def init_db(session: Session) -> None:
    user = crud.get_user_by_phone_number(session=session, phone_number="0123456789")
    if not user:
        admin_in = UserCreate(
            full_name="Admin",
            phone_number="0123456789",
            password="Admin@123",
            email="admin@example.com",
            role=UserRole.ADMIN
        )   
        crud.create_user(session, admin_in)
    
    admin_user = crud.get_user_by_phone_number(session=session, phone_number="0123456789")
    category_slug = "nuoc-khoang"
    category = crud.get_category_by_slug(session=session, slug=category_slug)
    if not category:
        category_in = CategoryCreate(
            name="Nước khoáng",
            slug=category_slug,
            description="Chai nước khoáng",
            unit="chai",
            icon_url="https://i.ibb.co/hJMWFQgV/pngtree-mineral-water-bottle-png-image-6577494.png",
            estimated_price_per_unit=10000
        )
        from app.schemas.user import UserPublic
        admin_public = UserPublic.model_validate(admin_user)
        crud.create_category(session, category_in, admin_public)
