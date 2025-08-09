import uuid
from app import crud
from fastapi import APIRouter
from fastapi import HTTPException, Depends, status
from sqlmodel import Session

from app.core.config import settings
from app.api.deps import SessionDep, CurrentUser
from app.schemas.order import OrderCreate, OrderPublic, OrderAcceptRequest, OrderAcceptResponse
from app.schemas.order import OrderCreate, OrderItemCreate, OrderPublic
from app.models import User
from app import crud
from app.api.deps import get_db, get_current_active_collector
from app.schemas.transaction import OrderCompletionRequest, TransactionReadResponse
import uuid


router = APIRouter(
    tags=["Orders & Payment"]
)


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


@router.post("/{order_id}/accept", response_model=OrderAcceptResponse, status_code=status.HTTP_200_OK)
def accept_order(
    order_id: uuid.UUID,
    payload: OrderAcceptRequest,
    db: Session = Depends(get_db),
    current_collector: User = Depends(get_current_active_collector)
):
    """Collector accepts (claims) an order -> status becomes ACCEPTED and collector assigned.

    Preconditions:
    - Order exists and in PENDING state.
    - Not already assigned.
    - Collector performing action matches current_collector (redundant but explicit).
    """
    order = crud.accept_order_service(db=db, order_id=order_id, collector=current_collector, note=payload.note)
    return order






@router.post(
    "/{order_id}/complete",
    response_model=TransactionReadResponse,
    status_code=status.HTTP_201_CREATED
)
def complete_order_and_pay(
    order_id: uuid.UUID,
    completion_data: OrderCompletionRequest, # FastAPI validates the request body against this schema.
    db: Session = Depends(get_db), # Dependency to get a database session.
    current_collector: User = Depends(get_current_active_collector) # Dependency to get and authorize the current user.
):
    """
    API endpoint for a collector to complete an order. This action will:
    1. Update the actual quantities of all items in the order.
    2. Calculate the final total amount.
    3. Change the order status to 'COMPLETED'.
    4. Record a successful financial transaction.
    """
    
    # Call the service layer to execute the core business logic.
    transaction = crud.complete_order_payment_service(
        db=db,
        order_id=order_id,
        collector=current_collector,
        completion_data=completion_data
    )
    
    # FastAPI automatically serializes the returned 'transaction' object
    # using the `TransactionReadResponse` schema.
    return transaction