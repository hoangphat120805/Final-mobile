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
    full_name: str | None = Field(default=None, max_length=100, nullable=True)
    phone_number: str = Field(unique=True, nullable=False)
    hashed_password: str = Field(nullable=False)
    role: UserRole = Field(default=UserRole.USER)
    address: str | None = Field(default=None, max_length=255, nullable=True)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    orders: List["Order"] = Relationship(
        back_populates="owner", 
        sa_relationship_kwargs={"foreign_keys": "Order.owner_id"}
    )
    received_orders: List["Order"] = Relationship(
        back_populates="collector", 
        sa_relationship_kwargs={"foreign_keys": "Order.collector_id"}
    )
    reviews: List["Review"] = Relationship(back_populates="user")
    categories_created: List["ScrapCategory"] = Relationship(
        back_populates="created_by_user",
        sa_relationship_kwargs={"foreign_keys": "ScrapCategory.created_by"}
    )
    categories_updated: List["ScrapCategory"] = Relationship(
        back_populates="last_updated_by_user",
        sa_relationship_kwargs={"foreign_keys": "ScrapCategory.last_updated_by"}
    )


class ScrapCategory(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(unique=True)
    description: str | None = Field(default=None, max_length=255, nullable=True)
    unit: str = Field(default="kg")
    icon_url: str | None = Field(default=None, max_length=255, nullable=True)
    estimated_price_per_unit: float = Field(ge=0)
    created_by: uuid.UUID | None = Field(foreign_key="user.id")
    last_updated_by: uuid.UUID | None = Field(foreign_key="user.id")
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    listing_items: List["OrderItem"] = Relationship(back_populates="category")
    created_by_user: User = Relationship(
        back_populates="categories_created",
        sa_relationship_kwargs={"foreign_keys": "ScrapCategory.created_by"}
    )
    last_updated_by_user: User = Relationship(
        back_populates="categories_updated",
        sa_relationship_kwargs={"foreign_keys": "ScrapCategory.last_updated_by"}
    )


class OrderStatus(str, Enum):
    PENDING = "pending"
    COMPLETED = "completed"
    CANCELLED = "cancelled"

class Order(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    owner_id: uuid.UUID = Field(foreign_key="user.id")
    collector_id: uuid.UUID | None = Field(foreign_key="user.id", nullable=True)
    total_amount: float
    status: OrderStatus = Field(default=OrderStatus.PENDING)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    owner: "User" = Relationship(back_populates="orders", sa_relationship_kwargs={"foreign_keys": "Order.owner_id"})
    collector: Optional["User"] = Relationship(back_populates="received_orders", sa_relationship_kwargs={"foreign_keys": "Order.collector_id"})
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
    comment: str | None = Field(default=None, max_length=500, nullable=True)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    user: "User" = Relationship(back_populates="reviews")
    order: "Order" = Relationship(back_populates="review")
