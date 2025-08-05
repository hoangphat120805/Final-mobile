from app import crud
from fastapi import APIRouter
from fastapi import HTTPException, Depends, status
from sqlmodel import Session

from app.core.config import settings
from app.api.deps import SessionDep, CurrentUser
from app.schemas.order import OrderCreate, OrderPublic
from app.models import User
from app import crud

router = APIRouter(prefix="/order", tags=["order"])

@router.post("/", status_code=status.HTTP_201_CREATED)
def create_order(session: SessionDep, current_user: CurrentUser, order: OrderCreate) -> OrderPublic:
    """
    Create a new order.
    """
    return crud.create_order(session=session, order_create=order, owner_id=current_user.id)
