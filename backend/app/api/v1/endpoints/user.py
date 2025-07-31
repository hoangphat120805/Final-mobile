import uuid
from typing import Annotated, Any
from app.models import User
from fastapi import APIRouter, HTTPException, status

from app.schemas.user import UserPublic 
from app.schemas.auth import Message
from app.api.deps import CurrentUser, SessionDep
from app.schemas.user import UserCreate, UserUpdate, UserUpdatePassword
from app import crud
from app.core.security import verify_password, get_password_hash

router = APIRouter(prefix="/user", tags=["user"])

@router.get("/me", response_model=UserPublic)
def get_me(current_user: CurrentUser) -> Any:
    """
    Get the current authenticated user.
    """
    return current_user

@router.patch("/me", response_model=UserPublic)
def update_me(session: SessionDep, current_user: CurrentUser, user_update: UserUpdate) -> Any:
    """
    Update the current authenticated user.
    """
    if user_update.email:
        existing_user = crud.get_user_by_email(session=session, email=user_update.email)
        if existing_user and existing_user.id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Email already exists",
            )
    if user_update.phone_number:
        existing_user = crud.get_user_by_phone(session=session, phone=user_update.phone_number)
        if existing_user and existing_user.id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Phone number already exists",
            )
    user_data = user_update.dict(exclude_unset=True)
    current_user = current_user.sqlmodel_update(user_data)
    session.add(current_user)
    session.commit()
    session.refresh(current_user)
    return current_user

@router.delete("/me", response_model=Message)
def delete_me(session: SessionDep, current_user: CurrentUser) -> Any:
    """
    Delete the current authenticated user.
    """
    session.delete(current_user)
    session.commit()
    return Message(message="User deleted successfully")

@router.patch("/me/password", response_model=Message)
def update_password(session: SessionDep, current_user: CurrentUser, password_update: UserUpdatePassword) -> Any:
    """
    Update the password of the current authenticated user.
    """
    if not verify_password(password_update.old_password, current_user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Old password is incorrect",
        )
    
    current_user.hashed_password = get_password_hash(password_update.new_password)
    session.add(current_user)
    session.commit()
    session.refresh(current_user)
    return Message(message="Password updated successfully")
