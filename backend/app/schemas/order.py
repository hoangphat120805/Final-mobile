from datetime import datetime
import uuid
from app.models import OrderStatus
from typing import Optional
from sqlmodel import SQLModel, Field
from pydantic import field_validator
from geoalchemy2.elements import WKBElement
from geoalchemy2.shape import to_shape
from shapely.geometry import mapping

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
    created_at: datetime
    updated_at: datetime

    @field_validator("location", mode="before")
    def convert_location(cls, v):
        if isinstance(v, WKBElement):
            return mapping(to_shape(v))
        return v

class OrderItemCreate(SQLModel):
    category_id: uuid.UUID
    quantity: float

class OrderItemPublic(SQLModel):
    id: uuid.UUID
    order_id: uuid.UUID
    category_id: uuid.UUID
    quantity: float
    created_at: datetime
    updated_at: datetime


class OrderAcceptRequest(SQLModel):
    note: str | None = None

class OrderAcceptResponse(SQLModel):
    id: uuid.UUID
    status: OrderStatus
    owner_id: uuid.UUID
    collector_id: uuid.UUID | None

class NearbyOrderPublic(OrderPublic):
    distance_km: float
    travel_time_seconds: Optional[float] = None 
    travel_distance_meters: Optional[float] = None 
