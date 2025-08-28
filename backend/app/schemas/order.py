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
    items: list['OrderItemPublic'] = []

class OrderItemCreate(SQLModel):
    category_id: uuid.UUID
    quantity: float = Field(gt=0, le=10000)  # Must be positive and reasonable
    price_per_unit: float = Field(gt=0, le=1000000)  # Must be positive and reasonable

    @validator("quantity")
    def validate_quantity(cls, v):
        if v <= 0:
            raise ValueError("Quantity must be greater than 0")
        if v > 10000:
            raise ValueError("Quantity cannot exceed 10,000 units")
        # Round to 2 decimal places for consistency
        return round(v, 2)
    
    @validator("price_per_unit")
    def validate_price_per_unit(cls, v):
        if v <= 0:
            raise ValueError("Price per unit must be greater than 0")
        if v > 1000000:
            raise ValueError("Price per unit cannot exceed 1,000,000")
        # Round to 2 decimal places for currency
        return round(v, 2)

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



    