from datetime import timedelta
from app.services.email import verify_token
from app.models import UserRole
from fastapi import APIRouter, HTTPException, Depends, status
from fastapi.security import OAuth2PasswordRequestForm
from typing import Annotated

from app.schemas.auth import Message, Token
from app.schemas.user import UserLogin, UserPublic, UserRegister, UserCreate
from app.api.deps import SessionDep
from app.core.security import create_access_token
from app.core.config import settings
from app import crud

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/login/access-token", response_model=Token)
def signin_access_token(session: SessionDep, form_data: Annotated[OAuth2PasswordRequestForm, Depends()]):
    user = crud.authenticate(session=session, phone_number=form_data.username, password=form_data.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect phone number or password",
        )
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return Token(
        access_token=create_access_token(user.id, expires_delta=access_token_expires),
        token_type="bearer",
    )

@router.post("/login", response_model=Token)
def login(session: SessionDep, login: UserLogin):
    user = crud.authenticate(session=session, phone_number=login.phone_number, password=login.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect phone number or password",
        )
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return Token(
        access_token=create_access_token(user.id, expires_delta=access_token_expires),
        token_type="bearer",
    )

@router.post("/signup", response_model=UserPublic, status_code=status.HTTP_201_CREATED)
def signup(session: SessionDep, user_in: UserRegister):
    existing_user = crud.get_user_by_phone_number(session=session, phone_number=user_in.phone_number)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Phone number already exists",
        )
    existing_user = crud.get_user_by_email(session=session, email=user_in.email)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already exists",
        )
    if verify_token(user_in.email, user_in.register_token, purpose="register") is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired registration token",
        )
    user_create = UserCreate.model_validate(user_in, update={"role": UserRole.USER})
    user = crud.create_user(session=session, user_create=user_create)
    return user

@router.get("/keep-alive", response_model=Message)
def keep_alive():
    return {"message": "I'm alive!"}

@router.post("/collector/signup", response_model=UserPublic, status_code=status.HTTP_201_CREATED)
def signup(session: SessionDep, user_in: UserRegister):
    existing_user = crud.get_user_by_phone_number(session=session, phone_number=user_in.phone_number)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Phone number already exists",
        )
    existing_user = crud.get_user_by_email(session=session, email=user_in.email)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already exists",
        )
    if verify_token(user_in.email, user_in.register_token, purpose="register") is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired registration token",
        )
    user_create = UserCreate.model_validate(user_in, update={"role": UserRole.COLLECTOR})
    user = crud.create_user_collector(session=session, user_create=user_create)
    return user