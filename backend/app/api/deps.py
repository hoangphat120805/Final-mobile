from collections.abc import Generator
from typing import Annotated,Tuple

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jwt.exceptions import InvalidTokenError
from pydantic import ValidationError
from sqlmodel import Session

from app.core import security
from app.core.config import settings
from app.core.db import engine
from app.models import User
from app.schemas.auth import TokenPayLoad
from app.schemas.user import UserPublic
from app.models import UserRole
from fastapi import WebSocket, Query

reusable_oauth2 = OAuth2PasswordBearer(tokenUrl=f"{settings.API_STR}/auth/login/access-token")

def get_db() -> Generator[Session, None, None]:
    with Session(engine) as session:
        yield session

SessionDep = Annotated[Session, Depends(get_db)]
TokenDep = Annotated[str, Depends(reusable_oauth2)]

def get_current_user(session: SessionDep, token: TokenDep) -> User:
    try: 
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[security.ALGORITHM])
        token_data = TokenPayLoad(**payload)
    except (InvalidTokenError, ValidationError):
        raise HTTPException(
            status_code = status.HTTP_403_FORBIDDEN,
            detail = "Could not validate credentials"
        )
    user = session.get(User, token_data.sub)
    if not user:
        raise HTTPException(
            status_code = status.HTTP_404_NOT_FOUND,
            detail = "User not found"
        )
    return user

CurrentUser = Annotated[UserPublic, Depends(get_current_user)]





def get_current_active_collector(
    current_user: User = Depends(get_current_user),
) -> User:
    """
    Checks if the current user is an active collector.
    
    This dependency calls get_current_user and then performs an
    additional check on the user's role.
    """
    if current_user.role != UserRole.COLLECTOR:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="The user does not have sufficient privileges. Collector role required.",
        )
    return current_user

# Create a convenient shortcut, similar to CurrentUser
CurrentCollector = Annotated[User, Depends(get_current_active_collector)]

async def get_ws_session_and_user(
    websocket: WebSocket,
    token: str = Query(...),
) -> tuple[Session, User | None]:
    """
    Một dependency duy nhất cho WebSocket:
    1. Tạo một session DB.
    2. Xác thực token và lấy user.
    3. Trả về cả session và user.
    4. Sẽ không đóng kết nối WebSocket, chỉ trả về None nếu lỗi.
    """
    db = Session(engine)
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[security.ALGORITHM])
        token_data = TokenPayLoad(**payload)
        user = db.get(User, token_data.sub)
        
        if not user:
            # Nếu user không tồn tại, đóng session và trả về None
            db.close()
            return None, None

        # Trả về cả session và user nếu thành công
        return db, user

    except (InvalidTokenError, ValidationError):
        
        db.close()
        return None, None
