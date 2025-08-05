from typing import Optional
from sqlmodel import Session, select
import uuid

from app.schemas.user import UserCreate, UserPublic, UserUpdate
from app.schemas.category import CategoryCreate
from app.schemas.order import OrderItemCreate, OrderCreate
from app.models import OrderStatus, User, UserRole, ScrapCategory, Order
from app.core.security import get_password_hash, verify_password

def authenticate(session: Session, phone_number: str, password: str) -> Optional[User]:
    db_user = get_user_by_phone_number(session=session, phone_number=phone_number)
    if not db_user:
        return None
    if not verify_password(password, db_user.hashed_password):
        return None
    return db_user

def get_user_by_phone_number(*, session: Session, phone_number: str) -> Optional[User]:
    statement = select(User).where(User.phone_number == phone_number)
    return session.exec(statement).first()

def create_user(session: Session, user_create: UserCreate) -> User:
    db_user = User.model_validate(
        user_create,
        update={"hashed_password": get_password_hash(user_create.password), "role": UserRole.USER} 
    )
    session.add(db_user)
    session.commit()
    session.refresh(db_user)
    return db_user

def get_user_by_id(session: Session, user_id: uuid.UUID) -> Optional[User]:
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

def get_category_by_slug(session: Session, slug: str) -> Optional[ScrapCategory]:
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
    statement = select(Order).where(Order.id == order_id).join(Order.items)
    return session.exec(statement).first()

def create_order(session: Session, order_create: OrderCreate, owner_id: uuid.UUID) -> Order:
    db_order = Order.model_validate(
        order_create,
        update={"owner_id": owner_id, "status": OrderStatus.PENDING}
    )
    session.add(db_order)
    session.commit()
    session.refresh(db_order)
    return db_order

def add_order_items(session: Session, order_id: uuid.UUID, order_items: list[OrderItemCreate]) -> None:
    for item in order_items:
        db_item = OrderItemCreate.model_validate(item, update={"order_id": order_id})
        session.add(db_item)
    session.commit()

def get_order_items(session: Session, order_id: uuid.UUID) -> list[OrderItemCreate]:
    statement = select(OrderItemCreate).where(OrderItemCreate.order_id == order_id)
    return session.exec(statement).all()