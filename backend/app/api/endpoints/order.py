import uuid
from app import crud
from fastapi import APIRouter
from fastapi import HTTPException, Depends, status
from sqlmodel import Session

from app.core.config import settings
from app.api.deps import SessionDep, CurrentUser
from app.schemas.order import OrderCreate, OrderItemCreate, OrderPublic
from app.models import User
from app import crud

router = APIRouter(prefix="/order", tags=["order"])

@router.post("/", status_code=status.HTTP_201_CREATED, response_model=OrderPublic)
def create_order(session: SessionDep, current_user: CurrentUser, order: OrderCreate) -> OrderPublic:
    """
    Create a new order.
    """
    return crud.create_order(session=session, order_create=order, owner_id=current_user.id)

@router.post("/{order_id}/items", response_model=OrderPublic)
def add_order_items(session: SessionDep, current_user: CurrentUser, order_id: uuid.UUID, items: list[OrderItemCreate]) -> OrderPublic:
    """
    Add items to an order.
    """
    # Verify order exists and belongs to current user
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    
    # Add items to order
    for item in items:
        crud.add_order_item(session=session, order_id=order_id, item=item)
    
    # Return updated order with items
    updated_order = crud.get_order_by_id(session=session, order_id=order_id)
    return updated_order

@router.get("/{order_id}", response_model=OrderPublic)
def get_order(session: SessionDep, order_id: uuid.UUID) -> OrderPublic:
    """
    Get order details by ID.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    return order

@router.get("/", response_model=list[OrderPublic])
def get_orders(session: SessionDep, current_user: CurrentUser) -> list[OrderPublic]:
    """
    Get all orders for the current user.
    """
    return crud.get_orders_by_user(session=session, user_id=current_user.id)
