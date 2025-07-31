from typing import Optional
from sqlmodel import Session, select

from app.schemas.user import UserCreate, UserPublic
from app.schemas.category import CategoryCreate
from app.models import OrderStatus, User, UserRole, ScrapCategory
from app.core.security import get_password_hash, verify_password

def get_user_by_phone_number(*, session: Session, phone_number: str) -> Optional[User]:
    statement = select(User).where(User.phone_number == phone_number)
    return session.exec(statement).first()

def get_user_by_email(*, session: Session, email: str) -> Optional[User]:
    statement = select(User).where(User.email == email)
    return session.exec(statement).first()

def authenticate(session: Session, phone_number: str, password: str) -> Optional[User]:
    db_user = get_user_by_phone_number(session=session, phone_number=phone_number)
    if not db_user:
        return None
    if not verify_password(password, db_user.hashed_password):
        return None
    return db_user

def create_user(session: Session, user_create: UserCreate) -> None:
    db_user = User.model_validate(
        user_create,
        update={"hashed_password": get_password_hash(user_create.password), "role": UserRole.USER} 
    )
    session.add(db_user)
    session.commit()
    session.refresh(db_user)

def create_category(session: Session, category_create: CategoryCreate, current_user: UserPublic) -> ScrapCategory:
    db_category = ScrapCategory.model_validate(
        category_create,
        update={"created_by": current_user.id, "last_updated_by": current_user.id},
    )
    session.add(db_category)
    session.commit()
    session.refresh(db_category)
    return db_category

def get_all_categories(session: Session) -> list[ScrapCategory]:
    statement = select(ScrapCategory)
    return session.exec(statement).all()
