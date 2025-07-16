from datetime import timedelta
from app.schemas.user import UserRegister
from fastapi import APIRouter, HTTPException, Query, Path, Body, Depends, status
from fastapi.security import OAuth2PasswordRequestForm
from typing import Annotated

from app.schemas.auth import Token
from app.api.deps import SessionDep, Current_user
from app.core.security import get_password_hash, create_access_token
from app.core.config import settings
from app import crud

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login/access-token")
def signin_access_token(session: SessionDep, form_data: Annotated[OAuth2PasswordRequestForm, Depends()]):
    user = crud.authenticate(session=session, username=form_data.username, password=form_data.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
        )
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return Token(
        access_token=create_access_token(user.id, expires_delta=access_token_expires),
        token_type="bearer",
        expires_in=access_token_expires
    )

@router.post("/register")
def register_user(session: SessionDep, user_create: UserRegister):
    user = crud.create_user(session=session, user_create=user_create)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User registration failed",
        )
    return user
