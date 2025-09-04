from app.models import Review
import uuid
import httpx
from typing import Optional
from sqlmodel import Session, select

from app.core.config import settings
from app.core.security import get_password_hash, verify_password
from app.schemas.user import UserCreate, UserPublic, UserUpdate
from app.schemas.category import CategoryCreate
from app.schemas.order import OrderItemCreate, OrderCreate
from app.schemas.notification import NotificationCreate, NotificationPublic, UserNotification
from app.schemas.chat import MessageCreate
from app.models import Message, Noti_User, Notification, OrderStatus, User, UserRole, ScrapCategory, Order, OrderItem,Transaction
from math import radians, sin, cos, asin, sqrt
from geoalchemy2.functions import ST_DWithin, ST_Distance
from shapely.geometry import Point
import func

def authenticate(session: Session, phone_number: str, password: str) -> User | None:
    db_user = get_user_by_phone_number(session=session, phone_number=phone_number)
    if not db_user:
        return None
    if not verify_password(password, db_user.hashed_password):
        return None
    return db_user

def get_user_by_phone_number(*, session: Session, phone_number: str) -> User | None:
    statement = select(User).where(User.phone_number == phone_number)
    return session.exec(statement).first()

def get_user_by_email(session: Session, email: str) -> User | None:
    statement = select(User).where(User.email == email)
    return session.exec(statement).first()

def create_user(session: Session, user_create: UserCreate) -> User:
    db_user = User.model_validate(
        user_create,
        update={
            "hashed_password": get_password_hash(user_create.password),
            "avt_url": settings.DEFAULT_AVATAR_URL,
            }
    )
    session.add(db_user)
    session.flush()
    add_noti_to_new_user(session, db_user.id)
    session.commit()
    session.refresh(db_user)
    return db_user

def create_user_collector(session: Session, user_create: UserCreate) -> User:
    db_user = User.model_validate(
        user_create,
        update={"hashed_password": get_password_hash(user_create.password), "role": UserRole.COLLECTOR} 
    )
    session.add(db_user)
    session.flush()
    add_noti_to_new_user(session, db_user.id)
    session.commit()
    session.refresh(db_user)
    return db_user

def get_user_by_id(session: Session, user_id: uuid.UUID) -> User | None:
    statement = select(User).where(User.id == user_id)
    return session.exec(statement).first()

def update_user(session: Session, user: User, user_update: UserUpdate) -> User:
    user_data = user_update.dict(exclude_unset=True)
    current_user = user.sqlmodel_update(user_data)
    session.add(current_user)
    session.commit()
    session.refresh(current_user)
    return current_user

def delete_user(session: Session, user: User) -> None:
    session.delete(user)
    session.commit()

def create_category(session: Session, category_create: CategoryCreate, current_user: UserPublic) -> ScrapCategory:
    db_category = ScrapCategory.model_validate(
        category_create,
        update={"created_by": current_user.id, "last_updated_by": current_user.id},
    )
    session.add(db_category)
    session.commit()
    session.refresh(db_category)
    return db_category

def get_category_by_slug(session: Session, slug: str) -> ScrapCategory | None:
    statement = select(ScrapCategory).where(ScrapCategory.slug == slug)
    return session.exec(statement).first()

def get_all_categories(session: Session) -> list[ScrapCategory]:
    statement = select(ScrapCategory).order_by(ScrapCategory.name.asc())
    return session.exec(statement).all()

def delete_category(session: Session, category: ScrapCategory) -> None:
    session.delete(category)
    session.commit()

def update_category(session: Session, category: ScrapCategory, category_update: CategoryCreate, current_user: UserPublic) -> ScrapCategory:
    category_data = category_update.dict(exclude_unset=True)
    current_category = category.sqlmodel_update(category_data)
    current_category.last_updated_by = current_user.id
    session.add(current_category)
    session.commit()
    session.refresh(current_category)
    return current_category

def get_order_by_id(session: Session, order_id: uuid.UUID) -> Order:
    statement = select(Order).where(Order.id == order_id).join(OrderItem, isouter=True)
    return session.exec(statement).first()

def get_orders_by_user(session: Session, user_id: uuid.UUID) -> list[Order]:
    statement = select(Order).where(Order.owner_id == user_id)
    return session.exec(statement).all()

