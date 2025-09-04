from datetime import datetime
import uuid
from app.models import OrderStatus
from typing import Optional
from sqlmodel import SQLModel, Field
from pydantic import validator
import re

class OrderCreate(SQLModel):
    pickup_address: str = Field(min_length=10, max_length=500)

class OrderPublic(SQLModel):
    id: uuid.UUID
    owner_id: uuid.UUID
    collector_id: uuid.UUID | None
    status: OrderStatus
    pickup_address: str
    location: dict
    img_url1: str | None
    img_url2: str | None
    items: list['OrderItemPublic'] = []
    create_at: datetime
    updated_at: datetime

class OrderItemCreate(SQLModel):
    category_id: uuid.UUID
    quantity: float = Field(gt=0, le=10000)  # Must be positive and reasonable
    price_per_unit: float = Field(gt=0, le=1000000)  # Must be positive and reasonable

class OrderItemPublic(SQLModel):
    id: uuid.UUID
    order_id: uuid.UUID
    category_id: uuid.UUID
    quantity: float
    price_per_unit: float


class OrderAcceptRequest(SQLModel):
    note: str | None = None

class OrderAcceptResponse(SQLModel):
    id: uuid.UUID
    status: OrderStatus
    owner_id: uuid.UUID
    collector_id: uuid.UUID | None
    # optional echo of note later if persisted (not stored yet)

class NearbyOrderPublic(OrderPublic):
    distance_km: float
    travel_time_seconds: Optional[float] = None 
    travel_distance_meters: Optional[float] = None 
