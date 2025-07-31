from datetime import datetime
import uuid
from typing import Optional
from sqlmodel import SQLModel

class CategoryPublic(SQLModel):
    id: uuid.UUID
    name: str
    description: Optional[str] = None
    unit: str = "kg"
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None
    created_at: datetime
    updated_at: datetime

class CategoryCreate(SQLModel):
    name: str
    description: Optional[str] = None
    unit: str = "kg"
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None