async def create_order(session: Session, order_create: OrderCreate, owner_id: uuid.UUID) -> Order:
    MAPBOX_TOKEN = settings.MAPBOX_ACCESS_TOKEN

    address = order_create.pickup_address
    geocode_url = f"https://api.mapbox.com/geocoding/v5/mapbox.places/{address}.json?access_token={MAPBOX_TOKEN}&limit=1"
    async with httpx.AsyncClient() as client:
        resp = await client.get(geocode_url)
        data = resp.json()
        if "features" not in data:
            raise ValueError(f"Mapbox API error: {data}")   
        if not data["features"]:
            raise ValueError(f"Could not find coordinates for address: {address}")
        coords = data["features"][0]["geometry"]["coordinates"]  # [lng, lat]

    # Convert coordinates to WKT for PostGIS
    point = Point(coords[0], coords[1])
    location_wkt = f'SRID=4326;{point.wkt}'



    db_order = Order.model_validate(
        order_create,
        update={
            "owner_id": owner_id,
            "status": OrderStatus.PENDING,
            "location": location_wkt,
        }
    )
    session.add(db_order)
    session.commit()
    session.refresh(db_order)
    return db_order

def add_order_item(session: Session, order_id: uuid.UUID, item: OrderItemCreate) -> None:
    db_item = OrderItem.model_validate(item, update={"order_id": order_id})
    session.add(db_item)
    session.commit()

def accept_order_service(db: Session, order_id: uuid.UUID, collector: User, note: str | None = None):
    from fastapi import HTTPException
    from fastapi import status as fas
    order = db.get(Order, order_id)
    if not order:
        raise HTTPException(status_code=fas.HTTP_404_NOT_FOUND, detail="Order not found")
    if order.status != OrderStatus.PENDING:
        raise HTTPException(status_code=fas.HTTP_400_BAD_REQUEST, detail="Order not in pending state")
    if order.collector_id and order.collector_id != collector.id:
        raise HTTPException(status_code=fas.HTTP_409_CONFLICT, detail="Order already assigned")
    order.collector_id = collector.id
    order.status = OrderStatus.ACCEPTED
    db.add(order)
    db.commit()
    db.refresh(order)
    return order

def get_nearby_pending_orders_candidates(
    db: Session, 
    latitude: float, 
    longitude: float, 
    radius_km: float = 5.0, 
    limit: int = 10
) -> list[tuple[Order, float]]:
    """
    Lấy ra một danh sách các ứng cử viên đơn hàng gần nhất.
    Chỉ dùng PostGIS, không gọi API bên ngoài.
    Trả về một tuple (Order, distance_in_km).
    """
    collector_location_wkt = f'SRID=4326;POINT({longitude} {latitude})'
    radius_meters = radius_km * 1000

    # Tính khoảng cách đường chim bay bằng ST_Distance và chia cho 1000 để ra km
    distance_expression = (func.ST_Distance(
        Order.location,
        collector_location_wkt
    ) / 1000).label("distance_km")

    query = db.query(Order, distance_expression).filter(
        Order.status == OrderStatus.PENDING,
        Order.collector_id == None, # Chỉ lấy đơn hàng chưa được gán
        ST_DWithin(Order.location, collector_location_wkt, radius_meters)
    ).order_by("distance_km").limit(limit)
    
    return query.all()





from app.schemas.transaction import OrderCompletionRequest
from fastapi import HTTPException, status
from app.models import Transaction, TransactionStatus

def complete_order_payment_service(
    db: Session,
    order_id: uuid.UUID,
    collector: User,
    completion_data: OrderCompletionRequest
):
    """
    Handles the business logic for completing and paying for an order.
    This is a critical operation and is executed within a database transaction.
    """
    try:
        # Step 1: Fetch and Lock the Order.
        # `with_for_update=True` places a row-level lock on this order,
        # preventing race conditions where two requests might process the same order simultaneously.
        order_to_complete = db.exec(
            select(Order).where(Order.id == order_id).with_for_update()
        ).first()

        # Step 2: Perform Business Rule Validations.
        if not order_to_complete:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Order not found.")
        
        if order_to_complete.collector_id != collector.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You are not authorized to complete this order.")

        if order_to_complete.status not in [OrderStatus.ACCEPTED]:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Order is in an invalid state: {order_to_complete.status}")

        # Step 3: Iterate, Update Quantities, and Calculate Total Amount.
        final_total_amount = 0
        for item_data in completion_data.items:
            order_item = db.get(OrderItem, item_data.order_item_id)
            
            if not order_item or order_item.order_id != order_id:
                raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid OrderItem ID: {item_data.order_item_id}")

            order_item.quantity = item_data.actual_quantity
            final_total_amount += order_item.price_per_unit * order_item.quantity
            db.add(order_item)

        # Step 4: Update the main Order record.
        order_to_complete.status = OrderStatus.COMPLETED
        order_to_complete.total_amount_paid = final_total_amount
        db.add(order_to_complete)
        
        # Step 5: Create the financial Transaction record.
        new_transaction = Transaction(
            order_id=order_id,
            payer_id=collector.id,
            payee_id=order_to_complete.owner_id,
            amount=final_total_amount,
            method=completion_data.payment_method,
            status=TransactionStatus.SUCCESSFUL
        )
        db.add(new_transaction)
        
        # Step 6: Commit the Transaction.
        # If all steps succeed, this saves all changes to the database atomically.
        db.commit()

        # Refresh the object to get the latest state from the DB (e.g., generated IDs, dates).
        db.refresh(new_transaction)
        
        return new_transaction
        
    except Exception as e:
        # If any error occurs, rollback all changes made during this session.
        # This ensures the database remains in a consistent state.
        db.rollback()
        raise e

