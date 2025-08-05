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
    # Validate that at least one field is being updated
    update_data = user_update.dict(exclude_unset=True)
    if not update_data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No fields provided for update",
        )
    
    # Check phone number uniqueness if being updated
    if user_update.phone_number:
        existing_user = crud.get_user_by_phone_number(session=session, phone_number=user_update.phone_number)
        if existing_user and existing_user.id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Phone number already exists",
            )
    
    return crud.update_user(session=session, user=current_user, user_update=user_update)

@router.delete("/me", response_model=Message)
def delete_me(session: SessionDep, current_user: CurrentUser) -> Any:
    """
    Delete the current authenticated user.
    """
    crud.delete_user(session=session, user=current_user)
    return Message(message="User deleted successfully")

@router.patch("/me/password", response_model=Message)
def update_password(session: SessionDep, current_user: CurrentUser, password_update: UserUpdatePassword) -> Any:
    """
    Update the password of the current authenticated user.
    """
    # Additional validation: ensure old password is not the same as new password
    if password_update.old_password == password_update.new_password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="New password must be different from the old password",
        )
    
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
