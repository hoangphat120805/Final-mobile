from datetime import datetime
import uuid
from typing import Optional
from sqlmodel import SQLModel

class CategoryPublic(SQLModel):
    id: uuid.UUID
    name: str
    slug: str
    description: Optional[str] = None
    unit: str = "kg"
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None
    created_by: uuid.UUID
    last_updated_by: uuid.UUID
    created_at: datetime
    updated_at: datetime

class CategoryCreate(SQLModel):
    name: str
    slug: str
    description: Optional[str] = None
    unit: str = "kg"
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None

class CategoryUpdate(SQLModel):
    name: Optional[str] = None
    slug: Optional[str] = None
    description: Optional[str] = None
    unit: Optional[str] = None
    icon_url: Optional[str] = None
    estimated_price_per_unit: Optional[float] = None
