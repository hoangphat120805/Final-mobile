import uuid
from typing import Optional, List
from datetime import datetime
from enum import Enum
from sqlmodel import SQLModel, Field, Relationship
from sqlalchemy.sql import func
from geoalchemy2 import Geometry
from sqlalchemy import Column
class UserRole(str, Enum):
    ADMIN = "admin"
    USER = "user"
    COLLECTOR = "collector"
    BUSINESS = "business"

class User(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    full_name: str | None = Field(default=None, max_length=100, nullable=True)
    phone_number: str = Field(unique=True, nullable=False, index=True, max_length=15)
    hashed_password: str = Field(nullable=False)
    role: UserRole = Field(default=UserRole.USER)
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
    addresses: List["Address"] = Relationship(back_populates="user")

class Address(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id", index=True)
    is_default: bool = Field(default=False)
    street: str = Field(max_length=255)
    city: str = Field(max_length=100)
    district: str = Field(max_length=100)
    ward: str = Field(max_length=100)
    street_address: str = Field(max_length=255)
    longitude: float = Field(ge=-180, le=180)
    latitude: float = Field(ge=-90, le=90)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    user: User = Relationship(back_populates="addresses")


class ScrapCategory(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    name: str = Field(unique=True, max_length=100)
    slug: str = Field(unique=True, max_length=100, index=True)
    description: str | None = Field(default=None, max_length=255, nullable=True)
    unit: str = Field(default="kg")
    icon_url: str | None = Field(default=None, max_length=255, nullable=True)
    estimated_price_per_unit: float = Field(ge=0)
    created_by: uuid.UUID = Field(foreign_key="user.id")
    last_updated_by: uuid.UUID = Field(foreign_key="user.id")
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
    ACCEPTED = "accepted"
    COMPLETED = "completed"
    CANCELLED = "cancelled"

class Order(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    owner_id: uuid.UUID = Field(foreign_key="user.id", index=True)
    pickup_address: str = Field(max_length=255, nullable=True)
    # Use PostGIS geometry for location
    
    location: str = Field(sa_column=Column(Geometry(geometry_type="POINT", srid=4326), nullable=True))
    collector_id: uuid.UUID | None = Field(foreign_key="user.id", nullable=True, index=True, default=None)
    status: OrderStatus
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    owner: "User" = Relationship(back_populates="orders", sa_relationship_kwargs={"foreign_keys": "Order.owner_id"})
    collector: Optional["User"] = Relationship(back_populates="received_orders", sa_relationship_kwargs={"foreign_keys": "Order.collector_id"})
    items: List["OrderItem"] = Relationship(back_populates="order")
    review: Optional["Review"] = Relationship(back_populates="order")
    
    total_amount_paid: float | None = Field(default=None)
    transaction: Optional["Transaction"] = Relationship(back_populates="order")


class OrderItem(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    order_id: uuid.UUID = Field(foreign_key="order.id", index=True)
    category_id: uuid.UUID = Field(foreign_key="scrapcategory.id", index=True)

    quantity: float
    price_per_unit: float
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    order: "Order" = Relationship(back_populates="items")
    category: "ScrapCategory" = Relationship(back_populates="listing_items")

class Review(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    user_id: uuid.UUID = Field(foreign_key="user.id", index=True)
    order_id: uuid.UUID = Field(foreign_key="order.id", index=True)
    rating: int = Field(ge=1, le=5)  
    comment: str | None = Field(default=None, max_length=500, nullable=True)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now, sa_column_kwargs={"onupdate": func.now()})

    user: "User" = Relationship(back_populates="reviews")
    order: "Order" = Relationship(back_populates="review")


class TransactionMethod(str, Enum):
    CASH = "cash"
    WALLET = "wallet"

class TransactionStatus(str, Enum):
    SUCCESSFUL = "successful"
    FAILED = "failed"
    PENDING = "pending"

class Transaction(SQLModel, table=True):
    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    order_id: uuid.UUID = Field(foreign_key="order.id", index=True, unique=True) 
    
   
    payer_id: uuid.UUID = Field(foreign_key="user.id", index=True) 
    payee_id: uuid.UUID = Field(foreign_key="user.id", index=True) 
    
    amount: float = Field(ge=0)
    method: TransactionMethod
    status: TransactionStatus
    
    transaction_date: datetime = Field(default_factory=datetime.now)

    order: "Order" = Relationship(back_populates="transaction")
    payer: "User" = Relationship(sa_relationship_kwargs={"foreign_keys": "Transaction.payer_id"})
    payee: "User" = Relationship(sa_relationship_kwargs={"foreign_keys": "Transaction.payee_id"})