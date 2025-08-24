import uuid
from app import crud
from fastapi import APIRouter
from fastapi import HTTPException, Depends, status
from sqlmodel import Session

from app.core.config import settings
from app.api.deps import SessionDep, CurrentUser, CurrentCollector

from app.schemas.order import OrderCreate, OrderItemCreate, OrderPublic, OrderAcceptRequest, OrderAcceptResponse, NearbyOrderPublic

from app.models import User, Order, OrderStatus
from app import crud
from app.api.deps import get_db, get_current_active_collector
from app.schemas.transaction import OrderCompletionRequest, TransactionReadResponse
import uuid


router = APIRouter(
    tags=["Orders & Payment"]
)


from geoalchemy2.shape import from_shape
from shapely.geometry import Point

@router.post("/", status_code=status.HTTP_201_CREATED, response_model=OrderPublic)
def create_order(order: OrderCreate, current_user: CurrentUser, session: SessionDep) -> OrderPublic:
    """
    Create a new order.
    """
    point = from_shape(Point(order.pickup_longitude, order.pickup_latitude), srid=4326)
    db_order = Order(
        owner_id=current_user.id,
        pickup_address=order.pickup_address,
        location=point,
        status=OrderStatus.PENDING
    )
    session.add(db_order)
    session.commit()
    session.refresh(db_order)
    # Extract lat/lng from geometry for response
    coords = None
    if db_order.location:
        from geoalchemy2.elements import WKBElement
        from shapely import wkb
        coords = wkb.loads(bytes(db_order.location.data)).coords[0]
    return OrderPublic(
        id=db_order.id,
        owner_id=db_order.owner_id,
        collector_id=db_order.collector_id,
        status=db_order.status,
        pickup_address=db_order.pickup_address,
        pickup_latitude=coords[1] if coords else None,
        pickup_longitude=coords[0] if coords else None,
        items=[]
    )

@router.post("/{order_id}/items", response_model=OrderPublic)
def add_order_items(order_id: uuid.UUID, items: list[OrderItemCreate], current_user: CurrentUser, session: SessionDep) -> OrderPublic:
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

@router.get("/nearby", response_model=list[NearbyOrderPublic])
def list_nearby_orders(
    lat: float,
    lng: float,
    current_collector: CurrentCollector,
    session: SessionDep,
    radius_km: float = 5.0,
    limit: int = 50
):
    """List pending, unassigned orders near the collector's current location within given radius (km)."""
    # current_collector is accessed here to ensure dependency is triggered
    _ = current_collector.id
    pairs = crud.get_nearby_orders(db=session, latitude=lat, longitude=lng, radius_km=radius_km, limit=limit)
    response = []
    for order, distance in pairs:
        response.append(NearbyOrderPublic(
            id=order.id,
            owner_id=order.owner_id,
            collector_id=order.collector_id,
            status=order.status,
            pickup_address=order.pickup_address,
            pickup_latitude=order.pickup_latitude,
            pickup_longitude=order.pickup_longitude,
            items=order.items,
            distance_km=distance
        ))
    return response

@router.get("/{order_id}", response_model=OrderPublic)
def get_order(order_id: uuid.UUID, session: SessionDep) -> OrderPublic:
    """
    Get order details by ID.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    return order

@router.get("/", response_model=list[OrderPublic])
def get_orders(current_user: CurrentUser, session: SessionDep) -> list[OrderPublic]:
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
def get_order(order_id: uuid.UUID, session: SessionDep):
    """
    Get order details by ID.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    return order

@router.get("/", response_model=list[OrderPublic])
def get_orders(current_user: CurrentUser, session: SessionDep):
    """
    Get all orders for the current user.
    """
    return crud.get_orders_by_user(session=session, user_id=current_user.id)


@router.post("/{order_id}/accept", response_model=OrderAcceptResponse, status_code=status.HTTP_200_OK)
def accept_order(
    order_id: uuid.UUID,
    payload: OrderAcceptRequest,
    current_collector: CurrentCollector,
    session: SessionDep
):
    """Collector accepts (claims) an order -> status becomes ACCEPTED and collector assigned.

    Preconditions:
    - Order exists and in PENDING state.
    - Not already assigned.
    - Collector performing action matches current_collector (redundant but explicit).
    """
    order = crud.accept_order_service(db=session, order_id=order_id, collector=current_collector, note=payload.note)
    return order





@router.post(
    "/{order_id}/complete",
    response_model=TransactionReadResponse,
    status_code=status.HTTP_201_CREATED
)
def complete_order_and_pay(
    order_id: uuid.UUID,
    completion_data: OrderCompletionRequest,
    current_collector: CurrentCollector,
    session: SessionDep
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
        db=session,
        order_id=order_id,
        collector=current_collector,
        completion_data=completion_data
    )
    
    # FastAPI automatically serializes the returned 'transaction' object
    # using the `TransactionReadResponse` schema.
    return transaction