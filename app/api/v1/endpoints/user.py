import uuid
from typing import Annotated, Any
from fastapi import APIRouter

from app.api.v1.schemas.user import UserResponse
from app.api.deps import Current_user
from app.schemas.user import UserRegister, UserUpdate, UserUpdatePassword
from app import crud

router = APIRouter(prefix="/user", tags=["user"])

@router.post("/me")
def get_me(current_user: Current_user) -> Any:
    """
    Get the current authenticated user.
    """
    return current_user
    