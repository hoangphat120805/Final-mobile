import uuid
import requests
from typing import Any
from pydantic import EmailStr
from sqlmodel import select, func
from fastapi import APIRouter, HTTPException, status, File, UploadFile, Depends, Body

from app import crud
from app.models import User
from app.api.deps import CurrentUser, SessionDep, get_current_admin
from app.services.email import verify_token
from app.core.config import settings
from app.core.security import verify_password, get_password_hash
from app.schemas.user import UserUpdateMe, UpdatePassword, UserPublic, UsersPublic
from app.schemas.auth import Message
from app.schemas.notification import NotificationPublic, UserNotification

router = APIRouter(prefix="/user", tags=["user"])

@router.get(
    "/",
    dependencies=[Depends(get_current_admin)],
    response_model=UsersPublic,
)
def read_users(session: SessionDep, skip: int = 0, limit: int = 100) -> Any: # type: ignore
    """
    Retrieve users.
    """

    count_statement = select(func.count()).select_from(User)
    count = session.exec(count_statement).one()

    statement = select(User).offset(skip).limit(limit)
    users = session.exec(statement).all()

    return UsersPublic(data=users, count=count)

@router.get("/me", response_model=UserPublic)
def get_me(current_user: CurrentUser) -> Any:
    """
    Get the current authenticated user.
    """
    return current_user

@router.patch("/me", response_model=UserPublic)
def update_me(session: SessionDep, current_user: CurrentUser, user_update: UserUpdateMe) -> Any: # type: ignore
    """
    Update the current authenticated user.
    """
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
def update_password(session: SessionDep, current_user: CurrentUser, password_update: UpdatePassword) -> Any:
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

@router.post("/upload/avatar", response_model=Message)
def upload_avatar(session: SessionDep, current_user: CurrentUser, file: UploadFile = File(...)) -> Any:
    """
    Upload a new avatar image for the current authenticated user.
    """
    # Validate and process the uploaded file
    if not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file type. Please upload an image.",
        )
    
    response = requests.post(
        "https://api.imgbb.com/1/upload",
        params={
            "key": settings.IMGBB_API_KEY,
        },
        files={
            "image": file.file.read()
        }
    )
    if response.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Failed to upload image"
        )
    image_url = response.json().get("data", {}).get("url")
    crud.update_user(session, current_user, UserUpdateMe(avt_url=image_url))
    return Message(message="Avatar uploaded successfully")

@router.get("/me/notifications", response_model=list[UserNotification])
def get_notifications(
    session: SessionDep,
    current_user: CurrentUser   
):
    notifications = crud.get_user_notifications(session, current_user.id)
    return notifications

@router.post("/read/{notification_id}")
def mark_as_read(
    notification_id: uuid.UUID,
    session: SessionDep,
    current_user: CurrentUser
):
    success = crud.mark_notification_as_read(session, notification_id, current_user.id)
    if not success:
        raise HTTPException(status_code=404, detail="Notification not found")
    return {"msg": "Notification marked as read"}

@router.post("/reset-password")
def reset_password(
    session: SessionDep,
    email: EmailStr = Body(...),
    reset_token: str = Body(...),
    new_password: str = Body(...),
):
    if not verify_token(email, reset_token, purpose="reset"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired reset token")
    user = session.exec(select(User).where(User.email == email)).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    user.hashed_password = get_password_hash(new_password)
    session.add(user)
    session.commit()
    return {"message": "Password reset successful"}

@router.get("/{user_id}", response_model=UserPublic)
def get_user_by_id(
    user_id: uuid.UUID,
    session: SessionDep,
):
    user = crud.get_user(session, user_id)
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user