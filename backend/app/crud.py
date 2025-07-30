from typing import Optional
from sqlmodel import Session, select

from app.schemas.user import UserCreate
from app.models import OrderStatus, User, UserRole
from app.core.security import get_password_hash, verify_password

def get_user_by_username(*, session: Session, username: str) -> Optional[User]:
    statement = select(User).where(User.username == username)
    return session.exec(statement).first()

def get_user_by_email(*, session: Session, email: str) -> Optional[User]:
    statement = select(User).where(User.email == email)
    return session.exec(statement).first()

def authenticate(session: Session, username: str, password: str) -> Optional[User]:
    db_user = get_user_by_username(session=session, username=username)
    if not db_user:
        return None
    if not verify_password(password, db_user.hashed_password):
        return None
    return db_user

def create_user(session: Session, user_create: UserCreate) -> None:
    user_role = session.exec(select(UserRole).where(UserRole.name == "user")).first()
    db_user = User.model_validate(
        user_create,
        update={"hashed_password": get_password_hash(user_create.password), "role_id": user_role.id}
    )
    session.add(db_user)
    session.commit()
    session.refresh(db_user)
