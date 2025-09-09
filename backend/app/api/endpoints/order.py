

import uuid
from app import crud
from fastapi import HTTPException, Depends, status, UploadFile, APIRouter, Query
from sqlmodel import Session

from app.core.config import settings
from app.api.deps import SessionDep, CurrentUser, CurrentCollector

from app.schemas.order import OrderCreate, OrderItemCreate, OrderItemUpdate, OrderPublic, OrderAcceptRequest, OrderAcceptResponse, NearbyOrderPublic
from app.schemas.route import RoutePublic
from app.schemas.auth import Message
from app.schemas.user import UserPublic
from app.schemas.user import CollectorPublic
from app.schemas.review import ReviewCreate, ReviewPublic
from app.models import User, Order, OrderStatus
from app import crud
from app.services import mapbox
from app.api.deps import get_db, get_current_active_collector
from app.schemas.transaction import OrderCompletionRequest, TransactionReadResponse
import uuid
from typing import Annotated, List, Tuple
import requests
from geoalchemy2.shape import to_shape

import asyncio


router = APIRouter(
    tags=["Orders & Payment"]
)

@router.post("/", status_code=status.HTTP_201_CREATED, response_model=OrderPublic)
def create_order(order: OrderCreate, current_user: CurrentUser, session: SessionDep):
    """
    Create a new order. Backend will geocode pickup_address using Mapbox.
    """
    db_order = asyncio.run(crud.create_order(session=session, order_create=order, owner_id=current_user.id))
    return db_order


@router.post("/{order_id}/item", response_model=OrderPublic)
def add_order_items(
    order_id: uuid.UUID, item: OrderItemCreate, current_collector: CurrentCollector, session: SessionDep
):
    """
    Add items to an order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.collector_id != current_collector.id:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    
    order_items = crud.get_order_items(session=session, order_id=order_id)
    for existing_item in order_items:
        if existing_item.category_id == item.category_id:
            raise HTTPException(status_code=400, detail="Item already exists in order")
    
    crud.add_order_item(session=session, order_id=order_id, item=item)
    updated_order = crud.get_order_by_id(session=session, order_id=order_id)
    return updated_order

@router.patch("/{order_id}/item/{order_item_id}", response_model=OrderPublic)
def update_order_item(
    session: SessionDep,
    current_user: CurrentUser, 
    order_id: uuid.UUID, 
    order_item_id: uuid.UUID, 
    item: OrderItemUpdate, 
):
    """
    Update an item in an order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    order_item = crud.get_order_item_by_id(session=session, order_item_id=order_item_id)
    if not order_item or order_item.order_id != order_id:
        raise HTTPException(status_code=404, detail="Order item not found in this order")

    crud.update_order_item(session=session, order_item_id=order_item_id, item_update=item)
    updated_item = crud.get_order_by_id(session=session, order_id=order_id)
    return updated_item

@router.delete("/{order_id}/item/{order_item_id}", response_model=OrderPublic)
def delete_order_item(
    session: SessionDep,
    current_user: CurrentUser, 
    order_id: uuid.UUID, 
    order_item_id: uuid.UUID
):
    """
    Delete an item from an order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    order_item = crud.get_order_item_by_id(session=session, order_item_id=order_item_id)
    if not order_item or order_item.order_id != order_id:
        raise HTTPException(status_code=404, detail="Order item not found in this order")

    crud.delete_order_item(session=session, order_item_id=order_item_id)
    updated_order = crud.get_order_by_id(session=session, order_id=order_id)
    return updated_order

@router.get("/nearby", response_model=List[NearbyOrderPublic])
async def list_nearby_orders(
    lat: float,
    lng: float,
    current_collector: CurrentCollector,
    session: SessionDep,
    radius_km: float = 5.0,
    limit: int = 10
):
    """
    List pending orders, enriched with bird-fly distance and real travel info.
    """
    # 1. LẤY ỨNG CỬ VIÊN VÀ KHOẢNG CÁCH ĐƯỜNG CHIM BAY TỪ POSTGIS
    candidate_pairs: List[Tuple[Order, float]] = crud.get_nearby_pending_orders_candidates(
        db=session, latitude=lat, longitude=lng, radius_km=radius_km, limit=limit
    )

    if not candidate_pairs:
        return []

    # 2. CHUẨN BỊ DỮ LIỆU VÀ GỌI MAPBOX
    orders_only = [pair[0] for pair in candidate_pairs] # Chỉ lấy list các object Order
    origin_coords = (lng, lat)
    destination_coords = []
    for order in orders_only:
        if order.location:
            point = to_shape(order.location)   
            destination_coords.append((point.x, point.y))
    travel_info_list = await mapbox.get_travel_info_from_mapbox(
        origin=origin_coords, destinations=destination_coords
    )

    # 3. KẾT HỢP DỮ LIỆU VÀ TẠO RESPONSE OBJECTS
    response_objects = []
    for i, (order_model, distance) in enumerate(candidate_pairs):
        # Tạo một dictionary từ model object
        order_data = order_model.__dict__
        
        # Thêm các trường đã tính toán vào dictionary
        order_data['distance_km'] = distance
        if travel_info_list and i < len(travel_info_list):
            order_data['travel_time_seconds'] = travel_info_list[i]["duration"]
            order_data['travel_distance_meters'] = travel_info_list[i]["distance"]
            
        # Tạo đối tượng schema từ dictionary đã hoàn chỉnh
        # Pydantic sẽ tự động xác thực và chuyển đổi kiểu dữ liệu
        response_objects.append(NearbyOrderPublic(**order_data))

    return response_objects


@router.get("/{order_id}", response_model=OrderPublic)
def get_order(
    order_id: uuid.UUID, 
    session: SessionDep, 
    current_collector: CurrentCollector,
    include_user: bool = Query(False, description="Include owner and collector details"),
    include_collector: bool = Query(False, description="Include collector details only")
):
    """
    Get order details by ID.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.collector_id != None and order.collector_id != current_collector.id:
        raise HTTPException(status_code=403, detail="You can only view your own orders or unassigned orders")
    return order