def create_notification(session: Session, notification_create: NotificationCreate, user_ids: list[uuid.UUID]) -> Notification:
    db_notification = Notification.model_validate(notification_create)
    session.add(db_notification)
    session.commit()
    session.refresh(db_notification)
    for user_id in user_ids:
        noti_user = Noti_User(notification_id=db_notification.id, user_id=user_id)
        session.add(noti_user)
    session.commit()
    return db_notification

def get_all_notifications(session: Session) -> list[Notification]:
    stmt = select(Notification)
    return session.exec(stmt).all()

def get_user_notifications(session: Session, user_id: uuid.UUID) -> list[UserNotification]:
    stmt = select(
        Notification.id,
        Notification.title,
        Notification.message,
        Noti_User.is_read,
        Noti_User.created_at
    ).join(Noti_User).where(Noti_User.user_id == user_id)
    return session.exec(stmt).all()


def mark_notification_as_read(session: Session, notification_id: uuid.UUID, user_id: uuid.UUID) -> bool:
    stmt = select(Noti_User).where(
        Noti_User.notification_id == notification_id,
        Noti_User.user_id == user_id
    )
    noti_user = session.exec(stmt).first()
    if not noti_user:
        return False
    noti_user.is_read = True
    session.add(noti_user)
    session.commit()
    return True

def add_noti_to_new_user(session: Session, user_id: uuid.UUID):
    stmt = select(Notification).where(Notification.is_important == True)
    important_notifications = session.exec(stmt).all()
    for notification in important_notifications:
        noti_user = Noti_User(notification_id=notification.id, user_id=user_id)
        session.add(noti_user)

def create_message(session: Session, message: MessageCreate) -> Message:
    db_message = Message(
        sender_id=message.sender_id,
        content=message.content
    )
    session.add(db_message)
    session.commit()
    session.refresh(db_message)
    return db_message

def get_chat_history(session: Session, user_id: uuid.UUID, receiver_id: uuid.UUID):
    stmt = select(Message).where(
        ((Message.sender_id == user_id) & (Message.receiver_id == receiver_id)) |
        ((Message.sender_id == receiver_id) & (Message.receiver_id == user_id))
    ).order_by(Message.timestamp)
    return list(session.exec(stmt))

def get_user_chats(session: Session, user_id: uuid.UUID):
    stmt = select(Message).where(
        ((Message.sender_id == user_id) | (Message.receiver_id == user_id)) &
        (Message.timestamp == select(Message.timestamp).where(
            ((Message.sender_id == user_id) & (Message.receiver_id == Message.receiver_id)) |
            ((Message.sender_id == Message.receiver_id) & (Message.receiver_id == user_id))
        ).order_by(Message.timestamp.desc()).limit(1))
    )
    return list(session.exec(stmt))

def update_order_img(sesion: Session, order_id: uuid.UUID, img_url1: Optional[str] = None, img_url2: Optional[str] = None) -> Order:
    order = sesion.get(Order, order_id)
    if not order:
        raise ValueError("Order not found")
    if img_url1:
        order.img_url1 = img_url1
    if img_url2:
        order.img_url2 = img_url2
    sesion.add(order)
    sesion.commit()
    sesion.refresh(order)
    return order


def get_user_reviews(session: Session, user_id: uuid.UUID):
    return session.exec(select(Review).where(Review.user_id == user_id)).all()

def get_user_average_rating(session: Session, user_id: uuid.UUID):
    reviews = get_user_reviews(session, user_id)
    if reviews:
        return sum(r.rating for r in reviews) / len(reviews)
    return None

