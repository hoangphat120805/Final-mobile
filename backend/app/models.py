import uuid
from typing import Optional, List
from datetime import datetime
from enum import Enum
from sqlmodel import SQLModel, Field, Relationship
from sqlalchemy.sql import func

class UserRole(str, Enum):
    ADMIN = "admin"
    USER = "user"
    COLLECTOR = "collector"
    BUSINESS = "business"

class User(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    full_name: str = Field(max_length=100, nullable=False)
    phone_number: str = Field(unique=True, nullable=False)
    hashed_password: str = Field(nullable=False)
    role_id: UserRole = Field(default=UserRole.USER)
    address: str = Field(default="")
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    orders: List["Order"] = Relationship(back_populates="owner")
    received_orders: List["Order"] = Relationship(back_populates="collector")
    reviews: List["Review"] = Relationship(back_populates="user")

class ScrapCategory(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(unique=True)
    description: Optional[str] = None
    unit: str = Field(default="kg")
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    listing_items: List["OrderItem"] = Relationship(back_populates="category")

class OrderStatus(str, Enum):
    PENDING = "pending"
    COMPLETED = "completed"
    CANCELLED = "cancelled"

class Order(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    owner_id: uuid.UUID = Field(foreign_key="user.id")
    collector_id: Optional[uuid.UUID] = Field(foreign_key="user.id", nullable=True)
    total_amount: float
    status: OrderStatus = Field(default=OrderStatus.PENDING)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    owner: "User" = Relationship(back_populates="orders")
    collector: Optional["User"] = Relationship(back_populates="received_orders")
    items: List["OrderItem"] = Relationship(back_populates="order")
    review: Optional["Review"] = Relationship(back_populates="order")


class OrderItem(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    order_id: uuid.UUID = Field(foreign_key="order.id")
    category_id: uuid.UUID = Field(foreign_key="scrapcategory.id")

    quantity: float
    price_per_unit: float
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    order: "Order" = Relationship(back_populates="items")
    category: "ScrapCategory" = Relationship(back_populates="listing_items")

class Review(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id")
    order_id: uuid.UUID = Field(foreign_key="order.id")
    rating: int = Field(ge=1, le=5)  
    comment: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    user: "User" = Relationship(back_populates="reviews")
    order: "Order" = Relationship(back_populates="review")
