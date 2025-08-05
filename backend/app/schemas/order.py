from datetime import datetime
import uuid
from app.models import OrderStatus

from sqlmodel import SQLModel

class OrderCreate(SQLModel):
    pickup_address: str
    pickup_latitude: float
    pickup_longitude: float

class OrderPublic(SQLModel):
    id: uuid.UUID
    owner_id: uuid.UUID
    collector_id: uuid.UUID | None
    status: OrderStatus
    created_at: datetime
    updated_at: datetime

class OrderItemCreate(SQLModel):
    category_id: uuid.UUID
    quantity: float
    price_per_unit: float


    