@router.get("/", response_model=list[OrderPublic])
def get_orders(current_user: CurrentUser, session: SessionDep):
    """
    Get all orders for the current user.
    """
    orders = crud.get_orders_by_user(session=session, user_id=current_user.id)
    return orders

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


@router.get("/{order_id}/route", response_model=RoutePublic)
async def get_route_for_order(order_id: uuid.UUID, current_user: CurrentUser, session: SessionDep):
    """
    Get route information from collector's current location to the order's pickup location.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if not order.location:
        raise HTTPException(status_code=400, detail="Order does not have a valid location")
    
    if not current_user.location:
        raise HTTPException(status_code=400, detail="Collector does not have a valid location")
    
    route_info = await mapbox.get_route_from_mapbox(
        start_lon=current_user.location.x,
        start_lat=current_user.location.y,
        end_lon=order.location.x,
        end_lat=order.location.y
    )
    
    if not route_info:
        raise HTTPException(status_code=500, detail="Failed to retrieve route information")
    
    return route_info

@router.post("/{order_id}/upload/img", response_model=Message)
def upload_order_image(
    order_id: uuid.UUID,
    current_collector: CurrentCollector,
    session: SessionDep,
    file1: UploadFile,
    file2: UploadFile,
):
    """
    Upload images for an order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.collector_id != current_collector.id:
        raise HTTPException(status_code=403, detail="Not authorized to upload image for this order")

    if not file1.content_type.startswith("image/") or not file2.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file type. Please upload an image.",
        )

    response1 = requests.post(
        "https://api.imgbb.com/1/upload",
        params={
            "key": settings.IMGBB_API_KEY,
        },
        files={
            "image": file1.file.read()
        }
    )

    if response1.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Failed to upload image"
        )
    image1_url = response1.json().get("data", {}).get("url")

    response2 = requests.post(
        "https://api.imgbb.com/1/upload",
        params={
            "key": settings.IMGBB_API_KEY,
        },
        files={
            "image": file2.file.read()
        }
    )

    if response2.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Failed to upload image"
        )

    image2_url = response2.json().get("data", {}).get("url")

    crud.update_order_images(session, order_id, image1_url=image1_url, image2_url=image2_url)
    return {"message": "Images uploaded successfully"}

@router.get("/{order_id}/owner", response_model=UserPublic)
def get_order_owner(order_id: uuid.UUID, current_collector:CurrentCollector, session:SessionDep):
    """
    Collector can view info of the user who created the order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if order.collector_id != current_collector.id:
        raise HTTPException(status_code=403, detail="You can only view owners of your own orders")
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    owner = crud.get_user_by_id(session=session, user_id=order.owner_id)
    if not owner:
        raise HTTPException(status_code=404, detail="Owner not found")
    return owner

@router.get("/{order_id}/collector", response_model=CollectorPublic)
def get_order_collector(order_id: uuid.UUID, current_user:CurrentUser, session:SessionDep):
    """
    User can view info of the collector assigned to the order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="You can only view collectors for your own orders")
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if not order.collector_id:
        raise HTTPException(status_code=404, detail="No collector assigned yet")
    collector = crud.get_user_by_id(session=session, user_id=order.collector_id)
    if not collector:
        raise HTTPException(status_code=404, detail="Collector not found")
    avg_rating = crud.get_user_average_rating(session, collector.id)
    return CollectorPublic(**collector.dict(), average_rating=avg_rating)


@router.post("/{order_id}/review", response_model=ReviewPublic)
def review_collector_for_order(order_id: uuid.UUID, review: ReviewCreate, current_user:CurrentUser, session:SessionDep):
    """
    User reviews collector for an order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="You can only review orders you created")
    if not order.collector_id:
        raise HTTPException(status_code=400, detail="Order has no collector assigned")
    if order.status != OrderStatus.COMPLETED:
        raise HTTPException(status_code=400, detail="Can only review completed orders")
    # Only allow one review per order
    if order.review:
        raise HTTPException(status_code=400, detail="Order already reviewed")
    from app.models import Review
    db_review = Review(
        user_id=current_user.id,
        order_id=order_id,
        rating=review.rating,
        comment=review.comment
    )
    session.add(db_review)
    session.commit()
    session.refresh(db_review)
    return db_review

@router.get("/{order_id}/review", response_model=ReviewPublic)
def get_order_review(order_id: uuid.UUID, session:SessionDep, current_user:CurrentUser):
    """
    Get review for a specific order.
    """
    order = crud.get_order_by_id(session=session, order_id=order_id)
    if order.owner_id != current_user.id:
        raise HTTPException(status_code=403, detail="You can only view reviews for your own orders")
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if not order.review:
        raise HTTPException(status_code=404, detail="No review for this order")
    return order.review