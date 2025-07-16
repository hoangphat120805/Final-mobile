from sqlmodel import Session, select

from app.core.security import verify_password
from app.models import User

def get_user_by_username(*, session: Session, username: str) -> User | None:
    statement = select(User).where(User.username == username)
    session_user = session.exec(statement).first()
    return session_user

def authenticate(*, session: Session, username: str, password: str) -> User | None:
    db_user = get_user_by_username(session=session, username=username)
    if db_user and verify_password(password, db_user.hashed_password):
        return db_user
    return None